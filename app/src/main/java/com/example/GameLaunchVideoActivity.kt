package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.overlay.LvngRuntimeBus
import com.example.ui.theme.LvngBorderSubtle
import com.example.ui.theme.LvngSpaceTheme
import com.example.ui.theme.LvngTextPrimary
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

class GameLaunchVideoActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_GAME_PACKAGE = "extra_game_package"
        private const val EXTRA_GAME_NAME = "extra_game_name"
        private const val SAFETY_TIMEOUT_MS = 15_000L

        fun launch(context: Context, packageName: String, gameName: String) {
            val intent = Intent(context, GameLaunchVideoActivity::class.java).apply {
                putExtra(EXTRA_GAME_PACKAGE, packageName)
                putExtra(EXTRA_GAME_NAME, gameName)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }

    private val launchedTarget = AtomicBoolean(false)
    private var mediaPlayer: MediaPlayer? = null
    private var videoSurface: Surface? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var videoWidth: Int = 848
    private var videoHeight: Int = 480

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        LvngRuntimeBus.update { it.copy(isLaunchVideoPlaying = true) }

        val targetPackage = intent.getStringExtra(EXTRA_GAME_PACKAGE).orEmpty()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        setContent {
            LvngSpaceTheme {
                BackHandler {
                    launchTargetGameAndFinish(targetPackage)
                }
                LaunchedEffect(Unit) {
                    delay(SAFETY_TIMEOUT_MS)
                    launchTargetGameAndFinish(targetPackage)
                }
                GameLaunchVideoScreen(
                    onTextureReady = { textureView, surfaceTexture ->
                        startVideoPlayback(textureView, surfaceTexture, targetPackage)
                    },
                    onTextureSizeChanged = { textureView, w, h ->
                        applyAspectFitTransform(textureView, w, h, videoWidth, videoHeight)
                    },
                    onSkip = {
                        launchTargetGameAndFinish(targetPackage)
                    }
                )
            }
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    @Suppress("DEPRECATION")
    private fun requestPlaybackAudioFocus() {
        val am = audioManager ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = req
            runCatching { am.requestAudioFocus(req) }
        } else {
            runCatching {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonPlaybackAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { req ->
                runCatching { am.abandonAudioFocusRequest(req) }
            }
            audioFocusRequest = null
        } else {
            runCatching { am.abandonAudioFocus(null) }
        }
    }

    private fun applyAspectFitTransform(
        textureView: TextureView,
        viewWidth: Int,
        viewHeight: Int,
        vidW: Int,
        vidH: Int
    ) {
        if (viewWidth <= 0 || viewHeight <= 0 || vidW <= 0 || vidH <= 0) return
        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
        val videoRatio = vidW.toFloat() / vidH.toFloat()

        val scaleX: Float
        val scaleY: Float
        if (viewRatio > videoRatio) {
            scaleX = videoRatio / viewRatio
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = viewRatio / videoRatio
        }

        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        }
        textureView.setTransform(matrix)
    }

    private fun startVideoPlayback(
        textureView: TextureView,
        surfaceTexture: SurfaceTexture,
        targetPackage: String
    ) {
        if (launchedTarget.get()) return
        if (mediaPlayer != null) {
            // Re-attach existing player if surface was recreated during orientation change
            val newSurface = Surface(surfaceTexture)
            videoSurface?.release()
            videoSurface = newSurface
            runCatching { mediaPlayer?.setSurface(newSurface) }
            applyAspectFitTransform(textureView, textureView.width, textureView.height, videoWidth, videoHeight)
            return
        }

        requestPlaybackAudioFocus()
        val surface = Surface(surfaceTexture)
        videoSurface = surface

        var retriedUri = false

        fun configureAndPrepare(useUriFallback: Boolean) {
            runCatching {
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    setSurface(surface)
                    if (useUriFallback) {
                        val uri = Uri.parse("android.resource://$packageName/${R.raw.lvng_launch_video}")
                        setDataSource(this@GameLaunchVideoActivity, uri)
                    } else {
                        resources.openRawResourceFd(R.raw.lvng_launch_video).use { afd ->
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        }
                    }
                    setVolume(1.0f, 1.0f)
                    setOnVideoSizeChangedListener { _, width, height ->
                        if (width > 0 && height > 0) {
                            this@GameLaunchVideoActivity.videoWidth = width
                            this@GameLaunchVideoActivity.videoHeight = height
                            textureView.post {
                                applyAspectFitTransform(
                                    textureView,
                                    textureView.width,
                                    textureView.height,
                                    width,
                                    height
                                )
                            }
                        }
                    }
                    setOnPreparedListener { player ->
                        applyAspectFitTransform(
                            textureView,
                            textureView.width,
                            textureView.height,
                            this@GameLaunchVideoActivity.videoWidth,
                            this@GameLaunchVideoActivity.videoHeight
                        )
                        player.setVolume(1.0f, 1.0f)
                        player.start()
                    }
                    setOnCompletionListener {
                        launchTargetGameAndFinish(targetPackage)
                    }
                    setOnErrorListener { failedPlayer, _, _ ->
                        runCatching { failedPlayer.release() }
                        mediaPlayer = null
                        if (!retriedUri) {
                            retriedUri = true
                            configureAndPrepare(useUriFallback = true)
                        } else {
                            launchTargetGameAndFinish(targetPackage)
                        }
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = mp
            }.onFailure {
                if (!retriedUri) {
                    retriedUri = true
                    configureAndPrepare(useUriFallback = true)
                } else {
                    launchTargetGameAndFinish(targetPackage)
                }
            }
        }

        configureAndPrepare(useUriFallback = false)
    }

    private fun releasePlayer() {
        runCatching {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        }
        mediaPlayer = null
        runCatching { videoSurface?.release() }
        videoSurface = null
        abandonPlaybackAudioFocus()
    }

    private fun launchTargetGameAndFinish(targetPackage: String) {
        if (!launchedTarget.compareAndSet(false, true)) return
        LvngRuntimeBus.update { it.copy(isLaunchVideoPlaying = false) }
        releasePlayer()
        if (targetPackage.isNotBlank()) {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                runCatching { startActivity(launchIntent) }
            }
        }
        finish()
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        } else {
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        runCatching {
            mediaPlayer?.let { player ->
                if (!player.isPlaying && !launchedTarget.get()) {
                    player.start()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LvngRuntimeBus.update { it.copy(isLaunchVideoPlaying = false) }
        releasePlayer()
    }
}

@Composable
private fun GameLaunchVideoScreen(
    onTextureReady: (TextureView, SurfaceTexture) -> Unit,
    onTextureSizeChanged: (TextureView, Int, Int) -> Unit,
    onSkip: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    val textureView = TextureView(ctx).apply {
                        keepScreenOn = true
                        isOpaque = true
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                onTextureReady(this@apply, surface)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                onTextureSizeChanged(this@apply, width, height)
                            }

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                        }
                    }
                    addView(
                        textureView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xAA101216))
                .border(1.dp, LvngBorderSubtle, RoundedCornerShape(20.dp))
                .clickable(onClick = onSkip)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .testTag("launch_video_skip_button")
        ) {
            Text(
                text = stringResource(id = R.string.launch_video_skip),
                style = MaterialTheme.typography.labelLarge,
                color = LvngTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
