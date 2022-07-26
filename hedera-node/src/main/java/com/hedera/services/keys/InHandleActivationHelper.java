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
package com.hedera.services.keys;

import static com.hedera.services.keys.HederaKeyTraversal.visitSimpleKeys;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides information about the primitive keys that compose the Hedera keys linked to the active
 * transaction (and the schedule it references, if applicable).
 *
 * <p>In particular, lets a visitor traverse these primitive keys along with their expanded {@code
 * TransactionSignature}s (if present).
 */
public class InHandleActivationHelper {
    private static final List<JKey> NO_OTHER_PARTIES = null;
    private static final TxnAccessor NO_LAST_ACCESSOR = null;
    private static final Function<byte[], TransactionSignature> NO_LAST_SIGS_FN = null;

    static Activation activation = HederaKeyActivation::isActive;

    private final CharacteristicsFactory characteristics;
    private final Supplier<SwirldsTxnAccessor> accessorSource;

    private List<JKey> otherParties = NO_OTHER_PARTIES;
    private TxnAccessor accessor = NO_LAST_ACCESSOR;
    private Function<byte[], TransactionSignature> sigsFn = NO_LAST_SIGS_FN;

    public InHandleActivationHelper(
            CharacteristicsFactory characteristics, Supplier<SwirldsTxnAccessor> accessorSource) {
        this.characteristics = characteristics;
        this.accessorSource = accessorSource;
    }

    /**
     * Returns true if the set of primitive signing keys for the active transaction suffice to meet
     * the signing requirements of all Hedera keys prerequisite to the active transaction.
     *
     * @param tests the predicate(s) to use for testing if an primitive key has signed
     * @return whether or not the given set of keys are sufficient for signing the active
     *     transaction
     */
    public boolean areOtherPartiesActive(BiPredicate<JKey, TransactionSignature> tests) {
        ensureUpToDate();
        return arePartiesActive(false, accessor.getTxn(), tests);
    }

    /**
     * Returns true if the set of primitive signing keys for the active transaction suffice to meet
     * the signing requirements of all Hedera keys prerequisite to the schedule referenced by the
     * active transaction.
     *
     * @param scheduledTxn the scheduled transaction
     * @param tests the predicate(s) to use for testing if a primitive key has signed
     * @return whether or not the given set of keys are sufficient for signing the schedule
     *     referenced by the active transaction
     */
    public boolean areScheduledPartiesActive(
            TransactionBody scheduledTxn, BiPredicate<JKey, TransactionSignature> tests) {
        ensureUpToDate();
        return arePartiesActive(true, scheduledTxn, tests);
    }

    /**
     * Permits a visitor to traverse the primitive keys, and their expanded signatures, that
     * constitute the Hedera keys prerequisite to the schedule referenced by the active transaction.
     *
     * @param visitor the consumer to give the tour to
     */
    public void visitScheduledCryptoSigs(BiConsumer<JKey, TransactionSignature> visitor) {
        ensureUpToDate();
        for (final var req : otherParties) {
            if (req.isForScheduledTxn()) {
                visitSimpleKeys(
                        req, key -> visitor.accept(key, sigsFn.apply(key.primitiveKeyIfPresent())));
            }
        }
    }

    /**
     * Returns the canonical mapping between primitive public keys and expanded signatures for the
     * active transaction.
     *
     * @return the canonical mapping between primitive public keys and expanded signatures for the
     *     active transaction.
     */
    public Function<byte[], TransactionSignature> currentSigsFn() {
        return sigsFn;
    }

    private boolean arePartiesActive(
            boolean useScheduleKeys,
            TransactionBody txn,
            BiPredicate<JKey, TransactionSignature> givenTests) {
        final var activeCharacter = characteristics.inferredFor(txn);
        for (final var key : otherParties) {
            if (key.isForScheduledTxn() != useScheduleKeys) {
                continue;
            }
            if (!activation.test(key, sigsFn, givenTests, activeCharacter)) {
                return false;
            }
        }
        return true;
    }

    private void ensureUpToDate() {
        var current = accessorSource.get();
        if (accessor != current) {
            final var sigMeta = current.getSigMeta();
            if (sigMeta.couldRationalizeOthers()) {
                otherParties = sigMeta.othersReqSigs();
            } else {
                otherParties = Collections.emptyList();
            }
            sigsFn = sigMeta.pkToVerifiedSigFn();
            accessor = current;
        }
    }

    @FunctionalInterface
    public interface Activation {
        boolean test(
                JKey key,
                Function<byte[], TransactionSignature> sigsFn,
                BiPredicate<JKey, TransactionSignature> tests,
                KeyActivationCharacteristics characteristics);
    }
}
