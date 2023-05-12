/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.utility;

import com.swirlds.common.utility.DurationUtils;
import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DurationUtilsTest {
    @Test
    void isLongerTest() {
        Assertions.assertTrue(
                DurationUtils.isLonger(Duration.ofSeconds(2), Duration.ofSeconds(1)), "2 should be bigger than 1");
        Assertions.assertFalse(
                DurationUtils.isLonger(Duration.ofSeconds(1), Duration.ofSeconds(1)), "1==1, so not longer");
        Assertions.assertFalse(
                DurationUtils.isLonger(Duration.ofSeconds(1), Duration.ofSeconds(2)), "1 < 2, so not longer");
    }

    @Test
    void maxDurationTest() {
        Assertions.assertEquals(
                Duration.ofSeconds(2),
                DurationUtils.max(Duration.ofSeconds(1), Duration.ofSeconds(2)),
                "2 should be the max");
        Assertions.assertEquals(
                Duration.ofSeconds(2),
                DurationUtils.max(Duration.ofSeconds(2), Duration.ofSeconds(1)),
                "2 should be the max");
        Assertions.assertEquals(
                Duration.ofSeconds(1),
                DurationUtils.max(Duration.ofSeconds(1), Duration.ofSeconds(1)),
                "1 should be the max");
    }
}
