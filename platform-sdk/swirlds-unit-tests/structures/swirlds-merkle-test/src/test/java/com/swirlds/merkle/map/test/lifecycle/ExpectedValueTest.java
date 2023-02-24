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

package com.swirlds.merkle.map.test.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ExpectedValueTest {

    @Test
    void getDefaultUidTest() {
        final ExpectedValue value = new ExpectedValue(null, null);
        assertEquals(0, value.getUid(), "No uid set, so default for long should be returned");
    }

    @Test
    void getAssignedUidTest() {
        final Random random = new Random();
        final long uid = random.nextLong();
        final ExpectedValue value = new ExpectedValue(null, null, false, null, null, null, uid);
        assertEquals(uid, value.getUid(), "Uid assigned should match");
    }

    @Test
    void getSetUidTest() {
        final Random random = new Random();
        final long uid = random.nextLong();
        final ExpectedValue value = new ExpectedValue(null, null);
        value.setUid(uid);
        assertEquals(uid, value.getUid(), "Uid set should match");
    }
}
