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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.test.utils.TxnUtils.withAdjustments;

@RunWith(JUnitPlatform.class)
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

		subject = new CryptoTransferTransitionLogic(ledger, validator, txnCtx);
	}

	@Test
	public void requiresOnlyCryptoAccounts() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		given(ledger.isSmartContract(any())).willReturn(true);

		// when:
		subject.doStateTransition();

		// expect:
		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	public void translatesMissingAccountException() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		willThrow(MissingAccountException.class).given(ledger).doTransfers(any());

		// when:
		subject.doStateTransition();

		// expect:
		verify(txnCtx).setStatus(ACCOUNT_ID_DOES_NOT_EXIST);
	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));

		// when:
		subject.doStateTransition();

		// expect:
		verify(ledger).doTransfers(cryptoTransferTxn.getCryptoTransfer().getTransfers());
		// and:
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void translatesAccountDeletedException() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		willThrow(DeletedAccountException.class).given(ledger).doTransfers(any());

		// when:
		subject.doStateTransition();

		// expect:
		verify(txnCtx).setStatus(ACCOUNT_DELETED);
	}

	@Test
	public void translatesInsufficientFundsException() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		willThrow(InsufficientFundsException.class).given(ledger).doTransfers(any());

		// when:
		subject.doStateTransition();

		// expect:
		verify(txnCtx).setStatus(INSUFFICIENT_ACCOUNT_BALANCE);
	}

	@Test
	public void translatesUnknownException() {
		givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));
		willThrow(RuntimeException.class).given(ledger).doTransfers(any());

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
		given(validator.isAcceptableLength(any())).willReturn(false);

		// expect:
		assertEquals(TRANSFER_LIST_SIZE_LIMIT_EXCEEDED, subject.syntaxCheck().apply(cryptoTransferTxn));
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
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}


	private void withRubberstampingValidator() {
		given(validator.isAcceptableLength(any())).willReturn(true);
	}
}
