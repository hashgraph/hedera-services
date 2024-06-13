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

import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransactionSizeUtilsTest {
    private static final SplittableRandom RANDOM = new SplittableRandom();

    @ParameterizedTest
    @MethodSource("buildArguments")
    void testSizeComparisons(final OneOf<PayloadOneOfType> payload, final SwirldTransaction swirldTransaction) {
        assertEquals((int) TransactionSizeUtils.getTransactionSize(payload), swirldTransaction.getSerializedLength());

    }

    protected static Stream<Arguments> buildArguments() {
        final List<Arguments> arguments = new ArrayList<>();
        // arguments.add(Arguments.of(0, keyValueProvider));

        IntStream.range(0, 100).forEach(i -> {
            final var payload = randomBytes();
            arguments.add(Arguments.of(new OneOf<>(PayloadOneOfType.APPLICATION_PAYLOAD, payload), new SwirldTransaction(payload)));
        });

        return arguments.stream();
    }


    private static Bytes randomBytes() {
        final var bytes = new byte[RANDOM.nextInt(1, 1024 / 32)];
        RANDOM.nextBytes(bytes);
        return Bytes.wrap(bytes);
    }
}
