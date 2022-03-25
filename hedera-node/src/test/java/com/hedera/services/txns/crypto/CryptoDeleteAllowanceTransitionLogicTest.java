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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoWipeAllowance;
import com.hederahashgraph.api.proto.java.NftWipeAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAllowance;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CryptoDeleteAllowanceTransitionLogicTest {
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private AccountStore accountStore;
	@Mock
	private TypedTokenStore tokenStore;
	@Mock
	private DeleteAllowanceChecks deleteAllowanceChecks;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private GlobalDynamicProperties dynamicProperties;

	private TransactionBody cryptoDeleteAllowanceTxn;
	private CryptoDeleteAllowanceTransactionBody op;

	CryptoDeleteAllowanceTransitionLogic subject;


	@BeforeEach
	private void setup() {
		subject = new CryptoDeleteAllowanceTransitionLogic(txnCtx, accountStore, deleteAllowanceChecks, tokenStore);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		assertTrue(subject.applicability().test(cryptoDeleteAllowanceTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void happyPathDeletesAllowances() {
		givenValidTxnCtx();
		addExistingAllowances(ownerAccount);
		given(accessor.getTxn()).willReturn(cryptoDeleteAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
		given(accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(ownerId), INVALID_ALLOWANCE_OWNER_ID)).willReturn(ownerAccount);

		assertEquals(2, ownerAccount.getCryptoAllowances().size());
		assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAccount.getApprovedForAllNftsAllowances().size());

		subject.doStateTransition();

		assertEquals(0, ownerAccount.getCryptoAllowances().size());
		assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAccount.getApprovedForAllNftsAllowances().size());

		verify(tokenStore, times(2)).persistNft(any());
		verify(accountStore).commitAccount(ownerAccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void doesntThrowIfAllowancesDoesNotExist() {
		final CryptoWipeAllowance cryptoAllowance = CryptoWipeAllowance.newBuilder().setOwner(ownerId).build();
		final TokenWipeAllowance tokenAllowance = TokenWipeAllowance.newBuilder().setOwner(ownerId).setTokenId(token1).build();

		cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDeleteAllowance(
						CryptoDeleteAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(List.of(cryptoAllowance))
								.addAllTokenAllowances(List.of(tokenAllowance))
				).build();

		given(accessor.getTxn()).willReturn(cryptoDeleteAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
		given(accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(ownerId), INVALID_ALLOWANCE_OWNER_ID)).willReturn(ownerAccount);

		assertEquals(0, ownerAccount.getCryptoAllowances().size());
		assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());

		subject.doStateTransition();

		verify(tokenStore, never()).persistNft(any());
		verify(accountStore).commitAccount(ownerAccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void semanticCheckDelegatesWorks() {
		givenValidTxnCtx();
		given(deleteAllowanceChecks.deleteAllowancesValidation(op.getCryptoAllowancesList(), op.getTokenAllowancesList(),
				op.getNftAllowancesList(), payerAccount)).willReturn(OK);

		given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);

		assertEquals(OK, subject.semanticCheck().apply(cryptoDeleteAllowanceTxn));
	}

	@Test
	void clearsPayerIfOwnerNotSpecified() {
		givenValidTxnCtxWithNoOwner();
		addExistingAllowances(payerAccount);

		given(accessor.getTxn()).willReturn(cryptoDeleteAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(payerAccount.getId())).willReturn(payerAccount);

		assertEquals(2, payerAccount.getCryptoAllowances().size());
		assertEquals(1, payerAccount.getFungibleTokenAllowances().size());
		assertEquals(1, payerAccount.getApprovedForAllNftsAllowances().size());

		subject.doStateTransition();

		assertEquals(0, payerAccount.getCryptoAllowances().size());
		assertEquals(0, payerAccount.getFungibleTokenAllowances().size());
		assertEquals(1, payerAccount.getApprovedForAllNftsAllowances().size());

		verify(tokenStore, times(2)).persistNft(any());
		verify(accountStore).commitAccount(payerAccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void emptyAllowancesInStateTransitionWorks() {
		cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDeleteAllowance(
						CryptoDeleteAllowanceTransactionBody.newBuilder()
				).build();

		given(accessor.getTxn()).willReturn(cryptoDeleteAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);

		subject.doStateTransition();
		assertEquals(0, ownerAccount.getCryptoAllowances().size());
		assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAccount.getApprovedForAllNftsAllowances().size());
		verify(accountStore, never()).commitAccount(ownerAccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	private void givenValidTxnCtx() {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDeleteAllowance(
						CryptoDeleteAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();
		op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

		ownerAccount.setApproveForAllNfts(new TreeSet<>());
		ownerAccount.setCryptoAllowances(new HashMap<>());
		ownerAccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private void givenValidTxnCtxWithNoOwner() {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		final CryptoWipeAllowance cryptoAllowance = CryptoWipeAllowance.newBuilder().build();
		final TokenWipeAllowance tokenAllowance = TokenWipeAllowance.newBuilder().setTokenId(token1).build();
		final NftWipeAllowance nftAllowance = NftWipeAllowance.newBuilder()
				.setTokenId(token2)
				.addAllSerialNumbers(List.of(12L, 10L)).build();

		cryptoAllowances.add(cryptoAllowance);
		tokenAllowances.add(tokenAllowance);
		nftAllowances.add(nftAllowance);

		cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDeleteAllowance(
						CryptoDeleteAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();
		op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

		ownerAccount.setApproveForAllNfts(new TreeSet<>());
		ownerAccount.setCryptoAllowances(new HashMap<>());
		ownerAccount.setFungibleTokenAllowances(new HashMap<>());

		given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
	}


	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payerId)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private void addExistingAllowances(Account ownerAccount) {
		List<Long> serials = new ArrayList<>();
		serials.add(10L);
		serials.add(12L);

		existingCryptoAllowances.put(EntityNum.fromAccountId(spender1), 20L);
		existingCryptoAllowances.put(EntityNum.fromAccountId(spender2), 10L);
		existingTokenAllowances.put(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1)), 10L);
		existingNftAllowances.add(FcTokenAllowanceId.from(EntityNum.fromTokenId(token2), EntityNum.fromAccountId(spender1)));

		ownerAccount.setCryptoAllowances(existingCryptoAllowances);
		ownerAccount.setFungibleTokenAllowances(existingTokenAllowances);
		ownerAccount.setApproveForAllNfts(existingNftAllowances);

		uniqueToken1.setSpender(Id.fromGrpcAccount(spender1));
		uniqueToken2.setSpender(Id.fromGrpcAccount(spender2));
		given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 10L)).willReturn(uniqueToken1);
		given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 12L)).willReturn(uniqueToken2);
	}

	private static final TokenID token1 = asToken("0.0.100");
	private static final TokenID token2 = asToken("0.0.200");
	private static final AccountID ownerId = asAccount("0.0.5000");
	private static final AccountID payerId = asAccount("0.0.5001");
	private static final Instant consensusTime = Instant.now();
	private final Token token1Model = new Token(Id.fromGrpcToken(token1));
	private final Token token2Model = new Token(Id.fromGrpcToken(token2));
	private final CryptoWipeAllowance cryptoAllowance1 = CryptoWipeAllowance.newBuilder().setOwner(ownerId).build();
	private final TokenWipeAllowance tokenAllowance1 = TokenWipeAllowance.newBuilder().setOwner(ownerId).setTokenId(token1).build();
	private final NftWipeAllowance nftAllowance1 = NftWipeAllowance.newBuilder()
			.setOwner(ownerId)
			.setTokenId(token2)
			.addAllSerialNumbers(List.of(12L, 10L)).build();
	private List<CryptoWipeAllowance> cryptoAllowances = new ArrayList<>();
	private List<TokenWipeAllowance> tokenAllowances = new ArrayList<>();
	private List<NftWipeAllowance> nftAllowances = new ArrayList<>();
	private final Account ownerAccount = new Account(Id.fromGrpcAccount(ownerId));
	private final Account payerAccount = new Account(Id.fromGrpcAccount(payerId));

	private final Map<EntityNum, Long> existingCryptoAllowances = new TreeMap<>();
	private final Map<FcTokenAllowanceId, Long> existingTokenAllowances = new TreeMap<>();
	private final Set<FcTokenAllowanceId> existingNftAllowances = new TreeSet<>();

	private static final AccountID spender1 = asAccount("0.0.123");
	private static final AccountID spender2 = asAccount("0.0.1234");

	private static final UniqueToken uniqueToken1 = new UniqueToken(Id.fromGrpcToken(token2), 12L);
	private static final UniqueToken uniqueToken2 = new UniqueToken(Id.fromGrpcToken(token2), 10L);
}
