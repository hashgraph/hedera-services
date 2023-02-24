/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.consensus.impl.test.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.consensus.entity.Topic;
import com.hedera.node.app.service.consensus.impl.entity.TopicBuilderImpl;
import com.hedera.node.app.service.consensus.impl.entity.TopicImpl;
import com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusHandlerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TopicImplTest extends ConsensusHandlerTestBase {
    private Topic subject;

    @BeforeEach
    void setUp() {
        subject = setUpTopicImpl();
    }

    @Test
    void equalsWorks() {
        final var o1 = subject;
        final var o2 = o1.copy().build();
        final var o3 = o1.copy().memo("test1").build();
        assertEquals(o1, o1);
        assertEquals(o1, o2);
        assertNotEquals(o1, o3);
        assertNotEquals(null, o1);
        assertNotNull(o1);
        assertNotNull(o2);
    }

    @Test
    void hashCodeWorks() {
        assertEquals(482_675_570, subject.hashCode());
    }

    @Test
    void toStringWorks() {
        final var actual = subject.toString();
        final var expected = "TopicImpl{adminKey=<JThresholdKey: thd=2, keys=<JKeyList: keys=[<JEd25519Key: ed25519 hex=6161616161616161616161616161616161616161616161616161616161616161>, <JEd25519Key: ed25519 hex=6262626262626262626262626262626262626262626262626262626262626262>, <JThresholdKey: thd=2, keys=<JKeyList: keys=[<JEd25519Key: ed25519 hex=6161616161616161616161616161616161616161616161616161616161616161>, <JEd25519Key: ed25519 hex=6262626262626262626262626262626262626262626262626262626262626262>, <JEd25519Key: ed25519 hex=6363636363636363636363636363636363636363636363636363636363636363>]>>]>>, submitKey=<JThresholdKey: thd=2, keys=<JKeyList: keys=[<JEd25519Key: ed25519 hex=6161616161616161616161616161616161616161616161616161616161616161>, <JEd25519Key: ed25519 hex=6262626262626262626262626262626262626262626262626262626262626262>, <JThresholdKey: thd=2, keys=<JKeyList: keys=[<JEd25519Key: ed25519 hex=6161616161616161616161616161616161616161616161616161616161616161>, <JEd25519Key: ed25519 hex=6262626262626262626262626262626262626262626262626262626262626262>, <JEd25519Key: ed25519 hex=6363636363636363636363636363636363636363636363636363636363636363>]>>]>>, memo='test memo', autoRenewAccountNumber=4, autoRenewSecs=100, expiry=1234567, deleted=true, sequenceNumber=1}";
        assertEquals(expected, actual);
    }

    @Test
    void gettersWork() {
        assertEquals(topicId.getTopicNum(), subject.topicNumber());
        assertSame(hederaKey, subject.getAdminKey().get());
        assertSame(hederaKey, subject.getSubmitKey().get());
        assertEquals(memo, subject.memo());
        assertEquals(autoRenewId.getAccountNum(), subject.autoRenewAccountNumber());
        assertEquals(autoRenewSecs, subject.autoRenewSecs());
        assertTrue(subject.deleted());
        assertEquals(sequenceNumber, subject.sequenceNumber());
        assertEquals(expirationTime, subject.expiry());
    }

    @Test
    void gettersWorkForDefaultAccount() {
        subject = new TopicBuilderImpl().build();
        assertFalse(subject.deleted());
    }
}
