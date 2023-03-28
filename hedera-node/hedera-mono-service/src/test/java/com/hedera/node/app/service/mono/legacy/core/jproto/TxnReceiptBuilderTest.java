/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.legacy.core.jproto;

import static com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt.MISSING_NEW_TOTAL_SUPPLY;
import static com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt.MISSING_RUNNING_HASH_VERSION;
import static com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt.MISSING_TOPIC_SEQ_NO;
import static com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt.REVERTED_SUCCESS_LITERAL;
import static com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt.SUCCESS_LITERAL;
import static com.hedera.node.app.service.mono.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.node.app.service.mono.state.submerkle.TxnId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TxnReceiptBuilderTest {
    private TxnReceipt.Builder subject;

    @BeforeEach
    void setUp() {
        subject = TxnReceipt.newBuilder();
    }

    @Test
    void doesntOverrideStatusForUnsuccessful() {
        final var failureStatus = "INVALID_ACCOUNT_ID";

        subject.setStatus(failureStatus);

        subject.revert();

        assertSame(failureStatus, subject.getStatus());
    }

    @Test
    void revertsSideEffectsForSuccessAsExpected() {
        subject.setStatus("SUCCESS");
        subject.setAccountId(MISSING_ENTITY_ID);
        subject.setContractId(MISSING_ENTITY_ID);
        subject.setFileId(MISSING_ENTITY_ID);
        subject.setTokenId(MISSING_ENTITY_ID);
        subject.setTopicId(MISSING_ENTITY_ID);
        subject.setScheduleId(MISSING_ENTITY_ID);
        subject.setScheduledTxnId(new TxnId());
        subject.setNewTotalSupply(123);
        subject.setSerialNumbers(new long[] {1, 2, 3});
        subject.setRunningHashVersion(1);
        subject.setTopicRunningHash("ABC".getBytes());
        subject.setTopicSequenceNumber(321);

        subject.revert();

        assertEquals(REVERTED_SUCCESS_LITERAL, subject.getStatus());
        assertNull(subject.getAccountId());
        assertNull(subject.getContractId());
        assertNull(subject.getFileId());
        assertNull(subject.getTokenId());
        assertNull(subject.getTopicId());
        assertNull(subject.getScheduleId());
        assertNull(subject.getScheduledTxnId());
        assertNull(subject.getSerialNumbers());
        assertNull(subject.getTopicRunningHash());
        assertEquals(MISSING_TOPIC_SEQ_NO, subject.getTopicSequenceNumber());
        assertEquals(MISSING_NEW_TOTAL_SUPPLY, subject.getNewTotalSupply());
        assertEquals(MISSING_RUNNING_HASH_VERSION, subject.getRunningHashVersion());
    }

    @Test
    void doesNotRevertIfNonRevertable() {
        subject.setStatus(SUCCESS_LITERAL);
        subject.setAccountId(MISSING_ENTITY_ID);
        subject.setContractId(MISSING_ENTITY_ID);
        subject.setFileId(MISSING_ENTITY_ID);
        subject.setTokenId(MISSING_ENTITY_ID);
        subject.setTopicId(MISSING_ENTITY_ID);
        subject.setScheduleId(MISSING_ENTITY_ID);
        final TxnId scheduledTxnId = new TxnId();
        subject.setScheduledTxnId(scheduledTxnId);
        subject.setNewTotalSupply(123);
        final long[] serialNumbers = {1, 2, 3};
        subject.setSerialNumbers(serialNumbers);
        subject.setRunningHashVersion(1);
        subject.setTopicRunningHash("ABC".getBytes());
        subject.setTopicSequenceNumber(321);
        subject.nonRevertable();

        subject.revert();

        assertEquals(SUCCESS_LITERAL, subject.getStatus());
        assertEquals(MISSING_ENTITY_ID, subject.getAccountId());
        assertEquals(MISSING_ENTITY_ID, subject.getContractId());
        assertEquals(MISSING_ENTITY_ID, subject.getFileId());
        assertEquals(MISSING_ENTITY_ID, subject.getTokenId());
        assertEquals(MISSING_ENTITY_ID, subject.getTopicId());
        assertEquals(MISSING_ENTITY_ID, subject.getScheduleId());
        assertEquals(MISSING_ENTITY_ID, subject.getScheduleId());
        assertEquals(scheduledTxnId, subject.getScheduledTxnId());
        assertEquals(123, subject.getNewTotalSupply());
        assertArrayEquals(serialNumbers, subject.getSerialNumbers());
        assertEquals(1, subject.getRunningHashVersion());
    }

}
