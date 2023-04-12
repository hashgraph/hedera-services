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

package com.hedera.node.app.service.mono.utils.replay;

import static com.hedera.node.app.service.mono.sigs.order.SigRequirementsTest.sanityRestored;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;

import java.util.List;

class PbjLeafConvertersTest {
    private static final long AUTO_RENEW_DURATION_SECONDS = 7776000L;
    private static final EntityNum SOME_NUMBER = EntityNum.fromLong(666L);
    private static final long SOME_SEQ_NO = 789L;
    private static final byte[] SOME_RUNNING_HASH = "abcdefghabcdefghabcdefghabcdefghabcdefghabcdefgh".getBytes();
    private static final RichInstant EXPIRY = new RichInstant(1_234_567, 890);

    @Test
    void canConvertWithNoOptionalFields() {
        final var topic = new MerkleTopic();
        setRequiredFields(topic);

        final var pbjTopic = PbjLeafConverters.leafFromMerkle(topic);

        assertRequiredFieldsAsExpected(pbjTopic);

        assertTrue(pbjTopic.memo().isEmpty());
        assertFalse(pbjTopic.hasAdminKey());
        assertFalse(pbjTopic.hasSubmitKey());
    }


    @Test
    void canConvertWithAllOptionalFields() {
        final var memo = "Some memo text";
        final var adminKey = new JEd25519Key("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes());
        final var submitKey = new JEd25519Key("cccccccccccccccccccccccccccccccc".getBytes());

        final var topic = new MerkleTopic();
        setRequiredFields(topic);
        topic.setMemo(memo);
        topic.setAdminKey(adminKey);
        topic.setSubmitKey(submitKey);

        final var pbjTopic = PbjLeafConverters.leafFromMerkle(topic);
        assertRequiredFieldsAsExpected(pbjTopic);
        assertEquals(memo, pbjTopic.memo());
        assertEquals(PbjConverter.toPbj(adminKey), pbjTopic.adminKey());
        assertEquals(PbjConverter.toPbj(submitKey), pbjTopic.submitKey());
    }

    private void assertRequiredFieldsAsExpected(final Topic topic) {
        assertEquals(AUTO_RENEW_DURATION_SECONDS, topic.autoRenewPeriod());
        assertPbjBytesEqual(SOME_RUNNING_HASH, topic.runningHash());
        assertEquals(SOME_NUMBER.longValue(), topic.topicNumber());
        assertEquals(SOME_SEQ_NO, topic.sequenceNumber());
        assertEquals(EXPIRY.getSeconds(), topic.expiry());
        assertTrue(topic.deleted());
    }

    public static void assertPbjBytesEqual(final byte[] expected, final Bytes actual) {
        assertArrayEquals(expected, PbjConverter.asBytes(actual));
    }

    private void setRequiredFields(final MerkleTopic topic) {
        topic.setKey(SOME_NUMBER);
        topic.setAutoRenewDurationSeconds(AUTO_RENEW_DURATION_SECONDS);
        topic.setExpirationTimestamp(EXPIRY);
        topic.setDeleted(true);
        topic.setSequenceNumber(SOME_SEQ_NO);
        topic.setRunningHash(SOME_RUNNING_HASH);
    }
}
