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
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
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

import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ContractDeleteTransitionLogicTest {
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private ContractID TARGET_ID = ContractID.newBuilder().setContractNum(9_999L).build();
	final private AccountID BENEFICIARY_ID = AccountID.newBuilder().setAccountNum(4_321L).build();
	private static final Instant CONSENSUS_TIME = Instant.ofEpochSecond(1_234_567L);

	@Mock
	private Account beneficiary;
	@Mock
	private Account target;
	@Mock
	private AccountStore accountStore;
	@Mock
	private HederaLedger hederaLedger;
	@Mock
	private TransactionBody contractDeleteTxn;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;

	private ContractDeleteTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new ContractDeleteTransitionLogic(hederaLedger, accountStore, txnCtx);
	}

	@Test
	void hasCorrectApplicability() {
		contractDeleteTxn = body().setContractDeleteInstance(delete()).build();

		assertTrue(subject.applicability().test(contractDeleteTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void acceptsValidTxn() {
		contractDeleteTxn = body().setContractDeleteInstance(delete()).build();

		assertEquals(OK, subject.semanticCheck().apply(contractDeleteTxn));
	}

	@Test
	void rejectsInvalidTxn() {
		contractDeleteTxn = body().setContractDeleteInstance(deleteHasNoContractId()).build();

		assertEquals(INVALID_CONTRACT_ID, subject.semanticCheck().apply(contractDeleteTxn));
	}

	@Test
	void acceptsContractWithZeroBalance() {
		givenValidTxnCtxWithObtainerAccount();
		given(accountStore.loadContract(any())).willReturn(target);
		given(target.getBalance()).willReturn(0L);

		subject.doStateTransition();

		verifyAlways();
	}

	@Test
	void acceptsObtainerAccount() {
		givenValidTxnCtxWithObtainerAccount();
		acceptsWithNonZeroBalance();
	}

	@Test
	void acceptsObtainerContract() {
		givenValidTxnCtxWithObtainerContract();
		acceptsWithNonZeroBalance();
	}

	private void acceptsWithNonZeroBalance() {
		givenLoadedContractWithNonZeroBalance();
		given(accountStore.loadEntityOrFailWith(any(), any(), any(), any())).willReturn(beneficiary);
		given(beneficiary.getId()).willReturn(Id.fromGrpcAccount(BENEFICIARY_ID));
		given(target.getId()).willReturn(Id.fromGrpcContract(TARGET_ID));

		subject.doStateTransition();

		verifyWithNonZeroBalance();
	}

	@Test
	void rejectsInvalidContractId() {
		givenValidTxnCtxWithObtainerAccount();
		given(accountStore.loadContract(any())).willThrow(new InvalidTransactionException(INVALID_CONTRACT_ID));

		thenWillFailWith(INVALID_CONTRACT_ID);
	}

	@Test
	void rejectsContractDeleted() {
		givenValidTxnCtxWithObtainerAccount();
		given(accountStore.loadContract(any())).willThrow(new InvalidTransactionException(CONTRACT_DELETED));

		thenWillFailWith(CONTRACT_DELETED);
	}

	@Test
	void rejectsObtainerRequired() {
		givenValidTxnCtx();
		givenLoadedContractWithNonZeroBalance();

		thenWillFailWith(OBTAINER_REQUIRED);
	}

	@Test
	void rejectsObtainerAccountSameContractId() {
		contractDeleteTxn = body().setContractDeleteInstance(delete().setTransferAccountID(asAccount(TARGET_ID))).build();
		accessor();

		failWithObtainerSameContractId();
	}

	@Test
	void rejectsObtainerContractSameContractId() {
		contractDeleteTxn = body().setContractDeleteInstance(delete().setTransferContractID(TARGET_ID)).build();
		accessor();

		failWithObtainerSameContractId();
	}

	private void failWithObtainerSameContractId() {
		givenLoadedContractWithNonZeroBalance();
		given(target.getId()).willReturn(Id.fromGrpcContract(TARGET_ID));

		thenWillFailWith(OBTAINER_SAME_CONTRACT_ID);
	}

	@Test
	void rejectsObtainerAccountDoesNotExist() {
		givenValidTxnCtxWithObtainerAccount();
		failWithObtainerDoesNotExist();
	}

	@Test
	void rejectsObtainerContractDoesNotExist() {
		givenValidTxnCtxWithObtainerContract();
		failWithObtainerDoesNotExist();
	}

	private void failWithObtainerDoesNotExist() {
		givenLoadedContractWithNonZeroBalance();
		given(accountStore.loadEntityOrFailWith(any(), any(), any(), any())).willThrow(new InvalidTransactionException(OBTAINER_DOES_NOT_EXIST));

		thenWillFailWith(OBTAINER_DOES_NOT_EXIST);
	}

	@Test
	void rejectsDetachedTransferAccount() {
		givenValidTxnCtxWithObtainerAccount();
		givenLoadedContractWithNonZeroBalance();
		given(accountStore.loadEntityOrFailWith(any(), any(), any(), any())).willThrow(new InvalidTransactionException(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));

		thenWillFailWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	private void verifyWithNonZeroBalance() {
		verify(hederaLedger).doTransfer(any(), any(), anyLong());
		verifyAlways();
	}

	private void verifyAlways() {
		verify(target).setDeleted(anyBoolean());
		verify(accountStore).persistAccount(any());
	}

	private void thenWillFailWith(final ResponseCodeEnum responseCode) {
		assertFailsWith(() -> subject.doStateTransition(), responseCode);
		verify(hederaLedger, never()).doTransfer(any(), any(), anyLong());
		verify(target, never()).setDeleted(anyBoolean());
		verify(accountStore, never()).persistAccount(any());
	}

	private void givenLoadedContractWithNonZeroBalance() {
		given(accountStore.loadContract(any())).willReturn(target);
		given(target.getBalance()).willReturn(1L);
	}

	private void givenValidTxnCtxWithObtainerAccount() {
		contractDeleteTxn = body().setContractDeleteInstance(delete().setTransferAccountID(BENEFICIARY_ID)).build();
		accessor();
	}

	private void givenValidTxnCtxWithObtainerContract() {
		contractDeleteTxn = body().setContractDeleteInstance(delete().setTransferContractID(asContract(BENEFICIARY_ID))).build();
		accessor();
	}

	private void givenValidTxnCtx() {
		contractDeleteTxn = body().setContractDeleteInstance(delete()).build();
		accessor();
	}

	private TransactionBody.Builder body() {
		return TransactionBody.newBuilder()
				.setTransactionID(ourTxnId());
	}

	private ContractDeleteTransactionBody.Builder delete() {
		return ContractDeleteTransactionBody.newBuilder()
				.setContractID(TARGET_ID);
	}

	private ContractDeleteTransactionBody.Builder deleteHasNoContractId() {
		return ContractDeleteTransactionBody.newBuilder();
	}

	private void accessor() {
		when(accessor.getTxn()).thenReturn(contractDeleteTxn);
		when(txnCtx.accessor()).thenReturn(accessor);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(CONSENSUS_TIME.getEpochSecond()))
				.build();
	}
}
