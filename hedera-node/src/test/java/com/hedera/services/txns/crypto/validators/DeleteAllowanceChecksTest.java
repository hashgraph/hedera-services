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
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
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
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRemoveAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DeleteAllowanceChecksTest {
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
	private OptionValidator validator;
			

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

	private final TokenRemoveAllowance tokenAllowance1 = TokenRemoveAllowance.newBuilder().setTokenId(token1).setOwner(ownerId).build();
	private final TokenRemoveAllowance tokenAllowance2 = TokenRemoveAllowance.newBuilder().setTokenId(token1).setOwner(payerId).build();

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

		final var validity = subject.deleteAllowancesValidation(cryptoAllowances, tokenAllowances, nftAllowances, payer, view);

		assertEquals(REPEATED_ALLOWANCES_TO_DELETE, validity);
	}

	@Test
	void failsWhenNotSupported(){
		given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
		assertEquals(NOT_SUPPORTED, subject.deleteAllowancesValidation(cryptoAllowances, tokenAllowances, nftAllowances, payer,
				view));
	}


	@Test
	void validateIfSerialsEmptyWithoutApproval() {
		final List<Long> serials = List.of();
		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
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
		assertEquals(EMPTY_ALLOWANCES, subject.commonDeleteChecks(op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(), op.getNftAllowancesList()));
	}

	@Test
	void validatesCryptoAllowancesRepeated(){
		cryptoAllowances.add(cryptoAllowance2);
		cryptoAllowances.add(CryptoRemoveAllowance.newBuilder().setOwner(ownerId).build());

		assertEquals(3, cryptoAllowances.size());
		assertEquals(REPEATED_ALLOWANCES_TO_DELETE, subject.validateCryptoDeleteAllowances(cryptoAllowances, payer,
				accountStore));
	}

	@Test
	void validatesFungibleAllowancesRepeated(){
		tokenAllowances.add(tokenAllowance2);
		tokenAllowances.add(TokenRemoveAllowance.newBuilder().setTokenId(token1).setOwner(ownerId).build());
		tokenAllowances.add(TokenRemoveAllowance.newBuilder().setTokenId(token1).setOwner(payerId).build());
		tokenAllowances.add(TokenRemoveAllowance.newBuilder().setTokenId(token2).setOwner(payerId).build());

		assertEquals(5, tokenAllowances.size());
		assertEquals(REPEATED_ALLOWANCES_TO_DELETE, subject.validateTokenDeleteAllowances(tokenAllowances, payer, tokenStore, accountStore));
	}

	@Test
	void validatesNftAllowancesRepeated(){
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willReturn(owner);
		given(tokenStore.hasAssociation(token2Model, owner)).willReturn(true);

		nftAllowances.add(nftAllowance2);
		nftAllowances.add(NftRemoveAllowance.newBuilder().setOwner(ownerId)
				.setTokenId(token1).addAllSerialNumbers(List.of(1L, 10L)).build());
		nftAllowances.add(NftRemoveAllowance.newBuilder().setOwner(ownerId)
				.setTokenId(token1).addAllSerialNumbers(List.of(30L)).build());

		assertEquals(4, nftAllowances.size());
		assertNotEquals(REPEATED_ALLOWANCES_TO_DELETE, subject.validateNftDeleteAllowances(nftAllowances, payer, tokenStore, accountStore));
		nftAllowances.add(NftRemoveAllowance.newBuilder().setOwner(ownerId)
				.setTokenId(token1).addAllSerialNumbers(List.of(1L)).build());
		assertEquals(REPEATED_ALLOWANCES_TO_DELETE, subject.validateNftDeleteAllowances(nftAllowances, payer, tokenStore, accountStore));
	}

	@Test
	void validatesIfOwnerExists(){
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token1))).willReturn(token1Model);
		cryptoAllowances.add(cryptoAllowance2);
		tokenAllowances.add(tokenAllowance2);
		nftAllowances.add(nftAllowance2);
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willThrow(InvalidTransactionException.class);
		assertEquals(INVALID_ALLOWANCE_OWNER_ID, subject.validateCryptoDeleteAllowances(cryptoAllowances, payer,
				accountStore));
		assertEquals(INVALID_ALLOWANCE_OWNER_ID, subject.validateTokenDeleteAllowances(tokenAllowances, payer, tokenStore, accountStore));
		assertEquals(INVALID_ALLOWANCE_OWNER_ID, subject.validateNftDeleteAllowances(nftAllowances, payer, tokenStore, accountStore));
	}

	@Test
	void considersPayerIfOwnerMissing(){
		final var allowance = CryptoRemoveAllowance.newBuilder().build();
		cryptoAllowances.add(allowance);
		assertEquals(Pair.of(payer, OK), subject.fetchOwnerAccount(Id.fromGrpcAccount(allowance.getOwner()), payer, accountStore));
	}

	@Test
	void failsIfTokenNotAssociatedToAccount(){
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token1))).willReturn(token1Model);
		tokenAllowances.add(tokenAllowance2);
		nftAllowances.add(nftAllowance2);
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willReturn(owner);
		given(tokenStore.hasAssociation(token2Model, owner)).willReturn(false);
		given(tokenStore.hasAssociation(token1Model, owner)).willReturn(false);
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, subject.validateTokenDeleteAllowances(tokenAllowances, payer, tokenStore, accountStore));
		assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, subject.validateNftDeleteAllowances(nftAllowances, payer, tokenStore, accountStore));
	}

	@Test
	void failsIfInvalidTypes(){
		tokenAllowances.clear();
		nftAllowances.clear();

		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
		given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token1))).willReturn(token1Model);
		tokenAllowances.add(TokenRemoveAllowance.newBuilder().setTokenId(token2).build());
		nftAllowances.add(NftRemoveAllowance.newBuilder().setTokenId(token1).build());
		assertEquals(NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES, subject.validateTokenDeleteAllowances(tokenAllowances, payer, tokenStore, accountStore));
		assertEquals(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES, subject.validateNftDeleteAllowances(nftAllowances, payer, tokenStore, accountStore));
	}


	@Test
	void returnsValidationOnceFailed() {
		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		given(payer.getId()).willReturn(Id.fromGrpcAccount(payerId));
		given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willReturn(owner);
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);

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
				subject.deleteAllowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer, view));

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
				subject.deleteAllowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer, view));

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
				subject.deleteAllowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer, view));

		cryptoAllowances.clear();
		nftAllowances.clear();
		tokenAllowances.clear();
		cryptoAllowances.add(cryptoAllowance1);
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

		assertEquals(OK, subject.deleteAllowancesValidation(op.getCryptoAllowancesList(),
						op.getTokenAllowancesList(), op.getNftAllowancesList(), payer, view));
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
				cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance().getTokenAllowancesList(), payer, tokenStore, accountStore));
		assertEquals(OK, subject.validateNftDeleteAllowances(
				cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance().getNftAllowancesList(), payer, tokenStore, accountStore));
	}


	@Test
	void happyPath() {
		setUpForTest();
		getValidTxnCtx();

		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft2))).willReturn(true);
		given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
		given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
		assertEquals(OK, subject.deleteAllowancesValidation(op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(), op.getNftAllowancesList(), payer, view));
	}


	@Test
	void validateSerialsExistence() {
		final var serials = List.of(1L, 10L);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(false);

		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
	}

	@Test
	void deletesAllowanceFromTreasury(){
		final var serials = List.of(1L);
		token2Model.setTreasury(treasury);
		given(nftsMap.get(EntityNumPair.fromNftId(token2Nft1))).willReturn(token);

		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(true);
		given(token.getOwner()).willReturn(EntityId.MISSING_ENTITY_ID);

		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(OK, validity);
	}

	@Test
	void doesNodeDeleteAllowanceFromInvalidTreasury(){
		final var serials = List.of(1L);
		given(nftsMap.get(EntityNumPair.fromNftId(token2Nft1))).willReturn(token);
		token2Model.setTreasury(treasury);

		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.get(EntityNumPair.fromNftId(token2Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(spender1));
		given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId));

		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, validity);
	}

	@Test
	void returnsIfSerialsFail() {
		final var serials = List.of(1L, 10L);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(false);

		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
	}

	@Test
	void addsSerialsCorrectly(){
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
	void validateSerialsOwner() {
		final var serials = List.of(1L, 10L);
		given(nftsMap.get(EntityNumPair.fromNftId(token2Nft1))).willReturn(token);
		given(nftsMap.containsKey(EntityNumPair.fromNftId(token2Nft1))).willReturn(true);
		given(nftsMap.get(EntityNumPair.fromNftId(token2Nft1)).getOwner()).willReturn(
				EntityId.fromGrpcAccountId(spender1));

		given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));

		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, validity);
	}

	@Test
	void validateRepeatedSerials() {
		given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));

		var serials = List.of(1L, 10L, 1L);
		var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
		assertEquals(REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES, validity);

		serials = List.of(10L, 4L);
		validity = subject.validateSerialNums(serials, token2Model, tokenStore);
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
		given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
		given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
		given(tokenStore.hasAssociation(token2Model, payer)).willReturn(true);
		given(tokenStore.hasAssociation(token2Model, payer)).willReturn(true);
		given(tokenStore.hasAssociation(token2Model, payer)).willReturn(true);

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
