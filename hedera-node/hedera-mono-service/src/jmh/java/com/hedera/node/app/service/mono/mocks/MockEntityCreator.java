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
package com.hedera.node.app.service.mono.mocks;

import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.ledger.HederaLedger;
import com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord.Builder;
import com.hedera.node.app.service.mono.state.submerkle.FcAssessedCustomFee;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MockEntityCreator implements EntityCreator {
    @Inject
    public MockEntityCreator() {
        // Dagger2
    }

    @Override
    public void setLedger(final HederaLedger ledger) {
        // No-op
    }

    @Override
    public ExpirableTxnRecord saveExpiringRecord(
            final AccountID id,
            final ExpirableTxnRecord expiringRecord,
            final long consensusTime,
            final long submittingMember) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Builder createTopLevelRecord(
            final long fee,
            final byte[] hash,
            final TxnAccessor accessor,
            final Instant consensusTime,
            final TxnReceipt.Builder receiptBuilder,
            final List<FcAssessedCustomFee> assessedCustomFees,
            final SideEffectsTracker sideEffectsTracker) {
        return ExpirableTxnRecord.newBuilder();
    }

    @Override
    public Builder createSuccessfulSyntheticRecord(
            final List<FcAssessedCustomFee> assessedCustomFees,
            final SideEffectsTracker sideEffectsTracker,
            final String memo) {
        return ExpirableTxnRecord.newBuilder();
    }

    @Override
    public Builder createUnsuccessfulSyntheticRecord(final ResponseCodeEnum failureReason) {
        return ExpirableTxnRecord.newBuilder();
    }

    @Override
    public Builder createInvalidFailureRecord(
            final TxnAccessor accessor, final Instant consensusTimestamp) {
        return ExpirableTxnRecord.newBuilder();
    }
}
