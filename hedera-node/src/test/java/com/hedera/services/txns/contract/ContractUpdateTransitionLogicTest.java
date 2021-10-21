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
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.annotation.Nullable;
import java.time.Instant;

import static com.hedera.services.sigs.utils.ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ContractUpdateTransitionLogicTest {

	final private String memo = "Who, me?";
	final private ContractID targetGrpcId = ContractID.newBuilder().setContractNum(9_999L).build();
	final private Id targetModelId = Id.fromGrpcContract(targetGrpcId);
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private AccountID proxy = AccountID.newBuilder().setAccountNum(4_321L).build();
	final private Key clearingKey = IMMUTABILITY_SENTINEL_KEY;
	final private Key newKey = MISC_ACCOUNT_KT.asKey();
	final private Key invalidKey = Key.getDefaultInstance();
	final private Instant consensusTime = Instant.now();
	private long customAutoRenewPeriod = 100_001L;

	private TransactionBody contractUpdateTxn;
	private ContractUpdateTransitionLogic subject;

	@Mock private PlatformTxnAccessor accessor;
	@Mock private TransactionContext txnCtx;
	@Mock private OptionValidator validator;
	@Mock private AccountStore accountStore;
	@Mock private Account target;

	@BeforeEach
	private void setup() {
		subject = new ContractUpdateTransitionLogic(txnCtx, validator, accountStore);
	}

	/* --- Happy path tests --- */

	@Test
	void runsHappyPathWithoutAdminKey() {
		// given:
		givenValidTxnCtx(null);
		givenContractLoad(true, null);
		// when:
		subject.doStateTransition();
		// then:
		verify(target).updateFromGrpcContract(any(), any(), any(), any(), any());
		verify(accountStore).persistAccount(target);
	}

	@Test
	void runsHappyPathWithAdminKeyRemoval() {
		// given:
		givenValidTxnCtx(clearingKey);
		givenContractLoad(true, null);
		// when:
		subject.doStateTransition();
		// then:
		verify(target).updateFromGrpcContract(any(), any(), any(), any(), any());
		verify(accountStore).persistAccount(target);
	}

	@Test
	void runsHappyPathWithNewAdminKey() {
		// given:
		givenValidTxnCtx(newKey);
		givenContractLoad(true, null);
		// when:
		subject.doStateTransition();
		// then:
		verify(target).updateFromGrpcContract(any(), any(), any(), any(), any());
		verify(accountStore).persistAccount(target);
	}

	/* --- Unhappy path tests --- */

	@Test
	void failsWithInvalidContractId() {
		// given:
		givenValidTxnCtx(null);
		givenContractLoad(false, INVALID_CONTRACT_ID);
		// when:
		assertFailsWith(() -> subject.doStateTransition(), INVALID_CONTRACT_ID);
		// then:
		verify(target, never()).updateFromGrpcContract(any(), any(), any(), any(), any());
		verify(accountStore, never()).persistAccount(target);
	}

	@Test
	void failsWithContractDeleted() {
		// given:
		givenValidTxnCtx(null);
		givenContractLoad(false, CONTRACT_DELETED);
		// when:
		assertFailsWith(() -> subject.doStateTransition(), CONTRACT_DELETED);
		// then:
		verify(target, never()).updateFromGrpcContract(any(), any(), any(), any(), any());
		verify(accountStore, never()).persistAccount(target);
	}

	@Test
	void failsWithAutoRenewDurationNotInRange() {
		// given:
		givenValidTxnCtx(null);
		givenContractLoad(false, AUTORENEW_DURATION_NOT_IN_RANGE);
		// when:
		assertFailsWith(() -> subject.doStateTransition(), AUTORENEW_DURATION_NOT_IN_RANGE);
		// then:
		verify(target, never()).updateFromGrpcContract(any(), any(), any(), any(), any());
		verify(accountStore, never()).persistAccount(target);
	}

	@Test
	void failsWithInvalidAdminKey() {
		// given:
		givenValidTxnCtx(invalidKey);
		givenContractLoad(true, null);
		// when:
		assertFailsWith(() -> subject.doStateTransition(), INVALID_ADMIN_KEY);
		// then:
		verify(target, never()).updateFromGrpcContract(any(), any(), any(), any(), any());
		verify(accountStore, never()).persistAccount(target);
	}

	/* --- Applicability and semantics tests --- */

	@Test
	void acceptsOkSyntax() {
		// given:
		givenRubberstampingValidator();
		givenValidTxnCtx(null);
		given(validator.memoCheck(memo)).willReturn(OK);
		// then:
		assertEquals(OK, subject.semanticCheck().apply(contractUpdateTxn));
	}

	@Test
	void acceptsOmittedAutoRenew() {
		// given:
		givenRubberstampingValidator();
		givenValidTxnCtx(null);
		given(validator.memoCheck(memo)).willReturn(OK);
		// then:
		assertEquals(OK, subject.semanticCheck().apply(contractUpdateTxn));
	}

	@Test
	void rejectsInvalidMemoInSyntaxCheck() {
		// given:
		givenRubberstampingValidator();
		givenValidTxnCtx(null);
		given(validator.memoCheck(any())).willReturn(INVALID_ZERO_BYTE_IN_STRING);
		// then:
		assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.semanticCheck().apply(contractUpdateTxn));
	}

	@Test
	void hasCorrectApplicability() {
		// given:
		givenValidTxnCtx(null);
		// then:
		assertTrue(subject.applicability().test(contractUpdateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void rejectsOutOfRangeAutoRenew() {
		// given:
		givenValidTxnCtx(null);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);
		// then:
		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.semanticCheck().apply(contractUpdateTxn));
	}

	@Test
	void rejectsInvalidAutoRenew() {
		// given:
		customAutoRenewPeriod = -1;
		givenValidTxnCtx(null);
		// then:
		assertEquals(INVALID_RENEWAL_PERIOD, subject.semanticCheck().apply(contractUpdateTxn));
	}

	/* --- Test scenarios --- */

	private void givenValidTxnCtx(@Nullable final Key adminKey) {
		givenValidTxnCtx(true, adminKey);
	}

	private void givenValidTxnCtx(final boolean useAutoRenew, @Nullable final Key adminKey) {
		Duration autoRenewDuration = Duration.newBuilder().setSeconds(customAutoRenewPeriod).build();

		var op = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractUpdateInstance(
						ContractUpdateTransactionBody.newBuilder()
								.setMemo(memo)
								.setContractID(targetGrpcId)
								.setProxyAccountID(proxy));
		if (useAutoRenew) {
			op.getContractUpdateInstanceBuilder().setAutoRenewPeriod(autoRenewDuration);
			op.getContractUpdateInstanceBuilder().setMemoWrapper(StringValue.newBuilder().setValue(memo));
		}
		if (adminKey != null) {
			op.getContractUpdateInstanceBuilder().setAdminKey(adminKey);
		}
		contractUpdateTxn = op.build();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	/* --- Helpers --- */

	private void givenContractLoad(final boolean isHappy, final ResponseCodeEnum failCode) {
		given(accessor.getTxn()).willReturn(contractUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		if (isHappy) {
			given(accountStore.loadContract(targetModelId)).willReturn(target);
		} else {
			given(accountStore.loadContract(targetModelId)).willThrow(new InvalidTransactionException(failCode));
		}
	}

	private void givenRubberstampingValidator() {
		Duration autoRenewDuration = Duration.newBuilder().setSeconds(customAutoRenewPeriod).build();
		given(validator.isValidAutoRenewPeriod(autoRenewDuration)).willReturn(true);
	}
}
