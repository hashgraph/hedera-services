package com.hedera.services.txns.contract;

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
import com.hedera.services.context.TransactionContext;
import com.hedera.services.contracts.execution.CallEvmTxProcessor;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.db.ServicesRepositoryRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_BYTECODE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContractCallTransitionLogicTest {
	final private ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();
	private long gas = 1_234L;
	private long sent = 1_234L;


	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private AccountStore accountStore;
	@Mock
	private HederaWorldState worldState;
	@Mock
	private TransactionRecordService recordService;
	@Mock
	private CallEvmTxProcessor evmTxProcessor;
	@Mock
	private ServicesRepositoryRoot repositoryRoot;
	@Mock
	private SigImpactHistorian sigImpactHistorian;

	private TransactionBody contractCallTxn;
	private final Instant consensusTime = Instant.now();
	private final Account senderAccount = new Account(new Id(0, 0, 1002));
	private final Account contractAccount = new Account(new Id(0, 0, 1006));
	private final byte[] bytecode = "not-a-real-bytecode".getBytes();
	ContractCallTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new ContractCallTransitionLogic(
				txnCtx, accountStore, sigImpactHistorian, worldState, recordService, evmTxProcessor, repositoryRoot);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(contractCallTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void verifyExternaliseContractResultCall() {
		// setup:
		givenValidTxnCtx();
		// and:
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
				.willReturn(contractAccount);
		// and:
		given(repositoryRoot.getCode(contractAccount.getId().asEvmAddress().toArray())).willReturn(bytecode);
		var results = TransactionProcessingResult.successful(
				null, 1234L, 0L, 124L, Bytes.EMPTY, contractAccount.getId().asEvmAddress());
		given(evmTxProcessor.execute(
				senderAccount, contractAccount.getId().asEvmAddress(), gas, sent, Bytes.EMPTY, txnCtx.consensusTime()))
				.willReturn(results);
		given(worldState.persist()).willReturn(List.of(target));
		// when:
		subject.doStateTransition();

		// then:
		verify(recordService).externaliseEvmCallTransaction(any());
		verify(worldState).persist();
	}

	@Test
	void verifyProcessorCallingWithCorrectCallData() {
		// setup:
		ByteString functionParams = ByteString.copyFromUtf8("0x00120");
		var op = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractCall(
						ContractCallTransactionBody.newBuilder()
								.setGas(gas)
								.setAmount(sent)
								.setFunctionParameters(functionParams)
								.setContractID(target));
		contractCallTxn = op.build();
		// and:
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
				.willReturn(contractAccount);
		// and:
		given(repositoryRoot.getCode(contractAccount.getId().asEvmAddress().toArray())).willReturn(bytecode);
		var results = TransactionProcessingResult.successful(
				null, 1234L, 0L, 124L, Bytes.EMPTY, contractAccount.getId().asEvmAddress());
		given(evmTxProcessor.execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
				Bytes.fromHexString(CommonUtils.hex(functionParams.toByteArray())), txnCtx.consensusTime()))
				.willReturn(results);
		given(worldState.persist()).willReturn(List.of(target));
		// when:
		subject.doStateTransition();

		// then:
		verify(evmTxProcessor).execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
				Bytes.fromHexString(CommonUtils.hex(functionParams.toByteArray())), txnCtx.consensusTime());
		verify(sigImpactHistorian).markEntityChanged(target.getContractNum());
	}

	@Test
	void acceptsOkSyntax() {
		givenValidTxnCtx();
		// expect:
		assertEquals(OK, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	void rejectsNegativeSend() {
		// setup:
		sent = -1;

		givenValidTxnCtx();
		// expect:
		assertEquals(CONTRACT_NEGATIVE_VALUE, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	void rejectsNegativeGas() {
		// setup:
		gas = -1;

		givenValidTxnCtx();

		// expect:
		assertEquals(CONTRACT_NEGATIVE_GAS, subject.semanticCheck().apply(contractCallTxn));
	}

	@Test
	void throwsWhenBytecodeIsEmpty() {
		// setup:
		givenValidTxnCtx();
		// and:
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
				.willReturn(contractAccount);
		// and:
		given(repositoryRoot.getCode(contractAccount.getId().asEvmAddress().toArray())).willReturn(
				Bytes.EMPTY.toArray());

		// when:
		final var exception = assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());

		// then:
		assertEquals(CONTRACT_BYTECODE_EMPTY, exception.getResponseCode());
	}

	private void givenValidTxnCtx() {
		var op = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractCall(
						ContractCallTransactionBody.newBuilder()
								.setGas(gas)
								.setAmount(sent)
								.setContractID(target));
		contractCallTxn = op.build();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(senderAccount.getId().asGrpcAccount())
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
