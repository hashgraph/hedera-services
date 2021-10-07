package com.hedera.services.contracts.execution;

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

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.contracts.operation.HederaExceptionalHaltReason;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;

@Singleton
public class CallLocalExecutor {
	private static final Logger log = LogManager.getLogger(CallLocalExecutor.class);

	private final AccountStore accountStore;
	private final CallLocalEvmTxProcessor evmTxProcessor;

	@Inject
	public CallLocalExecutor(
			AccountStore accountStore,
			CallLocalEvmTxProcessor evmTxProcessor
	) {
		this.accountStore = accountStore;
		this.evmTxProcessor = evmTxProcessor;
	}

	public ContractCallLocalResponse execute(ContractCallLocalQuery op) {

		try {
			TransactionBody body =
					SignedTxnAccessor.uncheckedFrom(op.getHeader().getPayment()).getTxn();
			final var senderId = Id.fromGrpcAccount(body.getTransactionID().getAccountID());
			final var contractId = Id.fromGrpcContract(op.getContractID());

			/* --- Load the model objects --- */
			final var sender = accountStore.loadAccount(senderId);
			final var receiver = accountStore.loadContract(contractId);
			final var callData = !op.getFunctionParameters().isEmpty()
					? Bytes.fromHexString(CommonUtils.hex(op.getFunctionParameters().toByteArray())) : Bytes.EMPTY;

			/* --- Do the business logic --- */
			final var result = evmTxProcessor.execute(
					sender,
					receiver.getId().asEvmAddress(),
					op.getGas(),
					0,
					callData,
					Instant.now());

			var status = OK;
			if (!result.isSuccessful()) {
				status = CONTRACT_REVERT_EXECUTED;
				if (result.getHaltReason().isPresent()) {
					final var haltReason = result.getHaltReason().get();
					if (haltReason.equals(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE)) {
						status = LOCAL_CALL_MODIFICATION_EXCEPTION;
					} else if (haltReason.equals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS)) {
						status = INVALID_SOLIDITY_ADDRESS;
					} else if (haltReason.equals(HederaExceptionalHaltReason.INVALID_SIGNATURE)) {
						status = INVALID_SIGNATURE;
					}
				}
			}

			final var responseHeader = RequestBuilder.getResponseHeader(status, 0L,
					ANSWER_ONLY, ByteString.EMPTY);

			return ContractCallLocalResponse
					.newBuilder()
					.setHeader(responseHeader)
					.setFunctionResult(result.toGrpc())
					.build();
		} catch (InvalidTransactionException ite) {
			final var responseHeader = RequestBuilder.getResponseHeader(ite.getResponseCode(), 0L,
					ANSWER_ONLY, ByteString.EMPTY);

			return ContractCallLocalResponse.newBuilder().setHeader(responseHeader).build();
		}
	}
}
