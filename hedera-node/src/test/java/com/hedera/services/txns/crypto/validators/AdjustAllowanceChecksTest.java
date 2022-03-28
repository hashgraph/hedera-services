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

import com.google.protobuf.BoolValue;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAdjustAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.buildEntityNumPairFrom;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.buildTokenAllowanceKey;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedId;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSpender;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdjustAllowanceChecksTest {
	@Mock
	private MerkleMap<EntityNumPair, MerkleUniqueToken> nftsMap;
	@Mock
	private AccountStore accountStore;
	@Mock
	private TypedTokenStore tokenStore;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private Account payer;
	@Mock
	private Account owner;
	@Mock
	private MerkleUniqueToken token;
	@Mock
	private Account treasury;
	@Mock
	private StateView view;
	@Mock
	private MerkleToken merkleToken;
	@Mock
	private OptionValidator validator;

	AdjustAllowanceChecks subject;

	private final AccountID spender1 = asAccount("0.0.123");
	private final AccountID spender2 = asAccount("0.0.1234");
	private final TokenID token1 = asToken("0.0.100");
	private final TokenID token2 = asToken("0.0.200");
	private final AccountID payerId = asAccount("0.0.5000");
	private final AccountID ownerId = asAccount("0.0.5001");

	private final Token token1Model = new Token(Id.fromGrpcToken(token1));
	private final Token token2Model = new Token(Id.fromGrpcToken(token2));

	private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setOwner(ownerId).build();
	private final CryptoAllowance cryptoAllowance2 = CryptoAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setOwner(payerId).build();

	private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setTokenId(token1).setOwner(ownerId).build();
	private final TokenAllowance tokenAllowance2 = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setTokenId(token1).setOwner(payerId).build();

	private final NftAllowance nftAllowance1 = NftAllowance.newBuilder().setSpender(spender1).setOwner(ownerId)
			.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).addAllSerialNumbers(List.of(1L, 10L)).build();
	private final NftAllowance nftAllowance2 = NftAllowance.newBuilder().setSpender(spender1).setOwner(ownerId)
			.setTokenId(token2).setApprovedForAll(BoolValue.of(true)).addAllSerialNumbers(List.of(1L, 10L)).build();
	private final NftAllowance nftAllowance3 = NftAllowance.newBuilder().setSpender(spender1).setOwner(payerId)
			.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).addAllSerialNumbers(List.of(1L, 10L)).build();

	final NftId token2Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
	final NftId token2Nft2 = new NftId(0, 0, token2.getTokenNum(), 10L);

	private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
	private List<TokenAllowance> tokenAllowances = new ArrayList<>();
	private List<NftAllowance> nftAllowances = new ArrayList<>();

	private final Map<EntityNum, Long> existingCryptoAllowances = new TreeMap<>();
	private final Map<FcTokenAllowanceId, Long> existingTokenAllowances = new TreeMap<>();
	private final Map<FcTokenAllowanceId, FcTokenAllowance> existingNftAllowances = new TreeMap<>();
	private List<Long> existingSerials = new ArrayList<>();

	private TransactionBody cryptoAdjustAllowanceTxn;
	private CryptoAdjustAllowanceTransactionBody op;

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

		subject = new AdjustAllowanceChecks(dynamicProperties, validator);
	}

	@Test
	void failsIfAllowanceFeatureIsNotTurnedOn() {
		given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);

		cryptoAllowances.add(cryptoAllowance2);
		tokenAllowances.add(tokenAllowance2);
		nftAllowances.add(nftAllowance3);

		final var validity = subject.allowancesValidation(cryptoAllowances, tokenAllowances, nftAllowances, payer,
				dynamicProperties.maxAllowanceLimitPerTransaction(), view);

		assertEquals(NOT_SUPPORTED, validity);
	}

	@Test
	void isEnabledReturnsCorrectly() {
		given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
		assertEquals(false, subject.isEnabled());

		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
		assertEquals(true, subject.isEnabled());
	}

	private void addExistingAllowancesAndSerials() {
		List<Long> serials = new ArrayList<>();
		serials.add(30L);
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

		existingSerials.addAll(serials);
	}

	@Test
	void validatesDuplicateSpenders() {
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
		assertNoRepeated();

		cryptoAllowances.add(cryptoAllowance2);
		tokenAllowances.add(tokenAllowance2);
		nftAllowances.add(nftAllowance3);

		assertNoRepeated();

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);
		given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));

		final var validity = subject.allowancesValidation(cryptoAllowances, tokenAllowances, nftAllowances, payer,
				dynamicProperties.maxAllowanceLimitPerTransaction(), view);

		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES, validity);

		assertRepeated();
	}

	private void assertRepeated() {
		assertTrue(hasRepeatedSpender(cryptoAllowances.stream()
				.map(a -> buildEntityNumPairFrom(a.getOwner(), a.getSpender(),
						EntityNum.fromAccountId(ownerId))).toList()));
		assertTrue(hasRepeatedId(tokenAllowances.stream()
				.map(a -> buildTokenAllowanceKey(a.getOwner(), a.getTokenId(), a.getSpender())).toList()));
		assertTrue(hasRepeatedId(nftAllowances.stream()
				.map(a -> buildTokenAllowanceKey(a.getOwner(), a.getTokenId(), a.getSpender())).toList()));
	}

	private void assertNoRepeated() {
		assertFalse(hasRepeatedSpender(cryptoAllowances.stream()
				.map(a -> buildEntityNumPairFrom(a.getOwner(), a.getSpender(),
						EntityNum.fromAccountId(ownerId))).toList()));
		assertFalse(hasRepeatedId(tokenAllowances.stream()
				.map(a -> buildTokenAllowanceKey(a.getOwner(), a.getTokenId(), a.getSpender())).toList()));
		assertFalse(hasRepeatedId(nftAllowances.stream()
				.map(a -> buildTokenAllowanceKey(a.getOwner(), a.getTokenId(), a.getSpender())).toList()));
	}

	@Test
	void returnsValidationOnceFailed() {
		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);

		given(payer.getId()).willReturn(Id.fromGrpcAccount(payerId));
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willReturn(owner);
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId));
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
		given(owner.isAssociatedWith(token2Model.getId())).willReturn(true);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
		given(owner.getNftAllowances()).willReturn(existingNftAllowances);
		given(owner.isAssociatedWith(token2Model.getId())).willReturn(true);
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);


		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.build()
				)
				.build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer,
						dynamicProperties.maxAllowanceLimitPerTransaction(), view));

		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllTokenAllowances(tokenAllowances)
								.build()
				)
				.build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer,
						dynamicProperties.maxAllowanceLimitPerTransaction(), view));

		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer,
						dynamicProperties.maxAllowanceLimitPerTransaction(), view));

		nftAllowances.clear();
		nftAllowances.add(nftAllowance2);
		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		assertEquals(OK,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer,
						dynamicProperties.maxAllowanceLimitPerTransaction(), view));
	}

	@Test
	void returnsInvalidOwnerIdOnceValidationOnceFailed() {
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);

		given(payer.getId()).willReturn(Id.fromGrpcAccount(payerId));
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willThrow(InvalidTransactionException.class);
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.build()
				)
				.build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		assertEquals(INVALID_ALLOWANCE_OWNER_ID,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer,
						dynamicProperties.maxAllowanceLimitPerTransaction(), view));

		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllTokenAllowances(tokenAllowances)
								.build()
				)
				.build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		assertEquals(INVALID_ALLOWANCE_OWNER_ID,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer,
						dynamicProperties.maxAllowanceLimitPerTransaction(), view));

		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		assertEquals(INVALID_ALLOWANCE_OWNER_ID,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer,
						dynamicProperties.maxAllowanceLimitPerTransaction(), view));

		nftAllowances.clear();
		nftAllowances.add(nftAllowance2);
		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		assertEquals(INVALID_ALLOWANCE_OWNER_ID,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer,
						dynamicProperties.maxAllowanceLimitPerTransaction(), view));
	}


	@Test
	void succeedsWithEmptyLists() {
		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.build()
				)
				.build();
		assertEquals(OK, subject.validateCryptoAllowances(
				cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance().getCryptoAllowancesList(), payer, view));
		assertEquals(OK, subject.validateFungibleTokenAllowances(
				cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance().getTokenAllowancesList(), payer, view));
		assertEquals(OK, subject.validateNftAllowances(
				cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance().getNftAllowancesList(), payer, view));
	}

	@Test
	void failsIfOwnerSameAsSpender() {
		setUpForTest();
		final var badCryptoAllowance = CryptoAllowance.newBuilder().
				setSpender(ownerId).setOwner(ownerId).setAmount(10L).build();
		final var badTokenAllowance = TokenAllowance.newBuilder().
				setSpender(ownerId).setOwner(ownerId).setAmount(20L).setTokenId(token1).build();
		final var badNftAllowance = NftAllowance.newBuilder().setSpender(ownerId)
				.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).setOwner(ownerId).
				addAllSerialNumbers(List.of(1L)).build();
		final var badNftAllowance1 = NftAllowance.newBuilder().setSpender(spender2)
				.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).setOwner(ownerId).
				addAllSerialNumbers(List.of(1L, 1L)).build();
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft2))).willReturn(true);

		cryptoAllowances.add(badCryptoAllowance);
		assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateCryptoAllowances(cryptoAllowances, payer,
				view));

		tokenAllowances.add(badTokenAllowance);
		assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateFungibleTokenAllowances(tokenAllowances, payer,
				view));

		nftAllowances.add(badNftAllowance);
		assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateNftAllowances(nftAllowances, payer, view));

		nftAllowances.clear();
		nftAllowances.add(badNftAllowance1);
		assertEquals(REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES, subject.validateNftAllowances(nftAllowances, payer,
				view));
	}

	@Test
	void validateNegativeAmountsForNoKeys() {
		givenNecessaryStubs();

		final var badCryptoAllowance = CryptoAllowance.newBuilder().setSpender(spender2).setAmount(
				-10L).setOwner(ownerId).build();
		final var badTokenAllowance = TokenAllowance.newBuilder().setSpender(spender2).setAmount(
				-20L).setTokenId(token1).setOwner(ownerId).build();

		cryptoAllowances.add(badCryptoAllowance);
		assertEquals(NEGATIVE_ALLOWANCE_AMOUNT, subject.validateCryptoAllowances(cryptoAllowances, payer, view));

		tokenAllowances.add(badTokenAllowance);
		assertEquals(NEGATIVE_ALLOWANCE_AMOUNT, subject.validateFungibleTokenAllowances(tokenAllowances, payer,
				view));
	}

	@Test
	void validateNegativeAggregatedAmountsForExistingKeys() {
		givenNecessaryStubs();
		given(owner.getCryptoAllowances()).willReturn(existingCryptoAllowances);
		given(owner.getFungibleTokenAllowances()).willReturn(existingTokenAllowances);

		final var badCryptoAllowance = CryptoAllowance.newBuilder().setSpender(spender2).setAmount(
				-30L).setOwner(ownerId).build();
		final var badTokenAllowance = TokenAllowance.newBuilder().setSpender(spender2).setAmount(
				-20L).setTokenId(token1).setOwner(ownerId).build();

		cryptoAllowances.add(badCryptoAllowance);
		assertEquals(NEGATIVE_ALLOWANCE_AMOUNT, subject.validateCryptoAllowances(cryptoAllowances, payer, view));

		tokenAllowances.add(badTokenAllowance);
		assertEquals(NEGATIVE_ALLOWANCE_AMOUNT, subject.validateFungibleTokenAllowances(tokenAllowances, payer,
				view));
	}

	@Test
	void spenderRepeatedInAllowances() {
		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);
		given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));
		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES, subject.validateCryptoAllowances(cryptoAllowances, payer,
				view));
		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES,
				subject.validateFungibleTokenAllowances(tokenAllowances, payer, view));
		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES, subject.validateNftAllowances(nftAllowances, payer,
				view));
	}

	@Test
	void failsWhenExceedsMaxTokenSupply() {
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		tokenAllowances.clear();
		addExistingAllowancesAndSerials();

		given(payer.getId()).willReturn(Id.fromGrpcAccount(payerId));
		given(payer.getFungibleTokenAllowances()).willReturn(existingTokenAllowances);

		final var badTokenAllowance = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
				4991).setTokenId(token1).setOwner(payerId).build();

		tokenAllowances.add(badTokenAllowance);
		assertEquals(AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY, subject.validateFungibleTokenAllowances(tokenAllowances, payer,
				view));
	}

	@Test
	void failsForNftInFungibleTokenAllowances() {
		givenNecessaryStubs();
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
		final var badTokenAllowance = TokenAllowance.newBuilder().setSpender(spender2).setAmount(
				100000L).setTokenId(token2).setOwner(payerId).build();

		tokenAllowances.add(badTokenAllowance);
		assertEquals(NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES, subject.validateFungibleTokenAllowances(tokenAllowances, payer,
				view));
	}

	@Test
	void failsWhenTokenNotAssociatedToAccount() {
		given(payer.getId()).willReturn(Id.fromGrpcAccount(payerId));
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willReturn(owner);
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId));
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(owner.isAssociatedWith(token1Model.getId())).willReturn(false);
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, subject.validateFungibleTokenAllowances(tokenAllowances, payer,
				view));
	}

	@Test
	void happyPath() {
		setUpForTest();
		getValidTxnCtx();
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft2))).willReturn(true);
		assertEquals(OK, subject.allowancesValidation(op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(), op.getNftAllowancesList(), payer,
				dynamicProperties.maxAllowanceLimitPerTransaction(), view));
	}

	@Test
	void fungibleInNFTAllowances() {
		given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(payer.isAssociatedWith(token2Model.getId())).willReturn(true);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft2))).willReturn(true);

		final NftId token1Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
		final NftId tokenNft2 = new NftId(0, 0, token2.getTokenNum(), 10L);
		given(nftsMap.get(EntityNumPair.fromNftId(token1Nft1))).willReturn(token);
		given(nftsMap.get(EntityNumPair.fromNftId(tokenNft2))).willReturn(token);

		given(nftsMap.get(EntityNumPair.fromNftId(token1Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId));
		given(nftsMap.get(EntityNumPair.fromNftId(tokenNft2)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId));

		final var badNftAllowance = NftAllowance.newBuilder().setSpender(spender2)
				.addAllSerialNumbers(List.of(1L)).setTokenId(token1).setOwner(ownerId).setApprovedForAll(
						BoolValue.of(false)).build();

		nftAllowances.add(badNftAllowance);
		assertEquals(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES, subject.validateNftAllowances(nftAllowances, payer, view));
	}

	@Test
	void validateSerialsExistence() {
		final var serials = List.of(1L, 10L);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(false);

		var validity = subject.validateSerialNums(serials, payer, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
	}

	@Test
	void adjustsAllowanceFromTreasury() {
		final var serials = List.of(1L);
		token2Model.setTreasury(treasury);
		given(nftsMap.get(EntityNumPair.fromNftId(token2Nft1))).willReturn(token);

		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(true);
		given(token.getOwner()).willReturn(EntityId.MISSING_ENTITY_ID);

		var validity = subject.validateSerialNums(serials, treasury, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(OK, validity);
	}

	@Test
	void rejectsAllowanceFromInvalidTreasury() {
		final var serials = List.of(1L);
		given(nftsMap.get(EntityNumPair.fromNftId(token2Nft1))).willReturn(token);
		token2Model.setTreasury(treasury);

		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.get(EntityNumPair.fromNftId(token2Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(spender1));
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId));

		var validity = subject.validateSerialNums(serials, owner, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, validity);
	}

	@Test
	void returnsIfSerialsFail() {
		final var serials = List.of(1L, 10L);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(false);

		var validity = subject.validateSerialNums(serials, payer, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
	}

	@Test
	void validatesIfNegativeSerialsNotInExistingList() {
		final var serials = List.of(-100L, 10L);

//		var validity = subject.validateSerialNums(serials, payer, token2Model,
//				existingNftAllowances.get(
//								FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
//										EntityNum.fromAccountId(spender1)))
//						.getSerialNumbers());

		var validity = subject.validateSerialNums(serials, payer, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
	}

	@Test
	void validateSerialsOwner() {
		final var serials = List.of(1L, 10L);
		given(nftsMap.get(EntityNumPair.fromNftId(token2Nft1))).willReturn(token);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.get(EntityNumPair.fromNftId(token2Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(spender1));

		given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));

		var validity = subject.validateSerialNums(serials, payer, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, validity);
	}

	@Test
	void validateRepeatedSerials() {
		given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));

		var serials = List.of(1L, 10L, 1L);
		var validity = subject.validateSerialNums(serials, payer, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES, validity);

		serials = List.of(10L, 4L);
		validity = subject.validateSerialNums(serials, payer, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);

		final NftId token1Nft1 = new NftId(0, 0, token2.getTokenNum(), 20L);
		final NftId tokenNft2 = new NftId(0, 0, token2.getTokenNum(), 4L);
		given(nftsMap.get(EntityNumPair.fromNftId(token1Nft1))).willReturn(token);
		given(nftsMap.get(EntityNumPair.fromNftId(tokenNft2))).willReturn(token);
		given(nftsMap.get(EntityNumPair.fromNftId(token1Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId));
		given(nftsMap.get(EntityNumPair.fromNftId(tokenNft2)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId));
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token1Nft1))).willReturn(true);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(tokenNft2))).willReturn(true);

		serials = List.of(20L, 4L);
		validity = subject.validateSerialNums(serials, payer, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(OK, validity);
	}

	@Test
	void validateRepeatedNegativeSerials() {
		final var serials = List.of(-1L, 10L, 1L);
		var validity = subject.validateSerialNums(serials, payer, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES, validity);
	}

	@Test
	void validateGivingExistingDuplicateSerials() {
		final var serials = List.of(12L, 1L);
		var validity = subject.validateSerialNums(serials, payer, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES, validity);
	}


	@Test
	void validateIfSerialsEmptyWithoutApproval() {
		final List<Long> serials = List.of();
		var validity = subject.validateSerialNums(serials, payer, merkleToken, view,
				Id.fromGrpcToken(token1), Id.fromGrpcAccount(spender1));
		assertEquals(EMPTY_ALLOWANCES, validity);
	}

	@Test
	void semanticCheckForEmptyAllowancesInOp() {
		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
				).build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();
		assertEquals(EMPTY_ALLOWANCES, subject.commonChecks(op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(), op.getNftAllowancesList(),
				dynamicProperties.maxAllowanceLimitPerTransaction()));
	}

	@Test
	void semanticCheckForExceededLimitOfAllowancesInOp() {
		addAllowances();
		getValidTxnCtx();
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

		assertEquals(MAX_ALLOWANCES_EXCEEDED, subject.allowancesValidation(op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(), op.getNftAllowancesList(), payer,
				dynamicProperties.maxAllowanceLimitPerTransaction(), view));
	}

	private void addAllowances() {
		for (int i = 0; i < dynamicProperties.maxAllowanceLimitPerAccount(); i++) {
			cryptoAllowances.add(cryptoAllowance1);
			tokenAllowances.add(tokenAllowance1);
			nftAllowances.add(nftAllowance1);
		}
	}

	private void getValidTxnCtx() {
		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payerId)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}

	private void givenNecessaryStubs() {
		given(payer.getId()).willReturn(Id.fromGrpcAccount(payerId));
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willReturn(owner);
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId));
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(owner.isAssociatedWith(token1Model.getId())).willReturn(true);
		addExistingAllowancesAndSerials();
	}

	private void resetAllowances() {
		tokenAllowances.clear();
		cryptoAllowances.clear();
		nftAllowances.clear();
	}

	private void setUpForTest() {
		given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
		given(payer.isAssociatedWith(token1Model.getId())).willReturn(true);
		given(payer.isAssociatedWith(token2Model.getId())).willReturn(true);

		given(payer.getCryptoAllowances()).willReturn(existingCryptoAllowances);
		given(payer.getFungibleTokenAllowances()).willReturn(existingTokenAllowances);
		given(payer.getNftAllowances()).willReturn(existingNftAllowances);
		given(payer.isAssociatedWith(token2Model.getId())).willReturn(true);

		final NftId token1Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
		final NftId tokenNft2 = new NftId(0, 0, token2.getTokenNum(), 10L);
		given(nftsMap.get(EntityNumPair.fromNftId(token1Nft1))).willReturn(token);
		given(nftsMap.get(EntityNumPair.fromNftId(tokenNft2))).willReturn(token);
		given(nftsMap.get(EntityNumPair.fromNftId(token1Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId));
		given(nftsMap.get(EntityNumPair.fromNftId(tokenNft2)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId));
	}
}
