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

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.metadata.SigMetadataLookup;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.sigs.utils.MiscCryptoUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.PendingCompletion;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class HollowScreening {
    final Map<Bytes, JECDSASecp256k1Key> evmAddressToECDSAKey = new HashMap<>();

    public List<PendingCompletion> pendingCompletionsFrom(
            @NonNull final List<TransactionSignature> txnSigs,
            @NonNull final SigMetadataLookup sigMetaLookup,
            @Nullable final LinkedRefs linkedRefs,
            @NonNull final AliasManager aliasManager) {
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
                maybeLink(linkedRefs, entityNum, alias, key);
            } else {
                final var lookupResult = sigMetaLookup.accountSigningMetaFor(alias, null);
                if (!lookupResult.succeeded()) {
                    // in pre-handle context, there may be a hollow account with this evmAddress in the working state,
                    // but `SigMetaDataLookUp` is not aware of it; or there is a pending hollow account creation txn
                    // not yet executed;
                    maybeLink(linkedRefs, entityNum, alias, key);
                } else {
                    final var metadata = lookupResult.metadata();
                    final var accountKey = metadata.key();
                    if (accountKey.hasHollowKey()) {
                        pendingCompletions.add(new PendingCompletion(key, entityNum));
                        // in pre-handle context, make sure to update the `linkedRefs` so we handle hollow account’s sig
                        // metadata changes from another txn until `handle()` of the current one;
                        maybeLink(linkedRefs, entityNum, alias, key);
                    }
                }
            }
        }
        return pendingCompletions;
    }

    private void maybeLink(
            final LinkedRefs linkedRefs,
            final EntityNum entityNum,
            final ByteString alias,
            final JECDSASecp256k1Key key) {
        if (linkedRefs != null) {
            linkedRefs.link(alias);
            linkedRefs.link(entityNum.longValue());
            linkedRefs.link(Key.newBuilder()
                    .setECDSASecp256K1(ByteStringUtils.wrapUnsafely(key.getECDSASecp256k1Key()))
                    .build()
                    .toByteString());
        }
    }

    /**
     * Given a JHollowKey, tries to return it's corresponding JECDSASecp256k1Key.
     *
     * NOTE this method should be called after @code{pendingCompletionFrom}, because it depends on the collected
     * ECDSA keys from iterating through all the sigs @code{pendingCompletionFrom}, in order to perform the "de-hollowing"
     *
     * @param key the instance of JHollowKey to (possibly) convert to corresponding JECDSASecp2561k1Key
     * @return The corresponding ECDSA key for the evm address in the key param. If no match, returns the key param.
     */
    public JKey maybeDeHollowKey(final JKey key) {
        final var correspondingECDSAKey =
                evmAddressToECDSAKey.get(Bytes.of(key.getHollowKey().getEvmAddress()));
        return correspondingECDSAKey != null ? correspondingECDSAKey : key;
    }

    /**
     * Given a (possibly immutable) list of keys, tries to "de-hollow" any JHollowKeys to it's corresponding JECDSASecp256k1Key.
     *
     * NOTE this method should be called after @code{pendingCompletionFrom}, because it depends on the collected
     * ECDSA keys from iterating through all the sigs @code{pendingCompletionFrom}, in order to perform the "de-hollowing"
     *
     * @param keys the (possibly immutable) list of keys to "de-hollow"
     * @return If there are no hollow keys in the list or the method could not de-hollow any of the present JHollowKey, returns the same list.
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
