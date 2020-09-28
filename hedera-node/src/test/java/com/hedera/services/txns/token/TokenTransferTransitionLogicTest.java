package com.hedera.services.txns.token;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenTransfersTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.List;

import static com.hedera.test.utils.IdUtils.adjustFrom;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class TokenTransferTransitionLogicTest {
	TokenTransfersTransactionBody xfers = TokenTransfersTransactionBody.newBuilder()
			.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(asToken("0.0.12345"))
					.addAllTransfers(List.of(
							adjustFrom(asAccount("0.0.2"), -1_000),
							adjustFrom(asAccount("0.0.3"), +1_000)
					)))
			.build();

	private HederaLedger ledger;
	private OptionValidator validator;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;

	private TransactionBody tokenTransactTxn;
	private TokenTransferTransitionLogic subject;

	@BeforeEach
	private void setup() {
		ledger = mock(HederaLedger.class);
		validator = mock(OptionValidator.class);
		accessor = mock(PlatformTxnAccessor.class);

		txnCtx = mock(TransactionContext.class);

		subject = new TokenTransferTransitionLogic(ledger, validator, txnCtx);
	}

	@Test
	public void capturesInvalidXfers() {
		givenValidTxnCtx();
		// and:
		given(ledger.doAtomicZeroSumTokenTransfers(xfers)).willReturn(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);
	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx();
		// and:
		given(ledger.doAtomicZeroSumTokenTransfers(xfers)).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).doAtomicZeroSumTokenTransfers(xfers);
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenTransactTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		// and:
		given(ledger.doAtomicZeroSumTokenTransfers(any())).willThrow(IllegalArgumentException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(tokenTransactTxn));
	}

	@Test
	public void rejectsDuplicateTokens() {
		givenDuplicateTokens();

		// expect:
		assertEquals(TOKEN_ID_REPEATED_IN_TOKEN_LIST, subject.syntaxCheck().apply(tokenTransactTxn));
	}

	@Test
	public void rejectsInvalidTokenDuplicateAccounts() {
		givenInvalidTokenDuplicateAccounts();

		// expect:
		assertEquals(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS, subject.syntaxCheck().apply(tokenTransactTxn));
	}

	@Test
	public void rejectsInvalidTokenAdjustment() {
		givenInvalidTokenAdjustment();

		// expect:
		assertEquals(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN, subject.syntaxCheck().apply(tokenTransactTxn));
	}

	@Test
	public void rejectsMissingTokenInTransfer() {
		givenMissingTokenInTransfer();

		// expect:
		assertEquals(INVALID_TOKEN_ID, subject.syntaxCheck().apply(tokenTransactTxn));
	}

	@Test
	public void rejectsMissingAccountInTokenAccountAmount() {
		givenMissingAccountInTokenAccountAmount();

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, subject.syntaxCheck().apply(tokenTransactTxn));
	}

	@Test
	public void rejectsInvalidTokenZeroAmounts() {
		givenInvalidTokenZeroAmounts();

		assertEquals(INVALID_ACCOUNT_AMOUNTS, subject.syntaxCheck().apply(tokenTransactTxn));
	}

	private void givenValidTxnCtx() {
		tokenTransactTxn = TransactionBody.newBuilder()
				.setTokenTransfers(xfers)
				.build();
		given(accessor.getTxn()).willReturn(tokenTransactTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private void givenDuplicateTokens() {
		xfers = TokenTransfersTransactionBody.newBuilder()
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.setToken(asToken("0.0.12345"))
								.addAllTransfers(List.of(
										adjustFrom(asAccount("0.0.2"), -1_000),
										adjustFrom(asAccount("0.0.3"), +1_000)
								)))
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.setToken(asToken("0.0.12345"))
								.addAllTransfers(List.of(
										adjustFrom(asAccount("0.0.2"), -1_000),
										adjustFrom(asAccount("0.0.3"), +1_000)
								))
				)
				.build();

		tokenTransactTxn = TransactionBody.newBuilder()
				.setTokenTransfers(xfers)
				.build();
	}

	private void givenInvalidTokenDuplicateAccounts() {
		xfers = TokenTransfersTransactionBody.newBuilder()
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.setToken(asToken("0.0.12345"))
								.addAllTransfers(List.of(
										adjustFrom(asAccount("0.0.2"), -1_000),
										adjustFrom(asAccount("0.0.2"), +1_000)
								))
				)
				.build();

		tokenTransactTxn = TransactionBody.newBuilder()
				.setTokenTransfers(xfers)
				.build();
	}

	private void givenInvalidTokenAdjustment() {
		xfers = TokenTransfersTransactionBody.newBuilder()
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.setToken(asToken("0.0.12345"))
								.addAllTransfers(List.of(
										adjustFrom(asAccount("0.0.2"), -1_000),
										adjustFrom(asAccount("0.0.3"), -1_000)
								))
				)
				.build();

		tokenTransactTxn = TransactionBody.newBuilder()
				.setTokenTransfers(xfers)
				.build();
	}

	private void givenMissingTokenInTransfer() {
		xfers = TokenTransfersTransactionBody.newBuilder()
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.addAllTransfers(List.of(
										adjustFrom(asAccount("0.0.2"), -1_000),
										adjustFrom(asAccount("0.0.3"), +1_000)
								))
				)
				.build();

		tokenTransactTxn = TransactionBody.newBuilder()
				.setTokenTransfers(xfers)
				.build();
	}

	private void givenMissingAccountInTokenAccountAmount() {
		xfers = TokenTransfersTransactionBody.newBuilder()
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.setToken(asToken("0.0.12345"))
								.addAllTransfers(List.of(
										AccountAmount.newBuilder()
												.setAmount(-1000)
												.build(),
										adjustFrom(asAccount("0.0.3"), +1_000)
								))
				)
				.build();

		tokenTransactTxn = TransactionBody.newBuilder()
				.setTokenTransfers(xfers)
				.build();
	}

	private void givenInvalidTokenZeroAmounts() {
		xfers = TokenTransfersTransactionBody.newBuilder()
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.setToken(asToken("0.0.12345"))
								.addAllTransfers(List.of(
										adjustFrom(asAccount("0.0.2"), 0),
										adjustFrom(asAccount("0.0.3"), 0)
								))
				)
				.build();

		tokenTransactTxn = TransactionBody.newBuilder()
				.setTokenTransfers(xfers)
				.build();
	}
}