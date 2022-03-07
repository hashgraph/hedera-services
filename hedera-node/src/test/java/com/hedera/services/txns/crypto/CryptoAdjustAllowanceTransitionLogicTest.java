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
import com.hedera.services.context.SideEffectsTracker;
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
import com.hedera.services.txns.crypto.validators.AdjustAllowanceChecks;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.CryptoAllowanceAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAdjustAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
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
class CryptoAdjustAllowanceTransitionLogicTest {
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private AccountStore accountStore;
	@Mock
	private AdjustAllowanceChecks adjustAllowanceChecks;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private SideEffectsTracker sideEffectsTracker;
	@Mock
	private AliasManager aliasManager;

	private TransactionBody cryptoAdjustAllowanceTxn;
	private CryptoAllowanceAccessor accessor;

	CryptoAdjustAllowanceTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new CryptoAdjustAllowanceTransitionLogic(txnCtx, accountStore,
				adjustAllowanceChecks, dynamicProperties, sideEffectsTracker);
	}

	@Test
	void hasCorrectApplicability() throws InvalidProtocolBufferException {
		givenValidTxnCtx();

		assertTrue(subject.applicability().test(cryptoAdjustAllowanceTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void happyPathAdjustsAllowances() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		addExistingAllowances();
		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(ownerId)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender1)).willReturn(spenderNum);
		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		subject.doStateTransition();

		assertEquals(2, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(2, ownerAcccount.getNftAllowances().size());
		assertEquals(30L, ownerAcccount.getCryptoAllowances().get(EntityNum.fromAccountId(spender1)));
		assertEquals(10L, ownerAcccount.getCryptoAllowances().get(EntityNum.fromAccountId(spender2)));
		assertEquals(20, ownerAcccount.getFungibleTokenAllowances()
				.get(FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1))));
		assertEquals(FcTokenAllowance.from(true), ownerAcccount.getNftAllowances()
				.get(FcTokenAllowanceId.from(EntityNum.fromTokenId(token2), EntityNum.fromAccountId(spender1))));
		assertEquals(FcTokenAllowance.from(List.of(1L, 20L)), ownerAcccount.getNftAllowances()
				.get(FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1))));

		verify(accountStore).commitAccount(ownerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void doesntDoAnythingIfAmountZeroForNonExistingKey() throws InvalidProtocolBufferException {
		final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder().setSpender(spender1).setAmount(
				0L).build();
		final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
				0L).setTokenId(token1).build();

		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(List.of(cryptoAllowance1))
								.addAllTokenAllowances(List.of(tokenAllowance1))
				).build();
		setAccessor();
		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(ownerId)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender1)).willReturn(spenderNum);
		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(ownerAcccount);

		subject.doStateTransition();

		assertEquals(0, ownerAcccount.getCryptoAllowances().size());
		assertEquals(0, ownerAcccount.getFungibleTokenAllowances().size());
	}

	@Test
	void wipesSerialsWhenApprovedForAll() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		addExistingAllowances();

		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(ownerId)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender1)).willReturn(spenderNum);
		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		subject.doStateTransition();

		assertEquals(2, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(2, ownerAcccount.getNftAllowances().size());
		assertEquals(0, ownerAcccount.getNftAllowances()
				.get(FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
						EntityNum.fromAccountId(spender1))).getSerialNumbers().size());
		verify(accountStore).commitAccount(ownerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void adjustsSerialsCorrectly() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		addExistingAllowances();

		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(ownerId)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender1)).willReturn(spenderNum);
		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		subject.doStateTransition();
		List<Long> expectedSerials = new ArrayList<>();
		expectedSerials.add(1L);
		expectedSerials.add(20L);

		assertEquals(2, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(2, ownerAcccount.getNftAllowances().size());
		assertEquals(expectedSerials, ownerAcccount.getNftAllowances()
				.get(FcTokenAllowanceId.from(EntityNum.fromTokenId(token1),
						EntityNum.fromAccountId(spender1))).getSerialNumbers());
		verify(accountStore).commitAccount(ownerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void checksIfAllowancesExceedLimit() throws InvalidProtocolBufferException {
		addExistingAllowances();
		Account owner = mock(Account.class);
		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(owner);
		given(owner.getTotalAllowances()).willReturn(101);

		givenValidTxnCtx();

		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(ownerId)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender1)).willReturn(spenderNum);

		var exception = assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());
		assertEquals(MAX_ALLOWANCES_EXCEEDED, exception.getResponseCode());
		assertEquals(0, ownerAcccount.getCryptoAllowances().size());
		assertEquals(0, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAcccount.getNftAllowances().size());
		verify(accountStore, never()).commitAccount(ownerAcccount);
	}

	@Test
	void semanticCheckDelegatesWorks() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		given(aliasManager.unaliased(ownerId)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender1)).willReturn(spenderNum);
		given(adjustAllowanceChecks.allowancesValidation(accessor.getCryptoAllowances(), accessor.getTokenAllowances(),
				accessor.getNftAllowances(), ownerAcccount,
				dynamicProperties.maxAllowanceLimitPerTransaction())).willReturn(OK);
		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(ownerAcccount);
		assertEquals(OK, subject.validateSemantics(accessor));
	}

	@Test
	void emptyAllowancesInStateTransitionWorks() throws InvalidProtocolBufferException {
		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
				).build();
		setAccessor();
		given(aliasManager.unaliased(ownerId)).willReturn(ownerNum);
		given(txnCtx.accessor()).willReturn(accessor);

		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(ownerAcccount);

		subject.doStateTransition();
		assertEquals(0, ownerAcccount.getCryptoAllowances().size());
		assertEquals(0, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAcccount.getNftAllowances().size());
		verify(accountStore, never()).commitAccount(ownerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}


	@Test
	void removesAllowancesWhenAmountIsZero() throws InvalidProtocolBufferException {
		givenTxnCtxWithZeroAmount();
		addExistingAllowances();

		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(ownerId)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender1)).willReturn(spenderNum);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(ownerAcccount);

		assertEquals(2, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(2, ownerAcccount.getNftAllowances().size());

		subject.doStateTransition();

		assertEquals(1, ownerAcccount.getCryptoAllowances().size());
		assertEquals(0, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getNftAllowances().size());

		verify(accountStore).commitAccount(ownerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void addsAllowancesWhenKeyDoesntExist() throws InvalidProtocolBufferException {
		var ownerAcccount = new Account(Id.fromGrpcAccount(ownerId));
		setUpOwnerWithSomeKeys(ownerAcccount);

		assertEquals(1, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getNftAllowances().size());

		givenValidTxnCtx();

		given(txnCtx.accessor()).willReturn(accessor);
		given(aliasManager.unaliased(ownerId)).willReturn(ownerNum);
		given(aliasManager.unaliased(spender1)).willReturn(spenderNum);
		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		subject.doStateTransition();

		assertEquals(2, ownerAcccount.getCryptoAllowances().size());
		assertEquals(2, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(2, ownerAcccount.getNftAllowances().size());

		verify(accountStore).commitAccount(ownerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	private void setUpOwnerWithSomeKeys(final Account ownerAcccount) {
		Map<EntityNum, Long> cryptoAllowances = new TreeMap<>();
		Map<FcTokenAllowanceId, Long> tokenAllowances = new TreeMap<>();
		Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances = new TreeMap<>();
		final var id = FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
				EntityNum.fromAccountId(spender2));
		final var Nftid = FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
				EntityNum.fromAccountId(spender1));
		final var val = FcTokenAllowance.from(false, List.of(1L, 100L));
		cryptoAllowances.put(EntityNum.fromAccountId(spender2), 10000L);
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
		List<Long> serials = new ArrayList<>();
		serials.add(-12L);
		serials.add(-10L);

		final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder().setSpender(spender1).setAmount(
				-20L).build();
		final TokenAllowance tokenAllowance = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
				-10L).setTokenId(token1).build();
		final NftAllowance nftAllowance = NftAllowance.newBuilder().setSpender(spender1)
				.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).addAllSerialNumbers(
						serials).build();

		cryptoAllowances.add(cryptoAllowance);
		tokenAllowances.add(tokenAllowance);
		nftAllowances.add(nftAllowance);

		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();
		setAccessor();
		ownerAcccount.setNftAllowances(new HashMap<>());
		ownerAcccount.setCryptoAllowances(new HashMap<>());
		ownerAcccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private void givenValidTxnCtx() throws InvalidProtocolBufferException {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);
		nftAllowances.add(nftAllowance2);

		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();
		setAccessor();
		ownerAcccount.setNftAllowances(new HashMap<>());
		ownerAcccount.setCryptoAllowances(new HashMap<>());
		ownerAcccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private void setAccessor() throws InvalidProtocolBufferException {
		final var txn = new SwirldTransaction(
				Transaction.newBuilder().setBodyBytes(cryptoAdjustAllowanceTxn.toByteString()).build().toByteArray());
		accessor = new CryptoAllowanceAccessor(txn, aliasManager);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(ownerId)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private void addExistingAllowances() {
		List<Long> serials = new ArrayList<>();
		serials.add(10L);
		serials.add(12L);

		existingCryptoAllowances.put(EntityNum.fromAccountId(spender1), 20L);
		existingCryptoAllowances.put(EntityNum.fromAccountId(spender2), 10L);
		existingTokenAllowances.put(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1)), 10L);
		existingNftAllowances.put(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token2), EntityNum.fromAccountId(spender1)),
				FcTokenAllowance.from(false, serials));
		existingNftAllowances.put(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1)),
				FcTokenAllowance.from(true, new ArrayList<>()));
		ownerAcccount.setCryptoAllowances(existingCryptoAllowances);
		ownerAcccount.setFungibleTokenAllowances(existingTokenAllowances);
		ownerAcccount.setNftAllowances(existingNftAllowances);
	}

	private static final AccountID spender1 = asAccount("0.0.123");
	private static final AccountID spender2 = asAccount("0.0.1234");
	private static final TokenID token1 = asToken("0.0.100");
	private static final TokenID token2 = asToken("0.0.200");
	private static final AccountID ownerId = asAccount("0.0.5000");
	private static final Instant consensusTime = Instant.now();
	private final EntityNum spenderNum = EntityNum.fromAccountId(spender1);
	private final EntityNum ownerNum = EntityNum.fromAccountId(ownerId);
	private final Token token1Model = new Token(Id.fromGrpcToken(token1));
	private final Token token2Model = new Token(Id.fromGrpcToken(token2));
	private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).build();
	private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setTokenId(token1).build();
	private final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
			.setSpender(spender1)
			.setTokenId(token2).setApprovedForAll(BoolValue.of(true))
			.addAllSerialNumbers(List.of(-1L, 10L)).build();
	private final NftAllowance nftAllowance2 = NftAllowance.newBuilder()
			.setSpender(spender1)
			.setTokenId(token1).setApprovedForAll(BoolValue.of(false))
			.addAllSerialNumbers(List.of(1L, -10L, 20L)).build();
	private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
	private List<TokenAllowance> tokenAllowances = new ArrayList<>();
	private List<NftAllowance> nftAllowances = new ArrayList<>();
	private final Account ownerAcccount = new Account(Id.fromGrpcAccount(ownerId));

	private final Map<EntityNum, Long> existingCryptoAllowances = new TreeMap<>();
	private final Map<FcTokenAllowanceId, Long> existingTokenAllowances = new TreeMap<>();
	private final Map<FcTokenAllowanceId, FcTokenAllowance> existingNftAllowances = new TreeMap<>();

}
