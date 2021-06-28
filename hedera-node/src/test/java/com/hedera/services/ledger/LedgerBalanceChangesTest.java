package com.hedera.services.ledger;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.ledger.accounts.HashMapBackingAccounts;
import com.hedera.services.ledger.accounts.HashMapBackingNfts;
import com.hedera.services.ledger.accounts.HashMapBackingTokenRels;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcmap.internal.FCMLeaf;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.services.ledger.BalanceChange.changingNftOwnership;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.hbarChange;
import static com.hedera.test.utils.IdUtils.nftXfer;
import static com.hedera.test.utils.IdUtils.tokenChange;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LedgerBalanceChangesTest {
	private final BackingStore<NftId, MerkleUniqueToken> backingNfts = new HashMapBackingNfts();
	private final BackingStore<AccountID, MerkleAccount> backingAccounts = new HashMapBackingAccounts();
	private final BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> backingRels = new HashMapBackingTokenRels();

	private TokenStore tokenStore;
	private final FCMap<MerkleEntityId, MerkleToken> tokens = new FCMap<>();
	private final FCOneToManyRelation<EntityId, MerkleUniqueTokenId> uniqueOwnershipAssociations = new FCOneToManyRelation<>();
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private TransactionalLedger<
			Pair<AccountID, TokenID>,
			TokenRelProperty,
			MerkleTokenRelStatus> tokenRelsLedger;
	private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;

	@Mock
	private EntityIdSource ids;
	@Mock
	private ExpiringCreations creator;
	@Mock
	private OptionValidator validator;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private AccountRecordsHistorian historian;

	private HederaLedger subject;

	@BeforeEach
	void setUp() throws ConstructableRegistryException {
		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class, MerkleAccount::new, backingAccounts, new ChangeSummaryManager<>());
		tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class, MerkleTokenRelStatus::new, backingRels, new ChangeSummaryManager<>());
		nftsLedger = new TransactionalLedger<>(
				NftProperty.class, MerkleUniqueToken::new, backingNfts, new ChangeSummaryManager<>());
		tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);

		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMLeaf.class, FCMLeaf::new));
		tokens.put(tokenKey, fungibleTokenWithTreasury(aModel));
		tokens.put(anotherTokenKey, fungibleTokenWithTreasury(aModel));
		tokens.put(yetAnotherTokenKey, fungibleTokenWithTreasury(aModel));
		tokens.put(aNftKey, nonFungibleTokenWithTreasury(aModel));
		tokens.put(bNftKey, nonFungibleTokenWithTreasury(bModel));
		tokenStore = new HederaTokenStore(
				ids,
				validator,
				dynamicProperties,
				() -> tokens,
				() -> uniqueOwnershipAssociations,
				tokenRelsLedger,
				nftsLedger);

		subject = new HederaLedger(tokenStore, ids, creator, validator, historian, dynamicProperties, accountsLedger);
		subject.setTokenRelsLedger(tokenRelsLedger);
	}

	@Test
	void rejectsContractInAccountAmounts() {
		givenInitialBalancesAndOwnership();
		backingAccounts.getRef(aModel).setSmartContract(true);

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());

		subject.commit();

		// then:
		assertEquals(INVALID_ACCOUNT_ID, result);
		// and:
		assertInitialBalanceUnchanged();
	}

	@Test
	void rejectsMissingAccount() {
		givenInitialBalancesAndOwnership();
		backingAccounts.remove(aModel);

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());

		subject.commit();

		// then:
		assertEquals(INVALID_ACCOUNT_ID, result);
		// and:
		assertInitialBalanceUnchanged(-1L);
	}

	@Test
	void rejectsDetachedAccount() {
		givenInitialBalancesAndOwnership();
		given(dynamicProperties.autoRenewEnabled()).willReturn(true);

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());

		subject.commit();

		// then:
		assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, result);
		// and:
		assertInitialBalanceUnchanged();
	}

	@Test
	void rejectsDeletedAccount() {
		givenInitialBalancesAndOwnership();
		// and:
		backingAccounts.getRef(bModel).setDeleted(true);

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());
		subject.commit();

		// then:
		assertEquals(ACCOUNT_DELETED, result);
		// and:
		assertInitialBalanceUnchanged();
	}

	@Test
	void rejectsMissingToken() {
		// setup:
		tokens.clear();
		tokens.put(anotherTokenKey.copy(), fungibleTokenWithTreasury(aModel));
		tokens.put(yetAnotherTokenKey.copy(), fungibleTokenWithTreasury(aModel));
		tokenStore = new HederaTokenStore(
				ids,
				validator,
				dynamicProperties,
				() -> tokens,
				() -> uniqueOwnershipAssociations,
				tokenRelsLedger,
				nftsLedger);

		subject = new HederaLedger(tokenStore, ids, creator, validator, historian, dynamicProperties, accountsLedger);
		subject.setTokenRelsLedger(tokenRelsLedger);

		givenInitialBalancesAndOwnership();

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());
		subject.commit();

		// then:
		assertEquals(INVALID_TOKEN_ID, result);
		// and:
		assertInitialBalanceUnchanged();
	}

	@Test
	void happyPathRecordsTransfersAndChangesBalancesAsExpected() {
		givenInitialBalancesAndOwnership();

		// when:
		TransferList inProgress;
		List<TokenTransferList> inProgressTokens;
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());
		inProgress = subject.netTransfersInTxn();
		inProgressTokens = subject.netTokenTransfersInTxn();
		// and:
		subject.commit();

		// then:
		assertEquals(OK, result);
		// and:
		assertEquals(
				aStartBalance + aHbarChange,
				backingAccounts.getImmutableRef(aModel).getBalance());
		assertEquals(
				bStartBalance + bHbarChange,
				backingAccounts.getImmutableRef(bModel).getBalance());
		assertEquals(
				cStartBalance + cHbarChange,
				backingAccounts.getImmutableRef(cModel).getBalance());
		// and:
		assertEquals(
				bTokenStartBalance + bTokenChange,
				backingRels.getImmutableRef(rel(bModel, token)).getBalance());
		assertEquals(
				cTokenStartBalance + cTokenChange,
				backingRels.getImmutableRef(rel(cModel, token)).getBalance());
		// and:
		assertEquals(
				aAnotherTokenStartBalance + aAnotherTokenChange,
				backingRels.getImmutableRef(rel(aModel, anotherToken)).getBalance());
		assertEquals(
				bAnotherTokenStartBalance + bAnotherTokenChange,
				backingRels.getImmutableRef(rel(bModel, anotherToken)).getBalance());
		assertEquals(
				cAnotherTokenStartBalance + cAnotherTokenChange,
				backingRels.getImmutableRef(rel(cModel, anotherToken)).getBalance());
		// and:
		assertEquals(
				aYetAnotherTokenBalance + aYetAnotherTokenChange,
				backingRels.getImmutableRef(rel(aModel, yetAnotherToken)).getBalance());
		assertEquals(
				bYetAnotherTokenBalance + bYetAnotherTokenChange,
				backingRels.getImmutableRef(rel(bModel, yetAnotherToken)).getBalance());
		// and:
		assertEquals(expectedXfers(), inProgress);
		assertEquals(expectedTokenXfers(), inProgressTokens);
	}

	@Test
	void understandsTreasuries() {
		givenInitialBalancesAndOwnership();

		// expect:
		Assertions.assertTrue(subject.isKnownTreasury(aModel));
		Assertions.assertFalse(subject.isKnownTreasury(cModel));
	}

	private TransferList expectedXfers() {
		return TransferList.newBuilder()
				.addAccountAmounts(aaBuilderWith(aModel, aHbarChange))
				.addAccountAmounts(aaBuilderWith(bModel, bHbarChange))
				.addAccountAmounts(aaBuilderWith(cModel, cHbarChange))
				.build();
	}

	private List<TokenTransferList> expectedTokenXfers() {
		return List.of(
				TokenTransferList.newBuilder()
						.setToken(asGprcToken(aNft))
						.addNftTransfers(nftXfer(aModel, bModel, aSerialNo))
						.build(),
				TokenTransferList.newBuilder()
						.setToken(asGprcToken(bNft))
						.addNftTransfers(nftXfer(bModel, cModel, aSerialNo))
						.addNftTransfers(nftXfer(cModel, aModel, bSerialNo))
						.build(),
				TokenTransferList.newBuilder()
						.setToken(token.asGrpcToken())
						.addTransfers(aaBuilderWith(bModel, bTokenChange))
						.addTransfers(aaBuilderWith(cModel, cTokenChange))
						.build(),
				TokenTransferList.newBuilder()
						.setToken(anotherToken.asGrpcToken())
						.addTransfers(aaBuilderWith(aModel, aAnotherTokenChange))
						.addTransfers(aaBuilderWith(bModel, bAnotherTokenChange))
						.addTransfers(aaBuilderWith(cModel, cAnotherTokenChange))
						.build(),
				TokenTransferList.newBuilder()
						.setToken(yetAnotherToken.asGrpcToken())
						.addTransfers(aaBuilderWith(aModel, aYetAnotherTokenChange))
						.addTransfers(aaBuilderWith(bModel, bYetAnotherTokenChange))
						.build()
		);
	}

	private AccountAmount.Builder aaBuilderWith(AccountID account, long amount) {
		return AccountAmount.newBuilder().setAccountID(account).setAmount(amount);
	}

	private void assertInitialBalanceUnchanged() {
		assertInitialBalanceUnchanged(aStartBalance, bTokenStartBalance);
	}

	private void assertInitialBalanceUnchanged(long modifiedABalance) {
		assertInitialBalanceUnchanged(modifiedABalance, bTokenStartBalance);
	}

	private void assertInitialBalanceUnchanged(long modifiedABalance, long modifiedBTokenBalance) {
		if (modifiedABalance >= 0L) {
			assertEquals(
					modifiedABalance,
					backingAccounts.getImmutableRef(aModel).getBalance());
		}
		assertEquals(
				bStartBalance,
				backingAccounts.getImmutableRef(bModel).getBalance());
		assertEquals(
				cStartBalance,
				backingAccounts.getImmutableRef(cModel).getBalance());
		// and:
		assertEquals(
				modifiedBTokenBalance,
				backingRels.getImmutableRef(rel(bModel, token)).getBalance());
		assertEquals(
				cTokenStartBalance,
				backingRels.getImmutableRef(rel(cModel, token)).getBalance());
		// and:
		assertEquals(
				aAnotherTokenStartBalance,
				backingRels.getImmutableRef(rel(aModel, anotherToken)).getBalance());
		assertEquals(
				bAnotherTokenStartBalance,
				backingRels.getImmutableRef(rel(bModel, anotherToken)).getBalance());
		assertEquals(
				cAnotherTokenStartBalance,
				backingRels.getImmutableRef(rel(cModel, anotherToken)).getBalance());
		// and:
		assertEquals(
				aYetAnotherTokenBalance,
				backingRels.getImmutableRef(rel(aModel, yetAnotherToken)).getBalance());
		assertEquals(
				bYetAnotherTokenBalance,
				backingRels.getImmutableRef(rel(bModel, yetAnotherToken)).getBalance());
	}

	private void givenInitialBalancesAndOwnership() {
		final var aAccount = MerkleAccountFactory.newAccount().balance(aStartBalance).get();
		backingAccounts.put(aModel, aAccount);
		final var bAccount = MerkleAccountFactory.newAccount().balance(bStartBalance).get();
		backingAccounts.put(bModel, bAccount);
		final var cAccount = MerkleAccountFactory.newAccount().balance(cStartBalance).get();
		backingAccounts.put(cModel, cAccount);

		Pair<AccountID, TokenID> bTokenKey = rel(bModel, token);
		final var bTokenRel = new MerkleTokenRelStatus(bTokenStartBalance, false, true);
		backingRels.put(bTokenKey, bTokenRel);
		Pair<AccountID, TokenID> cTokenKey = rel(cModel, token);
		final var cTokenRel = new MerkleTokenRelStatus(cTokenStartBalance, false, true);
		backingRels.put(cTokenKey, cTokenRel);
		Pair<AccountID, TokenID> aAnotherTokenKey = rel(aModel, anotherToken);
		final var aAnotherTokenRel = new MerkleTokenRelStatus(aAnotherTokenStartBalance, false, true);
		backingRels.put(aAnotherTokenKey, aAnotherTokenRel);
		Pair<AccountID, TokenID> bAnotherTokenKey = rel(bModel, anotherToken);
		final var bAnotherTokenRel = new MerkleTokenRelStatus(bAnotherTokenStartBalance, false, true);
		backingRels.put(bAnotherTokenKey, bAnotherTokenRel);
		Pair<AccountID, TokenID> cAnotherTokenKey = rel(cModel, anotherToken);
		final var cAnotherTokenRel = new MerkleTokenRelStatus(cAnotherTokenStartBalance, false, true);
		backingRels.put(cAnotherTokenKey, cAnotherTokenRel);
		Pair<AccountID, TokenID> aYaTokenKey = rel(aModel, yetAnotherToken);
		final var aYaTokenRel = new MerkleTokenRelStatus(aYetAnotherTokenBalance, false, true);
		backingRels.put(aYaTokenKey, aYaTokenRel);
		Pair<AccountID, TokenID> bYaTokenKey = rel(bModel, yetAnotherToken);
		final var bYaTokenRel = new MerkleTokenRelStatus(bYetAnotherTokenBalance, false, true);
		backingRels.put(bYaTokenKey, bYaTokenRel);

		Pair<AccountID, TokenID> aaNftTokenKey = rel(aModel, aNft);
		final var aaNftTokenRel = new MerkleTokenRelStatus(2, false, true);
		backingRels.put(aaNftTokenKey, aaNftTokenRel);
		Pair<AccountID, TokenID> abNftTokenKey = rel(aModel, bNft);
		final var abNftTokenRel = new MerkleTokenRelStatus(2, false, true);
		backingRels.put(abNftTokenKey, abNftTokenRel);
		Pair<AccountID, TokenID> baNftTokenKey = rel(bModel, aNft);
		final var baNftTokenRel = new MerkleTokenRelStatus(2, false, true);
		backingRels.put(baNftTokenKey, baNftTokenRel);
		Pair<AccountID, TokenID> bbNftTokenKey = rel(bModel, bNft);
		final var bbNftTokenRel = new MerkleTokenRelStatus(2, false, true);
		backingRels.put(bbNftTokenKey, bbNftTokenRel);
		Pair<AccountID, TokenID> caNftTokenKey = rel(cModel, aNft);
		final var caNftTokenRel = new MerkleTokenRelStatus(2, false, true);
		backingRels.put(caNftTokenKey, caNftTokenRel);
		Pair<AccountID, TokenID> cbNftTokenKey = rel(cModel, bNft);
		final var cbNftTokenRel = new MerkleTokenRelStatus(2, false, true);
		backingRels.put(cbNftTokenKey, cbNftTokenRel);

		backingNfts.put(
				aaNft,
				new MerkleUniqueToken(EntityId.fromGrpcAccountId(aModel), "aa".getBytes(), MISSING_INSTANT));
		backingNfts.put(
				baNft,
				new MerkleUniqueToken(EntityId.fromGrpcAccountId(bModel), "ba".getBytes(), MISSING_INSTANT));
		backingNfts.put(
				bbNft,
				new MerkleUniqueToken(EntityId.fromGrpcAccountId(cModel), "bb".getBytes(), MISSING_INSTANT));
	}

	private List<BalanceChange> fixtureChanges() {
		return List.of(
				tokenChange(yetAnotherToken, aModel, aYetAnotherTokenChange),
				hbarChange(aModel, aHbarChange),
				hbarChange(bModel, bHbarChange),
				tokenChange(anotherToken, aModel, aAnotherTokenChange),
				tokenChange(anotherToken, cModel, cAnotherTokenChange),
				hbarChange(cModel, cHbarChange),
				tokenChange(token, bModel, bTokenChange),
				tokenChange(token, cModel, cTokenChange),
				tokenChange(anotherToken, bModel, bAnotherTokenChange),
				tokenChange(yetAnotherToken, bModel, bYetAnotherTokenChange),
				changingNftOwnership(aNft, aNft.asGrpcToken(), nftXfer(aModel, bModel, aSerialNo)),
				changingNftOwnership(bNft, bNft.asGrpcToken(), nftXfer(bModel, cModel, aSerialNo)),
				changingNftOwnership(bNft, bNft.asGrpcToken(), nftXfer(cModel, aModel, bSerialNo)));
	}

	private Pair<AccountID, TokenID> rel(AccountID account, Id token) {
		return Pair.of(account, token.asGrpcToken());
	}

	private TokenID asGprcToken(Id id) {
		return TokenID.newBuilder()
				.setShardNum(id.getShard())
				.setRealmNum(id.getRealm())
				.setTokenNum(id.getNum())
				.build();
	}

	private MerkleToken fungibleTokenWithTreasury(AccountID treasury) {
		final var token = new MerkleToken();
		token.setTreasury(new EntityId(treasury.getShardNum(), treasury.getRealmNum(), treasury.getAccountNum()));
		token.setTokenType(TokenType.FUNGIBLE_COMMON);
		return token;
	}

	private MerkleToken nonFungibleTokenWithTreasury(AccountID treasury) {
		final var token = new MerkleToken();
		token.setTreasury(new EntityId(treasury.getShardNum(), treasury.getRealmNum(), treasury.getAccountNum()));
		token.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
		return token;
	}

	private final long aSerialNo = 1_234L;
	private final long bSerialNo = 2_234L;
	private final AccountID aModel = asAccount("1.2.3");
	private final AccountID bModel = asAccount("2.3.4");
	private final AccountID cModel = asAccount("3.4.5");
	private final Id token = new Id(0, 0, 75231);
	private final Id anotherToken = new Id(0, 0, 75232);
	private final Id yetAnotherToken = new Id(0, 0, 75233);
	private final Id aNft = new Id(0, 0, 9999);
	private final Id bNft = new Id(0, 0, 10000);
	private final NftId aaNft = new NftId(aNft.getShard(), aNft.getRealm(), aNft.getNum(), aSerialNo);
	private final NftId baNft = new NftId(bNft.getShard(), bNft.getRealm(), bNft.getNum(), aSerialNo);
	private final NftId bbNft = new NftId(bNft.getShard(), bNft.getRealm(), bNft.getNum(), bSerialNo);
	private final MerkleEntityId aNftKey = new MerkleEntityId(0, 0, 9999);
	private final MerkleEntityId bNftKey = new MerkleEntityId(0, 0, 10000);
	private final MerkleEntityId tokenKey = new MerkleEntityId(0, 0, 75231);
	private final MerkleEntityId anotherTokenKey = new MerkleEntityId(0, 0, 75232);
	private final MerkleEntityId yetAnotherTokenKey = new MerkleEntityId(0, 0, 75233);

	private final long aStartBalance = 1_000L;
	private final long bStartBalance = 0L;
	private final long cStartBalance = 3_000L;
	private final long bTokenStartBalance = 123;
	private final long cTokenStartBalance = 234;
	private final long aAnotherTokenStartBalance = 345;
	private final long bAnotherTokenStartBalance = 456;
	private final long cAnotherTokenStartBalance = 567;
	private final long aYetAnotherTokenBalance = 678;
	private final long bYetAnotherTokenBalance = 789;
	private final long aHbarChange = -100L;
	private final long bHbarChange = +50L;
	private final long cHbarChange = +50L;
	private final long aAnotherTokenChange = -50L;
	private final long bAnotherTokenChange = +25L;
	private final long cAnotherTokenChange = +25L;
	private final long bTokenChange = -100L;
	private final long cTokenChange = +100L;
	private final long aYetAnotherTokenChange = -15L;
	private final long bYetAnotherTokenChange = +15L;
}
