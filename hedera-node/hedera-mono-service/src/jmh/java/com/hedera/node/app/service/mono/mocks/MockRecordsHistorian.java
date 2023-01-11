/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.mocks;

import com.hedera.node.app.service.mono.records.InProgressChildRecord;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.TxnId;
import com.hedera.node.app.service.mono.stream.RecordStreamObject;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import com.swirlds.common.crypto.RunningHash;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MockRecordsHistorian implements RecordsHistorian {
    @Inject
    public MockRecordsHistorian() {
        // Dagger2
    }

    @Override
    public void clearHistory() {
        // No-op
    }

    @Override
    public void setCreator(final EntityCreator creator) {
        // No-op
    }

    @Override
    public void saveExpirableTransactionRecords() {
        // No-op
    }

    @Override
    public RecordStreamObject getTopLevelRecord() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasFollowingChildRecords() {
        return false;
    }

    @Override
    public boolean hasPrecedingChildRecords() {
        return false;
    }

    @Override
    public List<RecordStreamObject> getFollowingChildRecords() {
        return List.of();
    }

    @Override
    public List<RecordStreamObject> getPrecedingChildRecords() {
        return List.of();
    }

    @Override
    public int nextChildRecordSourceId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void trackFollowingChildRecord(
            final int sourceId,
            final Builder syntheticBody,
            final ExpirableTxnRecord.Builder recordSoFar,
            final List<TransactionSidecarRecord.Builder> sidecars) {
        // No-op
    }

    @Override
    public void trackPrecedingChildRecord(
            final int sourceId,
            final Builder syntheticBody,
            final ExpirableTxnRecord.Builder recordSoFar) {
        // No-op
    }

    @Override
    public void revertChildRecordsFromSource(final int sourceId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void noteNewExpirationEvents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant nextFollowingChildConsensusTime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void customizeSuccessor(
            final Predicate<InProgressChildRecord> matcher,
            final Consumer<InProgressChildRecord> customizer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RunningHash lastRunningHash() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean nextSystemTransactionIdIsUnknown() {
        return true;
    }

    @Override
    public TxnId computeNextSystemTransactionId() {
        throw new UnsupportedOperationException();
    }
}
