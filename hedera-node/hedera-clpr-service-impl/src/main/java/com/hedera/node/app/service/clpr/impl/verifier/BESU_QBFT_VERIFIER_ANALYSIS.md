# Besu QBFT Native Verifier: Analysis

This document reviews the native Besu QBFT verifier in Hiero consensus node against:

- the QBFT verifier spec in [clpr-spec PR #78](https://github.com/swirldslabs/clpr-spec/pull/78) (`verifiers/qbft/*`, branch `21-besu-verifier-spec`);
- the Besu client source ([hyperledger/besu](https://github.com/hyperledger/besu), `main`).

Reviewed at commit `cd437caf7b`. Line numbers below refer to that commit.

## Scope

|              Component              |                                                                                    Path                                                                                     |
|-------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Verifier                            | `hedera-node/hedera-clpr-service-impl/src/main/java/com/hedera/node/app/service/clpr/impl/verifier/BesuQbftVerifier.java`                                                   |
| `verifyBundle` system contract call | `hedera-node/hedera-smart-contract-service-impl/src/main/java/com/hedera/node/app/service/contract/impl/exec/systemcontracts/besuqbft/verify/BesuQBFTVerifyBundleCall.java` |
| `verifyBundle` translator           | `.../systemcontracts/besuqbft/verify/BesuQBFTVerifyBundleTranslator.java`                                                                                                   |
| `verifyConfig` system contract call | `.../systemcontracts/besuqbft/verify/BesuQBFTVerifyConfigCall.java`                                                                                                         |
| Bundle handler (consumer)           | `hedera-node/hedera-clpr-service-impl/src/main/java/com/hedera/node/app/service/clpr/impl/handlers/ClprSubmitBundleHandler.java`                                            |
| Reference for comparison            | `.../systemcontracts/ethereum/verify/EthereumVerifyBundleCall.java`                                                                                                         |

Short names used below: **Verifier**, **BundleCall**, **Translator**, **ConfigCall**, **Handler**, **EthCall**.

## Summary

The native verifier implements an older design than the spec (an "epoch header" walk with a full validator list as the trust anchor). Its cryptographic core (seal digest, `ecrecover`, MPT proofs) is sound. The main problems are:

1. **Critical:** the system contract returns queue metadata and the new trust anchor from relay-supplied content, not from the proof. A relay can inject messages and take over a channel.
2. **High (liveness):** the quorum formula and the hop rules do not match Besu. Networks with 3, 6, 9… validators, and any validator rotation, are likely to be rejected.
3. **Missing features:** contract-mode validator selection, a replay floor, block-number anchor ids and channel binding.
4. **Gas:** a flat price with no limits on seals, hops or payload size.
5. **Beyond the spec:** the shared RLP decoder can be pushed into a `StackOverflowError` or very large allocations, which escape error handling (5.1); and `completeChannel`, both native and Solidity, does not bind the verifier or the config proof to the owner's signature, so channel setup can be front-run (6.1).

Section 7 summarises a review of the Solidity verifiers in `clpr-smart-contracts`. Their main issue is that header-mode hops can be forged and chained by one key fewer than Besu's quorum (7.1).

Section 9 compares both implementations with the QBFT protocol itself (formal spec, paper, Besu). The per-block finality check is right, apart from native's quorum formula. Header-mode validator changes need vote evidence to be correct by QBFT's rules, while contract mode is sound by construction. Neither implementation has a trusting period or a misbehaviour freeze.

Section 10 covers the relay and test coverage. The relay sends epoch-boundary headers instead of detecting real changes, and an anchor-id length mismatch means the native verifier never receives rotations. No test, unit or end-to-end, runs a multi-validator Besu network or a rotation.

|  #  |                                      Finding                                       |        Severity         |
|-----|------------------------------------------------------------------------------------|-------------------------|
| 1.1 | Queue metadata taken from relay content, not the proof                             | Critical                |
| 1.2 | Relay can install an arbitrary trust anchor                                        | Critical                |
| 1.3 | `channelContext` dropped, so slots are not bound to the channel                    | High                    |
| 2.1 | Quorum is `floor(2n/3)+1` instead of Besu's `ceil(2n/3)`                           | High                    |
| 2.2 | Hop (rotation) rules do not match Besu's sealing semantics                         | High                    |
| 2.3 | Contract mode not supported                                                        | Medium (scope decision) |
| 3.1 | No replay floor for hops or the current block                                      | Medium                  |
| 3.2 | Trust anchor id is an epoch number, not a block number                             | Medium                  |
| 3.3 | No "at most one change per hop" rule, no new-set quorum                            | Low                     |
| 3.4 | Partial bundles (6th slot) proven but never used                                   | Low                     |
| 3.5 | Header field count capped at 23                                                    | Low                     |
| 3.6 | `verifyConfig` needs a separate genesis header                                     | Low                     |
| 4.1 | Flat gas price, no limits on seals, hops or payload size                           | Medium                  |
| 4.2 | `info` logs of attacker-controlled data on every call                              | Low                     |
| 5.1 | RLP decoder: unbounded recursion and quadratic copying; `Error`s escape            | High                    |
| 5.2 | Besu genesis `validators` override cannot be followed by any verifier              | Medium                  |
| 5.3 | Trust anchor can silently lag the real validator set                               | Medium                  |
| 5.4 | `LibSecp256k1` used directly, no fallback                                          | Low                     |
| 5.5 | Empty storage proof plus non-empty content accepted                                | Low                     |
| 5.6 | Older-state bundles accepted                                                       | Info                    |
| 5.7 | No minimum validator count                                                         | Info                    |
| 5.8 | Declared `verifyBundle` output type does not match the encoded return              | Info                    |
| 6.1 | `completeChannel` signature does not bind verifier or proofs (native and Solidity) | Critical                |
| 7.x | Solidity verifier findings (separate list)                                         | up to High              |
| 9.x | QBFT conformance: header-mode vote evidence, trusting period, misbehaviour freeze  | High                    |
| 10.1 | Relay uses epoch checkpoints; anchor-id length mismatch blocks native rotations  | High                    |
| 10.2 | No unit or end-to-end coverage of multi-validator networks or rotation            | High                    |

---

## 1. Security

### 1.1 Queue metadata is taken from relay content, not the proof (Critical)

The verifier proves the channel slots and builds a `QueueMetadata` from them. BundleCall then throws that away, except for `endpointManifestVersion`:

- `BundleCall:215`: `final ClprQueueMetadata meta = outContent.metadataOrElse(ClprQueueMetadata.DEFAULT);` — `outContent` is the relay's protobuf (bundle field 4).
- `Handler:488`: the running hash of the new messages is compared with `metadata.sentRunningHash()`, which is the relay's value.

**Attack.** A relay takes any validly sealed recent header and valid proofs for any slots. It makes up messages, computes their running hash, and puts that hash and a matching `nextMessageId` into the content metadata. Every check passes and the made-up messages are delivered.

**Reference.** EthCall rejects the bundle unless the relayed metadata equals the proven one: `relayedMetadataMatchesProven(...)` at `EthCall:101` (body at `EthCall:187`).

**Fix.** Do the same in BundleCall: compare `nextMessageId`, `receivedMessageId`, `status`, `sentRunningHash` and `receivedRunningHash` with `verified.queueMetadata()`, or build the returned metadata from the proven values only.

### 1.2 A relay can install an arbitrary trust anchor (Critical)

When the bundle has no epoch headers, the relay's content passes through unchanged:

- `BundleCall:185`: `finalBundleContentBytes = bundleContentBytes;`
- `BundleCall:224`: `final byte[] newTrustAnchor = outContent.newTrustAnchor().toByteArray();`
- `Handler:294`: the handler installs any non-empty anchor the verifier returns.

**Attack.** A relay sets `new_trust_anchor` in the content to a validator set holding only its own key. From the next bundle on, it can seal any header.

**Reference.** EthCall discards an anchor the proof did not produce: `reconcileTrustAnchorRotation(...)` at `EthCall:105` (body at `EthCall:222`).

**Fix.** Return the new anchor and id only from `verified.newTrustAnchor()` / `verified.newTrustAnchorId()`. Reject, or strip, any relay-supplied anchor.

### 1.3 `channelContext` is dropped (High)

- `Translator:55` decodes `channelContext` but never passes it to the call.
- `BundleCall:137` calls `verifyBundle(bundlePayload, trustedValidatorSetEncoded)` without a `channelId`.
- The verifier then falls back to the span heuristic `reorderQueueSlots` (`Verifier:1118`). The exact-key path `matchQueueSlotsByConnId` (`Verifier:1166`) is never used in production.

The heuristic finds "5 slots within a span of 15" plus one outlier. It does not tie the slots to the channel, so the slots of another channel on the same peer service also pass. Once 1.1 is fixed this becomes a real cross-channel risk.

**Fix.** Pass `channelContext[0..32]` as `channelId` and remove the heuristic path.

---

## 2. Correctness and liveness

The existing e2e runs a single Besu validator (`hedera-node/yahcli/scripts/clpre2e/hiero-to-besu-simple-message.md:150`), and no unit test covers 3 validators with 2 seals, or any epoch-header hop. That is likely why these findings went unnoticed.

### 2.1 Quorum formula (High)

- Native: `final int quorum = (2 * n) / 3 + 1;` (`Verifier:960`).
- Besu: `calculateRequiredValidatorQuorum(n) = fastDivCeiling(2 * n, 3)`, i.e. `ceil(2n/3)` ([`BftHelpers.java`](https://github.com/hyperledger/besu/blob/main/consensus/common/src/main/java/org/hyperledger/besu/consensus/common/bft/BftHelpers.java)).
- Spec: `ceil(2N/3)` (`consensus_finality.md`, `state_proof.md`).

| n | Besu | Native |
|---|------|--------|
| 1 | 1    | 1      |
| 3 | 2    | **3**  |
| 4 | 3    | 3      |
| 6 | 4    | **5**  |
| 7 | 5    | 5      |
| 9 | 6    | **7**  |

When `n` is a multiple of 3, native asks for one seal more than Besu. Besu marks a round committed as soon as `commitMessages.size() >= quorum` ([`RoundState.java:141`](https://github.com/hyperledger/besu/blob/main/consensus/qbft-core/src/main/java/org/hyperledger/besu/consensus/qbft/core/statemachine/RoundState.java)), so blocks usually carry exactly the quorum of seals. On a 3-validator network most headers carry 2 seals and native rejects them.

**Fix.** Use `(2 * n + 2) / 3`.

### 2.2 Hop (rotation) rules do not match Besu (High)

**Besu's semantics.** Each header is sealed by the same set it lists:

- [`BftValidatorsValidationRule`](https://github.com/hyperledger/besu/blob/main/consensus/common/src/main/java/org/hyperledger/besu/consensus/common/bft/headervalidationrules/BftValidatorsValidationRule.java): `extraData.validators` of header N must equal `getValidatorsAfterBlock(parent)`.
- [`BftCommitSealsValidationRule`](https://github.com/hyperledger/besu/blob/main/consensus/common/src/main/java/org/hyperledger/besu/consensus/common/bft/headervalidationrules/BftCommitSealsValidationRule.java): the committers of header N must be in `getValidatorsAfterBlock(parent)`, with at least `ceil(2n/3)` of them.

So the first header that lists a new set V' is sealed by V', not by the old set T.

**Native.** `processEpochHeaders` (`Verifier:858`) checks each hop against the old set T. It requires `floor(2|T|/3)+1` seals and rejects any signer outside T (`Verifier:1007`). Then it adopts the hop's listed set.

**Effect.**

- *Add a member* (V' = T + x): if x sealed the change block, native rejects it ("not in the validator set").
- *Remove a member* (V' = T − y): V' seals with `ceil(2(n−1)/3)`, but native needs `floor(2n/3)+1` from T. Example: n = 4 → Besu gives 2 seals, native needs 3 → rejected.

**Spec.** `trust_anchor.md` ("On-chain update") defines three header-mode rules for each hop and for the current block:

1. *One change at most:* V is T, T plus one, or T minus one.
2. *Old set sealed it:* count only signers in T; need `q` (no change), `q − 1` (one removed), or `q − 1` / `q` (one added, depending on whether the new member sealed).
3. *Own quorum:* at least `ceil(2|V|/3)` members of V sealed it.

Signers outside the set are ignored, not rejected.

**Fix.** Implement the spec's header-mode rules.

### 2.3 Contract mode is not supported (Medium, scope decision)

In contract mode Besu requires `extraData.validators` to be empty ([`QbftValidatorsValidationRule`](https://github.com/hyperledger/besu/blob/main/consensus/qbft/src/main/java/org/hyperledger/besu/consensus/qbft/headervalidationrules/QbftValidatorsValidationRule.java)). Native throws "validators list is empty" (`Verifier:841`), so a channel to a contract-mode network fails at `verifyConfig`.

This cannot be handled on the proof side alone. Seals prove only that some keys signed a header, not that those keys are the validator set. In contract mode the only link is: old set seals header N → header N commits to `stateRoot` → `stateRoot` holds the new array. The verifier must check that storage proof itself (spec: `state_proof.md`, "Field 1 in contract mode").

Supporting it would need:

- a contract-mode anchor (contract address, array slot, code hash, storage root);
- an array proof in `verifyConfig` and in each hop;
- the "skip the array while the storage root is unchanged" shortcut.

The existing MPT code can be reused.

**Decision needed.** Do the Besu networks we expect to connect to use contract-based validators? If not, ship header mode first. Either way, start the anchor format with a mode byte, as the spec does, so contract mode can be added later without breaking existing channels.

---

## 3. Gaps compared with the spec

### 3.1 No replay floor (Medium)

- `processEpochHeaders` starts with `prevBlockNumber = -1L` (`Verifier:862`). Hops must increase among themselves, but not relative to the anchor.
- The current block is not required to come after the last hop.
- The anchor does not store a block number at all.

**Risk.** An old header can be replayed as a hop. If its seals happen to come only from members of the current set, its (older) listed set is adopted. This can bring back a retired validator.

**Spec.** The anchor block is a floor: every hop and the current block must be after it, and a bundle that crosses hops moves the floor to the current block (`trust_anchor.md`, "No replay"; errors `HandoffHeaderNotAscending`, `CurrentHeaderBehindWalk`).

### 3.2 Trust anchor id is an epoch number (Medium)

- `newTrustAnchorId = (lastHopBlockNumber / 30000)` encoded with `BigInteger.toByteArray()` (`Verifier:906`), where 30000 is hard-coded (`BundleCall:70`, `ConfigCall`).
- The initial id is `BigInteger.ZERO.toByteArray()` = `0x00` (`Verifier:602`).
- In QBFT, `epochlength` only clears pending votes; it has no meaning for the validator set (`consensus_finality.md`).

**Effect.** Two rotations within the same 30000-block window (about 8.3 h at 1 s blocks) get the same id. The handler counts trust-anchor progress only when the id changes (`Handler:351`), so a rotation-only bundle in that window is rejected as no progress.

**Spec.** The id is the current block number, 8 bytes, big-endian.

### 3.3 No change limit per hop and no new-set quorum (Low)

Native adopts whatever set a hop lists, as long as the old set sealed it. Honest Besu changes at most one member per header-mode block. The spec's rules 1 and 3 (see 2.2) add defence in depth.

### 3.4 Partial bundles (Low)

The verifier proves an optional 6th slot (the last message's running hash), but it is never used. The handler requires every bundle to end at `nextMessageId − 1` (`Handler:401`, `Handler:488`). The spec allows a bundle to end at any message L, proven by that 6th slot. This affects all EVM verifiers, not only QBFT.

### 3.5 Header field count (Low)

`MAX_HEADER_FIELDS = 23` (`Verifier:94`). A future fork that adds a header field would break verification. The spec requires only at least 15 fields.

### 3.6 `verifyConfig` input shape (Low)

`verifyConfig` takes a "genesis" header (only its validator list is used, its seals are not checked) plus a current header sealed by that list. It works only if the set has not changed since that header. The spec uses the current header's own validator list and needs only one header.

Note: in both designs the config values (chain id, throttles, endpoints) are declared by the channel creator (trust on first use). Only `serviceAddress` is proven natively (slot 25).

---

## 4. Performance and gas

### 4.1 Flat gas price, no limits (Medium)

- `verifyBundle` always costs 100,000 gas (`BundleCall:69`); `verifyConfig` costs 50,000 (`ConfigCall`).
- There is no limit on the number of seals, hops or validators. Every seal is recovered before membership is checked (`Verifier:988`).
- The system contract accepts static calls and takes the trust anchor as an argument, so anyone can call it with self-made anchors and large payloads. The handler's `maxSyncBytes` limit does not apply to direct calls.
- For comparison, the spec's Solidity verifier measures about 8,500–13,000 gas per validator (`verifier_contract.md`, "Gas"), and the EVM `ecrecover` precompile costs 3,000 gas.

**Proposal.**

- Gas = base + about 3,000 per seal + a cost per hop + a cost per byte or per MPT node.
- Cap at 256 validators, like the spec.
- Reject a header with more seals than validators.
- Stop recovering once quorum is reached, if non-member signers are ignored as in the spec.

### 4.2 Logging (Low)

Every call logs the full trust anchor in hex at `info` (`BundleCall:102`, `BundleCall:126`, `BundleCall:188`; also in the verifier and ConfigCall). Failures log at `warn` with messages built from attacker input. Anyone calling `0x16f` can flood node logs. These should be `debug`.

### 4.3 Note: keep the full validator list in state

The spec stores only a hash of the set (`setCommitment`) because EVM storage reads are expensive, and the relay resends the list in every bundle. Natively, keeping the full list in channel state is cheaper and simpler. This is a valid divergence and should be kept.

---

## 5. Additional native findings (beyond the spec)

### 5.1 RLP decoder: unbounded recursion and quadratic copying (High)

`Rlp.decode` → `decodeList` → `decode` recurses once per nesting level, with no depth limit (`Rlp.java:117-172`). Every list item also copies its full raw bytes (`Arrays.copyOfRange(input, rawStart, end)`, then cloned again in the `Item` constructor). For a payload of size P nested d levels deep, that is about d·P bytes copied.

- **Reachability.** `Rlp.decodeOne(bundlePayload)` is the first thing `verifyBundle` does (`Verifier:246`), before any seal check. Anyone can call `0x16f` directly (static frames are allowed) with calldata built in EVM memory. The `ClprSubmitBundle` path is limited only by `maxSyncBytes`.
- **Effect.**
  - A nested payload of a few hundred KB gives either a `StackOverflowError` or very large allocations, all for the flat 100,000 gas.
  - `StackOverflowError` and `OutOfMemoryError` are `Error`s. They are not caught by `catch (Exception)` in BundleCall, ConfigCall or `AbstractNativeSystemContract.java:351`, so they escape the EVM.
  - The depth at which the stack overflows depends on each node's JVM, thread stack size and JIT state. A payload close to the limit can fail on some nodes and not others, which risks nodes diverging.
- **Scope.** The same `Rlp` class is used by the Ethereum verifier (`verifier/ethereum/*`, `verifier/evm/RlpDecoder.java`).
- **Fix.**
  - Cap the nesting depth (8 is enough for headers, proofs and bundles).
  - Make `Item` a view (offset and length into the input) instead of copying.
  - Cap the payload size in the system contract.
  - Charge gas per byte.

### 5.2 A Besu genesis `validators` override cannot be followed (Medium, operational)

Besu QBFT accepts a `validators` list in a genesis transition: `"transitions": {"qbft": [{"block": N, "validators": [...]}]}`. It is read by `BftFork.getValidators()` ([`BftFork.java`](https://github.com/hyperledger/besu/blob/main/config/src/main/java/org/hyperledger/besu/config/BftFork.java)), turned into `BftValidatorOverrides` by `QbftBesuControllerBuilder.convertBftForks` ([source](https://github.com/hyperledger/besu/blob/main/app/src/main/java/org/hyperledger/besu/controller/QbftBesuControllerBuilder.java)), and applied by `ForkingVoteTallyCache.getValidatorsAfter` ([source](https://github.com/hyperledger/besu/blob/main/consensus/common/src/main/java/org/hyperledger/besu/consensus/common/validator/blockbased/ForkingVoteTallyCache.java)). The [QBFT docs](https://docs.besu-eth.org/private-networks/how-to/configure/consensus/qbft) do not mention it.

At block N the set is replaced without a vote, and the old set seals nothing. No verifier design, native or spec, can tell this apart from an attack, so the channel stops. This is the natural recovery step when more than one third of the validators are lost, so it can happen in practice.

**Fix.** Nothing can be done in the verifier. Document it: a validator override needs a new channel, as a mode transition does. Tell Besu operators.

### 5.3 The trust anchor can silently lag the real validator set (Medium)

The current header's `extraData.validators` is never compared with the trusted set. The only check is that a quorum of the trusted set T sealed it (`Verifier:312`).

**Example.** T = {a, b, c, d}, and e is added, so V = {a, …, e}. Besu needs 4 of 5 seals; if those come from a, b, c and d, native accepts the header (it needs 3 of T). A relay can therefore skip hops forever while bundles keep passing. The anchor stays on an old set, which raises the risk of the retired-key attack (spec `alternatives.md` §2).

**Fix.** Require the current header's list to be T, or one change from T, after the hops (spec rule 1), and return the new anchor when it differs.

### 5.4 `LibSecp256k1` used directly (Low)

`recoverEthereumAddress` calls `LibSecp256k1.CONTEXT` directly (`Verifier:1055`). It is the only production code that does. If the native library is missing, every QBFT verification fails, while the EVM `ecrecover` precompile falls back to a Java implementation. `NativeLibVerifier` halts the node only when `contracts.nativeLibVerificationHaltEnabled` is set.

**Fix.** Use Besu's `SECP256K1` abstraction, as the EVM does, or make sure the halt flag is always on in production.

### 5.5 Empty storage proof with non-empty content (Low)

With an empty storage proof, the verifier returns the all-zero metadata, and it rejects the bundle only if the content and the manifest are also empty (`Verifier:347-349`, `Verifier:418`). Today this is hidden by 1.1. Once metadata comes from the proof, a bundle with content but no storage proof must be rejected.

### 5.6 Older-state bundles (Info)

There is no "last proven block height" per channel. A relay can prove any older state sealed by the trusted set. The handler's checks (replayed-prefix skip, `NoProgress`, ack monotonicity) block regressions in the cases we traced, but status transitions from older state are only as safe as those handler checks. Tracking a strictly increasing proven height, for example through block-number anchor ids (3.2), closes this. The same applies to the Solidity side (7.4).

### 5.7 No minimum validator count (Info)

Besu docs: "QBFT requires four validators for Byzantine fault tolerance". A channel to a 1–3 validator network trusts each key completely, or nearly. Consider a warning or a policy minimum in `verifyConfig`.

### 5.8 Declared output type mismatch (Info)

The translator declares `verifyBundle` outputs as `((uint64,bytes32,uint64,bytes32,uint8),bytes[],bytes,bytes)` (`Translator`), but the call encodes `ClprVerifierAbi.VERIFY_BUNDLE_RETURN`, which has 6-field metadata and a manifest struct. Anything that decodes using the declared type will break.

---

## 6. Channel setup (native and Solidity)

### 6.1 `completeChannel` signature does not bind the verifier or the proofs (Critical)

**Native.**

- `ClprCompleteChannelHandler` is permissionless. It checks that the commitment `keccak(channelId ‖ publicKey)` exists and that the signature covers `keccak256(channel_id)` (`ClprCompleteChannelHandler.java:193`).
- The `verifierContract`, `configProofBytes` and `endpointManifestProofBytes` are not signed.
- The channel id is not tied to the peer chain id.

**Solidity.** `ChannelLogic.completeChannel` verifies the signature over `_channelId` only (`clpr-smart-contracts` `src/logic/ChannelLogic.sol:131`). `_deriveChannelId(peerChainId, pubKey, salt)` (`:257`) uses a `peerChainId` returned by the verifier, so it is not authenticated either.

**Attack.** Anyone who sees a valid `(channelId, publicKey, signature)` can complete the channel first, with:

- a verifier contract they control; or
- the real QBFT verifier plus a header sealed by one key of their own (verifyConfig accepts any self-consistent set).

The channel then trusts the attacker's anchor, so forged inbound messages reach connectors. The real owner gets `CLPR_CHANNEL_ALREADY_EXISTS`.

- On EVM chains the transaction is visible in the mempool.
- On Hedera there is no public mempool, but transactions are visible to nodes in gossip before consensus. A failed `completeChannel` (for example a stale proof) also publishes the signature in the record stream, where anyone can reuse it.

**Fix.**

- Sign `keccak(channelId, verifier, keccak(configProof), keccak(endpointManifestProof))`, and the service address on Solidity. Or restrict completion to the account that registered the commitment.
- Ideally also include the expected chain id and the initial set commitment in the signed payload.

This affects every verifier type, not only QBFT.

---

## 7. Solidity verifier findings (`clpr-smart-contracts`)

Reviewed at `origin/main` `d5b5607c`, reading the code only (no tests run). Paths are relative to the `clpr-smart-contracts` root. I re-checked 6.1 and 7.1 against the code; the others are as reported by the review and should be confirmed with tests.

Things it gets right compared with native: quorum is `ceil(2n/3)`; honest add/remove hops pass; non-member seals are ignored; duplicates are deduped; queue metadata comes only from proven slots, bound to `channelId`; hops ascend past the anchor block; contract mode is implemented.

### 7.1 Header mode: one key fewer than Besu's quorum can forge a set change, and hops chain (High)

`ClprQbftQuorum.verifyRotationStep` (`src/libraries/proof/qbft/ClprQbftQuorum.sol:85-113`):

- removing a member needs `q(n) − 1` trusted signers plus `q(n − 1)` of the listed set;
- adding one needs `q(n + 1)` listed signers, one of whom may be new.

Hops per bundle are unlimited, and each forged removal raises the attacker's share.

**Example**, n = 7 (Besu quorum 5), attacker holds 4 keys:

1. Remove an honest member: listed 6, `q(6) = 4` ✓; trust `q(7) − 1 = 4` ✓.
2. Remove another: listed 5, `q(5) = 4` ✓; trust `q(6) − 1 = 3` ✓.
3. Remove another: listed 4, `q(4) = 3` ✓. The set is now entirely the attacker's.

This is a built-in tension. An honest Besu removal block carries only about `q(n − 1)` seals, so any rule that accepts honest removal blocks on their own also accepts `q(n − 1)` colluders. (Native avoids the forgery only by rejecting honest rotations; see 2.2.)

**Fix options.**

- Require Besu vote evidence for each change: the earlier headers, each sealed by a full `q(T)`, whose `extraData.vote` entries from distinct proposers (`coinbase`) reach a majority of T.
- At the very least, cap hops per bundle and document that the effective threshold is `q(n) − 1` with chaining.

### 7.2 Rule 2 ("trust link") never fires (Medium)

Within `verifyRotationStep`, rule 3 (own quorum) already implies rule 2 for single-member changes, so `TrustLinkNotReached` can never fire (`ClprQbftQuorum.sol:99-101`). Today's security rests on rules 1 and 3 alone, which is the root of 7.1. Either drop the check or make it meaningful (see 7.1).

### 7.3 A hop that changes nothing still produces a new anchor (Medium)

`crossedHops = count > 0` (`QbftHeaderModeVerifier.sol:107`, `QbftContractModeVerifier.sol:112`). A header with an unchanged set is accepted as a hop.

- A relay can add one in every bundle. The bundle then always counts as progress (`BundleLib.sol:79-111`), which defeats the `NoProgress` guard.
- It also moves the anchor floor up, so a competing relay's bundle at a slightly older height reverts with `CurrentHeaderBehindWalk`.

**Fix.** Return a new anchor only when the set commitment changes, or reject hops that do not change the set.

### 7.4 Older-state bundles (Medium, needs a test)

The only floor is the anchor block. A bundle may prove a block older than one an earlier bundle proved. An older proof showing the peer DRAINED with a smaller `nextMessageId` might move local DRAINED → CLOSED while later peer messages are still in flight (`BundleLib.sol:420-426`).

**Fix.** Track a strictly increasing proven height per channel.

### 7.5 Lower severity

- **Bad service address bricks the channel (Low).** A `serviceAddress` that is not 20 bytes passes `verifyConfig` when the manifest proof is empty (`QbftVerifierBase.sol:258-260`), and every later bundle then reverts. Fix: check the length in `verifyConfig`.
- **Throttles truncate silently (Low).** `uint32`/`uint64` casts in `_decodeConfigThrottles` (`QbftVerifierBase.sol:234-243`). Fix: reject oversized values.
- **Config manifest proof not tied to the config block (Low).** `QbftVerifierBase.sol:262-271`. Version monotonicity limits the impact.
- **Legacy verifier still deployed by default (Info).** `QBFTVerifier` is the default in `script/deploy/deployCore.ts:353`; only the E2E path uses `QbftHeaderModeVerifier`.
- **Spec drift (Info).** The spec's contract-mode "skip the array while the storage root is unchanged" is not implemented; the full array is proved every time, so the spec's gas figures for contract mode may not match the code.
- **Minor (Info).** An empty storage trie reverts instead of reading as zero (no practical impact); non-canonical 21-byte member encodings are accepted (harmless).

---

## 8. Open questions from the spec that also apply natively

From `alternatives.md` in the spec PR:

- **Seals do not name the chain.** A network cloned with the same validator keys cannot be told apart. Operators must not reuse keys.
- **Retired validator keys and an old anchor.** If more than two thirds of an old anchor's set have left the network, they can seal a fake chain of hops. Options: none, a freshness deadline, or a challenge period.
- **Mode transitions.** Besu can switch a running network between header and contract mode via genesis config, which cannot be proven. The channel must be closed and reopened.
- **Validator contract behind a proxy** (contract mode only). The code-hash pin does not see an implementation upgrade.

---

## 9. QBFT protocol conformance

This section asks a different question from the ones above: does our verification follow QBFT's own definition of a final block, as described in the protocol's formal sources? Only QBFT is in scope.

Sources:

- **Formal spec:** ConsenSys QBFT formal specification in Dafny, [qbft-formal-spec-and-verification](https://github.com/Consensys-inc-archive/qbft-formal-spec-and-verification), file `dafny/spec/L1/node_auxiliary_functions.dfy`, cited below as **FS**.
- **Paper:** H. Moniz, [The Istanbul BFT Consensus Algorithm](https://arxiv.org/abs/2002.03613), 2020. It assumes `n ≥ 3f + 1`; safety needs no timing assumptions, only liveness does.
- **Besu `main`:** `BftBlockHashing`, `QbftExtraDataCodec`, `BftHelpers`, `VoteTally`, `VoteTallyUpdater`, `BftBlockInterface`.

**Verdict.**

- The per-block finality check matches QBFT's definition, except for native's quorum formula (9.1).
- What does not match is how the verifier learns which validator set applies at a given height. In header mode neither implementation follows QBFT's rules (9.2).
- Neither has the safeguards a light client needs for heights it never sees (9.3).

### 9.1 Per-block finality check

FS `ValidNewBlock` (`:815-820`) is the check for a final block. It requires:

- `|commitSeals| ≥ quorum(|validators(chain)|)`;
- every seal's author is in `validators(chain)`;
- every seal signs `hashBlockForCommitSeal(block)`, which is the block with `commitSeals` emptied (`:289-298`).

| Rule | FS / Besu | Native | Solidity |
|---|---|---|---|
| Seal digest: header with seals emptied, round kept | FS `:289-298`; `QbftExtraDataCodec.encode` with `EXCLUDE_COMMIT_SEALS` (`:111-138`) | ✓ | ✓ |
| Quorum `(2n − 1)/3 + 1` = `ceil(2n/3)` | FS `:259-262`; `BftHelpers.calculateRequiredValidatorQuorum` | ✗ `2n/3 + 1` (2.1) | ✓ |
| Seal authors must be in `validators(height)` | FS `:819` | Uses the anchor's set, which equals `validators(height)` only if the set has not changed (5.3, 9.2) | Same |
| Seals form a set (no duplicates) | `commitSeals` is a set in FS | ✓ rejects duplicates | ✓ dedupes |

**Block identity.**

- FS notes that `commitSeals` does not count toward a block's identity or validity (`:222`).
- Besu's real block hash is `calculateHashOfBftBlockOnchain` (`BftBlockHashing:84-90`). It encodes extraData with the round set to 0 and the seals emptied (`EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER`).
- Native's `blockHash32 = keccak256(raw header)` (`Verifier:304`) is therefore **not** the Besu block hash. Today it is only logged (`BundleCall:191`). Anything that compares hashes, such as `parentHash` linking, deduplication or misbehaviour evidence (9.3), must use the on-chain hash.
- The seal list of a block is not canonical. Each validator seals the block with whatever quorum of commits it collected, so two nodes can return different seal lists for the same block. Raw header bytes must never be treated as a block's identity.

### 9.2 Validator set at a given height

In FS, `validators(chain)` is "the set of validators responsible for deciding on the block to be appended" (`:117`). It is a deterministic function of the **entire prior chain** (`:222`). The safety argument assumes every node computes it from the same chain. A light client that does not see every block must reach the same value from evidence. The two Besu modes give very different evidence.

**Contract mode: sound by construction.**

- Validators for block h are the result of the validator contract call evaluated on the state of block h − 1.
- Header h − 1 is final under `validators(h − 1)` and commits to its `stateRoot`.
- So a storage proof of the validator array under that `stateRoot` gives `validators(h)` exactly. The chain of trust is cryptographic and matches QBFT's definition.
- The Solidity `QbftContractModeVerifier` follows this model. Native does not support contract mode (2.3).
- **Requirement.** Besu calls the contract's validator function (`getValidators()`); the verifier reads a storage array. They are equivalent only if the function returns that array unchanged. This must be a documented deployment requirement, together with the code-hash pin, which is also why a proxy breaks it (spec `alternatives.md` §3).

**Header mode: not sound in either implementation.**

In Besu, `validators(h)` is the result of a vote tally:

- A vote is the header's `extraData.vote`. It is credited to the block's proposer, which is the header's `coinbase` (`BftBlockInterface.getProposerOfBlock`).
- A change is applied once `currentValidators / 2 + 1` distinct validators have voted for it (`VoteTally.validatorLimit`, `:100-102`). After that, the outstanding votes about that subject are cleared.
- Outstanding votes are discarded at every epoch block (`VoteTallyUpdater.updateForBlock`); `epochlength` is configured in genesis.

Header h lists `validators(h)` (`BftValidatorsValidationRule`), but only that same set seals it (`BftCommitSealsValidationRule`). **The list certifies itself.** The only evidence that links the old set T to the new set is the vote headers, which are final under T, so a minority of T cannot fake them.

Neither implementation checks votes. Each tries to make up for it in a different way:

- Native demands a quorum of the old set on the change block. Honest Besu change blocks don't carry that, so honest rotations are rejected (2.2).
- Solidity accepts the change block with `q(n) − 1` signers from T. That can be forged and chained into a full takeover (7.1).

**What a header-mode verifier would need to be correct by QBFT's rules:**

1. For each change at block N, the vote headers: at least `|T|/2 + 1` headers, each final under T (quorum of T), each with a distinct `coinbase` in T, each carrying the same vote (subject, add or remove), all after the last epoch block, and the last one at height N − 1.
2. Header N listing exactly T plus or minus that subject, and final under its own listed set.
3. `epochlength` in the trust anchor, to place epoch boundaries. The spec says `epochlength` "does not matter to the verifier"; that stops being true once votes are checked.
4. **Known limit.** A validator can withdraw or replace a vote, and the latest vote wins. Fully exact tallying would require every header between the first vote and N − 1. Requiring the vote headers plus N − 1 is a practical approximation, with a narrow residual case.

This is more relay work and more gas than today, but it is the only header-mode design that is both live (honest rotations pass) and safe (fewer than `q(T)` keys cannot rotate).

**Consequence.** If we want a verifier that is safe by QBFT's own definition with the least extra machinery, **contract mode is the cleaner target**. Header mode needs vote evidence before it can be both live and safe.

### 9.3 Trust across heights the verifier never sees

QBFT's guarantee holds **per height**: at most `f = (n − 1)/3` faulty validators (FS `:244-252`) in the set that decides that height. Two quorums of size `quorum(n)` then always share an honest validator (FS `:254-262`), so no two different blocks are decided at the same height.

When the verifier jumps from the anchor to the current block, it relies on that assumption for **every** validator set in between, including sets it never saw. It also relies on retired validators never colluding. Proof of authority has no stake to lose and no unbonding period, so a retired key is valid forever (spec `alternatives.md` §2).

Standard light clients protect against this with two mechanisms (the IBC Tendermint light client is the usual reference). Neither verifier has them, and the spec does not include them either:

- **Trusting period.**
  - The header `timestamp` (field 11) is covered by the seals, so it can be trusted.
  - The verifier could store the anchor block's timestamp and enforce:
    - a maximum anchor age (beyond it the channel needs re-anchoring);
    - timestamps that never go backwards across bundles;
    - no headers from the future compared with Hiero consensus time.
  - Today neither verifier reads the timestamp.
  - The spec's "freshness deadline" (`alternatives.md` §2, option 2) is one form of this.
- **Misbehaviour evidence and freezing.**
  - Two different final headers at the same height (different on-chain hashes, 9.1, each with a valid quorum) prove that at least `f + 1` validators signed conflicting blocks. That breaks QBFT's assumption, by quorum intersection.
  - Anyone should be able to submit such a pair to **freeze** the channel, so it stops accepting bundles until it is replaced.
  - This catches the retired-key and forged-rotation attacks after the fact. It costs nothing on the normal path.

---

## 10. Relay and end-to-end test coverage

### 10.1 How the relay tracks validator changes today

Reviewed `swirldslabs/clpr-evm-endpoint` at `main` `ac10aee2dd`, file `clpr-relay-evm/src/main/java/org/hiero/clpr/relay/evm/qbft/QbftBundleConstructor.java`.

**The relay does not detect validator changes.** It uses an epoch-checkpoint model:

- The trust anchor id is read as an **epoch number** (`QbftTrustAnchorId(long epoch)`, `:59`).
- If the peer's epoch is behind the current one, the relay fetches the header at every `epoch × epochLength` block (`fetchEpochBlockHeaders`, `:183-192`). It sends up to `maxEpochBlockHeadersPerBundle` of them as hops, whether or not the set changed.
- The native verifier and the legacy Solidity `QBFTVerifier` follow the same model. The spec PR and the new Solidity verifiers use change-block hops instead (section 3, 7).

This model does not fit QBFT. The set can change at **any** block, and epoch blocks only discard pending votes (9.2). Consequences:

| # | Problem | Effect |
|---|---|---|
| R1 | A mid-epoch change is only delivered at the next epoch boundary | Up to about 8.3 h of delay at 1 s blocks with `epochlength = 30000`. Meanwhile, headers sealed by the new set may fail verification |
| R2 | One epoch header can carry several changes at once | Native accepts it only if every sealer was in the old set; additions get stuck (2.2) |
| R3 | **Anchor id length mismatch with native** | The relay requires an 8-byte id (`ENCODED_TRUST_ANCHOR_ID_LENGTH = Long.BYTES`, `:63`). Native returns `BigInteger.toByteArray()`, usually 1 byte (`0x00` from `verifyConfig`, `Verifier:602`). Hiero sends the stored id back as `currentTrustAnchorId` (`hedera-app/.../workflows/clpr/ClprStreamingSynchronizer.java:403`, `ClprStreamingSyncSession.java:286`). The relay rejects the length, assumes the peer is up to date (`:226-228`), and **never sends hops to Hiero**. In practice the native verifier never receives a rotation |
| R4 | Payload format does not match the new Solidity verifiers | The relay sends field 1 as a plain list of epoch headers. `QbftHeaderModeVerifier` expects `[trustedSet, hops]` with change-block hops and an 8-byte block-number anchor id. The relay only matches the legacy verifier and native |

**What the relay needs** (whichever verifier model is chosen):

- detect real change blocks by scanning headers from the anchor block and comparing `extraData.validators`. Batched `eth_getBlockByNumber` is enough. `qbft_getValidatorsByBlockNumber` with a binary search is faster, but can miss a change that is later reverted (A → B → A);
- use a block-number anchor id with a fixed encoding agreed with the verifiers;
- emit the agreed hop format, which with vote evidence (9.2) is `[voteHeaders[], changeHeader]`, collected by walking back from N − 1 to the last epoch block.

### 10.2 End-to-end coverage

Reviewed `swirldslabs/clpr-e2e` at `main` `b5db733e7b`.

**No end-to-end test exercises Besu validator rotation, and the current setup cannot.**

- **Single validator by design.** `shared/scripts/networks/generate-besu-genesis.ts:41-47` defaults `BESU_VALIDATOR_COUNT` to 1, with the comment: "Hiero's BesuQbftProofVerifier system contract (0.0.367) hardcodes QBFT_EXPECTED_SEALS=1 … raise via BESU_VALIDATOR_COUNT only after the verifier is updated for quorum."
- **The Helm chart is pinned too.** `shared/charts/besu/values.yaml:1-3` sets `replicaCount: 1` with the same reason, so raising the env variable alone does not start more validators.
- **Nothing changes the validator set.** No file calls `qbft_proposeValidatorVote` or any other `qbft_*` method, and no genesis uses `validatorselectionmode` / `validatorcontractaddress`.
- **No test touches a Besu trust anchor.**
  - `clpr/test/*` (happy path, lifecycle, adversarial, relay outage, manifest, stress, …) has no rotation scenario.
  - `bundle-adversarial.test.ts` only lists legacy Solidity error names (`EpochMismatch`, `MultiValidatorNotSupported`).
  - `entente/test/rotation.test.ts` rotates the Entente signing committee, not Besu validators, and is disabled pending tooling.
- **The reason in the e2e comment is out of date.** The current `BesuQbftVerifier` has no `QBFT_EXPECTED_SEALS`; it counts a quorum over a validator set. Multi-validator networks could already be tried, and are expected to fail on 3 or 6 validators (2.1) and on any rotation (2.2, R3).

**HCN tests.** `BesuQbftVerifierTest` never passes a non-empty epoch-header list, has no 3-validator case, and has no test with exactly `ceil(2n/3)` seals. The manual walkthrough (`hedera-node/yahcli/scripts/clpre2e/hiero-to-besu-simple-message.md:150`) also uses a single validator.

**Consequence.** The quorum rules, the hop logic, the relay's hop delivery and the anchor-id handoff have **never run end to end**. Every finding in sections 2, 3, 7.1–7.3, 9.2 and 10.1 is in code that no test exercises.

**Coverage needed**, once the verifiers are fixed:

- networks with 4 and 7 validators, where blocks carry exactly `ceil(2n/3)` seals;
- a network with 3 or 6 validators, to cover the quorum boundary;
- header mode: add one validator, then remove one, through `qbft_proposeValidatorVote`, while messages flow both ways;
- two changes inside one epoch, and a change right before an epoch block;
- contract mode (if in scope): change the validator array in one transaction;
- negative cases: a forged rotation by fewer than `q(T)` keys, a replayed old hop, a relay-supplied trust anchor (1.2), relay-supplied metadata (1.1).

---

## 11. Suggested order of work

1. Fix 1.1, 1.2 and 1.3 by following the EthCall pattern. Small, self-contained change.
2. Fix 6.1 (bind the verifier and the proofs into the `completeChannel` signature) on both sides. This needs agreement with the clpr-smart-contracts team and a spec change.
3. Harden the RLP decoder (5.1): depth cap, no copying, payload cap. This also protects the Ethereum verifier.
4. Fix the quorum formula (2.1) and add a unit test with 3 validators and 2 seals.
5. Rewrite the hop logic (2.2, 3.1–3.3, 5.3). Settle the 7.1 trade-off (vote evidence versus accepting `q(n) − 1`) together with the Solidity team so both verifiers enforce the same rule. This changes the trust anchor format, so add a mode byte and an anchor block number.
6. Switch to dynamic gas and add limits (4.1); lower log levels (4.2); fix 5.4, 5.5 and 5.8.
7. Decide the validator-set model (9.2): header mode with vote evidence, or contract mode, which is sound by construction. This decision also settles step 5. Use the on-chain block hash wherever hashes are compared (9.1). Add a trusting period and a misbehaviour freeze (9.3).
8. Decide whether contract mode is in scope (2.3). Document the channel-replacement runbook for mode transitions and validator overrides (5.2).
9. Update the relay to detect real change blocks, use a fixed block-number anchor id, and emit the agreed hop format (10.1). R3 means native rotations cannot work end to end until this is done.
10. Add multi-validator and rotation coverage (10.2): first in HCN, with real Besu headers through a Testcontainers-based fixture generator or recorded fixtures; then in `clpr-e2e`, after lifting the single-validator limit.

---

## 12. Proposed issues for the native verifier

Four issues, under one epic, that make the native verifier verify Besu QBFT blocks correctly and safely. Each issue ships with its own tests. Section numbers in brackets point to the findings above.

**Source of truth.** Besu's QBFT behaviour, as defined by the Besu documentation and client source, and the QBFT protocol (Moniz 2020, ConsenSys QBFT formal specification). The Solidity verifiers in `clpr-smart-contracts` and the draft spec PR (swirldslabs/clpr-spec#78) are used for comparison only. Where they are mentioned below, it is as an implementation hint, not a requirement.

**Deliberate deviations (light-client approach).** The verifier checks the headers a relay sends, not every block.

- **Validator votes are not tracked.** Header-mode changes are accepted from the change block and its seals, without the vote headers. Checking votes exactly would mean processing up to a whole epoch of blocks (9.2). Trade-off: removing a member needs `q(n) − 1` old signers, and hops can be chained (7.1). This stays an open question.
- **No full block validation.** Seals prove finality; we trust that a quorum of validators only finalises valid blocks.

These deviations must be documented next to the verifier code.

### Epic

**Title:** CLPR: correct and harden the native Besu QBFT verifier

**Definition of done:**

- all four issues merged, each with its own unit tests;
- multi-validator cases (n = 3, 4, 6, 7) and honest add/remove rotations pass with real or recorded Besu headers;
- the deliberate deviations are documented next to the verifier code.

**Order.** Issues 1 and 4 are independent and can start now. Issue 3 changes the anchor format, and issue 2's hop rules build on it, so do 3 before 2, or in the same PR.

### Issue 1: Return only proven, channel-bound data (Critical) [1.1, 1.2, 1.3, 5.5, 5.8, 4.2]

`BesuQBFTVerifyBundleCall` returns queue metadata and the new trust anchor from the relay-supplied bundle content, so a relay can inject messages or install its own anchor. `channelContext` is decoded but dropped, so storage slots are matched by a heuristic that isn't tied to the channel. Everything the verifier returns must come from the proof, and every proven slot must be derived from `channelId`.

Tasks:

- [ ] Build the returned metadata from the proven storage slots, or reject the bundle when the relayed metadata differs (same pattern as `EthereumVerifyBundleCall.relayedMetadataMatchesProven`)
- [ ] Return a new anchor only when the proof produces one; reject relay-supplied anchors
- [ ] Pass `channelContext` through to the verifier; remove the `reorderQueueSlots` heuristic
- [ ] Reject a bundle with content but no storage proof
- [ ] Fix the declared `verifyBundle` output type to match the encoded return; lower per-call logging from `info` to `debug`

Tests:

- tampered metadata, relay-supplied anchor, slots from another channel, missing storage proof → rejected
- an honest bundle passes; reordered storage entries still decode

### Issue 2: Quorum and header-mode hop rules per Besu QBFT (High) [2.1, 2.2, 3.3, 5.3, 5.4]

Native requires `floor(2n/3) + 1` seals, where Besu uses `ceil(2n/3)` (`BftHelpers.calculateRequiredValidatorQuorum`). It checks a hop's seals against the old trusted set, but in Besu a header is sealed by the set it lists (`BftValidatorsValidationRule`, `BftCommitSealsValidationRule`). So a change block is sealed by the new set: a newly added validator's seal counts as a non-member, and honest rotations fail. It also never compares the current header's validator list with the trusted set, so the anchor can silently fall behind. (Implementation hint: `ClprQbftQuorum.verifyRotationStep` in the Solidity verifier.)

Tasks:

- [ ] Quorum `ceil(2n/3)`
- [ ] Count seals against the header's own validator list. Reject a seal from outside that list, or a duplicate seal, as Besu does (`BftCommitSealsValidationRule`)
- [ ] At most one validator change per hop (Besu applies at most one change per block, `VoteTally`)
- [ ] Check the current header's list against the trusted set (at most one change)
- [ ] Use the Besu `SECP256K1` abstraction instead of `LibSecp256k1` directly
- [ ] *(CLPR light-client rules, not Besu behaviour)* the old trusted set must provide at least `q − 1` signers on a hop (`q` when an added member did not seal); reject hops that don't change the set

Deliberate differences from the Solidity verifier (native is stricter, and only for input honest Besu never produces):

- stray or duplicate seals are rejected (Besu behaviour); Solidity ignores them and counts the rest;
- hops that don't change the set are rejected; Solidity accepts them until the proposed `HandoffSetUnchanged` rule lands.

Tests:

- n = 1, 3, 4, 6, 7 with exactly `ceil(2n/3)` seals
- seal from outside the header's list, duplicate seal → rejected
- honest add and remove (new member sealed or not), consecutive changes, A → B → A
- two changes in one hop, a no-op hop, a skipped change → rejected

### Issue 3: Trust anchor format, replay floor and `verifyConfig` (High) [3.1, 3.2, 3.5, 3.6, 9.1, 10.1 R3]

Native stores an RLP validator list, with an anchor id of `blockNumber / 30000` encoded with `BigInteger` (usually 1 byte). In QBFT, validators can change at any block and `epochlength` only clears pending votes, so an epoch number doesn't identify a validator set. The anchor needs a mode byte, an anchor block number and an 8-byte block-number id. The current id format also blocks native rotations end to end: the relay expects 8 bytes. There is no replay floor. `verifyConfig` needs a separate genesis header, although each QBFT header already lists the set that sealed it.

Tasks:

- [ ] Add a mode byte and the anchor block number to the anchor (keep the full validator list in state)
- [ ] Use an 8-byte big-endian block-number anchor id in `verifyConfig` and `verifyBundle`; return the new anchor at the current block number
- [ ] Replay floor: hops and the current block must be after the anchor block; the current block must be after the last hop
- [ ] `verifyConfig`: take the initial set from the config header; drop the genesis header
- [ ] Header field limit: at least 15 fields, no fixed maximum (today capped at 23); stop logging `keccak(raw header)` as the block hash

Tests:

- anchor round-trip encoding; wrong-mode anchor → rejected
- replayed old hop, current block behind the last hop → rejected
- config headers with 15, 21 and 24 fields; config header sealed by fewer than a quorum of its own list → rejected

### Issue 4: RLP decoder hardening and work-based gas (High) [5.1, 4.1]

`Rlp.decode` recurses without a depth limit and copies raw bytes for every list item. A nested payload causes a `StackOverflowError` or huge allocations, which `catch (Exception)` doesn't catch, and the overflow depth depends on each node's JVM. The Ethereum verifier shares the decoder. `verifyBundle` costs a flat 100,000 gas and `verifyConfig` 50,000, with no limits on seals, hops or payload size, and anyone can call the system contract.

Tasks:

- [ ] Cap the nesting depth; store each item as an offset and length into the input instead of copying
- [ ] Cap the payload size in the system contract
- [ ] Price by work: base + per seal + per hop + per byte
- [ ] Cap validators at 256, and cap seals and hops; reject over-cap input before doing expensive work

Tests:

- deep nesting → clean rejection with no `Error`; large flat payload within limits works
- gas grows with seals and hops; over-cap inputs → rejected
- existing Ethereum verifier tests still pass

### Not covered by these issues

- **Out of scope:** `completeChannel` signature binding [6.1], which affects every verifier type and needs a protocol change; relay change detection, block-number anchor id and change-block hops [10.1] (native rotations only work end to end once this lands); contract mode [2.3]; `clpr-e2e` multi-validator and rotation coverage [10.2].
- **Open questions:** is the `q(n) − 1` threshold with chained hops acceptable for header mode, or do we cap hops per bundle, add vote evidence, or prefer contract mode [9.2, 7.1]? Trusting period and misbehaviour freeze: in this epic or later [9.3]?
