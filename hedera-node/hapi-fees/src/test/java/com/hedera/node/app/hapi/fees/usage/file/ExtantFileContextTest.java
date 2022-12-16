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
package com.hedera.node.app.hapi.fees.usage.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.hapi.fees.test.KeyUtils;
import com.hederahashgraph.api.proto.java.KeyList;
import org.junit.jupiter.api.Test;

class ExtantFileContextTest {
    private final String memo = "Currently unavailable";
    private final long expiry = 1_234_567L;
    private final KeyList wacl = KeyUtils.A_KEY_LIST.getKeyList();
    private final long size = 54_321L;

    @Test
    void buildsAsExpected() {
        // given:
        final var subject =
                ExtantFileContext.newBuilder()
                        .setCurrentExpiry(expiry)
                        .setCurrentMemo(memo)
                        .setCurrentWacl(wacl)
                        .setCurrentSize(size)
                        .build();

        // expect:
        assertEquals(memo, subject.currentMemo());
        assertEquals(expiry, subject.currentExpiry());
        assertEquals(wacl, subject.currentWacl());
        assertEquals(size, subject.currentSize());
    }

    @Test
    void rejectsIncompleteContext() {
        // given:
        final var builder =
                ExtantFileContext.newBuilder()
                        .setCurrentExpiry(expiry)
                        .setCurrentMemo(memo)
                        .setCurrentWacl(wacl);

        // expect:
        assertThrows(IllegalStateException.class, builder::build);
    }
}
