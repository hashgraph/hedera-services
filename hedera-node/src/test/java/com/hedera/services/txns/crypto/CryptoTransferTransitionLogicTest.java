package com.hedera.services.txns.crypto;

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
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.txns.crypto.CryptoTransferTransitionLogic.tryTransfers;
import static com.hedera.test.utils.IdUtils.adjustFrom;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.test.utils.TxnUtils.withAdjustments;

public class CryptoTransferTransitionLogicTest {
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private AccountID a = AccountID.newBuilder().setAccountNum(9_999L).build();
	final private AccountID b = AccountID.newBuilder().setAccountNum(8_999L).build();
	final private AccountID c = AccountID.newBuilder().setAccountNum(7_999L).build();

	private HederaLedger ledger;
	private OptionValidator validator;
	private TransactionBody cryptoTransferTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private CryptoTransferTransitionLogic subject;

	@BeforeEach
	private void setup() {
		txnCtx = mock(TransactionContext.class);
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);

		validator = mock(OptionValidator.class);
		withRubberstampingValidator();

		given(ledger.isSmartContract(any())).willReturn(false);
		given(ledger.exists(any())).willReturn(true);

		subject = new CryptoTransferTransitionLogic(ledger, validator, txnCtx);
	}

	@Test
	public void hasOnlyCryptoHandlesMissingAccounts() {
		given(ledger.exists(asAccount("0.0.75231"))).willReturn(false);

		// expect:
		assertFalse(CryptoTransferTransitionLogic.hasOnlyCryptoAccounts(ledger, xfers.getTransfers()));
	}

	@Test
	public void capturesInvalidXfers() {
		givenValidTxnCtx();
		// and:
		given(ledger.doAtomicTransfers(xfers)).willReturn(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);
	}

	@Test
	public void rejectsRepeatedAccountAmounts() {
		// setup:
		xfers = xfers.toBuilder()
				.setTransfers(xfers.getTransfers().toBuilder()
						.addAccountAmounts(adjustFrom(asAccount("0.0.75231"), -1_000)))
				.build();

		givenValidTxnCtx();
		// and:
		given(validator.isAcceptableTokenTransfersLength(any())).willReturn(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);

		// expect:
		assertEquals(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsExceedingTransfersLength() {
		givenValidTxnCtx();
		given(validator.isAcceptableTokenTransfersLength(any())).willReturn(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);

		// expect:
		assertEquals(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsEmptyTokenTransferAccountAmounts() {
		givenValidTxnCtx();
		given(validator.isAcceptableTokenTransfersLength(any())).willReturn(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS);

		// expect:
		assertEquals(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void requiresOnlyCryptoAccounts() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		given(ledger.isSmartContract(any())).willReturn(true);

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, tryTransfers(ledger, xfers.getTransfers()));
	}

	@Test
	public void translatesMissingAccountException() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		willThrow(MissingAccountException.class).given(ledger).doTransfers(any());

		// expect:
		assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, tryTransfers(ledger, xfers.getTransfers()));
	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));

		// when:
		subject.doStateTransition();

		// expect:
		verify(ledger).doAtomicTransfers(cryptoTransferTxn.getCryptoTransfer());
		// and:
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void translatesAccountDeletedException() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		// and:
		willThrow(DeletedAccountException.class).given(ledger).doTransfers(any());

		// expect:
		assertEquals(ACCOUNT_DELETED, tryTransfers(ledger, xfers.getTransfers()));
	}

	@Test
	public void translatesInsufficientFundsException() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		willThrow(InsufficientFundsException.class).given(ledger).doTransfers(any());

		// expect:
		assertEquals(INSUFFICIENT_ACCOUNT_BALANCE, tryTransfers(ledger, xfers.getTransfers()));
	}

	@Test
	public void translatesUnknownException() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		willThrow(RuntimeException.class).given(ledger).doAtomicTransfers(any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));

		// expect:
		assertTrue(subject.applicability().test(cryptoTransferTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsRepeatedAccount() {
		givenValidTxnCtx(withAdjustments(a, -2L, a, 1L, c, 1L));

		// expect:
		assertEquals(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsNonNetZeroAdjustment() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 3L, c, 1L));

		// expect:
		assertEquals(INVALID_ACCOUNT_AMOUNTS, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsUndulyLongTransferList() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		given(validator.isAcceptableTransfersLength(any())).willReturn(false);

		// expect:
		assertEquals(TRANSFER_LIST_SIZE_LIMIT_EXCEEDED, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsDuplicateTokens() {
		givenDuplicateTokens();

		// expect:
		assertEquals(TOKEN_ID_REPEATED_IN_TOKEN_LIST, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsInvalidTokenDuplicateAccounts() {
		givenInvalidTokenDuplicateAccounts();

		// expect:
		assertEquals(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsInvalidTokenAdjustment() {
		givenInvalidTokenAdjustment();

		// expect:
		assertEquals(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsMissingTokenInTransfer() {
		givenMissingTokenInTransfer();

		// expect:
		assertEquals(INVALID_TOKEN_ID, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsMissingAccountInTokenAccountAmount() {
		givenMissingAccountInTokenAccountAmount();

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	@Test
	public void rejectsInvalidTokenZeroAmounts() {
		givenInvalidTokenZeroAmounts();

		assertEquals(INVALID_ACCOUNT_AMOUNTS, subject.syntaxCheck().apply(cryptoTransferTxn));
	}

	private void givenValidTxnCtx(TransferList wrapper) {
		cryptoTransferTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoTransfer(
						CryptoTransferTransactionBody.newBuilder()
								.setTransfers(wrapper)
								.build()
				).build();
		given(accessor.getTxn()).willReturn(cryptoTransferTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(ledger.doAtomicTransfers(cryptoTransferTxn.getCryptoTransfer())).willReturn(SUCCESS);
	}

	private void givenValidTxnCtx() {
		cryptoTransferTxn = TransactionBody.newBuilder()
				.setCryptoTransfer(xfers)
				.build();
		given(accessor.getTxn()).willReturn(cryptoTransferTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(ledger.doAtomicTransfers(xfers)).willReturn(SUCCESS);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}


	private void withRubberstampingValidator() {
		given(validator.isAcceptableTransfersLength(any())).willReturn(true);
		given(validator.isAcceptableTokenTransfersLength(any())).willReturn(OK);
	}

	CryptoTransferTransactionBody xfers = CryptoTransferTransactionBody.newBuilder()
			.setTransfers(TransferList.newBuilder()
					.addAccountAmounts(adjustFrom(asAccount("0.0.75231"), -1_000))
					.addAccountAmounts(adjustFrom(asAccount("0.0.2"), +1_000))
					.build())
			.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(asToken("0.0.12345"))
					.addAllTransfers(List.of(
							adjustFrom(asAccount("0.0.2"), -1_000),
							adjustFrom(asAccount("0.0.3"), +1_000)
					)))
			.build();

	private void givenDuplicateTokens() {
		xfers = CryptoTransferTransactionBody.newBuilder()
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

		cryptoTransferTxn = TransactionBody.newBuilder()
				.setCryptoTransfer(xfers)
				.build();
	}

	private void givenInvalidTokenDuplicateAccounts() {
		xfers = CryptoTransferTransactionBody.newBuilder()
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.setToken(asToken("0.0.12345"))
								.addAllTransfers(List.of(
										adjustFrom(asAccount("0.0.2"), -1_000),
										adjustFrom(asAccount("0.0.2"), +1_000)
								))
				)
				.build();

		cryptoTransferTxn = TransactionBody.newBuilder()
				.setCryptoTransfer(xfers)
				.build();
	}

	private void givenInvalidTokenAdjustment() {
		xfers = CryptoTransferTransactionBody.newBuilder()
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.setToken(asToken("0.0.12345"))
								.addAllTransfers(List.of(
										adjustFrom(asAccount("0.0.2"), -1_000),
										adjustFrom(asAccount("0.0.3"), -1_000)
								))
				)
				.build();

		cryptoTransferTxn = TransactionBody.newBuilder()
				.setCryptoTransfer(xfers)
				.build();
	}

	private void givenMissingTokenInTransfer() {
		xfers = CryptoTransferTransactionBody.newBuilder()
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.addAllTransfers(List.of(
										adjustFrom(asAccount("0.0.2"), -1_000),
										adjustFrom(asAccount("0.0.3"), +1_000)
								))
				)
				.build();

		cryptoTransferTxn = TransactionBody.newBuilder()
				.setCryptoTransfer(xfers)
				.build();
	}

	private void givenMissingAccountInTokenAccountAmount() {
		xfers = CryptoTransferTransactionBody.newBuilder()
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

		cryptoTransferTxn = TransactionBody.newBuilder()
				.setCryptoTransfer(xfers)
				.build();
	}

	private void givenInvalidTokenZeroAmounts() {
		xfers = CryptoTransferTransactionBody.newBuilder()
				.addTokenTransfers(
						TokenTransferList.newBuilder()
								.setToken(asToken("0.0.12345"))
								.addAllTransfers(List.of(
										adjustFrom(asAccount("0.0.2"), 0),
										adjustFrom(asAccount("0.0.3"), 0)
								))
				)
				.build();

		cryptoTransferTxn = TransactionBody.newBuilder()
				.setCryptoTransfer(xfers)
				.build();
	}
}
