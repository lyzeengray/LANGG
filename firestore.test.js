const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const crypto = require("node:crypto");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-lvngspac-fcef08f1-9838-4f53-b718-ab7bbff23ad0";
const OWNER_UID = "owner_hp1_uid";
const PHONE2_UID = "player_hp2_uid";
const PHONE3_UID = "player_hp3_uid";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function sha256(str) {
  return crypto.createHash("sha256").update(str).digest("hex");
}

function dbFor(uid) {
  return testEnv.authenticatedContext(uid).firestore({ databaseId: DATABASE_ID });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot access owners or accounts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore({ databaseId: DATABASE_ID });
  await assertFails(unauthDb.collection("owners").doc(OWNER_UID).get());
  await assertFails(unauthDb.collection("accounts").doc("vipuser").get());
  await assertFails(unauthDb.collection("accounts").get());
});

test("Owner registration succeeds only with code 100214 and fails with wrong code", async () => {
  const ownerDb = dbFor(OWNER_UID);
  const now = new Date(Date.now() - 1000);

  await assertFails(
    ownerDb.collection("owners").doc(OWNER_UID).set({
      ownerUid: OWNER_UID,
      ownerCode: "999999",
      createdAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection("owners").doc(OWNER_UID).set({
      ownerUid: OWNER_UID,
      ownerCode: "100214",
      createdAt: now,
    })
  );

  // Cannot create or read another user's owner doc
  const otherDb = dbFor(PHONE2_UID);
  await assertFails(otherDb.collection("owners").doc(OWNER_UID).get());
  await assertFails(
    otherDb.collection("owners").doc("someone_else").set({
      ownerUid: PHONE2_UID,
      ownerCode: "100214",
      createdAt: now,
    })
  );
});

test("Only verified Owner can create and list accounts; non-owners are blocked", async () => {
  const ownerDb = dbFor(OWNER_UID);
  const nonOwnerDb = dbFor(PHONE2_UID);
  const now = new Date(Date.now() - 5000);

  const accountPayload = {
    username: "PlayerOne",
    passwordHash: sha256("pass1234"),
    createdByUid: OWNER_UID,
    durationType: "DAYS_7",
    durationDays: 7,
    expiresAtMillis: Date.now() + 7 * 86400000,
    boundDeviceId: "",
    boundDeviceName: "",
    boundByUid: "",
    createdAt: now,
    updatedAt: now,
  };

  // Before owner registers, create fails
  await assertFails(ownerDb.collection("accounts").doc("playerone").set(accountPayload));

  // Register owner
  await assertSucceeds(
    ownerDb.collection("owners").doc(OWNER_UID).set({
      ownerUid: OWNER_UID,
      ownerCode: "100214",
      createdAt: now,
    })
  );

  // Non-owner cannot create account even spoofing createdByUid
  await assertFails(
    nonOwnerDb.collection("accounts").doc("playerone").set({
      ...accountPayload,
      createdByUid: PHONE2_UID,
    })
  );

  // Ghost field attack rejected
  await assertFails(
    ownerDb.collection("accounts").doc("playerone").set({
      ...accountPayload,
      isAdmin: true,
    })
  );

  // Valid owner account creation succeeds
  await assertSucceeds(
    ownerDb.collection("accounts").doc("playerone").set(accountPayload)
  );

  // Scoped owner list query succeeds; unfiltered list fails
  await assertSucceeds(
    ownerDb.collection("accounts").where("createdByUid", "==", OWNER_UID).get()
  );
  await assertFails(ownerDb.collection("accounts").get());
  await assertFails(
    nonOwnerDb.collection("accounts").where("createdByUid", "==", OWNER_UID).get()
  );
});

test("Phone 2 locks unbound account on first login; Phone 3 cannot hijack or overwrite lock", async () => {
  const ownerDb = dbFor(OWNER_UID);
  const hp2Db = dbFor(PHONE2_UID);
  const hp3Db = dbFor(PHONE3_UID);
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    ownerDb.collection("owners").doc(OWNER_UID).set({
      ownerUid: OWNER_UID,
      ownerCode: "100214",
      createdAt: created,
    })
  );

  await assertSucceeds(
    ownerDb.collection("accounts").doc("vip_user").set({
      username: "VIP_User",
      passwordHash: sha256("secret99"),
      createdByUid: OWNER_UID,
      durationType: "LIFETIME",
      durationDays: -1,
      expiresAtMillis: -1,
      boundDeviceId: "",
      boundDeviceName: "",
      boundByUid: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Phone 2 reads exact account doc by ID
  await assertSucceeds(hp2Db.collection("accounts").doc("vip_user").get());

  // Phone 2 cannot tamper with expiresAtMillis or passwordHash while claiming
  await assertFails(
    hp2Db.collection("accounts").doc("vip_user").update({
      boundDeviceId: "device-hp2-uuid",
      boundDeviceName: "Poco F5",
      boundByUid: PHONE2_UID,
      expiresAtMillis: Date.now() + 999999999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Phone 2 atomic device lock claim succeeds
  await assertSucceeds(
    hp2Db.collection("accounts").doc("vip_user").update({
      boundDeviceId: "device-hp2-uuid",
      boundDeviceName: "Poco F5",
      boundByUid: PHONE2_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Phone 3 cannot overwrite boundDeviceId once locked!
  await assertFails(
    hp3Db.collection("accounts").doc("vip_user").update({
      boundDeviceId: "device-hp3-uuid",
      boundDeviceName: "ROG Phone",
      boundByUid: PHONE3_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Phone 2 & Phone 3 cannot delete the account; only Owner can delete
  await assertFails(hp2Db.collection("accounts").doc("vip_user").delete());
  await assertFails(hp3Db.collection("accounts").doc("vip_user").delete());
  await assertSucceeds(ownerDb.collection("accounts").doc("vip_user").delete());
});
