/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchException;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.ThrottleBucket;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.ThrottleGroup;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThrottleManagerTest {

    ThrottleGroup throttleGroup = ThrottleGroup.newBuilder()
            .operations(ThrottleManager.expectedOps.stream().toList())
            .milliOpsPerSec(100)
            .build();

    ThrottleBucket throttleBucket = ThrottleBucket.newBuilder()
            .name("throttle1")
            .burstPeriodMs(100L)
            .throttleGroups(throttleGroup)
            .build();

    ThrottleGroup throttleGroup2 = ThrottleGroup.newBuilder()
            .operations(List.of(HederaFunctionality.CONTRACT_CREATE))
            .milliOpsPerSec(120)
            .build();

    ThrottleBucket throttleBucket2 = ThrottleBucket.newBuilder()
            .name("throttle2")
            .burstPeriodMs(120L)
            .throttleGroups(throttleGroup2)
            .build();

    ThrottleDefinitions throttleDefinitions = ThrottleDefinitions.newBuilder()
            .throttleBuckets(throttleBucket, throttleBucket2)
            .build();
    Bytes throttleDefinitionsByes = ThrottleDefinitions.PROTOBUF.toBytes(throttleDefinitions);

    @LoggingSubject
    ThrottleManager subject;

    @BeforeEach
    void setup() {
        subject = new ThrottleManager();
    }

    @Test
    void onUpdatedHasExpectedFields() {
        // when
        subject.update(throttleDefinitionsByes);

        // expect
        assertEquals(throttleDefinitions, subject.throttleDefinitions());
        assertEquals(throttleDefinitions.throttleBuckets(), subject.throttleBuckets());
    }

    @Test
    void defaultExpectedFields() {
        assertEquals(ThrottleDefinitions.DEFAULT, subject.throttleDefinitions());
        assertEquals(ThrottleDefinitions.DEFAULT.throttleBuckets(), subject.throttleBuckets());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void invalidArgumentsOnUpdate() {
        assertThatThrownBy(() -> subject.update(null)).isInstanceOf(NullPointerException.class);

        final var notParsableBytes = Bytes.wrap(new byte[] {0x01});
        assertThatThrownBy(() -> subject.update(notParsableBytes))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS));
    }

    @Test
    void handleThrottleFileTxBodyWithEmptyListOfGroups() {
        // given
        final var throttleBucket = ThrottleBucket.newBuilder()
                .name("test")
                .burstPeriodMs(100)
                .throttleGroups(List.of()) // no throttle groups added
                .build();

        var throttleDefinitions = new ThrottleDefinitions(List.of(throttleBucket));

        // when
        final var result = subject.update(ThrottleDefinitions.PROTOBUF.toBytes(throttleDefinitions));

        // then
        assertThat(result).isEqualTo(ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION);
    }

    @Test
    void handleThrottleFileTxBodyWithNotAllRequiredOperations() {
        // given
        var throttleGroup = ThrottleGroup.newBuilder()
                .milliOpsPerSec(10)
                .operations(
                        List.of(CRYPTO_CREATE, CRYPTO_TRANSFER)) // setting only a few operations. We require a lot more
                .build();

        final var throttleBucket = ThrottleBucket.newBuilder()
                .name("test")
                .burstPeriodMs(100)
                .throttleGroups(List.of(throttleGroup))
                .build();

        var throttleDefinitions = new ThrottleDefinitions(List.of(throttleBucket));

        // when
        final var result = subject.update(ThrottleDefinitions.PROTOBUF.toBytes(throttleDefinitions));

        // then
        assertThat(result).isEqualTo(ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION);
    }

    @Test
    void handleThrottleFileTxBodyWithZeroOpsPerSec() {
        // given
        var throttleGroup = ThrottleGroup.newBuilder()
                .milliOpsPerSec(0) // the ops per sec should be more than 0
                .operations(ThrottleManager.expectedOps.stream().toList())
                .build();

        final var throttleBucket = ThrottleBucket.newBuilder()
                .name("test")
                .burstPeriodMs(100)
                .throttleGroups(List.of(throttleGroup))
                .build();

        var throttleDefinitions = new ThrottleDefinitions(List.of(throttleBucket));

        // when
        Exception exception =
                catchException(() -> subject.update(ThrottleDefinitions.PROTOBUF.toBytes(throttleDefinitions)));

        // then
        assertThat(exception).isInstanceOf(HandleException.class);
        assertThat(((HandleException) exception).getStatus())
                .isEqualTo(ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC);
    }

    @Test
    void handleThrottleFileTxBodyWithRepeatedOperation() {
        // given
        final var throttleGroup = ThrottleGroup.newBuilder()
                .milliOpsPerSec(10)
                .operations(ThrottleManager.expectedOps.stream().toList())
                .build();

        final var repeatedThrottleGroup = ThrottleGroup.newBuilder()
                .milliOpsPerSec(10)
                .operations(List.of(CRYPTO_CREATE)) // repeating an operation that exists in the first throttle group
                .build();

        final var throttleBucket = ThrottleBucket.newBuilder()
                .name("test")
                .burstPeriodMs(100)
                .throttleGroups(List.of(throttleGroup, repeatedThrottleGroup))
                .build();

        var throttleDefinitions = new ThrottleDefinitions(List.of(throttleBucket));

        // when
        Exception exception =
                catchException(() -> subject.update(ThrottleDefinitions.PROTOBUF.toBytes(throttleDefinitions)));

        // then
        assertThat(exception).isInstanceOf(HandleException.class);
        assertThat(((HandleException) exception).getStatus())
                .isEqualTo(ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS);
    }
}
