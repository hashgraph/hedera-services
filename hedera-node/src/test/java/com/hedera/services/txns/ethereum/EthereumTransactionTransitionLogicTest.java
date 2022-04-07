package com.hedera.services.txns.ethereum;

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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.CallEvmTxProcessor;
import com.hedera.services.contracts.execution.CreateEvmTxProcessor;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.contract.ContractCallTransitionLogic;
import com.hedera.services.txns.contract.ContractCreateTransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class EthereumTransactionTransitionLogicTest {
	final private ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();
	private final Instant consensusTime = Instant.now();
	private final Account senderAccount = new Account(new Id(0, 0, 1002));
	private final Account contractAccount = new Account(new Id(0, 0, 1006));
	@Mock
	OptionValidator optionValidator;
	@Mock
	HederaLedger hederaLedger;
	@Mock
	GlobalDynamicProperties globalDynamicProperties;
	ContractCallTransitionLogic contractCallTransitionLogic;
	ContractCreateTransitionLogic contractCreateTransitionLogic;
	EthereumTransitionLogic subject;
	private int gas = 1_234;
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
	private CreateEvmTxProcessor createEvmTxProcessor;
	@Mock
	private GlobalDynamicProperties properties;
	@Mock
	private CodeCache codeCache;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	@Mock
	private HederaFs hfs;
	private TransactionBody contractCallTxn;

	@BeforeEach
	private void setup() {
		contractCallTransitionLogic = new ContractCallTransitionLogic(
				txnCtx, accountStore, worldState, recordService,
				evmTxProcessor, properties, codeCache, sigImpactHistorian, aliasManager);
		contractCreateTransitionLogic = new ContractCreateTransitionLogic(hfs, txnCtx, accountStore, optionValidator,
				worldState, recordService, createEvmTxProcessor, globalDynamicProperties, sigImpactHistorian);
		subject = new EthereumTransitionLogic(txnCtx, null, contractCallTransitionLogic, contractCreateTransitionLogic,
				hfs, globalDynamicProperties, aliasManager, accountsLedger);
	}

	@Test
	void hasCorrectApplicability() {
//		givenValidTxnCtx();
//
//		// expect:
//		assertTrue(contractCallTransitionLogic.applicability().test(contractCallTxn));
//		assertFalse(contractCallTransitionLogic.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void verifyExternaliseContractResultCall() {
//		// setup:
//		givenValidTxnCtx();
//		// and:
//		given(accessor.getTxn()).willReturn(contractCallTxn);
//		given(txnCtx.accessor()).willReturn(accessor);
//		// and:
//		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
//		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
//				.willReturn(contractAccount);
//		// and:
//		var results = TransactionProcessingResult.successful(
//				null, 1234L, 0L, 124L, Bytes.EMPTY,
//				contractAccount.getId().asEvmAddress(), Map.of());
//		given(evmTxProcessor.execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent, Bytes.EMPTY,
//				txnCtx.consensusTime()))
//				.willReturn(results);
//		given(worldState.persistProvisionalContractCreations()).willReturn(List.of(target));
//		// when:
//		contractCallTransitionLogic.doStateTransition();
//
//		// then:
//		verify(recordService).externaliseEvmCallTransaction(any());
//		verify(worldState).persistProvisionalContractCreations();
//		verify(txnCtx).setTargetedContract(target);
	}

	@Test
	void verifyProcessorCallingWithCorrectCallData() {
//		// setup:
//		ByteString functionParams = ByteString.copyFromUtf8("0x00120");
//		var op = TransactionBody.newBuilder()
//				.setTransactionID(ourTxnId())
//				.setContractCall(
//						ContractCallTransactionBody.newBuilder()
//								.setGas(gas)
//								.setAmount(sent)
//								.setFunctionParameters(functionParams)
//								.setContractID(target));
//		contractCallTxn = op.build();
//		// and:
//		given(accessor.getTxn()).willReturn(contractCallTxn);
//		given(txnCtx.accessor()).willReturn(accessor);
//		// and:
//		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
//		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
//				.willReturn(contractAccount);
//		// and:
//		var results = TransactionProcessingResult.successful(
//				null, 1234L, 0L, 124L, Bytes.EMPTY,
//				contractAccount.getId().asEvmAddress(), Map.of());
//		given(evmTxProcessor.execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
//				Bytes.fromHexString(CommonUtils.hex(functionParams.toByteArray())), txnCtx.consensusTime()))
//				.willReturn(results);
//		given(worldState.persistProvisionalContractCreations()).willReturn(List.of(target));
//		// when:
//		contractCallTransitionLogic.doStateTransition();
//
//		// then:
//		verify(evmTxProcessor).execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
//				Bytes.fromHexString(CommonUtils.hex(functionParams.toByteArray())), txnCtx.consensusTime());
//		verify(sigImpactHistorian).markEntityChanged(target.getContractNum());
	}

	@Test
	void successfulPreFetch() {
//		final var targetAlias = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e25");
//		final var target = ContractID.newBuilder()
//				.setEvmAddress(ByteString.copyFrom(targetAlias))
//				.build();
//		final var targetNum = EntityNum.fromLong(1234);
//		final var txnBody = Mockito.mock(TransactionBody.class);
//		final var ccTxnBody = Mockito.mock(ContractCallTransactionBody.class);
//
//		given(accessor.getTxn()).willReturn(txnBody);
//		given(txnBody.getContractCall()).willReturn(ccTxnBody);
//		given(ccTxnBody.getContractID()).willReturn(target);
//		given(aliasManager.lookupIdBy(target.getEvmAddress())).willReturn(targetNum);
//
//		contractCallTransitionLogic.preFetch(accessor);
//
//		verify(codeCache).getIfPresent(targetNum.toEvmAddress());
	}

	@Test
	void codeCacheThrowingExceptionDuringGetDoesntPropagate() {
//		TransactionBody txnBody = Mockito.mock(TransactionBody.class);
//		ContractCallTransactionBody ccTxnBody = Mockito.mock(ContractCallTransactionBody.class);
//
//		given(accessor.getTxn()).willReturn(txnBody);
//		given(txnBody.getContractCall()).willReturn(ccTxnBody);
//		given(ccTxnBody.getContractID()).willReturn(IdUtils.asContract("0.0.1324"));
//		given(codeCache.getIfPresent(any(Address.class))).willThrow(new RuntimeException("oh no"));
//
//		// when:
//		assertDoesNotThrow(() -> contractCallTransitionLogic.preFetch(accessor));
	}

	@Test
	void acceptsOkSyntax() {
//		givenValidTxnCtx();
//		given(properties.maxGas()).willReturn(gas + 1);
//		// expect:
//		assertEquals(OK, contractCallTransitionLogic.semanticCheck().apply(contractCallTxn));
	}

	@Test
	void providingGasOverLimitReturnsCorrectPrecheck() {
//		givenValidTxnCtx();
//		given(properties.maxGas()).willReturn(gas - 1);
//		// expect:
//		assertEquals(MAX_GAS_LIMIT_EXCEEDED,
//				contractCallTransitionLogic.semanticCheck().apply(contractCallTxn));
	}

	private void givenValidTxnCtx() {
//		var op = TransactionBody.newBuilder()
//				.setTransactionID(ourTxnId())
//				.setContractCall(
//						ContractCallTransactionBody.newBuilder()
//								.setGas(gas)
//								.setAmount(sent)
//								.setContractID(target));
//		contractCallTxn = op.build();
	}

	private TransactionID ourTxnId() {
//		return TransactionID.newBuilder()
//				.setAccountID(senderAccount.getId().asGrpcAccount())
//				.setTransactionValidStart(
//						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
//				.build();
		return null;
	}
}
