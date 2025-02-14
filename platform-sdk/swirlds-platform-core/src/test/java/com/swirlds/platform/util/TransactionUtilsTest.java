// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransactionUtilsTest {

    @ParameterizedTest
    @MethodSource("buildArgumentsAppTransactions")
    void testSizeComparisonsAppTransactions(
            final OneOf<TransactionOneOfType> oneOfTransaction,
            final Bytes transaction,
            final TransactionWrapper swirldTransaction) {
        assertEquals(TransactionUtils.getLegacyTransactionSize(transaction), swirldTransaction.getSize());
        assertFalse(TransactionUtils.isSystemTransaction(oneOfTransaction));
    }

    protected static Stream<Arguments> buildArgumentsAppTransactions() {
        final List<Arguments> arguments = new ArrayList<>();
        final Randotron randotron = Randotron.create();

        IntStream.range(0, 100).forEach(i -> {
            final Bytes payload = randotron.nextHashBytes();
            final OneOf<TransactionOneOfType> oneOfTransaction =
                    new OneOf<>(TransactionOneOfType.APPLICATION_TRANSACTION, payload);

            arguments.add(Arguments.of(oneOfTransaction, payload, new TransactionWrapper(payload)));
        });

        return arguments.stream();
    }

    @ParameterizedTest
    @MethodSource("buildArgumentsStateSignatureTransaction")
    void testSizeComparisonsStateSignatureTransaction(
            final OneOf<TransactionOneOfType> oneOfTransaction,
            final Bytes payload,
            final TransactionWrapper stateSignatureTransaction) {
        assertEquals(TransactionUtils.getLegacyTransactionSize(payload), stateSignatureTransaction.getSize());
        assertTrue(TransactionUtils.isSystemTransaction(oneOfTransaction));
    }

    protected static Stream<Arguments> buildArgumentsStateSignatureTransaction() {
        final List<Arguments> arguments = new ArrayList<>();
        final Randotron randotron = Randotron.create();

        IntStream.range(0, 100).forEach(i -> {
            final StateSignatureTransaction payload = StateSignatureTransaction.newBuilder()
                    .hash(randotron.nextHashBytes())
                    .signature(randotron.nextSignatureBytes())
                    .build();
            final Bytes bytes = StateSignatureTransaction.PROTOBUF.toBytes(payload);
            final OneOf<TransactionOneOfType> oneOfTransaction =
                    new OneOf<>(TransactionOneOfType.STATE_SIGNATURE_TRANSACTION, payload);

            arguments.add(Arguments.of(oneOfTransaction, bytes, new TransactionWrapper(bytes)));
        });

        return arguments.stream();
    }
}
