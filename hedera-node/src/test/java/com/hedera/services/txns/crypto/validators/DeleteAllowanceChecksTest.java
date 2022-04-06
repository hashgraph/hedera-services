package com.hedera.services.txns.crypto.validators;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.backing.BackingAccounts;
import com.hedera.services.ledger.backing.BackingNfts;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.backing.BackingTokenRels;
import com.hedera.services.ledger.backing.BackingTokens;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.submerkle.TokenAssociationMetadata;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.ReadOnlyTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRemoveAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_ALLOWANCES_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DeleteAllowanceChecksTest {
	@Mock
	private AccountStore accountStore;
	@Mock
	private ReadOnlyTokenStore tokenStore;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private Account payer;
	@Mock
	private Account ownerAccount;
	@Mock
	private StateView view;
	@Mock
	private OptionValidator validator;
	@Mock
	private MerkleToken merkleToken1;
	@Mock
	private MerkleToken merkleToken2;
	@Mock
	private MerkleAccount ownerMerkleAccount;
	@Mock
	private MerkleUniqueToken merkleUniqueToken;
	@Mock
	private UniqueToken uniqueToken;

	DeleteAllowanceChecks subject;

	private final AccountID spender1 = asAccount("0.0.123");
	private final AccountID spender2 = asAccount("0.0.1234");
	private final TokenID token1 = asToken("0.0.100");
	private final TokenID token2 = asToken("0.0.200");
	private final AccountID payerId = asAccount("0.0.5000");
	private final AccountID ownerId = asAccount("0.0.5001");

	private final Token token1Model = new Token(Id.fromGrpcToken(token1));
	private final Token token2Model = new Token(Id.fromGrpcToken(token2));

	private final CryptoRemoveAllowance cryptoAllowance1 = CryptoRemoveAllowance.newBuilder().setOwner(ownerId).build();
	private final CryptoRemoveAllowance cryptoAllowance2 = CryptoRemoveAllowance.newBuilder().setOwner(payerId).build();

	private final TokenRemoveAllowance tokenAllowance1 = TokenRemoveAllowance.newBuilder().setTokenId(token1).setOwner(
			ownerId).build();
	private final TokenRemoveAllowance tokenAllowance2 = TokenRemoveAllowance.newBuilder().setTokenId(token1).setOwner(
			payerId).build();

	private final NftRemoveAllowance nftAllowance1 = NftRemoveAllowance.newBuilder().setOwner(ownerId)
			.setTokenId(token2).addAllSerialNumbers(List.of(1L, 10L)).build();
	private final NftRemoveAllowance nftAllowance2 = NftRemoveAllowance.newBuilder().setOwner(ownerId)
			.setTokenId(token2).addAllSerialNumbers(List.of(20L)).build();
	private final NftRemoveAllowance nftAllowance3 = NftRemoveAllowance.newBuilder().setOwner(payerId)
			.setTokenId(token2).addAllSerialNumbers(List.of(30L)).build();

	private List<CryptoRemoveAllowance> cryptoAllowances = new ArrayList<>();
	private List<TokenRemoveAllowance> tokenAllowances = new ArrayList<>();
	private List<NftRemoveAllowance> nftAllowances = new ArrayList<>();

	private final Map<EntityNum, Long> existingCryptoAllowances = new TreeMap<>();
	private final Map<FcTokenAllowanceId, Long> existingTokenAllowances = new TreeMap<>();
	private final Set<FcTokenAllowanceId> existingApproveForAllNftsAllowances = new TreeSet<>();

	final NftId token2Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
	final NftId token2Nft2 = new NftId(0, 0, token2.getTokenNum(), 10L);

	private TransactionBody cryptoDeleteAllowanceTxn;
	private CryptoDeleteAllowanceTransactionBody op;

	@BeforeEach
	void setUp() {
		resetAllowances();
		token1Model.initSupplyConstraints(TokenSupplyType.FINITE, 5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.initSupplyConstraints(TokenSupplyType.FINITE, 5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		addExistingAllowancesAndSerials();

		subject = new DeleteAllowanceChecks(dynamicProperties, validator);
	}

	private void addExistingAllowancesAndSerials() {
		existingCryptoAllowances.put(EntityNum.fromAccountId(spender1), 20L);
		existingCryptoAllowances.put(EntityNum.fromAccountId(spender2), 10L);
		existingTokenAllowances.put(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1)), 10L);
		existingApproveForAllNftsAllowances.add(
				FcTokenAllowanceId.from(EntityNum.fromTokenId(token1), EntityNum.fromAccountId(spender1)));
	}

	@Test
	void validatesDuplicateAllowances() {
		cryptoAllowances.add(cryptoAllowance2);
		tokenAllowances.add(tokenAllowance2);
		nftAllowances.add(nftAllowance3);

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);

		final var validity = subject.deleteAllowancesValidation(nftAllowances, payer, view);

		assertEquals(REPEATED_ALLOWANCES_TO_DELETE, validity);
	}

	@Test
	void failsWhenNotSupported() {
		given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
		assertEquals(NOT_SUPPORTED,
				subject.deleteAllowancesValidation(nftAllowances, payer, view));
	}

	@Test
	void validateIfSerialsEmpty() {
		final List<Long> serials = List.of();
		var validity = subject.validateDeleteSerialNums(serials, token2Model, tokenStore);
		assertEquals(EMPTY_ALLOWANCES, validity);
	}

	@Test
	void semanticCheckForEmptyAllowancesInOp() {
		cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDeleteAllowance(
						CryptoDeleteAllowanceTransactionBody.newBuilder()
				).build();
		op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
		assertEquals(EMPTY_ALLOWANCES, subject.validateAllowancesCount(op.getNftAllowancesList()));
	}

	@Test
	void validatesCryptoAllowancesRepeated() {
		cryptoAllowances.add(cryptoAllowance2);
		cryptoAllowances.add(CryptoRemoveAllowance.newBuilder().setOwner(ownerId).build());

		assertEquals(3, cryptoAllowances.size());
		assertEquals(REPEATED_ALLOWANCES_TO_DELETE, subject.validateCryptoDeleteAllowances(cryptoAllowances, payer,
				accountStore));
	}

	@Test
	void validatesFungibleAllowancesRepeated() {
		tokenAllowances.add(tokenAllowance2);
		tokenAllowances.add(TokenRemoveAllowance.newBuilder().setTokenId(token1).setOwner(ownerId).build());
		tokenAllowances.add(TokenRemoveAllowance.newBuilder().setTokenId(token1).setOwner(payerId).build());
		tokenAllowances.add(TokenRemoveAllowance.newBuilder().setTokenId(token2).setOwner(payerId).build());

		assertEquals(5, tokenAllowances.size());
		assertEquals(REPEATED_ALLOWANCES_TO_DELETE,
				subject.validateTokenDeleteAllowances(tokenAllowances, payer, tokenStore, accountStore));
	}

	@Test
	void validatesNftAllowancesRepeated() {
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token1))).willReturn(token1Model);
		given(tokenStore.hasAssociation(token2Model, payer)).willReturn(true);
		given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));

		nftAllowances.add(nftAllowance2);
		nftAllowances.add(NftRemoveAllowance.newBuilder().setOwner(ownerId)
				.setTokenId(token1).addAllSerialNumbers(List.of(1L, 10L)).build());
		nftAllowances.add(NftRemoveAllowance.newBuilder().setOwner(ownerId)
				.setTokenId(token1).addAllSerialNumbers(List.of(30L)).build());

		assertEquals(4, nftAllowances.size());
		assertNotEquals(REPEATED_ALLOWANCES_TO_DELETE,
				subject.validateNftDeleteAllowances(nftAllowances, payer, tokenStore, accountStore));
		nftAllowances.add(NftRemoveAllowance.newBuilder().setOwner(ownerId)
				.setTokenId(token1).addAllSerialNumbers(List.of(1L)).build());
		assertEquals(REPEATED_ALLOWANCES_TO_DELETE,
				subject.validateNftDeleteAllowances(nftAllowances, payer, tokenStore, accountStore));
	}

	@Test
	void validatesIfOwnerExists() {
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token1))).willReturn(token1Model);
		cryptoAllowances.add(cryptoAllowance2);
		tokenAllowances.add(tokenAllowance2);
		nftAllowances.add(nftAllowance2);
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willThrow(InvalidTransactionException.class);
		assertEquals(INVALID_ALLOWANCE_OWNER_ID, subject.validateCryptoDeleteAllowances(cryptoAllowances, payer,
				accountStore));
		assertEquals(INVALID_ALLOWANCE_OWNER_ID,
				subject.validateTokenDeleteAllowances(tokenAllowances, payer, tokenStore, accountStore));
		assertEquals(INVALID_ALLOWANCE_OWNER_ID,
				subject.validateNftDeleteAllowances(nftAllowances, payer, tokenStore, accountStore));
	}

	@Test
	void considersPayerIfOwnerMissing() {
		final var allowance = CryptoRemoveAllowance.newBuilder().build();
		cryptoAllowances.add(allowance);
		assertEquals(Pair.of(payer, OK),
				subject.fetchOwnerAccount(Id.fromGrpcAccount(allowance.getOwner()), payer, accountStore));
	}

	@Test
	void failsIfTokenNotAssociatedToAccount() {
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token1))).willReturn(token1Model);
		tokenAllowances.add(tokenAllowance2);
		nftAllowances.add(nftAllowance2);
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willReturn(ownerAccount);
		given(tokenStore.hasAssociation(token2Model, ownerAccount)).willReturn(false);
		given(tokenStore.hasAssociation(token1Model, ownerAccount)).willReturn(false);
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
				subject.validateTokenDeleteAllowances(tokenAllowances, payer, tokenStore, accountStore));
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
				subject.validateNftDeleteAllowances(nftAllowances, payer, tokenStore, accountStore));
	}

	@Test
	void failsIfInvalidTypes() {
		tokenAllowances.clear();
		nftAllowances.clear();

		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token1))).willReturn(token1Model);
		tokenAllowances.add(TokenRemoveAllowance.newBuilder().setTokenId(token2).build());
		nftAllowances.add(NftRemoveAllowance.newBuilder().setTokenId(token1).build());
		assertEquals(NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES,
				subject.validateTokenDeleteAllowances(tokenAllowances, payer, tokenStore, accountStore));
		assertEquals(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES,
				subject.validateNftDeleteAllowances(nftAllowances, payer, tokenStore, accountStore));
	}


	@Test
	void returnsValidationOnceFailed() {
		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);

		final BackingStore<AccountID, MerkleAccount> store = mock(BackingAccounts.class);
		final BackingStore<TokenID, MerkleToken> tokens = mock(BackingTokens.class);
		final BackingStore<NftId, MerkleUniqueToken> nfts = mock(BackingNfts.class);
		final BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> rels = mock(BackingTokenRels.class);
		given(view.asReadOnlyAccountStore()).willReturn(store);
		given(view.asReadOnlyTokenStore()).willReturn(tokens);
		given(view.asReadOnlyNftStore()).willReturn(nfts);
		given(view.asReadOnlyAssociationStore()).willReturn(rels);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

		cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDeleteAllowance(
						CryptoDeleteAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.build()
				)
				.build();
		op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

		assertEquals(REPEATED_ALLOWANCES_TO_DELETE,
				subject.deleteAllowancesValidation(op.getNftAllowancesList(), payer, view));

		cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDeleteAllowance(
						CryptoDeleteAllowanceTransactionBody.newBuilder()
								.addAllTokenAllowances(tokenAllowances)
								.build()
				)
				.build();
		op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

		assertEquals(REPEATED_ALLOWANCES_TO_DELETE,
				subject.deleteAllowancesValidation(op.getNftAllowancesList(), payer, view));

		cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDeleteAllowance(
						CryptoDeleteAllowanceTransactionBody.newBuilder()
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
		op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

		assertEquals(REPEATED_ALLOWANCES_TO_DELETE,
				subject.deleteAllowancesValidation(op.getNftAllowancesList(), payer, view));
	}


	@Test
	void succeedsWithEmptyLists() {
		cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDeleteAllowance(
						CryptoDeleteAllowanceTransactionBody.newBuilder()
								.build()
				)
				.build();
		assertEquals(OK, subject.validateCryptoDeleteAllowances(
				cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance().getCryptoAllowancesList(), payer, accountStore));
		assertEquals(OK, subject.validateTokenDeleteAllowances(
				cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance().getTokenAllowancesList(), payer, tokenStore,
				accountStore));
		assertEquals(OK, subject.validateNftDeleteAllowances(
				cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance().getNftAllowancesList(), payer, tokenStore,
				accountStore));
	}


	@Test
	void happyPath() {
		setUpForTest();
		getValidTxnCtx();

		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
		assertEquals(OK, subject.deleteAllowancesValidation(op.getNftAllowancesList(), payer, view));
	}


	@Test
	void validateSerialsExistence() {
		final var serials = List.of(1L, 10L);
		given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 1L)).willThrow(InvalidTransactionException.class);

		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
	}

	@Test
	void returnsIfSerialsFail() {
		final var serials = List.of(1L, 10L);
		given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 1L)).willThrow(InvalidTransactionException.class);
		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
	}

	@Test
	void addsSerialsCorrectly() {
		nftAllowances.add(nftAllowance1);
		nftAllowances.add(nftAllowance2);
		assertEquals(5, subject.aggregateNftDeleteAllowances(nftAllowances));
	}

	@Test
	void validatesIfNegativeSerialsNotInExistingList() {
		final var serials = List.of(-100L, 10L);

		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
	}

	@Test
	void validatesNegativeSerialsAreNotValid() {
		final var serials = List.of(-100L, 10L);

		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
	}

	@Test
	void validatesAbsoluteZeroSerialIsNotValid() {
		final var serials = List.of(0L);

		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, subject.validateSerialNums(serials, token2Model, tokenStore));
	}

	@Test
	void validateRepeatedSerials() {
		given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 10L)).willReturn(uniqueToken);

		var serials = List.of(1L, 10L, 1L);
		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES, validity);

		serials = List.of(10L, 4L);
		given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 10L)).willThrow(InvalidTransactionException.class);
		validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);

		given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 20L)).willReturn(uniqueToken);
		given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 4L)).willReturn(uniqueToken);

		serials = List.of(20L, 4L);
		validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(OK, validity);
	}

	private void getValidTxnCtx() {
		cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoDeleteAllowance(
						CryptoDeleteAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
		op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payerId)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}

	private void resetAllowances() {
		tokenAllowances.clear();
		cryptoAllowances.clear();
		nftAllowances.clear();
	}

	private void setUpForTest() {
		given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));
		final BackingStore<AccountID, MerkleAccount> store = mock(BackingAccounts.class);
		final BackingStore<TokenID, MerkleToken> tokens = mock(BackingTokens.class);
		final BackingStore<NftId, MerkleUniqueToken> nfts = mock(BackingNfts.class);
		BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> rels = mock(BackingTokenRels.class);
		given(view.asReadOnlyAccountStore()).willReturn(store);
		given(view.asReadOnlyTokenStore()).willReturn(tokens);
		given(view.asReadOnlyNftStore()).willReturn(nfts);
		given(view.asReadOnlyAssociationStore()).willReturn(rels);
		given(ownerMerkleAccount.getTokenAssociationMetadata()).willReturn(
				new TokenAssociationMetadata(1, 0, EntityNumPair.MISSING_NUM_PAIR));

		given(store.getImmutableRef(ownerId)).willReturn(ownerMerkleAccount);
		given(tokens.getImmutableRef(token1)).willReturn(merkleToken1);
		given(tokens.getImmutableRef(token2)).willReturn(merkleToken2);
		given(nfts.getImmutableRef(token2Nft1)).willReturn(merkleUniqueToken);
		given(nfts.getImmutableRef(token2Nft2)).willReturn(merkleUniqueToken);
		given(rels.contains(Pair.of(ownerId, token1))).willReturn(true);
		given(rels.contains(Pair.of(ownerId, token2))).willReturn(true);

		given(merkleToken1.treasury()).willReturn(EntityId.fromGrpcAccountId(ownerId));
		given(merkleToken2.treasury()).willReturn(EntityId.fromGrpcAccountId(ownerId));
		given(ownerMerkleAccount.state()).willReturn(new MerkleAccountState());
		given(merkleUniqueToken.getOwner()).willReturn(EntityId.fromGrpcAccountId(ownerId));
		given(merkleUniqueToken.getSpender()).willReturn(Id.MISSING_ID.asEntityId());

		given(merkleToken1.supplyType()).willReturn(TokenSupplyType.INFINITE);
		given(merkleToken1.tokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
		given(merkleToken2.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
	}
}
