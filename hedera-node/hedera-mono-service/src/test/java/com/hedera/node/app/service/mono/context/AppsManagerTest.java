/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.context;

import static com.hedera.node.app.service.mono.context.AppsManager.APPS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.mono.ServicesApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppsManagerTest {
    private final long nodeIdA = 1L;
    private final long nodeIdB = 2L;

    @Mock private ServicesApp app;

    @AfterEach
    void cleanup() {
        if (APPS.includes(nodeIdA)) {
            APPS.clear(nodeIdA);
        }
        if (APPS.includes(nodeIdB)) {
            APPS.clear(nodeIdB);
        }
    }

    @Test
    void throwsIfNotInit() {
        // expect:
        assertThrows(IllegalArgumentException.class, () -> APPS.get(nodeIdA));
    }

    @Test
    void getsIfInit() {
        // given:
        APPS.save(nodeIdA, app);

        // expect:
        assertSame(app, APPS.get(nodeIdA));
    }

    @Test
    void recognizesInit() {
        // given:
        APPS.save(nodeIdA, app);

        // expect;
        assertTrue(APPS.includes(nodeIdA));
        assertFalse(APPS.includes(nodeIdB));
    }

    @Test
    void clearWorks() {
        // given:
        APPS.save(nodeIdA, app);

        // when:
        APPS.clear(nodeIdA);

        // then:
        assertFalse(APPS.includes(nodeIdA));
    }
}
