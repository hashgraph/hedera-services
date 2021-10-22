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
import com.hedera.services.contracts.operation.HederaExceptionalHaltReason;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.builder.RequestBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CallLocalExecutorTest {
	int gas = 1_234;
	ByteString params = ByteString.copyFrom("Hungry, and...".getBytes());
	Id callerID = new Id(0, 0, 123);
	Id contractID = new Id(0, 0, 456);

	ContractCallLocalQuery query;

	AccountStore accountStore;
	CallLocalEvmTxProcessor evmTxProcessor;

	CallLocalExecutor subject;

	@BeforeEach
	private void setup() {
		accountStore = mock(AccountStore.class);
		evmTxProcessor = mock(CallLocalEvmTxProcessor.class);

		query = localCallQuery(contractID.asGrpcContract(), ANSWER_ONLY);

		subject = new CallLocalExecutor(accountStore, evmTxProcessor);
	}

	@Test
	void processingSuccessful() {
		// setup:
		final var transactionProcessingResult = TransactionProcessingResult
				.successful(new ArrayList<>(), 0, 0, 1, Bytes.EMPTY, callerID.asEvmAddress(), List.of());
		final var expected = response(OK, transactionProcessingResult);

		given(accountStore.loadAccount(any())).willReturn(new Account(callerID));
		given(accountStore.loadContract(any())).willReturn(new Account(contractID));
		given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any(), any()))
				.willReturn(transactionProcessingResult);

		// when:
		final var result = subject.execute(query);

		// then:
		assertEquals(expected, result);
	}

	@Test
	void processingReturnsModificationHaltReason() {
		// setup:
		final var transactionProcessingResult = TransactionProcessingResult
				.failed(0, 0, 1, Optional.empty(), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE), List.of());
		final var expected = response(LOCAL_CALL_MODIFICATION_EXCEPTION, transactionProcessingResult);

		given(accountStore.loadAccount(any())).willReturn(new Account(callerID));
		given(accountStore.loadContract(any())).willReturn(new Account(contractID));
		given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any(), any()))
				.willReturn(transactionProcessingResult);

		// when:
		final var result = subject.execute(query);

		// then:
		assertEquals(expected, result);
	}

	@Test
	void processingReturnsInvalidSolidityAddressHaltReason() {
		// setup:
		final var transactionProcessingResult = TransactionProcessingResult
				.failed(0, 0, 1, Optional.empty(), Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS), List.of());
		final var expected = response(INVALID_SOLIDITY_ADDRESS, transactionProcessingResult);

		given(accountStore.loadAccount(any())).willReturn(new Account(callerID));
		given(accountStore.loadContract(any())).willReturn(new Account(contractID));
		given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any(), any()))
				.willReturn(transactionProcessingResult);

		// when:
		final var result = subject.execute(query);

		// then:
		assertEquals(expected, result);
	}

	@Test
	void processingReturnsRevertReason() {
		// setup:
		final var transactionProcessingResult = TransactionProcessingResult
				.failed(0, 0, 1, Optional.of(Bytes.of("out of gas".getBytes())), Optional.empty(), List.of());
		final var expected = response(CONTRACT_REVERT_EXECUTED, transactionProcessingResult);

		given(accountStore.loadAccount(any())).willReturn(new Account(callerID));
		given(accountStore.loadContract(any())).willReturn(new Account(contractID));
		given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any(), any()))
				.willReturn(transactionProcessingResult);

		// when:
		final var result = subject.execute(query);

		// then:
		assertEquals(expected, result);
	}

	@Test
	void catchesInvalidTransactionException() {
		// setup:
		given(accountStore.loadAccount(any())).willThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID));

		// when:
		final var result = subject.execute(query);

		assertEquals(failedResponse(INVALID_ACCOUNT_ID), result);
		// and:
		verifyNoInteractions(evmTxProcessor);
	}

	private ContractCallLocalResponse response(ResponseCodeEnum status, TransactionProcessingResult result) {
		return ContractCallLocalResponse.newBuilder()
				.setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(status))
				.setFunctionResult(result.toGrpc())
				.build();
	}

	private ContractCallLocalResponse failedResponse(ResponseCodeEnum status) {
		return ContractCallLocalResponse.newBuilder()
				.setHeader(RequestBuilder.getResponseHeader(status, 0l,
						ANSWER_ONLY, ByteString.EMPTY))
				.build();
	}

	private ContractCallLocalQuery localCallQuery(ContractID id, ResponseType type) {
		return ContractCallLocalQuery.newBuilder()
				.setContractID(id)
				.setGas(gas)
				.setFunctionParameters(params)
				.setHeader(QueryHeader.newBuilder()
						.setResponseType(type)
						.build())
				.build();
	}
}
