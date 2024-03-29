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

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.ThrottleBucket;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.ThrottleGroup;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

class ThrottleParserTest {

    ThrottleGroup throttleGroup = ThrottleGroup.newBuilder()
            .operations(ThrottleParser.EXPECTED_OPS.stream().toList())
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

    private ThrottleParser subject;

    @BeforeEach
    void setUp() {
        subject = new ThrottleParser();
    }
}
