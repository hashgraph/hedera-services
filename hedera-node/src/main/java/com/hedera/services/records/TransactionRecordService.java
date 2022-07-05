package com.hedera.services.records;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

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

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.utils.ResponseCodeUtil.getStatus;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@Singleton
public class TransactionRecordService {
	private final TransactionContext txnCtx;

	@Inject
	public TransactionRecordService(TransactionContext txnCtx) {
		this.txnCtx = txnCtx;
	}

	/**
	 * Updates the record of the current transaction with the changes in the given {@link Topic}.
	 * Currently, the only operation refactored is the TopicCreate.
	 * This function should be updated correspondingly while refactoring the other Topic operations.
	 *
	 * @param topic
	 * 		- the Topic, whose changes have to be included in the receipt
	 */
	public void includeChangesToTopic(Topic topic) {
		if (topic.isNew()) {
			txnCtx.setCreated(topic.getId().asGrpcTopic());
		}
	}

	/**
	 * Updates the record of the active transaction with the {@link TransactionProcessingResult} of the EVM transaction
	 *
	 * @param result
	 * 		the processing result of the EVM transaction
	 */
	public void externalizeUnsuccessfulEvmCreate(TransactionProcessingResult result) {
		txnCtx.setCreateResult(EvmFnResult.fromCall(result));
		externalizeGenericEvmCreate(result);
	}

	public void externalizeSuccessfulEvmCreate(final TransactionProcessingResult result, final byte[] evmAddress) {
		externalizeSuccessfulEvmCreate(result, evmAddress, null);
	}

	public void externalizeSuccessfulEvmCreate(
			final TransactionProcessingResult result,
			final byte[] evmAddress,
			final TransactionSidecarRecord.Builder contractBytecodeSidecarRecord
	) {
		txnCtx.setCreateResult(EvmFnResult.fromCreate(result, evmAddress));
		final var sidecars = extractSidecarsFrom(result);
		if (contractBytecodeSidecarRecord != null) {
			sidecars.add(contractBytecodeSidecarRecord);
		}
		txnCtx.setSidecarRecords(sidecars);
		externalizeGenericEvmCreate(result);
	}

	private void externalizeGenericEvmCreate(final TransactionProcessingResult result) {
		txnCtx.setStatus(getStatus(result, SUCCESS));
		final var finalGasPayment = result.getGasPrice() * (result.getGasUsed() - result.getSbhRefund());
		txnCtx.addNonThresholdFeeChargedToPayer(finalGasPayment);
	}

	/**
	 * Updates the record of the active transaction with the {@link TransactionProcessingResult} of the EVM transaction
	 *
	 * @param result
	 * 		the processing result of the EVM transaction
	 */
	public void externaliseEvmCallTransaction(final TransactionProcessingResult result) {
		txnCtx.setStatus(getStatus(result, SUCCESS));
		txnCtx.setCallResult(EvmFnResult.fromCall(result));
		txnCtx.addNonThresholdFeeChargedToPayer(result.getGasPrice() * (result.getGasUsed() - result.getSbhRefund()));
		txnCtx.setSidecarRecords(extractSidecarsFrom(result));
	}

	private List<TransactionSidecarRecord.Builder> extractSidecarsFrom(final TransactionProcessingResult result) {
		final List<TransactionSidecarRecord.Builder> sidecars = new ArrayList<>();
		if (!result.getStateChanges().isEmpty()) {
			sidecars.add(SidecarUtils.createStateChangesSidecar(result.getStateChanges()));
		}
		// FUTURE WORK - we should put the actions in the list here as well when they are added
		return sidecars;
	}

	public void updateForEvmCall(EthTxData callContext, EntityId senderId) {
		txnCtx.updateForEvmCall(callContext, senderId);
	}
}