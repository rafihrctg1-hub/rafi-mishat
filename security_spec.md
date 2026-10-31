# Security Specification — Khandaker Veg Invoice

## 1. Data Invariants
1. **Strict Multi-Tenant Ownership**: Every document in `/users/{userId}/{collection}/{docId}` must only be created, read (`get`/`list`), updated, or deleted when `request.auth != null`, `request.auth.uid == userId`, and `resource.data.userId == request.auth.uid` (or `request.resource.data.userId == request.auth.uid` on create).
2. **Immutable Identity & Creation Metadata**: `userId` and `createdAt` can never be mutated during `update`.
3. **Orphan-Free Invoice Items**: An `invoice_items` document can only be created if its parent invoice `/users/{userId}/invoices/{invoiceId}` exists after the transaction/batch (`existsAfter(...)`), and its `invoiceId` cannot be reassigned during `update`.
4. **Strict Key & Size Bounds**: Every `create` checks `keys().hasAll(...)` and `keys().hasOnly(...)`; every `update` wraps `isValid[Entity](incoming())` and restricts `affectedKeys().hasOnly(...)`; every string has `.size() <= MAX`; every ID path segment satisfies `isValidId(id)`.
5. **Financial Integrity**: Quantities (`> 0`), rates (`>= 0`), commissions (`0..100%`), expenses (`> 0`), and invoice totals (`>= 0`) are strictly validated server-side.
6. **Temporal Integrity**: `createdAt` and `updatedAt` must be Firestore `timestamp` values with `<= request.time`.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated List Scrape**: Unauthenticated client queries `/users/alice/invoices`. Rejected (`request.auth != null`).
2. **Cross-Tenant PII Theft**: Authenticated `bob` calls `get` on `/users/alice/customers/cust_1` (phone/address PII). Rejected (`request.auth.uid == userId`).
3. **Owner Spoofing on Create**: `alice` writes `/users/alice/products/prod_1` with `{"userId": "bob", ...}`. Rejected (`incoming().userId == request.auth.uid`).
4. **Shadow/Ghost Field Injection**: `alice` updates product with `{"isAdmin": true}` or creates invoice with extra key `{"bypassCommission": true}`. Rejected (`hasOnly` allowlist).
5. **Immutable Timestamp Rewrite**: `alice` updates an invoice and alters `createdAt`. Rejected (`incoming().createdAt == existing().createdAt`).
6. **Negative Quantity/Rate Poisoning**: `alice` creates an invoice item with `quantity: -50` or `rate: -30`. Rejected (`quantity > 0 && rate >= 0`).
7. **Negative Expense Poisoning**: `alice` creates an expense with `amount: -500` or `amount: 0`. Rejected (`amount > 0`).
8. **Orphaned Invoice Item**: `alice` creates `/users/alice/invoice_items/item_1` pointing to non-existent `invoiceId: "ghost_inv"`. Rejected (`existsAfter(...)`).
9. **Denial-of-Wallet String Bomb**: `alice` submits a 50KB `notes` string or 2KB `productId`. Rejected (`isValidId` <= 128 chars, `notes.size() <= 500`).
10. **Unfiltered Query Scrape**: `alice` queries collection without `where("userId", "==", aliceUid)` if data had mismatched `userId`. Rejected (`resource.data.userId == request.auth.uid`).
11. **Value Type Poisoning on Update**: `alice` updates `defaultRate` on a product with `"thirty"` (string instead of number). Rejected (`isValidProduct(incoming())`).
12. **Future Clock-Skew Timestamp Injection**: `alice` sets `createdAt` 1 hour in the future. Rejected (`createdAt <= request.time`).

## 3. Red Team Audit Summary
- **Shadow Update Test**: PASS — all 6 collections enforce `keys().hasOnly(...)` on create and `diff(existing()).affectedKeys().hasOnly(...)` on update.
- **PII Blanket Test**: PASS — `customers`, `invoices`, and `settings` restrict `get` and `list` strictly to `request.auth.uid == userId && resource.data.userId == request.auth.uid`.
- **Query Trust Test**: PASS — every `allow list` verifies `resource.data.userId == request.auth.uid` without `get()`/`exists()` inside `list`.
- **Value Poisoning Test**: PASS — every `allow update` begins with `isSignedIn() && request.auth.uid == userId && isValidId(...) && isValid[Entity](incoming())`.
