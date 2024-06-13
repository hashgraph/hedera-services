/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.hapi.platform.event.StateSignaturePayload;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransactionUtilsTest {
    private static final SplittableRandom RANDOM = new SplittableRandom();

    @ParameterizedTest
    @MethodSource("buildArgumentsSwirldTransactions")
    void testSizeComparisonsSwirldTransactions(final OneOf<PayloadOneOfType> payload, final SwirldTransaction swirldTransaction) {
        assertEquals((int) TransactionUtils.getTransactionSize(payload), swirldTransaction.getSerializedLength());
        assertFalse(TransactionUtils.isSystemTransaction(payload));
    }

    protected static Stream<Arguments> buildArgumentsSwirldTransactions() {
        final List<Arguments> arguments = new ArrayList<>();

        IntStream.range(0, 100).forEach(i -> {
            final var payload = randomBytes();
            arguments.add(Arguments.of(
                    new OneOf<>(PayloadOneOfType.APPLICATION_PAYLOAD, payload), new SwirldTransaction(payload)));
        });

        return arguments.stream();
    }

    @ParameterizedTest
    @MethodSource("buildArgumentsStateSignatureTransaction")
    void testSizeComparisonsStateSignatureTransaction(final OneOf<PayloadOneOfType> payload, final StateSignatureTransaction stateSignatureTransaction) {
        assertEquals((int) TransactionUtils.getTransactionSize(payload), stateSignatureTransaction.getSerializedLength());
        assertTrue(TransactionUtils.isSystemTransaction(payload));
    }

    protected static Stream<Arguments> buildArgumentsStateSignatureTransaction() {
        final List<Arguments> arguments = new ArrayList<>();

        IntStream.range(0, 100).forEach(i -> {
            final var payload = StateSignaturePayload.newBuilder().hash(randomBytes()).signature(randomBytes()).build();
            arguments.add(Arguments.of(
                    new OneOf<>(PayloadOneOfType.STATE_SIGNATURE_PAYLOAD, payload), new StateSignatureTransaction(payload)));
        });

        return arguments.stream();
    }

    private static Bytes randomBytes() {
        final var bytes = new byte[RANDOM.nextInt(1, 1024 / 32)];
        RANDOM.nextBytes(bytes);
        return Bytes.wrap(bytes);
    }
}
