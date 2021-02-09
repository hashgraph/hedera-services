package com.hedera.services.txns.crypto;

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
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.hedera.test.utils.IdUtils.asAccount;

import java.time.Instant;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class CryptoDeleteTransitionLogicTest {
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private AccountID target = AccountID.newBuilder().setAccountNum(9_999L).build();
	final private boolean withKnownTreasury = true;

	private HederaLedger ledger;
	private TransactionBody cryptoDeleteTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private CryptoDeleteTransitionLogic subject;

	@BeforeEach
	private void setup() {
		txnCtx = mock(TransactionContext.class);
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);

		given(ledger.allTokenBalancesVanish(target)).willReturn(true);

		subject = new CryptoDeleteTransitionLogic(ledger, txnCtx);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(cryptoDeleteTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void rejectsTargetAsBeneficiary() {
		givenValidTxnCtx(target);

		// expect:
		assertEquals(TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT, subject.syntaxCheck().apply(cryptoDeleteTxn));
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(cryptoDeleteTxn));
	}

	@Test
	public void translatesMissingAccount() {
		givenValidTxnCtx();
		willThrow(MissingAccountException.class).given(ledger).delete(any(), any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	public void translatesDeletedAccount() {
		givenValidTxnCtx();
		willThrow(DeletedAccountException.class).given(ledger).delete(any(), any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(ACCOUNT_DELETED);
	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx();

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).delete(target, payer);
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void rejectsDeletionOfKnownTreasury() {
		// setup:
		givenValidTxnCtx();
		given(ledger.isKnownTreasury(target)).willReturn(withKnownTreasury);

		// when:
		subject.doStateTransition();

		// when:
		verify(ledger, never()).delete(target, payer);
		verify(txnCtx).setStatus(ACCOUNT_IS_TREASURY);
	}

	@Test
	public void rejectsIfTargetHasNonZeroTokenBalances() {
		givenValidTxnCtx();
		given(ledger.allTokenBalancesVanish(target)).willReturn(false);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger, never()).delete(target, payer);
		verify(txnCtx).setStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
	}

	@Test
	public void rejectsIfTargetMissing() {
		givenDeleteTxnMissingTarget();

		// when:
		ResponseCodeEnum validity = subject.syntaxCheck().apply(cryptoDeleteTxn);

		// then:
		assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, validity);
	}

	@Test
	public void rejectsIfTransferMissing() {
		givenDeleteTxnMissingTransfer();

		// when:
		ResponseCodeEnum validity = subject.syntaxCheck().apply(cryptoDeleteTxn);

		// then:
		assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, validity);
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(payer);
	}

	private void givenValidTxnCtx(AccountID transfer) {
		cryptoDeleteTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDelete(
						CryptoDeleteTransactionBody.newBuilder()
								.setDeleteAccountID(target)
								.setTransferAccountID(transfer)
								.build()
				).build();
		given(accessor.getTxn()).willReturn(cryptoDeleteTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private void givenDeleteTxnMissingTarget() {
		cryptoDeleteTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDelete(
						CryptoDeleteTransactionBody.newBuilder()
								.setTransferAccountID(asAccount("0.0.1234"))
								.build()
				).build();
	}

	private void givenDeleteTxnMissingTransfer() {
		cryptoDeleteTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDelete(
						CryptoDeleteTransactionBody.newBuilder()
								.setDeleteAccountID(asAccount("0.0.1234"))
								.build()
				).build();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}
}
