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
package com.hedera.services.state.tasks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.logic.RecordStreaming;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TraceabilityRecordsHelperTest {
    private final EntityNum someContract = EntityNum.fromLong(1234);
    private final Instant now = Instant.ofEpochSecond(1_234_567L, 890);

    private final TransactionID mockSystemTxnId =
            TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.789")).build();

    @Mock private RecordStreaming recordStreaming;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private ConsensusTimeTracker consensusTimeTracker;

    private TransactionBody.Builder updateBuilder = TransactionBody.newBuilder();
    private TransactionSidecarRecord.Builder aBuilder = TransactionSidecarRecord.newBuilder();
    private TransactionSidecarRecord.Builder bBuilder = TransactionSidecarRecord.newBuilder();

    private TraceabilityRecordsHelper subject;

    @BeforeEach
    void setUp() {
        subject =
                new TraceabilityRecordsHelper(
                        recordStreaming,
                        recordsHistorian,
                        syntheticTxnFactory,
                        consensusTimeTracker);
    }

    @Test
    void cannotExportIfNoTimeLeft() {
        assertFalse(subject.canExportNow());
    }

    @Test
    void happyPathWorks() {
        ArgumentCaptor<RecordStreamObject> captor =
                ArgumentCaptor.forClass(RecordStreamObject.class);

        given(consensusTimeTracker.nextStandaloneRecordTime()).willReturn(now);
        given(recordsHistorian.computeNextSystemTransactionId())
                .willReturn(TxnId.fromGrpc(mockSystemTxnId));
        given(syntheticTxnFactory.synthNoopContractUpdate(someContract)).willReturn(updateBuilder);

        subject.exportSidecarsViaSynthUpdate(someContract.longValue(), List.of(aBuilder, bBuilder));

        verify(recordStreaming).streamSystemRecord(captor.capture());
        final var rso = captor.getValue();

        assertEquals(rso.getTimestamp(), now);
        final var streamedRecord = rso.getExpirableTransactionRecord();
        final var streamedReceipt = streamedRecord.getReceipt();
        assertEquals(ResponseCodeEnum.SUCCESS, streamedReceipt.getEnumStatus());
        assertEquals(now, streamedRecord.getConsensusTime().toJava());
        final var accessor = SignedTxnAccessor.uncheckedFrom(rso.getTransaction());
        assertEquals(mockSystemTxnId, accessor.getTxnId());
    }
}
