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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.CryptoDeleteAccessor;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(LogCaptureExtension.class)
class CryptoDeleteTransitionLogicTest {
	private final AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	private final AccountID target = AccountID.newBuilder().setAccountNum(9_999L).build();
	private final AccountID aliasAccountPayer = asAccountWithAlias("aaa");
	private final AccountID aliasAccountTarget = asAccountWithAlias("bbb");
	private final EntityNum payerNum = EntityNum.fromAccountId(payer);
	private final EntityNum targetNum = EntityNum.fromAccountId(target);
	private final boolean withKnownTreasury = true;

	private HederaLedger ledger;
	private TransactionBody cryptoDeleteTxn;
	private SigImpactHistorian sigImpactHistorian;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor platformAccessor;
	private CryptoDeleteAccessor accessor;
	private AliasManager aliasManager;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private CryptoDeleteTransitionLogic subject;

	@BeforeEach
	private void setup() {
		txnCtx = mock(TransactionContext.class);
		ledger = mock(HederaLedger.class);
		aliasManager = mock(AliasManager.class);
		sigImpactHistorian = mock(SigImpactHistorian.class);
		platformAccessor = mock(PlatformTxnAccessor.class);

		given(ledger.allTokenBalancesVanish(target)).willReturn(true);
		given(aliasManager.unaliased(payer)).willReturn(payerNum);
		given(aliasManager.unaliased(target)).willReturn(targetNum);

		subject = new CryptoDeleteTransitionLogic(ledger, sigImpactHistorian, txnCtx);
	}

