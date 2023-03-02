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
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JWildcardECDSAKey;
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
 * <p>Also exposes methods that, given {@link JWildcardECDSAKey}s as input, replace the {@link JWildcardECDSAKey}/s with their
 * corresponding {@link JECDSASecp256k1Key}, if such was present in the list of parsed {@link TransactionSignature}s.
 * This process is also referred to as "de-hollowing". The {@code payerReqSig} and {@code otherReqSigs} contained
 * in the {@link RationalizedSigMeta} must be free of
 * {@link JWildcardECDSAKey}s, so that subsequent key activation tests pass, so this de-hollow process is essential.
 */
public class HollowScreening {

    record HollowScreenResult(
            @Nullable List<PendingCompletion> pendingCompletions,
            @Nullable JKey deHollowedPayerKey,
            @Nullable List<JKey> deHollowedOtherKeys) {}

    static boolean atLeastOneWildcardKeyIn(@NonNull final JKey payerKey, @NonNull List<JKey> otherPartyKeys) {
        if (payerKey.hasHollowKey()) {
            return true;
        }
        for (final var otherReqKey : otherPartyKeys) {
            if (otherReqKey.hasHollowKey()) {
                return true;
            }
        }
        return false;
    }

    static HollowScreenResult performFor(
            @NonNull final List<TransactionSignature> txnSigs,
            @NonNull final JKey payerKey,
            @NonNull final List<JKey> otherKeys,
            @NonNull final AliasManager aliasManager) {
        final var evmAddressToEcdsaKeyIndex = createEvmAddressToEcdsaKeyIndexFrom(txnSigs);

        List<PendingCompletion> pendingCompletions = null;
        JKey deHollowedPayerKey = null;
        if (payerKey.hasHollowKey()) {
            final var hollowKey = payerKey.getHollowKey();
            final var payerEvmAddress = hollowKey.getEvmAddress();
            final var correspondingKey = evmAddressToEcdsaKeyIndex.get(Bytes.of(payerEvmAddress));
            if (correspondingKey != null) {
                deHollowedPayerKey = correspondingKey;
                if (hollowKey.isForHollowAccount()) {
                    pendingCompletions =
                            maybeAddToCompletions(payerEvmAddress, correspondingKey, pendingCompletions, aliasManager);
                }
            }
        }

        List<JKey> deHollowedOtherKeys = null;
        for (int i = 0; i < otherKeys.size(); i++) {
            final var otherKey = otherKeys.get(i);
            if (otherKey.hasHollowKey()) {
                final var hollowKey = otherKey.getHollowKey();
                final var evmAddress = hollowKey.getEvmAddress();
                final var correspondingKey = evmAddressToEcdsaKeyIndex.get(Bytes.of(evmAddress));
                if (correspondingKey != null) {
                    if (deHollowedOtherKeys == null) {
                        deHollowedOtherKeys = new ArrayList<>(otherKeys);
                    }
                    deHollowedOtherKeys.set(i, correspondingKey);

                    if (hollowKey.isForHollowAccount()) {
                        pendingCompletions =
                                maybeAddToCompletions(evmAddress, correspondingKey, pendingCompletions, aliasManager);
                    }
                }
            }
        }
        return new HollowScreenResult(pendingCompletions, deHollowedPayerKey, deHollowedOtherKeys);
    }

    private static Map<Bytes, JECDSASecp256k1Key> createEvmAddressToEcdsaKeyIndexFrom(
            final List<TransactionSignature> txnSigs) {
        final Map<Bytes, JECDSASecp256k1Key> evmAddressToEcdsaKeyIndex = new HashMap<>();
        for (final var sig : txnSigs) {
            if (sig.getSignatureType() != SignatureType.ECDSA_SECP256K1) {
                continue;
            }
            final var expandedPublicKeyDirect = sig.getExpandedPublicKeyDirect();
            final var evmAddress = MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(expandedPublicKeyDirect);
            final var compressedSecp256k1 = MiscCryptoUtils.compressSecp256k1(expandedPublicKeyDirect);
            final var key = new JECDSASecp256k1Key(compressedSecp256k1);

            evmAddressToEcdsaKeyIndex.put(Bytes.of(evmAddress), key);
        }
        return evmAddressToEcdsaKeyIndex;
    }

    private static List<PendingCompletion> maybeAddToCompletions(
            byte[] payerEvmAddress,
            JECDSASecp256k1Key correspondingKey,
            List<PendingCompletion> pendingCompletions,
            AliasManager aliasManager) {
        final var accountNum = aliasManager.lookupIdBy(ByteStringUtils.wrapUnsafely(payerEvmAddress));
        // a hollow account cannot be CryptoDelete-d, but it may have expired since the latest
        // immutable state
        if (accountNum != EntityNum.MISSING_NUM) {
            if (pendingCompletions == null) {
                pendingCompletions = new ArrayList<>();
            }
            pendingCompletions.add(new PendingCompletion(accountNum, correspondingKey));
        }
        return pendingCompletions;
    }
}
