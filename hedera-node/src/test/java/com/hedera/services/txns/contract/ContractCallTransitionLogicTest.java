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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class ContractCallTransitionLogicTest {
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private AccountID beneficient = AccountID.newBuilder().setAccountNum(2_222L).build();
	final private ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();
	private long gas = 1_234L;
	private long sent = 1_234L;
	private int maxGas = 2000;

	private Instant consensusTime;
	private OptionValidator validator;
	private TransactionBody contractCallTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private GlobalDynamicProperties properties;
	private AccountStore accountStore;
	private HbarCentExchange exchange;
	private HederaWorldState worldState;
	private UsagePricesProvider usagePrices;
	private TransactionRecordService recordService;

	MerkleMap<EntityNum, MerkleAccount> contracts;
	ContractCallTransitionLogic subject;

	@BeforeEach
	private void setup() {
		consensusTime = Instant.now();
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		properties = mock(GlobalDynamicProperties.class);
		accountStore = mock(AccountStore.class);
		exchange = mock(HbarCentExchange.class);
		worldState = mock(HederaWorldState.class);
		usagePrices = mock(UsagePricesProvider.class);
		recordService = mock(TransactionRecordService.class);
		withRubberstampingValidator();

		subject = new ContractCallTransitionLogic(txnCtx, accountStore, exchange, worldState, usagePrices, properties, recordService);
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
		given(accountStore.loadAccount(new Id(payer.getShardNum(), payer.getRealmNum(), payer.getAccountNum())))
				.willReturn(new Account(new Id(payer.getShardNum(), payer.getRealmNum(), payer.getAccountNum())));
		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
				.willReturn(new Account(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())));
		given(txnCtx.submittingNodeAccount()).willReturn(beneficient);

		// when:
		subject.doStateTransition();

		// then:
//		verify(contractsState).externaliseContractResult(any());
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

	private void givenValidTxnCtx() {
		var op = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractCall(
						ContractCallTransactionBody.newBuilder()
								.setGas(gas)
								.setAmount(sent)
								.setContractID(target));
		contractCallTxn = op.build();
		given(properties.maxGas()).willReturn(maxGas);
		given(accessor.getTxn()).willReturn(contractCallTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private void withRubberstampingValidator() {
		given(validator.queryableContractStatus(target, contracts)).willReturn(OK);
	}
}
