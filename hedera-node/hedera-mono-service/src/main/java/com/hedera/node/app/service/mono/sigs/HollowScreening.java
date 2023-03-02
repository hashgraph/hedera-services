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

    static boolean atLeastOneWildcardECDSAKeyIn(@NonNull final JKey payerKey, @NonNull List<JKey> otherPartyKeys) {
        if (payerKey.hasWildcardECDSAKey()) {
            return true;
        }
        for (final var otherReqKey : otherPartyKeys) {
            if (otherReqKey.hasWildcardECDSAKey()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Encapsulates the result of a hollow screening.
     *
     * @param pendingCompletions a list of {@link PendingCompletion} if there the hollow screening found possible
     *                           hollow account completions. {@code null} otherwise
     * @param replacedPayerKey a {@link JECDSASecp256k1Key} if payer key was {@link JWildcardECDSAKey} and a corresponding
     * {@link JECDSASecp256k1Key} was found in the txn sigs. {@code null} otherwise
     * @param replacedOtherKeys a list of all other req keys, where possible {@link JWildcardECDSAKey} -> {@link JECDSASecp256k1Key}
     *                          conversions were made; {@code null} if no conversions were made
     */
    record HollowScreenResult(
            @Nullable List<PendingCompletion> pendingCompletions,
            @Nullable JKey replacedPayerKey,
            @Nullable List<JKey> replacedOtherKeys) {}

    /**
     * <strong>NOTE</strong>- should be called only if {@link HollowScreening#atLeastOneWildcardECDSAKeyIn} for the same
     * {@code payerKey} and {@code otherKeys} returned true
     *
     * For each {@link JWildcardECDSAKey} in {@code payerKey} and {@code otherKeys}, tries to find its corresponding
     * {@link JECDSASecp256k1Key} from the {@code txnSigs}, and also construct a {@link PendingCompletion} if a hollow
     * account is linked to the evm address from the {@link JWildcardECDSAKey};
     *
     * <p>Note that this method tries to replace {@link JWildcardECDSAKey}s present in {@code otherKeys}
     * even if no pending completions are present. That's because a CryptoCreate with an evm address alias, derived from
     * a key, different than the key being set for the account, also adds a {@link JWildcardECDSAKey} to the {@code otherKeys},
     *  <strong>but that {@link JWildcardECDSAKey} may not be connected to a finalization.</strong>
     *
     * @param txnSigs the list of transaction signatures
     * @param payerKey the payer key of the current txn
     * @param otherKeys the other req keys for the current txn
     * @param aliasManager an alias resolver
     * @return an instance of {@link HollowScreenResult}
     */
    static HollowScreenResult performFor(
            @NonNull final List<TransactionSignature> txnSigs,
            @NonNull final JKey payerKey,
            @NonNull final List<JKey> otherKeys,
            @NonNull final AliasManager aliasManager) {
        final var evmAddressToEcdsaKeyIndex = createEvmAddressToEcdsaKeyIndexFrom(txnSigs);

        List<PendingCompletion> pendingCompletions = null;
        JKey replacedPayerKey = null;
        if (payerKey.hasWildcardECDSAKey()) {
            final var hollowKey = payerKey.getWildcardECDSAKey();
            final var payerEvmAddress = hollowKey.getEvmAddress();
            final var correspondingKey = evmAddressToEcdsaKeyIndex.get(Bytes.of(payerEvmAddress));
            if (correspondingKey != null) {
                replacedPayerKey = correspondingKey;
                pendingCompletions =
                        maybeAddToCompletions(payerEvmAddress, correspondingKey, pendingCompletions, aliasManager);
            }
        }

        List<JKey> replacedOtherKeys = null;
        for (int i = 0; i < otherKeys.size(); i++) {
            final var otherKey = otherKeys.get(i);
            if (otherKey.hasWildcardECDSAKey()) {
                final var hollowKey = otherKey.getWildcardECDSAKey();
                final var evmAddress = hollowKey.getEvmAddress();
                final var correspondingKey = evmAddressToEcdsaKeyIndex.get(Bytes.of(evmAddress));
                if (correspondingKey != null) {
                    if (replacedOtherKeys == null) {
                        replacedOtherKeys = new ArrayList<>(otherKeys);
                    }
                    replacedOtherKeys.set(i, correspondingKey);
                    if (hollowKey.isForHollowAccount()) {
                        pendingCompletions =
                                maybeAddToCompletions(evmAddress, correspondingKey, pendingCompletions, aliasManager);
                    }
                }
            }
        }
        return new HollowScreenResult(pendingCompletions, replacedPayerKey, replacedOtherKeys);
    }

    /**
     * Given a list of {@link TransactionSignature}, go through the signatures, and construct a
     * look-up table for all present evm address <-> ECDSA key pairs in the sigs.
     *
     * <p>Serves as an efficient way of obtaining a {@link JECDSASecp256k1Key} corresponding to {@link JWildcardECDSAKey}
     * in subsequent logic.
     *
     * @param txnSigs a list of {@link TransactionSignature}
     * @return a {@code Map} of all present evm addresses <-> {@link JECDSASecp256k1Key} pairs in the {@code txnSigs}
     */
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
