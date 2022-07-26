/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.schedule;

import static com.hedera.services.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.hedera.services.keys.HederaKeyTraversal.visitSimpleKeys;
import static com.hedera.services.sigs.sourcing.PojoSigMapPubKeyToSigBytes.beginsWith;
import static com.hedera.services.txns.schedule.SigClassification.INVALID_SCHEDULED_TXN_MATCH;
import static com.hedera.services.txns.schedule.SigClassification.NO_MATCH;
import static com.hedera.services.txns.schedule.SigClassification.TOP_LEVEL_MATCH;
import static com.hedera.services.txns.schedule.SigClassification.VALID_SCHEDULED_TXN_MATCH;
import static com.swirlds.common.crypto.VerificationStatus.VALID;

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provides a best-effort attempt to classify the signing outcomes of the primitive keys linked to
 * the active schedule. (That is, the primitive keys that compose the Hedera keys prerequisite to
 * the active scheduled transaction.)
 *
 * <p>Note that if a linked primitive key did provide a valid signature, it will never cause the
 * {@code validScheduleKeys} method to return an empty {@code Optional}. And in general, a valid
 * signature with an unrelated primitive key will <i>also</i> not cause the {@code
 * validScheduleKeys} method to return an empty {@code Optional}.
 *
 * <p>But it <i>is</i> possible for collisions in public key prefixes to make it appear that a valid
 * signature with an unrelated primitive key was actually an invalid signature with a linked key.
 * See for example the {@code ScheduleSignSpecs.overlappingKeysTreatedAsExpected()} in the {@code
 * test-clients} module.
 */
public class SigMapScheduleClassifier {
    /**
     * Returns the list of primitive keys linked to the active schedule that had valid signatures on
     * the active transaction. If a linked primitive key was expanded to an invalid signature,
     * returns an empty {@code Optional} to indicate some signatures were invalid.
     *
     * <p>Makes a best-effort attempt to return an empty optional <i>only</i> if a {@code
     * SignaturePair} in the active {@code SignatureMap} expanded to exclusively invalid signatures;
     * and seems linked to the active schedule; and cannot be "explained" as an invalid top-level
     * signature (e.g., for a payer or admin key).
     *
     * @param topLevelKeys a list of keys known linked to the top-level transaction
     * @param sigMap the active transaction's signature map
     * @param sigsFn the active mapping from primitive keys to expanded signatures
     * @param scheduleCryptoSigs a traversal accepting a visitor to the primitive keys linked to the
     *     active schedule
     * @return the list of linked primitive keys with valid signatures, if none seems to have given
     *     an invalid signature
     */
    Optional<List<JKey>> validScheduleKeys(
            final List<JKey> topLevelKeys,
            final SignatureMap sigMap,
            final Function<byte[], TransactionSignature> sigsFn,
            final Consumer<BiConsumer<JKey, TransactionSignature>> scheduleCryptoSigs) {
        final List<JKey> valid = new ArrayList<>();

        for (final var sp : sigMap.getSigPairList()) {
            var prefix = sp.getPubKeyPrefix().toByteArray();
            final var classification = new MutableSigClassification();

            for (final var key : topLevelKeys) {
                updateForTopLevel(key, prefix, classification, sigsFn);
            }
            updateForScheduled(prefix, valid, classification, scheduleCryptoSigs);

            if (classification.get() == INVALID_SCHEDULED_TXN_MATCH) {
                return Optional.empty();
            }
        }

        return Optional.of(valid);
    }

    private void updateForScheduled(
            final byte[] prefix,
            final List<JKey> valid,
            final MutableSigClassification classification,
            final Consumer<BiConsumer<JKey, TransactionSignature>> scheduleCryptoSigs) {
        scheduleCryptoSigs.accept(
                (key, sig) -> {
                    final var pk = key.primitiveKeyIfPresent();
                    if (beginsWith(pk, prefix) && sig != INVALID_MISSING_SIG) {
                        if (sig.getSignatureStatus() == VALID) {
                            classification.considerSetting(VALID_SCHEDULED_TXN_MATCH);
                            valid.add(key);
                        } else {
                            classification.considerSetting(INVALID_SCHEDULED_TXN_MATCH);
                        }
                    }
                });
    }

    private void updateForTopLevel(
            JKey topLevelKey,
            byte[] prefix,
            MutableSigClassification classification,
            Function<byte[], TransactionSignature> sigsFn) {
        visitSimpleKeys(
                topLevelKey,
                key -> {
                    final var pk = key.primitiveKeyIfPresent();
                    if (beginsWith(pk, prefix)) {
                        var sig = sigsFn.apply(pk);
                        if (sig != INVALID_MISSING_SIG) {
                            classification.considerSetting(TOP_LEVEL_MATCH);
                        }
                    }
                });
    }

    private static class MutableSigClassification {
        private SigClassification current = NO_MATCH;

        SigClassification get() {
            return current;
        }

        void considerSetting(SigClassification candidate) {
            if (candidate.compareTo(current) > 0) {
                current = candidate;
            }
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(MutableSigClassification.class)
                    .add("current", current)
                    .toString();
        }
    }
}
