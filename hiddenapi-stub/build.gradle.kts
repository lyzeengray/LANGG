plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val sdkStubs = sourceSets.create("sdkStubs")

dependencies {
    compileOnly(sdkStubs.output)
}
