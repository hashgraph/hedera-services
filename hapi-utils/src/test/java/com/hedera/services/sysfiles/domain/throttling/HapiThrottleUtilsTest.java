/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sysfiles.domain.throttling;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.TestUtils;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class HapiThrottleUtilsTest {
    @Test
    void factoryWorks() throws IOException {
        final var proto = TestUtils.protoDefs("bootstrap/throttles.json");

        final var bucketA = proto.getThrottleBuckets(0);

        final var fromResult = HapiThrottleUtils.hapiBucketFromProto(bucketA);
        final var fromToResult = HapiThrottleUtils.hapiBucketToProto(fromResult);
        assertEquals(bucketA, fromToResult);
    }
}
