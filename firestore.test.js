const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated users cannot read or write any Jarvis VIP data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      title: "Hack",
      category: "task",
      completed: false,
      priority: "normal",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, update, list, and delete UserProfile, Tasks, ChatMessages, and ActionLogs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Aarav",
      email: "aarav@example.com",
      preferredLanguage: "bilingual",
      voiceEnabled: true,
      handsFreeWakeWord: false,
      backendBaseUrl: "https://jarvis-proxy.example.com",
      openAiModel: "gpt-4o-mini",
      accessibilityConsentGranted: false,
      notificationListenerConsentGranted: false,
      approvedPreferencesSummary: "Prefers concise Hindi+English replies.",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "hi",
      updatedAt: now,
    })
  );

  // Tasks subcollection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      title: "Call Mom at 6 PM",
      notes: "Birthday wishes",
      category: "birthday",
      dueDateLabel: "2026-11-18",
      dueTimeLabel: "18:00",
      completed: false,
      priority: "high",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      completed: true,
      updatedAt: now,
    })
  );

  // Chat messages subcollection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_1").set({
      role: "assistant",
      content: "नमस्ते! मैं जार्विस हूँ।",
      languageTag: "hi",
      actionSummary: "Greeting initialized",
      actionStatus: "executed",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Action logs subcollection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("actionLogs").doc("log_1").set({
      commandText: "Check battery status",
      actionType: "BATTERY_STATUS",
      targetSummary: "Device Telemetry",
      status: "executed",
      detailMessage: "85% charging",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").delete()
  );
});

test("Rejects cross-user reads/writes, ghost fields, immutable createdAt edits, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").set({
      title: "Private Task",
      category: "task",
      completed: false,
      priority: "normal",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field & @DocumentId `id` field rejection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("bad_task").set({
      id: "bad_task",
      title: "Bad",
      category: "task",
      completed: false,
      priority: "normal",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Immutable createdAt rejection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp rejection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("future_task").set({
      title: "Future",
      category: "task",
      completed: false,
      priority: "normal",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});
