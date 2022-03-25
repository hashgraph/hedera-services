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
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.crypto.validators.AdjustAllowanceChecks;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAdjustAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
	private TypedTokenStore tokenStore;
	@Mock
	private AdjustAllowanceChecks adjustAllowanceChecks;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private SideEffectsTracker sideEffectsTracker;

	private TransactionBody cryptoAdjustAllowanceTxn;
	private CryptoAdjustAllowanceTransactionBody op;

	CryptoAdjustAllowanceTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new CryptoAdjustAllowanceTransitionLogic(txnCtx, accountStore, tokenStore,
				adjustAllowanceChecks, dynamicProperties, sideEffectsTracker);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		assertTrue(subject.applicability().test(cryptoAdjustAllowanceTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void happyPathAdjustsAllowances() {
		givenValidTxnCtx();
		addExistingAllowances();
		nft2.setSpender(Id.fromGrpcAccount(spender1));
		given(accessor.getTxn()).willReturn(cryptoAdjustAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
		given(tokenStore.loadUniqueToken(tokenId1, serial1)).willReturn(nft1);
		given(tokenStore.loadUniqueToken(tokenId1, serial2)).willReturn(nft2);
		given(tokenStore.loadUniqueToken(tokenId1, serial3)).willReturn(nft3);

		subject.doStateTransition();

		assertEquals(2, ownerAccount.getCryptoAllowances().size());
		assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(2, ownerAccount.getApprovedForAllNftsAllowances().size());
		assertEquals(30L, ownerAccount.getCryptoAllowances().get(EntityNum.fromAccountId(spender1)));
		assertEquals(10L, ownerAccount.getCryptoAllowances().get(EntityNum.fromAccountId(spender2)));
		assertEquals(20, ownerAccount.getFungibleTokenAllowances()
				.get(FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1))));
		assertTrue(ownerAccount.getApprovedForAllNftsAllowances().contains(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token2), EntityNum.fromAccountId(spender1))));
		assertTrue(ownerAccount.getApprovedForAllNftsAllowances().contains(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1))));
		assertEquals(Id.fromGrpcAccount(spender1), nft1.getSpender());
		assertEquals(Id.fromGrpcAccount(spender1), nft2.getSpender());
		assertEquals(Id.fromGrpcAccount(spender1), nft3.getSpender());

		verify(accountStore).commitAccount(ownerAccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void doesntDoAnythingIfAmountZeroForNonExistingKey() {
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

		given(accessor.getTxn()).willReturn(cryptoAdjustAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);

		subject.doStateTransition();

		assertEquals(0, ownerAccount.getCryptoAllowances().size());
		assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
	}

	@Test
	void checksIfAllowancesExceedLimit() {
		addExistingAllowances();
		Account owner = mock(Account.class);
		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(owner);
		given(owner.getTotalAllowances()).willReturn(101);

		givenValidTxnCtx();

		given(accessor.getTxn()).willReturn(cryptoAdjustAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		var exception = assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());
		assertEquals(MAX_ALLOWANCES_EXCEEDED, exception.getResponseCode());
		assertEquals(0, ownerAccount.getCryptoAllowances().size());
		assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAccount.getApprovedForAllNftsAllowances().size());
		verify(accountStore, never()).commitAccount(ownerAccount);
	}

	@Test
	void semanticCheckDelegatesWorks() {
		givenValidTxnCtx();
		given(adjustAllowanceChecks.allowancesValidation(op.getCryptoAllowancesList(), op.getTokenAllowancesList(),
				op.getNftAllowancesList(), ownerAccount,
				dynamicProperties.maxAllowanceLimitPerTransaction())).willReturn(OK);
		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);
		assertEquals(OK, subject.semanticCheck().apply(cryptoAdjustAllowanceTxn));
	}

	@Test
	void emptyAllowancesInStateTransitionWorks() {
		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
				).build();

		given(accessor.getTxn()).willReturn(cryptoAdjustAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);

		subject.doStateTransition();
		assertEquals(0, ownerAccount.getCryptoAllowances().size());
		assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAccount.getApprovedForAllNftsAllowances().size());
		verify(accountStore, never()).commitAccount(ownerAccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}


	@Test
	void removesAllowancesWhenAmountIsZero() {
		givenTxnCtxWithZeroAmount();
		addExistingAllowances();
		nft4.setSpender(Id.fromGrpcAccount(spender2));
		nft5.setSpender(Id.fromGrpcAccount(spender2));
		given(accessor.getTxn()).willReturn(cryptoAdjustAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);
		given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft4);
		given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft5);

		assertEquals(2, ownerAccount.getCryptoAllowances().size());
		assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(2, ownerAccount.getApprovedForAllNftsAllowances().size());

		subject.doStateTransition();

		assertEquals(1, ownerAccount.getCryptoAllowances().size());
		assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAccount.getApprovedForAllNftsAllowances().size());
		assertEquals(Id.fromGrpcAccount(spender1), nft4.getSpender());
		assertEquals(Id.fromGrpcAccount(spender1), nft5.getSpender());

		verify(accountStore).commitAccount(ownerAccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void addsAllowancesWhenKeyDoesntExist() {
		var ownerAcccount = new Account(ownerId);
		setUpOwnerWithSomeKeys(ownerAcccount);

		assertEquals(1, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getApprovedForAllNftsAllowances().size());

		givenValidTxnCtx();

		given(accessor.getTxn()).willReturn(cryptoAdjustAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(ownerAcccount.getId())).willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
		given(tokenStore.loadUniqueToken(tokenId1, serial1)).willReturn(nft1);
		given(tokenStore.loadUniqueToken(tokenId1, serial2)).willReturn(nft2);
		given(tokenStore.loadUniqueToken(tokenId1, serial3)).willReturn(nft3);

		subject.doStateTransition();

		assertEquals(2, ownerAcccount.getCryptoAllowances().size());
		assertEquals(2, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getApprovedForAllNftsAllowances().size());

		verify(accountStore).commitAccount(ownerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	private void setUpOwnerWithSomeKeys(final Account ownerAcccount) {
		Map<EntityNum, Long> cryptoAllowances = new TreeMap<>();
		Map<FcTokenAllowanceId, Long> tokenAllowances = new TreeMap<>();
		Set<FcTokenAllowanceId> approveForAllNftAllowances = new TreeSet<>();
		final var id = FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
				EntityNum.fromAccountId(spender2));
		final var Nftid = FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
				EntityNum.fromAccountId(spender1));
		cryptoAllowances.put(EntityNum.fromAccountId(spender2), 10000L);
		tokenAllowances.put(id, 100000L);
		approveForAllNftAllowances.add(Nftid);
		ownerAcccount.setCryptoAllowances(cryptoAllowances);
		ownerAcccount.setFungibleTokenAllowances(tokenAllowances);
		ownerAcccount.setApproveForAllNfts(approveForAllNftAllowances);
	}

	private void givenTxnCtxWithZeroAmount() {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);
		List<Long> serials = new ArrayList<>();
		serials.add(10L);
		serials.add(1L);

		final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder().setSpender(spender1).setAmount(
				-20L).build();
		final TokenAllowance tokenAllowance = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
				-10L).setTokenId(token1).build();
		final NftAllowance nftAllowance1 = NftAllowance.newBuilder().setSpender(spender1)
				.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).addAllSerialNumbers(
						serials).build();
		final NftAllowance nftAllowance2 = NftAllowance.newBuilder().setSpender(spender1)
				.setTokenId(token1).setApprovedForAll(BoolValue.of(false)).build();

		cryptoAllowances.add(cryptoAllowance);
		tokenAllowances.add(tokenAllowance);
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
		ownerAccount.setApproveForAllNfts(new TreeSet<>());
		ownerAccount.setCryptoAllowances(new HashMap<>());
		ownerAccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private void givenValidTxnCtx() {
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
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		ownerAccount.setApproveForAllNfts(new TreeSet<>());
		ownerAccount.setCryptoAllowances(new HashMap<>());
		ownerAccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(owner)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private void addExistingAllowances() {
		existingCryptoAllowances.put(EntityNum.fromAccountId(spender1), 20L);
		existingCryptoAllowances.put(EntityNum.fromAccountId(spender2), 10L);
		existingTokenAllowances.put(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1)), 10L);
		existingNftAllowances.add(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token2), EntityNum.fromAccountId(spender1)));
		existingNftAllowances.add(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1)));
		ownerAccount.setCryptoAllowances(existingCryptoAllowances);
		ownerAccount.setFungibleTokenAllowances(existingTokenAllowances);
		ownerAccount.setApproveForAllNfts(existingNftAllowances);
	}

	private static final long serial1 = 1L;
	private static final long serial2 = 10L;
	private static final long serial3 = 20L;
	private static final AccountID spender1 = asAccount("0.0.123");
	private static final AccountID spender2 = asAccount("0.0.1234");
	private static final AccountID owner = asAccount("0.0.5000");
	private static final TokenID token1 = asToken("0.0.100");
	private static final TokenID token2 = asToken("0.0.200");
	private static final Id ownerId = Id.fromGrpcAccount(owner);
	private static final Id tokenId1 = Id.fromGrpcToken(token1);
	private static final Id tokenId2 = Id.fromGrpcToken(token2);
	private static final Instant consensusTime = Instant.now();
	private final Account ownerAccount = new Account(ownerId);
	private final Token token1Model = new Token(tokenId1);
	private final Token token2Model = new Token(tokenId2);
	private final UniqueToken nft1 = new UniqueToken(tokenId1, serial1);
	private final UniqueToken nft2 = new UniqueToken(tokenId1, serial2);
	private final UniqueToken nft3 = new UniqueToken(tokenId1, serial3);
	private final UniqueToken nft4 = new UniqueToken(tokenId2, serial1);
	private final UniqueToken nft5 = new UniqueToken(tokenId2, serial2);

	private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).build();
	private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setTokenId(token1).build();
	private final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
			.setSpender(spender1)
			.setTokenId(token2).setApprovedForAll(BoolValue.of(true))
			.addAllSerialNumbers(List.of(1L, 10L)).build();
	private final NftAllowance nftAllowance2 = NftAllowance.newBuilder()
			.setSpender(spender1)
			.setTokenId(token1).setApprovedForAll(BoolValue.of(false))
			.addAllSerialNumbers(List.of(1L, 10L, 20L)).build();
	private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
	private List<TokenAllowance> tokenAllowances = new ArrayList<>();
	private List<NftAllowance> nftAllowances = new ArrayList<>();

	private final Map<EntityNum, Long> existingCryptoAllowances = new TreeMap<>();
	private final Map<FcTokenAllowanceId, Long> existingTokenAllowances = new TreeMap<>();
	private final Set<FcTokenAllowanceId> existingNftAllowances = new TreeSet<>();

}
