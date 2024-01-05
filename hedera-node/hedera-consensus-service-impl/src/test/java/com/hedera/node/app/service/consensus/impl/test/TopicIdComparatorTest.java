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

package com.hedera.node.app.service.consensus.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.node.app.service.consensus.impl.TopicIdComparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TopicIdComparatorTest {
    private TopicIdComparator subject;

    @BeforeEach
    void setUp() {
        subject = new TopicIdComparator();
    }

    @SuppressWarnings("EqualsWithItself")
    @Test
    void topicIdComparatorWorks() {
        assertEquals(0, subject.compare(null, null));
        assertTrue(subject.compare(null, TopicID.newBuilder().topicNum(1L).build()) < 0);
        assertTrue(subject.compare(TopicID.newBuilder().topicNum(1L).build(), null) > 0);
        assertTrue(subject.compare(
                        TopicID.newBuilder().topicNum(1L).build(),
                        TopicID.newBuilder().topicNum(2L).build())
                < 0);
        assertTrue(subject.compare(
                        TopicID.newBuilder().topicNum(4L).build(),
                        TopicID.newBuilder().topicNum(3L).build())
                > 0);
        assertTrue(subject.compare(
                        TopicID.newBuilder().shardNum(1).topicNum(1L).build(),
                        TopicID.newBuilder().shardNum(2).topicNum(1L).build())
                < 0);
        assertTrue(subject.compare(
                        TopicID.newBuilder().realmNum(1).topicNum(1L).build(),
                        TopicID.newBuilder().realmNum(2).topicNum(1L).build())
                < 0);
    }
}
