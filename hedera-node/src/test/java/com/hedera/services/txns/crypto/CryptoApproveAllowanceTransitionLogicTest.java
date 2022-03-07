package com.hedera.services.txns.crypto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.BoolValue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.CryptoAllowanceAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CryptoApproveAllowanceTransitionLogicTest {
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private AccountStore accountStore;
	@Mock
	private ApproveAllowanceChecks allowanceChecks;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private AliasManager aliasManager;

	private TransactionBody cryptoApproveAllowanceTxn;
	private CryptoAllowanceAccessor accessor;

	private CryptoApproveAllowanceTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new CryptoApproveAllowanceTransitionLogic(txnCtx, accountStore, allowanceChecks,
				dynamicProperties);
	}

	@Test
	void hasCorrectApplicability() throws InvalidProtocolBufferException {
		givenValidTxnCtx();

		assertTrue(subject.applicability().test(cryptoApproveAllowanceTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void happyPathAddsAllowances() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(owner)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender)).willReturn(spenderNum);
		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(accountStore.loadAccountOrFailWith(ownerAcccount.getId(), INVALID_ALLOWANCE_OWNER_ID))
				.willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		subject.doStateTransition();

		assertEquals(1, ownerAccount.getCryptoAllowances().size());
		assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAccount.getNftAllowances().size());

		verify(accountStore).commitAccount(ownerAccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void considersPayerAsOwnerIfNotMentioned() {
		givenValidTxnCtxWithOwnerAsPayer();

		given(accessor.getTxn()).willReturn(cryptoApproveAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		assertEquals(0, payerAcccount.getCryptoAllowances().size());
		assertEquals(0, payerAcccount.getFungibleTokenAllowances().size());
		assertEquals(0, payerAcccount.getNftAllowances().size());

		subject.doStateTransition();

		assertEquals(1, payerAcccount.getCryptoAllowances().size());
		assertEquals(1, payerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, payerAcccount.getNftAllowances().size());

		verify(accountStore).commitAccount(payerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void wipesSerialsWhenApprovedForAll() throws InvalidProtocolBufferException {
		givenValidTxnCtx();

		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(owner)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender)).willReturn(spenderNum);
		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(accountStore.loadAccountOrFailWith(ownerAcccount.getId(), INVALID_ALLOWANCE_OWNER_ID))
				.willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		subject.doStateTransition();

		assertEquals(1, ownerAccount.getCryptoAllowances().size());
		assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAccount.getNftAllowances().size());
		assertEquals(0, ownerAccount.getNftAllowances()
				.get(FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
						EntityNum.fromAccountId(spender))).getSerialNumbers().size());
		verify(accountStore).commitAccount(ownerAccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void checksIfAllowancesExceedLimit() throws InvalidProtocolBufferException {
		Account ownerAcc = mock(Account.class);
		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAcc);
		given(ownerAcc.getTotalAllowances()).willReturn(101);
	void checksIfAllowancesExceedLimit() {
		Account owner = mock(Account.class);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(accountStore.loadAccountOrFailWith(ownerAcccount.getId(), INVALID_ALLOWANCE_OWNER_ID))
				.willReturn(owner);
		given(owner.getTotalAllowances()).willReturn(101);

		givenValidTxnCtx();
		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(owner)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender)).willReturn(spenderNum);

		given(txnCtx.accessor()).willReturn(accessor);

		var exception = assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());
		assertEquals(MAX_ALLOWANCES_EXCEEDED, exception.getResponseCode());
		assertEquals(0, ownerAccount.getCryptoAllowances().size());
		assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAccount.getNftAllowances().size());
		verify(accountStore, never()).commitAccount(ownerAccount);
	}

	@Test
	void semanticCheckDelegatesWorks() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		given(aliasManager.unaliased(owner)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender)).willReturn(spenderNum);
		given(allowanceChecks.allowancesValidation(accessor.getCryptoAllowances(), accessor.getTokenAllowances(),
				accessor.getNftAllowances(), ownerAccount,
				dynamicProperties.maxAllowanceLimitPerTransaction())).willReturn(OK);
		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);
		assertEquals(OK, subject.validateSemantics(accessor));
		given(allowanceChecks.allowancesValidation(
				op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(),
				op.getNftAllowancesList(),
				payerAcccount,
				dynamicProperties.maxAllowanceLimitPerTransaction()))
				.willReturn(OK);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		assertEquals(OK, subject.semanticCheck().apply(cryptoApproveAllowanceTxn));
	}

	@Test
	void emptyAllowancesInStateTransitionWorks() throws InvalidProtocolBufferException {
		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
				).build();
		setAccessor();
		given(aliasManager.unaliased(owner)).willReturn(ownerNum);
		given(txnCtx.accessor()).willReturn(accessor);

		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);

		subject.doStateTransition();
		assertEquals(0, ownerAccount.getCryptoAllowances().size());
		assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAccount.getNftAllowances().size());
		verify(accountStore).commitAccount(ownerAccount);
		assertEquals(0, ownerAcccount.getCryptoAllowances().size());
		assertEquals(0, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAcccount.getNftAllowances().size());
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}


	@Test
	void removesAllowancesWhenAmountIsZero() throws InvalidProtocolBufferException {
		givenTxnCtxWithZeroAmount();
		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(owner)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender)).willReturn(spenderNum);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);
		given(accountStore.loadAccountOrFailWith(ownerAcccount.getId(), INVALID_ALLOWANCE_OWNER_ID))
				.willReturn(ownerAcccount);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);

		subject.doStateTransition();

		assertEquals(0, ownerAccount.getCryptoAllowances().size());
		assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAccount.getNftAllowances().size());

		verify(accountStore).commitAccount(ownerAccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void skipsTxnWhenKeyExistsAndAmountGreaterThanZero() throws InvalidProtocolBufferException {
		var ownerAcccount = new Account(Id.fromGrpcAccount(owner));
		setUpOwnerWithExistingKeys(ownerAcccount);

		assertEquals(1, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getNftAllowances().size());

		givenValidTxnCtx();

		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(owner)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender)).willReturn(spenderNum);
		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(accountStore.loadAccountOrFailWith(ownerAcccount.getId(), INVALID_ALLOWANCE_OWNER_ID))
				.willReturn(ownerAcccount);

		subject.doStateTransition();

		assertEquals(1, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getNftAllowances().size());

		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	private void setUpOwnerWithExistingKeys(final Account ownerAcccount) {
		Map<EntityNum, Long> cryptoAllowances = new TreeMap<>();
		Map<FcTokenAllowanceId, Long> tokenAllowances = new TreeMap<>();
		Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances = new TreeMap<>();
		final var id = FcTokenAllowanceId.from(EntityNum.fromTokenId(token1),
				EntityNum.fromAccountId(spender));
		final var Nftid = FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
				EntityNum.fromAccountId(spender));
		final var val = FcTokenAllowance.from(false, List.of(1L, 100L));
		cryptoAllowances.put(EntityNum.fromAccountId(spender), 10000L);
		tokenAllowances.put(id, 100000L);
		nftAllowances.put(Nftid, val);
		ownerAcccount.setCryptoAllowances(cryptoAllowances);
		ownerAcccount.setFungibleTokenAllowances(tokenAllowances);
		ownerAcccount.setNftAllowances(nftAllowances);
	}

	private void givenTxnCtxWithZeroAmount() throws InvalidProtocolBufferException {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder().setSpender(spender).setAmount(
				0L).build();
		final TokenAllowance tokenAllowance = TokenAllowance.newBuilder().setSpender(spender).setAmount(
				0L).setTokenId(token1).build();
		final NftAllowance nftAllowance = NftAllowance.newBuilder().setSpender(spender)
				.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).addAllSerialNumbers(List.of(1L, 10L)).build();
		final CryptoAllowance cryptoAllowance = CryptoAllowance
				.newBuilder()
				.setOwner(ownerId)
				.setSpender(spender1)
				.setAmount(0L).build();
		final TokenAllowance tokenAllowance = TokenAllowance.newBuilder()
				.setSpender(spender1)
				.setAmount(0L)
				.setTokenId(token1)
				.setOwner(ownerId)
				.build();
		final NftAllowance nftAllowance = NftAllowance.newBuilder()
				.setSpender(spender1)
				.setTokenId(token2)
				.setApprovedForAll(BoolValue.of(false))
				.setOwner(ownerId)
				.addAllSerialNumbers(List.of(1L, 10L))
				.build();

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);
		cryptoAllowances.add(cryptoAllowance);
		tokenAllowances.add(tokenAllowance);
		nftAllowances.add(nftAllowance);

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();

		setAccessor();
		ownerAccount.setNftAllowances(new HashMap<>());
		ownerAccount.setCryptoAllowances(new HashMap<>());
		ownerAccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private void givenValidTxnCtx() throws InvalidProtocolBufferException {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();
		setAccessor();
		ownerAccount.setNftAllowances(new HashMap<>());
		ownerAccount.setCryptoAllowances(new HashMap<>());
		ownerAccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private void setAccessor() throws InvalidProtocolBufferException {
		final var txn = new SwirldTransaction(
				Transaction.newBuilder().setBodyBytes(cryptoApproveAllowanceTxn.toByteString()).build().toByteArray());
		accessor = new CryptoAllowanceAccessor(txn, aliasManager);
	}

	private void givenValidTxnCtxWithOwnerAsPayer() {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder()
				.setSpender(spender1)
				.setAmount(10L).build();
		final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder()
				.setSpender(spender1)
				.setAmount(10L)
				.setTokenId(token1)
				.build();
		final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
				.setSpender(spender1)
				.setTokenId(token2)
				.setApprovedForAll(BoolValue.of(true))
				.addAllSerialNumbers(List.of(1L, 10L)).build();

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

		payerAcccount.setNftAllowances(new HashMap<>());
		payerAcccount.setCryptoAllowances(new HashMap<>());
		payerAcccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(owner)
				.setAccountID(payerId)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private final AccountID spender = asAccount("0.0.123");
	private final TokenID token1 = asToken("0.0.100");
	private final TokenID token2 = asToken("0.0.200");
	private final AccountID owner = asAccount("0.0.5000");
	private final Instant consensusTime = Instant.now();
	private final EntityNum spenderNum = EntityNum.fromAccountId(spender);
	private final EntityNum ownerNum = EntityNum.fromAccountId(owner);
	private static final AccountID spender1 = asAccount("0.0.123");
	private static final TokenID token1 = asToken("0.0.100");
	private static final TokenID token2 = asToken("0.0.200");
	private static final AccountID payerId = asAccount("0.0.5000");
	private static final AccountID ownerId = asAccount("0.0.6000");
	private static final Instant consensusTime = Instant.now();
	private final Token token1Model = new Token(Id.fromGrpcToken(token1));
	private final Token token2Model = new Token(Id.fromGrpcToken(token2));
	private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder().setSpender(spender).setAmount(
			10L).build();
	private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder().setSpender(spender).setAmount(
			10L).setTokenId(token1).build();
	private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder()
			.setSpender(spender1)
			.setOwner(ownerId)
			.setAmount(10L).build();
	private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder()
			.setSpender(spender1)
			.setAmount(10L)
			.setTokenId(token1)
			.setOwner(ownerId)
			.build();
	private final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
			.setSpender(spender)
			.setTokenId(token2).setApprovedForAll(BoolValue.of(true))
			.setSpender(spender1)
			.setOwner(ownerId)
			.setTokenId(token2)
			.setApprovedForAll(BoolValue.of(true))
			.addAllSerialNumbers(List.of(1L, 10L)).build();
	private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
	private List<TokenAllowance> tokenAllowances = new ArrayList<>();
	private List<NftAllowance> nftAllowances = new ArrayList<>();
	private final Account ownerAccount = new Account(Id.fromGrpcAccount(owner));
	private final Account payerAcccount = new Account(Id.fromGrpcAccount(payerId));
	private final Account ownerAcccount = new Account(Id.fromGrpcAccount(ownerId));
}