	@Test
	void hasCorrectApplicability() throws InvalidProtocolBufferException {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(cryptoDeleteTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void rejectsTargetAsBeneficiary() throws InvalidProtocolBufferException {
		givenValidTxnCtx(target);

		// expect:
		assertEquals(TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT, subject.validateSemantics(platformAccessor));
	}

	@Test
	void acceptsValidTxn() throws InvalidProtocolBufferException {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.validateSemantics(platformAccessor));
	}

	@Test
	void translatesMissingAccount() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		willThrow(MissingAccountException.class).given(ledger).delete(any(), any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	void translatesDeletedAccount() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		willThrow(DeletedAccountException.class).given(ledger).delete(any(), any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(ACCOUNT_DELETED);
	}

	@Test
	void followsHappyPath() throws InvalidProtocolBufferException {
		givenValidTxnCtx();

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger).delete(target, payer);
		verify(txnCtx).setStatus(SUCCESS);
		verify(sigImpactHistorian).markEntityChanged(target.getAccountNum());
	}

	@Test
	void rejectsDetachedAccountAsTarget() throws InvalidProtocolBufferException {
		// setup:
		givenValidTxnCtx();
		given(ledger.isDetached(target)).willReturn(true);

		// when:
		subject.doStateTransition();

		// when:
		verify(ledger, never()).delete(target, payer);
		verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void rejectsDetachedAccountAsReceiver() throws InvalidProtocolBufferException {
		// setup:
		final var receiver = asAccount("0.0.7676");
		final var receiverNum = EntityNum.fromAccountId(receiver);

		givenValidTxnCtx(receiver);
		given(aliasManager.unaliased(receiver)).willReturn(receiverNum);
		given(ledger.isDetached(receiver)).willReturn(true);

		// when:
		subject.doStateTransition();

		// when:
		verify(ledger, never()).delete(target, receiver);
		verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void capturesFailInvalid() throws InvalidProtocolBufferException {
		// setup:
		givenValidTxnCtx();
		given(ledger.isKnownTreasury(target)).willThrow(RuntimeException.class);
		// and:
		var desired = "Avoidable exception! java.lang.RuntimeException: null";

		// when:
		subject.doStateTransition();

		// when:
		verify(txnCtx).setStatus(FAIL_INVALID);
		assertThat(logCaptor.warnLogs(), contains(desired));
	}

	@Test
	void rejectsDeletionOfKnownTreasury() throws InvalidProtocolBufferException {
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
	void rejectsIfTargetHasNonZeroTokenBalances() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		given(ledger.allTokenBalancesVanish(target)).willReturn(false);

		// when:
		subject.doStateTransition();

		// then:
		verify(ledger, never()).delete(target, payer);
		verify(txnCtx).setStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
	}

	@Test
	void rejectsIfTargetMissing() throws InvalidProtocolBufferException {
		givenDeleteTxnMissingTarget();

		// when:
		ResponseCodeEnum validity = subject.validateSemantics(platformAccessor);

		// then:
		assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, validity);
	}

	@Test
	void rejectsIfTransferMissing() throws InvalidProtocolBufferException {
		givenDeleteTxnMissingTransfer();

		// when:
		ResponseCodeEnum validity = subject.validateSemantics(platformAccessor);

		// then:
		assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, validity);
	}

	@Test
	void worksWithAlias() throws InvalidProtocolBufferException {
		AccountID aliasedTransfer = asAccountWithAlias("ccc");
		givenDeleteTxnWithAlias(aliasedTransfer);
		given(ledger.allTokenBalancesVanish(target)).willReturn(true);

		ResponseCodeEnum validity = subject.validateSemantics(platformAccessor);
		assertEquals(OK, validity);

		subject.doStateTransition();

		verify(ledger).delete(target, payer);
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	void failsTransitionIfInvalidAccountID() throws InvalidProtocolBufferException {
		AccountID aliasedTransfer = asAccountWithAlias("ccc");
		givenDeleteTxnWithAlias(aliasedTransfer);
		given(ledger.allTokenBalancesVanish(target)).willReturn(true);
		willThrow(new MissingAccountException(target)).given(ledger).delete(target, payer);

		subject.doStateTransition();
		verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
	}

	@Test
	void failsTransitionIfInvalidTransferAccountID() throws InvalidProtocolBufferException {
		AccountID aliasedTransfer = asAccountWithAlias("ccc");
		givenDeleteTxnWithAlias(aliasedTransfer);
		given(aliasManager.unaliased(aliasedTransfer)).willReturn(EntityNum.MISSING_NUM);
		subject.doStateTransition();
		verify(txnCtx).setStatus(INVALID_TRANSFER_ACCOUNT_ID);
	}

	private void givenValidTxnCtx() throws InvalidProtocolBufferException {
		givenValidTxnCtx(payer);
	}

	private void givenValidTxnCtx(AccountID transfer) throws InvalidProtocolBufferException {
		cryptoDeleteTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDelete(
						CryptoDeleteTransactionBody.newBuilder()
								.setDeleteAccountID(target)
								.setTransferAccountID(transfer)
								.build()
				).build();
		setAccessor();
	}

	private void givenDeleteTxnMissingTarget() throws InvalidProtocolBufferException {
		cryptoDeleteTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDelete(
						CryptoDeleteTransactionBody.newBuilder()
								.setTransferAccountID(asAccount("0.0.1234"))
								.build()
				).build();
		setAccessor();
	}

	private void givenDeleteTxnMissingTransfer() throws InvalidProtocolBufferException {
		cryptoDeleteTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDelete(
						CryptoDeleteTransactionBody.newBuilder()
								.setDeleteAccountID(target)
								.build()
				).build();
		setAccessor();
	}

	private void givenDeleteTxnWithAlias(AccountID aliasedTransfer) throws InvalidProtocolBufferException {
		cryptoDeleteTxn = TransactionBody.newBuilder()
				.setTransactionID(txnIdWithAlias())
				.setCryptoDelete(
						CryptoDeleteTransactionBody.newBuilder()
								.setDeleteAccountID(aliasAccountTarget)
								.setTransferAccountID(aliasedTransfer)
								.build()
				).build();

		given(aliasManager.unaliased(aliasAccountTarget)).willReturn(targetNum);
		given(aliasManager.unaliased(aliasedTransfer)).willReturn(payerNum);
		setAccessor();
	}

	private void setAccessor() throws InvalidProtocolBufferException {
		final var txn = new SwirldTransaction(
				Transaction.newBuilder().setBodyBytes(cryptoDeleteTxn.toByteString()).build().toByteArray());
		accessor = new CryptoDeleteAccessor(txn.getContentsDirect(), aliasManager);
		given(txnCtx.accessor()).willReturn(platformAccessor);
		given(platformAccessor.getDelegate()).willReturn(accessor);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}

	private TransactionID txnIdWithAlias() {
		return TransactionID.newBuilder()
				.setAccountID(aliasAccountPayer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}
}
