/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.records;

import static com.hedera.services.utils.ResponseCodeUtil.getStatusOrDefault;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.store.models.Topic;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.services.utils.SidecarUtils;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TransactionRecordService {
    private final TransactionContext txnCtx;

    @Inject
    public TransactionRecordService(TransactionContext txnCtx) {
        this.txnCtx = txnCtx;
    }

    /**
     * Updates the record of the current transaction with the changes in the given {@link Topic}.
     * Currently, the only operation refactored is the TopicCreate. This function should be updated
     * correspondingly while refactoring the other Topic operations.
     *
     * @param topic - the Topic, whose changes have to be included in the receipt
     */
    public void includeChangesToTopic(Topic topic) {
        if (topic.isNew()) {
            txnCtx.setCreated(topic.getId().asGrpcTopic());
        }
    }

    /**
     * Updates the record of the active transaction with the {@link TransactionProcessingResult} of
     * the EVM transaction
     *
     * @param result the processing result of the EVM transaction
     */
    public void externalizeUnsuccessfulEvmCreate(final TransactionProcessingResult result) {
        externalizeUnsuccessfulEvmCreate(result, null);
    }

    public void externalizeUnsuccessfulEvmCreate(
            final TransactionProcessingResult result,
            final TransactionSidecarRecord.Builder contractBytecodeSidecarRecord) {
        txnCtx.setCreateResult(EvmFnResult.fromCall(result));
        addAllSidecarsToTxnContextFrom(result);
        if (contractBytecodeSidecarRecord != null) {
            txnCtx.addSidecarRecord(contractBytecodeSidecarRecord);
        }
        externalizeGenericEvmCreate(result);
    }

    public void externalizeSuccessfulEvmCreate(
            final TransactionProcessingResult result, final byte[] evmAddress) {
        externalizeSuccessfulEvmCreate(result, evmAddress, null);
    }

    public void externalizeSuccessfulEvmCreate(
            final TransactionProcessingResult result,
            final byte[] evmAddress,
            final TransactionSidecarRecord.Builder contractBytecodeSidecarRecord) {
        txnCtx.setCreateResult(EvmFnResult.fromCreate(result, evmAddress));
        addAllSidecarsToTxnContextFrom(result);
        if (contractBytecodeSidecarRecord != null) {
            txnCtx.addSidecarRecord(contractBytecodeSidecarRecord);
        }
        externalizeGenericEvmCreate(result);
    }

    private void externalizeGenericEvmCreate(final TransactionProcessingResult result) {
        txnCtx.setStatus(getStatusOrDefault(result, SUCCESS));
        final var finalGasPayment =
                result.getGasPrice() * (result.getGasUsed() - result.getSbhRefund());
        txnCtx.addFeeChargedToPayer(finalGasPayment);
    }

    /**
     * Updates the record of the active transaction with the {@link TransactionProcessingResult} of
     * the EVM transaction
     *
     * @param result the processing result of the EVM transaction
     */
    public void externaliseEvmCallTransaction(final TransactionProcessingResult result) {
        txnCtx.setStatus(getStatusOrDefault(result, SUCCESS));
        txnCtx.setCallResult(EvmFnResult.fromCall(result));
        txnCtx.addFeeChargedToPayer(
                result.getGasPrice() * (result.getGasUsed() - result.getSbhRefund()));
        addAllSidecarsToTxnContextFrom(result);
    }

    private void addAllSidecarsToTxnContextFrom(final TransactionProcessingResult result) {
        if (!result.getStateChanges().isEmpty()) {
            txnCtx.addSidecarRecord(
                    SidecarUtils.createStateChangesSidecarFrom(result.getStateChanges()));
        }
        final var actions = result.getActions();
        if (!actions.isEmpty()) {
            txnCtx.addSidecarRecord(SidecarUtils.createContractActionsSidecar(actions));
        }
    }

    public void updateForEvmCall(EthTxData callContext, EntityId senderId) {
        txnCtx.updateForEvmCall(callContext, senderId);
    }
}
