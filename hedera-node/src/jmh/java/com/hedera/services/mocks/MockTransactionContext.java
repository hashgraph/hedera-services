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
package com.hedera.services.mocks;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.setup.Constructables;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord.Builder;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MockTransactionContext implements TransactionContext {
    private Instant now = Constructables.SOME_TIME;

    @Override
    public List<TransactionSidecarRecord.Builder> sidecars() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addSidecarRecord(TransactionSidecarRecord.Builder sidecar) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addFeeChargedToPayer(long amount) {
        throw new UnsupportedOperationException();
    }

    @Inject
    public MockTransactionContext() {
        // Dagger2
    }

    @Override
    public void resetFor(
            @Nullable TxnAccessor accessor, Instant consensusTime, long submittingMember) {
        now = consensusTime;
    }

    @Override
    public boolean isPayerSigKnownActive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccountID submittingNodeAccount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long submittingSwirldsMember() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccountID activePayer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JKey activePayerKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant consensusTime() {
        return now;
    }

    @Override
    public ResponseCodeEnum status() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Builder recordSoFar() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TxnAccessor accessor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SwirldsTxnAccessor swirldsTxnAccessor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStatus(ResponseCodeEnum status) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreated(FileID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreated(AccountID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTargetedContract(ContractID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreated(TopicID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreated(ScheduleID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setScheduledTxnId(TransactionID txnId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCallResult(EvmFnResult result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateForEvmCall(EthTxData callContext, EntityId senderId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreateResult(EvmFnResult result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void payerSigIsKnownActive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTopicRunningHash(byte[] runningHash, long sequenceNumber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void trigger(TxnAccessor accessor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TxnAccessor triggeredTxn() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addExpiringEntities(Collection<ExpiringEntity> expiringEntities) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ExpiringEntity> expiringEntities() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAssessedCustomFees(List<FcAssessedCustomFee> assessedCustomFees) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasContractResult() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getGasUsedForContractTxn() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void recordBeneficiaryOfDeleted(long accountNum, long beneficiaryNum) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getBeneficiaryOfDeleted(long accountNum) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int numDeletedAccountsAndContracts() {
        throw new UnsupportedOperationException();
    }
}
