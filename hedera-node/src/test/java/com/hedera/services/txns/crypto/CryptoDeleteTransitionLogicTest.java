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
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CryptoDeleteTransitionLogicTest {
	final private AccountID targetAsGrpc = AccountID.newBuilder().setAccountNum(9_999L).build();
	final private AccountID beneficiaryAsGrpc = AccountID.newBuilder().setAccountNum(1_234L).build();

	@Mock private TransactionContext txnCtx;
	@Mock private TransactionBody cryptoDeleteTxn;
	@Mock private PlatformTxnAccessor accessor;
	@Mock private HederaLedger ledger;
	@Mock private AccountStore accountStore;
	@Mock private TypedTokenStore tokenStore;
	@Mock private Account target;
	@Mock private Id targetId;
	@Mock private Account beneficiary;
	@Mock private Token token;
	@Mock private CopyOnWriteIds copyOnWriteIds;
	@Mock private TokenRelationship tokenRelationship;

	private CryptoDeleteTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new CryptoDeleteTransitionLogic(txnCtx, ledger, accountStore, tokenStore);
	}

	@Test
	void hasCorrectApplicability() {

		// given:
		givenValidTxnCtx();
		// expect:
		assertTrue(subject.applicability().test(cryptoDeleteTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void acceptsValidTxn() {
		// given:
		givenValidTxnCtx();
		// expect:
		assertEquals(OK, subject.semanticCheck().apply(cryptoDeleteTxn));
	}

	@Test
	void followsHappyPath() {
		// given:
		givenValidTxnCtx();
		givenLoadedAccounts();
		givenTargetAsGrpcAccount();
		given(target.getBalance()).willReturn(1L);
		given(target.getAssociatedTokens()).willReturn(copyOnWriteIds);
		given(tokenStore.isKnownTreasury(any())).willReturn(false);
		given(copyOnWriteIds.getAsIds()).willReturn(List.of(IdUtils.asToken("1.2.3")));
		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.isDeleted()).willReturn(true);
		// when:
		subject.doStateTransition();
		// then:
		verify(ledger).doZeroSum(any());
		verify(target).delete();
		verify(accountStore, times(2)).persistAccount(any());
	}

	@Test
	void rejectsTargetAsBeneficiary() {
		// given:
		givenValidTxnCtx(targetAsGrpc);
		// expect:
		assertEquals(TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT, subject.semanticCheck().apply(cryptoDeleteTxn));
	}



	@Test
	void translatesMissingAccount() {
		givenValidTxnCtx();
		givenMissingAccount();

		thenTransactionWillFailWith(INVALID_ACCOUNT_ID);
	}

	@Test
	void translatesDeletedAccount() {
		givenValidTxnCtx();
		givenDeletedAccount();

		thenTransactionWillFailWith(ACCOUNT_DELETED);
	}

	@Test
	void rejectsDetachedAccountAsTarget() {
		givenValidTxnCtx();
		givenDetachedTarget();

		thenTransactionWillFailWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void rejectsDetachedAccountAsReceiver() {
		// given:
		givenValidTxnCtx();
		givenDetachedBeneficiary();
		thenTransactionWillFailWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void capturesFailInvalid() {
		// given:
		givenValidTxnCtx();
		givenLoadedAccounts();
		givenTargetAsGrpcAccount();
		given(tokenStore.isKnownTreasury(any())).willThrow(new InvalidTransactionException(FAIL_INVALID));

		thenTransactionWillFailWith(FAIL_INVALID);
	}

	@Test
	void rejectsDeletionOfKnownTreasury() {
		// given:
		givenValidTxnCtx();
		givenLoadedAccounts();
		givenTargetAsGrpcAccount();
		givenTreasury(true);

		thenTransactionWillFailWith(ACCOUNT_IS_TREASURY);
	}

	@Test
	void rejectsIfTargetHasNonZeroTokenBalances() {
		// given:
		givenValidTxnCtx();
		givenLoadedAccounts();
		givenTargetAsGrpcAccount();
		givenTreasury(false);
		given(copyOnWriteIds.getAsIds()).willReturn(List.of(IdUtils.asToken("1.2.3")));
		given(target.getAssociatedTokens()).willReturn(copyOnWriteIds);
		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.isDeleted()).willReturn(false);
		given(tokenStore.loadTokenRelationship(any(), any())).willReturn(tokenRelationship);
		given(tokenRelationship.getBalance()).willReturn(1L);

		thenTransactionWillFailWith(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
	}

	@Test
	void rejectsIfTargetMissing() {
		givenDeleteTxnMissingTarget();
		// when:
		ResponseCodeEnum validity = subject.semanticCheck().apply(cryptoDeleteTxn);

		// then:
		assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, validity);
	}

	@Test
	void rejectsIfTransferMissing() {
		givenDeleteTxnMissingTransfer();

		// when:
		ResponseCodeEnum validity = subject.semanticCheck().apply(cryptoDeleteTxn);

		// then:
		assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, validity);
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(beneficiaryAsGrpc);
	}

	private void givenValidTxnCtx(AccountID beneficiary) {
		cryptoDeleteTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDelete(
						CryptoDeleteTransactionBody.newBuilder()
								.setDeleteAccountID(targetAsGrpc)
								.setTransferAccountID(beneficiary)
								.build()
				).build();
		lenient().when(accessor.getTxn()).thenReturn(cryptoDeleteTxn);
		lenient().when(txnCtx.accessor()).thenReturn(accessor);
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
				.setAccountID(beneficiaryAsGrpc)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}

	private void givenMissingAccount() {
		given(accountStore.loadAccount(any())).willThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID));
	}

	private void givenDeletedAccount() {
		given(accountStore.loadAccount(any())).willThrow(new InvalidTransactionException(ACCOUNT_DELETED));
	}

	private void givenLoadedAccounts() {
		given(accountStore.loadAccount(Id.fromGrpcAccount(targetAsGrpc))).willReturn(target);
		given(accountStore.loadAccount(Id.fromGrpcAccount(beneficiaryAsGrpc))).willReturn(beneficiary);
	}

	private void givenDetachedTarget() {
		given(accountStore.loadAccount(Id.fromGrpcAccount(targetAsGrpc)))
				.willThrow(new InvalidTransactionException(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
	}

	private void givenDetachedBeneficiary() {
		given(accountStore.loadAccount(Id.fromGrpcAccount(targetAsGrpc)))
				.willReturn(target);
		given(accountStore.loadAccount(Id.fromGrpcAccount(beneficiaryAsGrpc)))
				.willThrow(new InvalidTransactionException(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
	}

	private void givenTargetAsGrpcAccount() {
		given(targetId.asGrpcAccount()).willReturn(IdUtils.asAccount("0.0.0"));
		given(target.getId()).willReturn(targetId);
	}

	private void givenTreasury(boolean isKnown) {
		given(tokenStore.isKnownTreasury(any())).willReturn(isKnown);
	}

	private void thenTransactionWillFailWith(final ResponseCodeEnum responseCode) {
		TxnUtils.assertFailsWith(() -> subject.doStateTransition(), responseCode);
		verify(ledger, never()).doZeroSum(any());
		verify(accountStore, never()).persistAccount(any());
	}
}