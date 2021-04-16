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

import com.google.protobuf.StringValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederLedgerTokensTest;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.txns.contract.helpers.UpdateCustomizerFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

class ContractUpdateTransitionLogicTest {
	final private AccountID proxy = AccountID.newBuilder().setAccountNum(4_321L).build();
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();
	final private AccountID targetId = AccountID.newBuilder().setAccountNum(9_999L).build();
	final private String memo = "Who, me?";

	private long customAutoRenewPeriod = 100_001L;

	private Instant consensusTime;
	private HederaLedger ledger;
	private MerkleAccount contract = new MerkleAccount();
	private OptionValidator validator;
	private HederaAccountCustomizer customizer;
	private UpdateCustomizerFactory customizerFactory;
	private TransactionBody contractUpdateTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private FCMap<MerkleEntityId, MerkleAccount> contracts;
	private ContractUpdateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		consensusTime = Instant.now();

		ledger = mock(HederaLedger.class);
		contracts = (FCMap<MerkleEntityId, MerkleAccount>) mock(FCMap.class);
		customizerFactory = mock(UpdateCustomizerFactory.class);
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		withRubberstampingValidator();

		subject = new ContractUpdateTransitionLogic(ledger, validator, txnCtx, customizerFactory, () -> contracts);
	}

	@Test
	void abortsIfCustomizerUnhappy() {
		// setup:
		customizer = mock(HederaAccountCustomizer.class);

		givenValidTxnCtx();
		given(customizerFactory.customizerFor(contract, contractUpdateTxn.getContractUpdateInstance()))
				.willReturn(Pair.of(Optional.empty(), MODIFYING_IMMUTABLE_CONTRACT));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger, never()).customize(targetId, customizer);
		verify(txnCtx).setStatus(MODIFYING_IMMUTABLE_CONTRACT);
	}

	@Test
	void runsHappyPath() {
		// setup:
		customizer = mock(HederaAccountCustomizer.class);

		givenValidTxnCtx();
		given(customizerFactory.customizerFor(contract, contractUpdateTxn.getContractUpdateInstance()))
				.willReturn(Pair.of(Optional.of(customizer), OK));

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).customize(targetId, customizer);
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(contractUpdateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void rejectsInvalidMemoInSyntaxCheck() {
		givenValidTxnCtx();
		// and:
		given(validator.memoCheck(any())).willReturn(INVALID_ZERO_BYTE_IN_STRING);

		// expect:
		assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.syntaxCheck().apply(contractUpdateTxn));
	}

	@Test
	void acceptsOkSyntax() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(contractUpdateTxn));
	}

	@Test
	void acceptsOmittedAutoRenew() {
		givenValidTxnCtx(false);

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(contractUpdateTxn));
	}

	@Test
	void rejectsInvalidCid() {
		givenValidTxnCtx();
		// and:
		given(validator.queryableContractStatus(target, contracts)).willReturn(CONTRACT_DELETED);

		// expect:
		assertEquals(CONTRACT_DELETED, subject.syntaxCheck().apply(contractUpdateTxn));
	}

	@Test
	void rejectsInvalidAutoRenew() {
		// setup:
		customAutoRenewPeriod = -1;

		givenValidTxnCtx();

		// expect:
		assertEquals(INVALID_RENEWAL_PERIOD, subject.syntaxCheck().apply(contractUpdateTxn));
	}

	@Test
	void rejectsOutOfRangeAutoRenew() {
		givenValidTxnCtx();
		// and:
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		// expect:
		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.syntaxCheck().apply(contractUpdateTxn));
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(true);
	}

	private void givenValidTxnCtx(boolean useAutoRenew) {
		Duration autoRenewDuration = Duration.newBuilder().setSeconds(customAutoRenewPeriod).build();
		var op = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractUpdateInstance(
						ContractUpdateTransactionBody.newBuilder()
								.setMemo(memo)
								.setContractID(target)
								.setProxyAccountID(proxy));
		if (useAutoRenew) {
			op.getContractUpdateInstanceBuilder().setAutoRenewPeriod(autoRenewDuration);
			op.getContractUpdateInstanceBuilder().setMemoWrapper(StringValue.newBuilder().setValue(memo));
		}
		contractUpdateTxn = op.build();
		given(accessor.getTxn()).willReturn(contractUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(contracts.get(MerkleEntityId.fromContractId(target))).willReturn(contract);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private void withRubberstampingValidator() {
		Duration autoRenewDuration = Duration.newBuilder().setSeconds(customAutoRenewPeriod).build();
		given(validator.queryableContractStatus(target, contracts)).willReturn(OK);
		given(validator.isValidAutoRenewPeriod(autoRenewDuration)).willReturn(true);
		given(validator.memoCheck(memo)).willReturn(OK);
	}
}
