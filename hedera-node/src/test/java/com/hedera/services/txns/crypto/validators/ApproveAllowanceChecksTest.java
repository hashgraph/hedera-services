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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApproveAllowanceChecksTest {
	@Mock
	private VirtualMap<UniqueTokenKey, UniqueTokenValue> nftsMap;
	@Mock
	private TypedTokenStore tokenStore;
	@Mock
	private AccountStore accountStore;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private Account owner;
	@Mock
	private Account treasury;
	@Mock
	private Account payerAccount;
	@Mock
	private UniqueTokenValue token;

	ApproveAllowanceChecks subject;

	private final AccountID spender1 = asAccount("0.0.123");
	private final AccountID spender2 = asAccount("0.0.1234");
	private final TokenID token1 = asToken("0.0.100");
	private final TokenID token2 = asToken("0.0.200");
	private final AccountID ownerId1 = asAccount("0.0.5000");
	private final AccountID ownerId2 = asAccount("0.0.5001");
	private final AccountID payer = asAccount("0.0.3000");

	private final Token token1Model = new Token(Id.fromGrpcToken(token1));
	private final Token token2Model = new Token(Id.fromGrpcToken(token2));

	private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder()
			.setSpender(spender1).setAmount(10L).setOwner(ownerId1).build();
	private final CryptoAllowance cryptoAllowance2 = CryptoAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setOwner(ownerId2).build();
	private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setTokenId(token1).setOwner(ownerId1).build();
	private final TokenAllowance tokenAllowance2 = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setTokenId(token1).setOwner(ownerId2).build();
	private final NftAllowance nftAllowance1 = NftAllowance.newBuilder().setSpender(spender1).setOwner(ownerId1)
			.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).addAllSerialNumbers(List.of(1L, 10L)).build();
	private final NftAllowance nftAllowance2 = NftAllowance.newBuilder().setSpender(spender1).setOwner(ownerId2)
			.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).addAllSerialNumbers(List.of(1L, 10L)).build();
	final NftId token2Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
	final NftId token2Nft2 = new NftId(0, 0, token2.getTokenNum(), 10L);

	private final List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
	private final List<TokenAllowance> tokenAllowances = new ArrayList<>();
	private final List<NftAllowance> nftAllowances = new ArrayList<>();

	private TransactionBody cryptoApproveAllowanceTxn;
	private CryptoApproveAllowanceTransactionBody op;

	@BeforeEach
	void setUp() {
		token1Model.initSupplyConstraints(TokenSupplyType.FINITE, 5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.initSupplyConstraints(TokenSupplyType.FINITE, 5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		subject = new ApproveAllowanceChecks(() -> nftsMap, tokenStore, dynamicProperties, accountStore);
	}

	private void setUpForTest() {
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
		given(tokenStore.hasAssociation(token2Model, owner)).willReturn(true);
		given(tokenStore.hasAssociation(token1Model, owner)).willReturn(true);

		final NftId token1Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
		final NftId tokenNft2 = new NftId(0, 0, token2.getTokenNum(), 10L);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token1Nft1))).willReturn(token);
		given(nftsMap.get(UniqueTokenKey.fromNftId(tokenNft2))).willReturn(token);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token1Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId1));
		given(nftsMap.get(UniqueTokenKey.fromNftId(tokenNft2)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId1));
	}

	@Test
	void isEnabledReturnsCorrectly() {
		given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
		assertEquals(false, subject.isEnabled());

		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
		assertEquals(true, subject.isEnabled());
	}

	@Test
	void failsIfAllowanceFeatureIsNotTurnedOn() {
		given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
		assertNoRepeated();

		cryptoAllowances.add(cryptoAllowance2);
		tokenAllowances.add(tokenAllowance2);
		nftAllowances.add(nftAllowance2);

		final var validity = subject.allowancesValidation(cryptoAllowances, tokenAllowances, nftAllowances,
				payerAccount, dynamicProperties.maxAllowanceLimitPerTransaction());

		assertEquals(NOT_SUPPORTED, validity);
	}

	@Test
	void validatesDuplicateSpenders() {
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
		assertNoRepeated();

		cryptoAllowances.add(cryptoAllowance2);
		tokenAllowances.add(tokenAllowance2);
		nftAllowances.add(nftAllowance2);

		assertNoRepeated();

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

		final var validity = subject.allowancesValidation(cryptoAllowances, tokenAllowances, nftAllowances, owner,
				dynamicProperties.maxAllowanceLimitPerTransaction());

		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES, validity);

		assertRepeated();
	}

	private void assertRepeated() {
		assertTrue(hasRepeatedSpender(cryptoAllowances.stream()
				.map(a -> buildEntityNumPairFrom(a.getOwner(), a.getSpender(),
						EntityNum.fromAccountId(payer))).toList()));
		assertTrue(hasRepeatedId(tokenAllowances.stream()
				.map(a -> buildTokenAllowanceKey(a.getOwner(), a.getTokenId(), a.getSpender())).toList()));
		assertTrue(hasRepeatedId(nftAllowances.stream()
				.map(a -> buildTokenAllowanceKey(a.getOwner(), a.getTokenId(), a.getSpender())).toList()));
	}

	private void assertNoRepeated() {
		assertFalse(hasRepeatedSpender(cryptoAllowances.stream()
				.map(a -> buildEntityNumPairFrom(a.getOwner(), a.getSpender(),
						EntityNum.fromAccountId(payer))).toList()));
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

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.build()
				)
				.build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

		assertEquals(MAX_ALLOWANCES_EXCEEDED,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), owner,
						dynamicProperties.maxAllowanceLimitPerTransaction()));

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllTokenAllowances(tokenAllowances)
								.build()
				)
				.build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

		assertEquals(MAX_ALLOWANCES_EXCEEDED,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), owner,
						dynamicProperties.maxAllowanceLimitPerTransaction()));

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

		assertEquals(MAX_ALLOWANCES_EXCEEDED,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), owner,
						dynamicProperties.maxAllowanceLimitPerTransaction()));
	}

	@Test
	void succeedsWithEmptyLists() {
		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.build()
				)
				.build();
		assertEquals(OK, subject.validateCryptoAllowances(
				cryptoApproveAllowanceTxn.getCryptoAdjustAllowance().getCryptoAllowancesList(), owner));
		assertEquals(OK, subject.validateFungibleTokenAllowances(
				cryptoApproveAllowanceTxn.getCryptoAdjustAllowance().getTokenAllowancesList(), owner));
		assertEquals(OK, subject.validateNftAllowances(
				cryptoApproveAllowanceTxn.getCryptoAdjustAllowance().getNftAllowancesList(), owner));
	}

	@Test
	void failsIfOwnerSameAsSpender() {
		setUpForTest();

		final var badCryptoAllowance = CryptoAllowance.newBuilder().
				setSpender(ownerId1).setOwner(ownerId1).setAmount(10L).build();
		final var badTokenAllowance = TokenAllowance.newBuilder().
				setSpender(ownerId1).setOwner(ownerId1).setAmount(20L).setTokenId(token1).build();
		final var badNftAllowance = NftAllowance.newBuilder().setSpender(ownerId1)
				.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).setOwner(ownerId1).
				addAllSerialNumbers(List.of(1L)).build();
		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft2))).willReturn(true);

		cryptoAllowances.add(badCryptoAllowance);
		assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateCryptoAllowances(cryptoAllowances, owner));

		tokenAllowances.add(badTokenAllowance);
		assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateFungibleTokenAllowances(tokenAllowances, owner));

		nftAllowances.add(badNftAllowance);
		assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateNftAllowances(nftAllowances, owner));
	}

	@Test
	void validateNegativeAmounts() {
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.hasAssociation(token1Model, owner)).willReturn(true);
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));

		final var badCryptoAllowance = CryptoAllowance.newBuilder().setSpender(spender2).setAmount(
				-10L).setOwner(ownerId1).build();
		final var badTokenAllowance = TokenAllowance.newBuilder().setSpender(spender2).setAmount(
				-20L).setTokenId(token1).setOwner(ownerId1).build();

		cryptoAllowances.add(badCryptoAllowance);
		assertEquals(NEGATIVE_ALLOWANCE_AMOUNT, subject.validateCryptoAllowances(cryptoAllowances, owner));

		tokenAllowances.add(badTokenAllowance);
		assertEquals(NEGATIVE_ALLOWANCE_AMOUNT, subject.validateFungibleTokenAllowances(tokenAllowances, owner));
	}

	@Test
	void spenderRepeatedInAllowances() {
		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES, subject.validateCryptoAllowances(cryptoAllowances, owner));
		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES,
				subject.validateFungibleTokenAllowances(tokenAllowances, owner));
		assertEquals(SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES, subject.validateNftAllowances(nftAllowances, owner));
	}

	@Test
	void failsWhenExceedsMaxTokenSupply() {
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.hasAssociation(token1Model, owner)).willReturn(true);
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
		final var badTokenAllowance = TokenAllowance.newBuilder().setSpender(spender2).setAmount(
				100000L).setTokenId(token1).setOwner(ownerId1).build();

		tokenAllowances.add(badTokenAllowance);
		assertEquals(AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY, subject.validateFungibleTokenAllowances(tokenAllowances, owner));
	}

	@Test
	void failsForNftInFungibleTokenAllowances() {
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.hasAssociation(token1Model, owner)).willReturn(true);
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
		final var badTokenAllowance = TokenAllowance.newBuilder().setSpender(spender2).setAmount(
				100000L).setTokenId(token2).setOwner(ownerId1).build();

		tokenAllowances.add(badTokenAllowance);
		assertEquals(NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES, subject.validateFungibleTokenAllowances(tokenAllowances, owner));
	}

	@Test
	void returnsInvalidOwnerIdOnceValidationOnceFailed() {
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId1))).willThrow(InvalidTransactionException.class);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(120);
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.build()
				)
				.build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
		given(payerAccount.getId()).willReturn(Id.fromGrpcAccount(payer));
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

		assertEquals(INVALID_ALLOWANCE_OWNER_ID,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payerAccount,
						dynamicProperties.maxAllowanceLimitPerTransaction()));

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllTokenAllowances(tokenAllowances)
								.build()
				)
				.build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

		assertEquals(INVALID_ALLOWANCE_OWNER_ID,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payerAccount,
						dynamicProperties.maxAllowanceLimitPerTransaction()));

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

		assertEquals(INVALID_ALLOWANCE_OWNER_ID,
				subject.allowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payerAccount,
						dynamicProperties.maxAllowanceLimitPerTransaction()));
	}

	@Test
	void failsWhenTokenNotAssociatedToAccount() {
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.hasAssociation(token1Model, owner)).willReturn(false);
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, subject.validateFungibleTokenAllowances(tokenAllowances, owner));
	}

	@Test
	void happyPath() {
		setUpForTest();
		getValidTxnCtx();

		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft2))).willReturn(true);
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
		assertEquals(OK, subject.allowancesValidation(op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(), op.getNftAllowancesList(), owner,
				dynamicProperties.maxAllowanceLimitPerTransaction()));
	}

	@Test
	void fungibleInNFTAllowances() {
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.hasAssociation(token2Model, owner)).willReturn(true);
		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft2))).willReturn(true);

		final NftId token1Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
		final NftId tokenNft2 = new NftId(0, 0, token2.getTokenNum(), 10L);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token1Nft1))).willReturn(token);
		given(nftsMap.get(UniqueTokenKey.fromNftId(tokenNft2))).willReturn(token);

		given(nftsMap.get(UniqueTokenKey.fromNftId(token1Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId1));
		given(nftsMap.get(UniqueTokenKey.fromNftId(tokenNft2)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId1));

		final var badNftAllowance = NftAllowance.newBuilder().setSpender(spender2)
				.addAllSerialNumbers(List.of(1L)).setTokenId(token1).setOwner(ownerId1).setApprovedForAll(
						BoolValue.of(false)).build();

		nftAllowances.add(badNftAllowance);
		assertEquals(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES, subject.validateNftAllowances(nftAllowances, owner));
	}

	@Test
	void validateSerialsExistence() {
		final var serials = List.of(1L, 10L);

		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(false);

		var validity = subject.validateSerialNums(serials, owner, token2Model);
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
	}

	@Test
	void approvesAllowanceFromTreasury() {
		final var serials = List.of(1L);
		token2Model.setTreasury(treasury);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(token);

		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(true);
		given(token.getOwner()).willReturn(EntityId.MISSING_ENTITY_ID);

		var validity = subject.validateSerialNums(serials, treasury, token2Model);
		assertEquals(OK, validity);
	}

	@Test
	void rejectsAllowanceFromInvalidTreasury() {
		final var serials = List.of(1L);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(token);

		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token2Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(spender1));
		given(treasury.getId()).willReturn(Id.fromGrpcAccount(ownerId1));

		var validity = subject.validateSerialNums(serials, treasury, token2Model);
		assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, validity);
	}

	@Test
	void validateSerialsOwner() {
		final var serials = List.of(1L, 10L);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(token);

		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token2Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(spender1));
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));

		var validity = subject.validateSerialNums(serials, owner, token2Model);
		assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, validity);
	}

	@Test
	void validateRepeatedSerials() {
		final var serials = List.of(1L, 10L, 1L);
		var validity = subject.validateSerialNums(serials, owner, token2Model);
		assertEquals(REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES, validity);
	}

	@Test
	void validateIfSerialsEmptyWithoutApproval() {
		final List<Long> serials = List.of();
		var validity = subject.validateSerialNums(serials, owner, token2Model);
		assertEquals(EMPTY_ALLOWANCES, validity);
	}

	@Test
	void semanticCheckForEmptyAllowancesInOp() {
		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
				).build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();


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
				op.getTokenAllowancesList(), op.getNftAllowancesList(), owner,
				dynamicProperties.maxAllowanceLimitPerTransaction()));
	}

	@Test
	void loadsOwnerAccountNotDefaultingToPayer() {
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.hasAssociation(token1Model, owner)).willReturn(true);
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId1))).willReturn(owner);

		getValidTxnCtx();

		assertEquals(OK, subject.validateFungibleTokenAllowances(op.getTokenAllowancesList(), payerAccount));
		verify(accountStore).loadAccount(Id.fromGrpcAccount(ownerId1));

		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId1))).willThrow(InvalidTransactionException.class);
		assertEquals(INVALID_ALLOWANCE_OWNER_ID,
				subject.validateFungibleTokenAllowances(op.getTokenAllowancesList(), payerAccount));
		verify(accountStore, times(2)).loadAccount(Id.fromGrpcAccount(ownerId1));
	}

	@Test
	void loadsOwnerAccountInNftNotDefaultingToPayer() {
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
		given(tokenStore.hasAssociation(token2Model, owner)).willReturn(true);

		final NftId token1Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
		final NftId tokenNft2 = new NftId(0, 0, token2.getTokenNum(), 10L);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token1Nft1))).willReturn(token);
		given(nftsMap.get(UniqueTokenKey.fromNftId(tokenNft2))).willReturn(token);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token1Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId1));
		given(nftsMap.get(UniqueTokenKey.fromNftId(tokenNft2)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId1));
		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft2))).willReturn(true);

		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId1))).willReturn(owner);

		getValidTxnCtx();

		assertEquals(OK, subject.validateNftAllowances(op.getNftAllowancesList(), payerAccount));
		verify(accountStore).loadAccount(Id.fromGrpcAccount(ownerId1));

		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId1))).willThrow(InvalidTransactionException.class);
		assertEquals(INVALID_ALLOWANCE_OWNER_ID, subject.validateNftAllowances(op.getNftAllowancesList(),
				payerAccount));
		verify(accountStore, times(2)).loadAccount(Id.fromGrpcAccount(ownerId1));
	}

	@Test
	void missingOwnerDefaultsToPayer() {
		setupNeeded();
		final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder()
				.setSpender(spender1).setAmount(10L).build();
		final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
				10L).setTokenId(token1).build();
		final NftAllowance nftAllowance1 = NftAllowance.newBuilder().setSpender(spender1)
				.setTokenId(token2).setApprovedForAll(BoolValue.of(false)).addAllSerialNumbers(List.of(1L, 10L)).build();

		cryptoAllowances.clear();
		tokenAllowances.clear();
		nftAllowances.clear();
		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder().addAllCryptoAllowances(cryptoAllowances)
				).build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
		assertEquals(OK, subject.validateCryptoAllowances(
				cryptoApproveAllowanceTxn.getCryptoApproveAllowance().getCryptoAllowancesList(),
				payerAccount));
		verify(accountStore, never()).loadAccount(any());

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder().addAllTokenAllowances(tokenAllowances)
				).build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
		assertEquals(OK, subject.validateFungibleTokenAllowances(
				cryptoApproveAllowanceTxn.getCryptoApproveAllowance().getTokenAllowancesList(),
				payerAccount));
		verify(accountStore, never()).loadAccount(any());


		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder().addAllNftAllowances(nftAllowances)
				).build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
		assertEquals(OK, subject.validateNftAllowances(
				cryptoApproveAllowanceTxn.getCryptoApproveAllowance().getNftAllowancesList(), payerAccount));
		verify(accountStore, never()).loadAccount(any());
	}

	private void setupNeeded() {
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);

		final NftId token1Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
		final NftId tokenNft2 = new NftId(0, 0, token2.getTokenNum(), 10L);

		given(nftsMap.get(UniqueTokenKey.fromNftId(token1Nft1))).willReturn(token);
		given(nftsMap.get(UniqueTokenKey.fromNftId(tokenNft2))).willReturn(token);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token1Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId1));
		given(nftsMap.get(UniqueTokenKey.fromNftId(tokenNft2)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(ownerId1));
		given(payerAccount.getId()).willReturn(Id.fromGrpcAccount(payer));
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.hasAssociation(token1Model, payerAccount)).willReturn(true);
		given(tokenStore.hasAssociation(token2Model, payerAccount)).willReturn(true);
		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.containsKey(UniqueTokenKey.fromNftId(token2Nft2))).willReturn(true);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token2Nft1))).willReturn(token);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token2Nft2))).willReturn(token);
		given(nftsMap.get(UniqueTokenKey.fromNftId(token2Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(payer));
		given(nftsMap.get(UniqueTokenKey.fromNftId(token2Nft2)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(payer));
	}

	private void addAllowances() {
		for (int i = 0; i < dynamicProperties.maxAllowanceLimitPerAccount(); i++) {
			cryptoAllowances.add(cryptoAllowance1);
			tokenAllowances.add(tokenAllowance1);
			nftAllowances.add(nftAllowance1);
		}
	}

	private void getValidTxnCtx() {
		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
								.build()
				)
				.build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
				.build();
	}
}
