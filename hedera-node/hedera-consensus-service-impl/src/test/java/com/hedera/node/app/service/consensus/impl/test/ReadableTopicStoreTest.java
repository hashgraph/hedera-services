/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl.test;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusHandlerTestBase;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadableTopicStoreTest extends ConsensusHandlerTestBase {
    private ReadableTopicStore subject;

    @BeforeEach
    void setUp() {
        subject = new ReadableTopicStore(states);
    }

    @Test
    void getsTopicMetadataIfTopicExists() {
        givenValidTopic();
        final var topicMeta = subject.getTopicMetadata(topicId);

        assertNotNull(topicMeta);
        assertNotNull(topicMeta.metadata());
        assertFalse(topicMeta.failed());

        final var meta = topicMeta.metadata();
        assertEquals(topicNum, meta.key());
        assertEquals(Optional.of(adminKey), meta.adminKey());
        assertEquals(Optional.of(adminKey), meta.submitKey());
        assertEquals(1L, meta.sequenceNumber());
        assertEquals(100L, meta.autoRenewDurationSeconds());
        assertEquals(Optional.of(autoRenewId.getAccountNum()), meta.autoRenewAccountId());
        assertEquals(Optional.of(memo), meta.memo());
        assertFalse(meta.isDeleted());
        assertArrayEquals(new byte[48], meta.runningHash());
    }

    @Test
    void constructorCreatesTopicState() {
        final var store = new ReadableTopicStore(states);
        assertNotNull(store);
    }
}
