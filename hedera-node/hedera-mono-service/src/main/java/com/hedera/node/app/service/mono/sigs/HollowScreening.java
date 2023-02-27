/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.mono.sigs;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JHollowKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.metadata.SigMetadataLookup;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.sigs.utils.MiscCryptoUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.PendingCompletion;
import com.hedera.node.app.service.mono.utils.RationalizedSigMeta;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * A utility class for {@link Expansion} and {@link Rationalization} that, given a list of {@link TransactionSignature}
 * and infrastructure for looking up accounts, is able to construct a list of {@link PendingCompletion} which may be
 * performed in the given {@link SwirldsTxnAccessor}, if all key/sig activation validations pass.
 *
 * <p>Also exposes methods that, given {@link JHollowKey}s as input, replace the {@link JHollowKey}/s with their
 * corresponding {@link JECDSASecp256k1Key}, if such was present in the list of parsed {@link TransactionSignature}s.
 * This process is also referred to as "de-hollowing". The {@code payerReqSig} and {@code otherReqSigs} contained
 * in the {@link RationalizedSigMeta} must be free of
 * {@link JHollowKey}s, so that subsequent key activation tests pass, so this de-hollow process is essential.
 *
 */
public class HollowScreening {

    private Map<Bytes, JECDSASecp256k1Key> evmAddressToECDSAKey;

    public List<PendingCompletion> pendingCompletionsFrom(
            @NonNull final List<TransactionSignature> txnSigs,
            @NonNull final SigMetadataLookup sigMetaLookup,
            @Nullable final LinkedRefs linkedRefs,
            @NonNull final AliasManager aliasManager) {
        evmAddressToECDSAKey = new HashMap<>();
        final List<PendingCompletion> pendingCompletions = new ArrayList<>();
        for (final var sig : txnSigs) {
            if (sig.getSignatureType() != SignatureType.ECDSA_SECP256K1) {
                continue;
            }
            final var expandedPublicKeyDirect = sig.getExpandedPublicKeyDirect();
            final var evmAddress = MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(expandedPublicKeyDirect);
            final var compressedSecp256k1 = MiscCryptoUtils.compressSecp256k1(expandedPublicKeyDirect);
            final var key = new JECDSASecp256k1Key(compressedSecp256k1);
            // remember all evmAddress <-> ECDSA key pairs for subsequent "de-hollowing"
            evmAddressToECDSAKey.putIfAbsent(Bytes.of(evmAddress), key);

            final var alias = ByteStringUtils.wrapUnsafely(evmAddress);
            final var entityNum = aliasManager.lookupIdBy(alias);
            if (entityNum == EntityNum.MISSING_NUM) {
                if (linkedRefs != null) {
                    // there may be a yet-to-be-executed transaction that will create a hollow account linked to this
                    // alias and this might be the finalizing transaction
                    linkedRefs.link(alias);
                }
            } else {
                // in pre-handle context, where we are working with a `SigMetadataLookup` from the latest immutable
                // state,
                // the sig meta lookup may fail if the account is in the working state, but not in the latest immutable
                // state
                // in pre-handle context, make sure to update the `linkedRefs` so we handle hollow account’s sig
                // metadata changes from another txn until `handle()` of the current one;
                final var lookupResult = sigMetaLookup.accountSigningMetaFor(alias, linkedRefs);
                if (lookupResult.succeeded()) {
                    final var accountKey = lookupResult.metadata().key();
                    if (accountKey.hasHollowKey()) {
                        pendingCompletions.add(new PendingCompletion(key, entityNum));
                    }
                }
            }
        }
        return pendingCompletions;
    }

    /**
     * Given a {@link JHollowKey},
     * tries to return it's corresponding {@link JECDSASecp256k1Key}.
     *
     * <p>NOTE this method should be called after {@link HollowScreening#pendingCompletionsFrom}, because it depends on the collected
     * {@link JECDSASecp256k1Key} keys from iterating through all the sigs
     * during {@link HollowScreening#pendingCompletionsFrom}, in order to perform the "de-hollowing"
     *
     * @param key the instance of {@link JHollowKey}
     *            to (possibly) convert to corresponding {@link JECDSASecp256k1Key}
     * @return The corresponding {@link JECDSASecp256k1Key} key for
     * the evm address in the key param. If no match, returns the key param.
     */
    public JKey maybeDeHollowKey(final JKey key) {
        final var correspondingECDSAKey =
                evmAddressToECDSAKey.get(Bytes.of(key.getHollowKey().getEvmAddress()));
        return correspondingECDSAKey != null ? correspondingECDSAKey : key;
    }

    /**
     * Given a (possibly immutable) list of keys, tries to "de-hollow" any JHollowKeys to it's corresponding JECDSASecp256k1Key.
     *
     * <p>NOTE this method should be called after {@link HollowScreening#pendingCompletionsFrom}, since it depends on the collected
     * {@link JECDSASecp256k1Key} keys from iterating through all
     * the sigs during {@link HollowScreening#pendingCompletionsFrom}, in order to be able to perform the "de-hollowing"
     *
     * @param keys the (possibly immutable) list of keys to "de-hollow"
     * @return If there are no hollow keys in the list or the method could not de-hollow any of the present
     * {@link JHollowKey}, returns the same list.
     * Otherwise, returns a new list where all possible de-hollowings were performed.
     */
    public List<JKey> maybeDeHollowKeys(final List<JKey> keys) {
        List<JKey> deHollowedKeys = null;
        for (int i = 0; i < keys.size(); i++) {
            final var key = keys.get(i);
            if (!key.hasHollowKey()) {
                continue;
            }
            final var ecdsaKey =
                    evmAddressToECDSAKey.get(Bytes.of(key.getHollowKey().getEvmAddress()));
            if (ecdsaKey != null) {
                if (deHollowedKeys == null) {
                    deHollowedKeys = new ArrayList<>(keys);
                }
                deHollowedKeys.set(i, ecdsaKey);
            }
        }
        return deHollowedKeys != null ? deHollowedKeys : keys;
    }
}
