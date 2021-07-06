package com.hedera.services.txns.token;

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
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.store.tokens.TokenStore.MISSING_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class TokenFeeScheduleUpdateTransitionLogicTest {
	long thisSecond = 1_234_567L;
	private Instant now = Instant.ofEpochSecond(thisSecond);
	private TokenID target = IdUtils.asToken("1.2.666");
	private AccountID oldAutoRenew = IdUtils.asAccount("4.2.1");
	private JKey adminKey = new JEd25519Key("w/e".getBytes());
	private TokenFeeScheduleUpdateTransactionBody tokenFeeScheduleUpdateTxn;
	private TransactionBody tokenFeeScheduleUpdateTxnBody;
	private MerkleToken token;

	private TokenStore store;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;

	private TokenFeeScheduleUpdateTransitionLogic subject;
	private GlobalDynamicProperties dynamicProperties;

	@BeforeEach
	public void setup() {
		store = mock(TokenStore.class);
		accessor = mock(PlatformTxnAccessor.class);

		token = mock(MerkleToken.class);
		given(token.adminKey()).willReturn(Optional.of(adminKey));
		given(store.resolve(target)).willReturn(target);
		given(store.get(target)).willReturn(token);

		txnCtx = mock(TransactionContext.class);

		dynamicProperties = mock(GlobalDynamicProperties.class);

		subject = new TokenFeeScheduleUpdateTransitionLogic(store, txnCtx, null, dynamicProperties);
	}

	@Test
	void happyPathWorks() {
		givenValidTxnCtx();
		given(token.isDeleted()).willReturn(false);
		given(store.updateFeeSchedule(any())).willReturn(OK);
		subject.doStateTransition();

		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	void failsOnMissingTokenId() {
		givenValidTxnCtx();
		given(store.resolve(target)).willReturn(MISSING_TOKEN);

		subject.doStateTransition();

		verify(txnCtx).setStatus(INVALID_TOKEN_ID);
	}

	@Test
	void failsIfNoFeeScheduleKey() {
		givenValidTxnCtx();
		given(store.updateFeeSchedule(any())).willReturn(INVALID_CUSTOM_FEE_SCHEDULE_KEY);

		subject.doStateTransition();

		verify(txnCtx).setStatus(INVALID_CUSTOM_FEE_SCHEDULE_KEY);
	}



	@Test
	void failsOnAlreadyDeletedToken() {
		givenValidTxnCtx();
		given(token.isDeleted()).willReturn(true);

		subject.doStateTransition();

		verify(txnCtx).setStatus(TOKEN_WAS_DELETED);
	}

	@Test
	void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		given(store.updateFeeSchedule(any())).willThrow(IllegalStateException.class);

		subject.doStateTransition();

		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	void failsCustomFeeListAlreadyEmpty() {
		givenValidTxnCtxWithEmptyFees();
		given(token.grpcFeeSchedule()).willReturn(null);
		given(store.updateFeeSchedule(any())).willReturn(CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES);

		subject.doStateTransition();

		verify(txnCtx).setStatus(CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES);
	}


	@Test
	void failsCustomFeeListTooLong() {
		givenValidTxnCtx();
		given(store.updateFeeSchedule(any())).willReturn(CUSTOM_FEES_LIST_TOO_LONG);

		subject.doStateTransition();

		verify(txnCtx).setStatus(CUSTOM_FEES_LIST_TOO_LONG);
	}


	private TokenID misc = IdUtils.asToken("3.2.1");
	private Fraction fraction = Fraction.newBuilder().setNumerator(15).setDenominator(100).build();
	private FractionalFee firstFractionalFee = FractionalFee.newBuilder()
			.setFractionalAmount(fraction)
			.setMaximumAmount(50)
			.setMinimumAmount(10)
			.build();
	private FractionalFee secondFractionalFee = FractionalFee.newBuilder()
			.setFractionalAmount(fraction)
			.setMaximumAmount(15)
			.setMinimumAmount(5)
			.build();
	private FixedFee fixedFeeInTokenUnits = FixedFee.newBuilder()
			.setDenominatingTokenId(misc)
			.setAmount(100)
			.build();
	private FixedFee fixedFeeInHbar = FixedFee.newBuilder()
			.setAmount(100)
			.build();
	private AccountID feeCollector = IdUtils.asAccount("6.6.6");
	private AccountID anotherFeeCollector = IdUtils.asAccount("1.2.777");
	private CustomFee customFixedFeeInHbar = CustomFee.newBuilder()
			.setFeeCollectorAccountId(feeCollector)
			.setFixedFee(fixedFeeInHbar)
			.build();
	private CustomFee customFixedFeeInHts = CustomFee.newBuilder()
			.setFeeCollectorAccountId(anotherFeeCollector)
			.setFixedFee(fixedFeeInTokenUnits)
			.build();
	private CustomFee customFractionalFeeA = CustomFee.newBuilder()
			.setFeeCollectorAccountId(feeCollector)
			.setFractionalFee(firstFractionalFee)
			.build();
	private CustomFee customFractionalFeeB = CustomFee.newBuilder()
			.setFeeCollectorAccountId(feeCollector)
			.setFractionalFee(secondFractionalFee)
			.build();
	private List<CustomFee> grpcCustomFees = List.of(
			customFixedFeeInHbar,
			customFixedFeeInHts,
			customFractionalFeeA,
			customFractionalFeeB
	);


	private void givenValidTxnCtx() {
		var builder = TokenFeeScheduleUpdateTransactionBody.newBuilder()
				.addAllCustomFees(grpcCustomFees)
				.setTokenId(target);
		tokenFeeScheduleUpdateTxn = builder.build();
		TransactionBody txn = mock(TransactionBody.class);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getTxn()).willReturn(txn);

		given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);

		given(txnCtx.consensusTime()).willReturn(now);
	}
	private void givenValidTxnCtxWithEmptyFees() {
		var builder = TokenFeeScheduleUpdateTransactionBody.newBuilder()
				.addAllCustomFees(List.of())
				.setTokenId(target);
		tokenFeeScheduleUpdateTxn = builder.build();
		TransactionBody txn = mock(TransactionBody.class);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getTxn()).willReturn(txn);

		given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);

		given(txnCtx.consensusTime()).willReturn(now);
	}

	@Test
	void rejectsInvalidTokenId() {
		givenInvalidTokenId();

		// expect:
		assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenFeeScheduleUpdateTxnBody));
	}

	@Test
	void acceptsValidTokenId() {
		givenValidTokenId();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenFeeScheduleUpdateTxnBody));
	}

	private void givenInvalidTokenId() {
		tokenFeeScheduleUpdateTxnBody = TransactionBody.newBuilder()
				.setTokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder()
						.addAllCustomFees(grpcCustomFees))
				.build();
	}

	private void givenValidTokenId() {
		tokenFeeScheduleUpdateTxnBody = TransactionBody.newBuilder()
				.setTokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder()
						.addAllCustomFees(grpcCustomFees).setTokenId(target))
				.build();
	}
}
