const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

function serverTs() {
  return new Date(Date.now() - 500);
}

test("Unauthenticated user: cannot read products, customers, invoices, or expenses", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("customers").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("invoices").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("expenses").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("settings").doc("business_config").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("business_config").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("settings").doc("business_config").get());
});

test("Cross-user isolation: Bob cannot read or modify Alice's private records", async () => {
  const aliceCtx = testEnv.authenticatedContext(ALICE_UID);
  const aliceDb = aliceCtx.firestore();
  const ts = serverTs(aliceDb);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").set({
      userId: ALICE_UID,
      name: "করিম মিয়া",
      phone: "01711000000",
      address: "রিয়াজউদ্দিন বাজার, চট্টগ্রাম",
      notes: "",
      isActive: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").delete());
});

test("Shadow Update & Ghost Field rejection: extra fields rejected on create and update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_ghost").set({
      userId: ALICE_UID,
      nameBn: "আলু",
      nameEn: "Potato",
      iconKey: "POTATO",
      defaultUnit: "কেজি",
      defaultRate: 30,
      notes: "",
      isActive: true,
      isVerified: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Atomic Invoice + Multi-Rate Same-Product InvoiceItems batch succeeds and Orphaned Item fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  // Orphaned item without parent invoice must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoice_items").doc("orphan_item").set({
      userId: ALICE_UID,
      invoiceId: "non_existent_inv",
      productId: "prod_1",
      productName: "আলু",
      quantity: 50,
      unit: "কেজি",
      rate: 30,
      lineTotal: 1500,
      sortOrder: 0,
      invoiceDateMillis: 1730000000000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Atomic batch creating Invoice + 2 separate আলু lines at different rates must succeed
  const batch = aliceDb.batch();
  const invRef = aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1001");
  const item1Ref = aliceDb.collection("users").doc(ALICE_UID).collection("invoice_items").doc("item_1");
  const item2Ref = aliceDb.collection("users").doc(ALICE_UID).collection("invoice_items").doc("item_2");

  batch.set(invRef, {
    userId: ALICE_UID,
    invoiceNumber: "KVT-2026-0001",
    customerId: "",
    customerName: "নগদ ক্রেতা",
    customerPhone: "",
    deliveryAddress: "",
    invoiceDateMillis: 1730000000000,
    itemCount: 2,
    totalQuantity: 70,
    productSubtotal: 2140,
    commissionEnabled: true,
    commissionRate: 7,
    commissionAmount: 149.8,
    additionalCharges: 0,
    discount: 0,
    finalAmount: 2289.8,
    notes: "",
    status: "SAVED",
    businessNameSnapshot: "মেসার্স কর্ণফুলী ট্রেডার্স",
    businessAddressSnapshot: "",
    businessPhoneSnapshot: "",
    createdAt: ts,
    updatedAt: ts,
  });

  batch.set(item1Ref, {
    userId: ALICE_UID,
    invoiceId: "inv_1001",
    productId: "prod_1",
    productName: "আলু",
    quantity: 50,
    unit: "কেজি",
    rate: 30,
    lineTotal: 1500,
    sortOrder: 0,
    invoiceDateMillis: 1730000000000,
    createdAt: ts,
    updatedAt: ts,
  });

  batch.set(item2Ref, {
    userId: ALICE_UID,
    invoiceId: "inv_1001",
    productId: "prod_1",
    productName: "আলু",
    quantity: 20,
    unit: "কেজি",
    rate: 32,
    lineTotal: 640,
    sortOrder: 1,
    invoiceDateMillis: 1730000000000,
    createdAt: ts,
    updatedAt: ts,
  });

  await assertSucceeds(batch.commit());

  // Query alignment test
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoices").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoice_items").where("userId", "==", ALICE_UID).get()
  );
});

test("Negative Expense and Invalid Category validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  // Negative amount rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_bad").set({
      userId: ALICE_UID,
      expenseDateMillis: 1730000000000,
      category: "LABOR",
      description: "খামালি খরচ",
      amount: -100,
      relatedInvoiceId: "",
      relatedInvoiceNumber: "",
      notes: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid manual expense succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_ok").set({
      userId: ALICE_UID,
      expenseDateMillis: 1730000000000,
      category: "LABOR",
      description: "খামালি খরচ",
      amount: 350,
      relatedInvoiceId: "",
      relatedInvoiceNumber: "",
      notes: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
