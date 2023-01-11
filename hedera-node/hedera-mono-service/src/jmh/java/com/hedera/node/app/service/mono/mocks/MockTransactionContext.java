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

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.setup.Constructables;
import com.hedera.node.app.service.mono.state.expiry.ExpiringEntity;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.EvmFnResult;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord.Builder;
import com.hedera.node.app.service.mono.state.submerkle.FcAssessedCustomFee;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
    public void addSidecarRecord(final TransactionSidecarRecord.Builder sidecar) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addFeeChargedToPayer(final long amount) {
        throw new UnsupportedOperationException();
    }

    @Inject
    public MockTransactionContext() {
        // Dagger2
    }

    @Override
    public void resetFor(
            @Nullable final TxnAccessor accessor,
            final Instant consensusTime,
            final long submittingMember) {
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
    public void setStatus(final ResponseCodeEnum status) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreated(final FileID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreated(final AccountID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEvmAddress(ByteString evmAddress) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTargetedContract(final ContractID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreated(final TopicID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreated(final ScheduleID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setScheduledTxnId(final TransactionID txnId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCallResult(final EvmFnResult result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateForEvmCall(final EthTxData callContext, final EntityId senderId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreateResult(final EvmFnResult result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void payerSigIsKnownActive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTopicRunningHash(final byte[] runningHash, final long sequenceNumber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void trigger(final TxnAccessor accessor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TxnAccessor triggeredTxn() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addExpiringEntities(final Collection<ExpiringEntity> expiringEntities) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ExpiringEntity> expiringEntities() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAssessedCustomFees(final List<FcAssessedCustomFee> assessedCustomFees) {
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
    public void recordBeneficiaryOfDeleted(final long accountNum, final long beneficiaryNum) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getBeneficiaryOfDeleted(final long accountNum) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int numDeletedAccountsAndContracts() {
        throw new UnsupportedOperationException();
    }
}
