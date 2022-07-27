package com.hedera.services.mocks;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.ExpirableTxnRecord.Builder;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.utils.accessors.TxnAccessor;
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
    public void setLedger(HederaLedger ledger) {
        // No-op
    }

    @Override
    public ExpirableTxnRecord saveExpiringRecord(
            AccountID id,
            ExpirableTxnRecord expiringRecord,
            long consensusTime,
            long submittingMember) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Builder createTopLevelRecord(
            long fee,
            byte[] hash,
            TxnAccessor accessor,
            Instant consensusTime,
            TxnReceipt.Builder receiptBuilder,
            List<FcAssessedCustomFee> assessedCustomFees,
            SideEffectsTracker sideEffectsTracker) {
        return ExpirableTxnRecord.newBuilder();
    }

    @Override
    public Builder createSuccessfulSyntheticRecord(
            List<FcAssessedCustomFee> assessedCustomFees,
            SideEffectsTracker sideEffectsTracker,
            String memo) {
        return ExpirableTxnRecord.newBuilder();
    }

    @Override
    public Builder createUnsuccessfulSyntheticRecord(ResponseCodeEnum failureReason) {
        return ExpirableTxnRecord.newBuilder();
    }

    @Override
    public Builder createInvalidFailureRecord(TxnAccessor accessor, Instant consensusTimestamp) {
        return ExpirableTxnRecord.newBuilder();
    }
}
