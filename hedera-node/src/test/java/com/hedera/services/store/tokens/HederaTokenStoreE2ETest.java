package com.hedera.services.store.tokens;

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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.backing.BackingAccounts;
import com.hedera.services.ledger.backing.BackingNfts;
import com.hedera.services.ledger.backing.BackingTokenRels;
import com.hedera.services.ledger.backing.BackingTokens;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.utils.ClassLoaderHelper;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.truth.Truth.assertThat;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FEE_SCHEDULE_KT;
import static com.hedera.test.mocks.TestContextValidator.TEST_VALIDATOR;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class HederaTokenStoreE2ETest {
	private static final int MAX_TOKENS_PER_ACCOUNT = 100;
	private static final int MAX_TOKEN_SYMBOL_UTF8_BYTES = 10;
	private static final int MAX_TOKEN_NAME_UTF8_BYTES = 100;
	private static final int MAX_CUSTOM_FEES = 5;
	private static final long FIRST_TOKEN_NUM = 100;
	private static final long FIRST_ACCOUNT_NUM = 1000;

	private VirtualMap<UniqueTokenKey, UniqueTokenValue> nftVm;
	private BackingNfts backingNfts;
	private TransactionalLedger<NftId, NftProperty, UniqueTokenValue> nftsLedger;

	private MerkleMap<EntityNum, MerkleAccount> accountsMm;
	private BackingAccounts backingAccounts;
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	private MerkleMap<EntityNum, MerkleToken> tokensMm;
	private BackingTokens backingTokens;
	private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;

	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRelsMm;
	private BackingTokenRels backingTokenRels;
	private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;

	private HederaTokenStore hederaTokenStore;
	private SideEffectsTracker sideEffectsTracker;

	@BeforeAll
	static void setupRegistry() {
		ClassLoaderHelper.loadClassPathDependencies();
	}

	@BeforeEach
	void setupHederaTokenStore() {

		// Nft store
		nftVm = new VirtualMapFactory(JasperDbBuilder::new).newVirtualizedUniqueTokenStorage();
		backingNfts = new BackingNfts(() -> nftVm);
		nftsLedger = new TransactionalLedger<>(
				NftProperty.class,
				UniqueTokenValue::new,
				backingNfts,
				new ChangeSummaryManager<>());

		// Accounts store
		accountsMm = new MerkleMap<>();
		backingAccounts = new BackingAccounts(() -> accountsMm);
		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				backingAccounts,
				new ChangeSummaryManager<>());

		// Tokens store
		tokensMm = new MerkleMap<>();
		backingTokens = new BackingTokens(() -> tokensMm);
		tokensLedger = new TransactionalLedger<>(
				TokenProperty.class,
				MerkleToken::new,
				backingTokens,
				new ChangeSummaryManager<>());

		// Tokens Relationship Store
		tokenRelsMm = new MerkleMap<>();
		backingTokenRels = new BackingTokenRels(() -> tokenRelsMm);
		tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class,
				MerkleTokenRelStatus::new,
				backingTokenRels,
				new ChangeSummaryManager<>());

		GlobalDynamicProperties properties = mock(GlobalDynamicProperties.class);
		given(properties.maxTokensRelsPerInfoQuery()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(properties.maxTokenSymbolUtf8Bytes()).willReturn(MAX_TOKEN_SYMBOL_UTF8_BYTES);
		given(properties.maxTokenNameUtf8Bytes()).willReturn(MAX_TOKEN_NAME_UTF8_BYTES);
		given(properties.maxCustomFeesAllowed()).willReturn(MAX_CUSTOM_FEES);
		sideEffectsTracker = new SideEffectsTracker();
		hederaTokenStore = new HederaTokenStore(
				new FakeEntityIdSource(),
				TEST_VALIDATOR,
				sideEffectsTracker,
				properties,
				tokenRelsLedger,
				nftsLedger,
				backingTokens
		);
		hederaTokenStore.setAccountsLedger(accountsLedger);
	}

	@Test
	void doOwnershipTransfer() throws DecoderException {
		// Create 10 accounts 0.0.100 - 0.0.109
		final int numNftTypes = 10;
		createNfts(numNftTypes);
		for (int r = 0; r < 100; r++) {
			for (int n = 0; n < numNftTypes; n++) {
				final var oldNftVm = nftVm;
				nftVm = nftVm.copy();
				oldNftVm.release();

				sideEffectsTracker.reset();
				accountsLedger.begin();
				nftsLedger.begin();
				tokenRelsLedger.begin();
				tokensLedger.begin();

				final var tokenId = TokenID.newBuilder()
						.setShardNum(0)
						.setRealmNum(0)
						.setTokenNum(FIRST_TOKEN_NUM + n).build();
				final var nftId = NftId.fromGrpc(tokenId, 1);
				final var currentOwnerId = AccountID.newBuilder()
						.setShardNum(0)
						.setRealmNum(0)
						.setAccountNum(FIRST_ACCOUNT_NUM + (n + r) % numNftTypes)
						.build();
				final var newOwnerId = AccountID.newBuilder()
						.setShardNum(0)
						.setRealmNum(0)
						.setAccountNum(FIRST_ACCOUNT_NUM + ((n + r + 1) % numNftTypes))
						.build();
				final var result = hederaTokenStore.changeOwner(nftId, currentOwnerId, newOwnerId);
				assertThat(result).isEqualTo(ResponseCodeEnum.OK);
				accountsLedger.commit();
				nftsLedger.commit();
				tokenRelsLedger.commit();
				tokensLedger.commit();
			}
			// Confirm modification
			for (int n = 0; n < numNftTypes; n++) {
				final var tokenId = TokenID.newBuilder()
						.setShardNum(0)
						.setRealmNum(0)
						.setTokenNum(FIRST_TOKEN_NUM + n).build();
				final var nftId = NftId.fromGrpc(tokenId, 1);
				int idx = ((n + r + 1) % numNftTypes);
				long nextOwner = (r % numNftTypes == numNftTypes - 1) ? 0 : FIRST_ACCOUNT_NUM + idx;
				assertThat(backingNfts.getImmutableRef(nftId).getOwnerAccountNum()).isEqualTo(nextOwner);
			}
		}
	}

	private void createNfts(int numNftTypes) throws DecoderException {
		for (int i = 0; i < numNftTypes; i++) {
			final var tokenID = TokenID.newBuilder()
					.setShardNum(0)
					.setRealmNum(0)
					.setTokenNum(FIRST_TOKEN_NUM + i).build();
			final var token = new MerkleToken();
			token.setExpiry(2 * 1_234_567L );
			token.setSymbol("NFT" + i);
			token.setAutoRenewAccount(EntityId.fromNum(105));
			token.setAdminKey(TOKEN_ADMIN_KT.asJKeyUnchecked());
			token.setName("NFTNAME" + i);
			token.setFeeScheduleKey(TOKEN_FEE_SCHEDULE_KT.asJKey());
			token.setTreasury(EntityId.fromNum(FIRST_ACCOUNT_NUM + i));
			token.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
			token.setDecimals(2);
			backingTokens.put(tokenID, token);

			final var uniqueTokenValue = new UniqueTokenValue(
					0, //FIRST_ACCOUNT_NUM + i,  // owner
					0,  // spender
					RichInstant.MISSING_INSTANT,
					("nft " + i).getBytes());
			final var nftId = NftId.fromGrpc(tokenID, 1);
			backingNfts.put(nftId, uniqueTokenValue);

			// Create an account per nft type to make things interesting
			final var accountId = AccountID.newBuilder()
					.setShardNum(0)
					.setRealmNum(0)
					.setAccountNum(FIRST_ACCOUNT_NUM + i)
					.build();
			final var merkleAccount = new MerkleAccount();
			merkleAccount.setMaxAutomaticAssociations(10000);
			backingAccounts.put(accountId, merkleAccount);

			final var relStatus = new MerkleTokenRelStatus(1L, false, true, false);
			backingTokenRels.put(Pair.of(accountId, tokenID), relStatus);
		}
	}

	private static class FakeEntityIdSource implements EntityIdSource {
		private final AtomicLong idCounter = new AtomicLong(10);

		@Override
		public TopicID newTopicId(final AccountID sponsor) {
			return IdUtils.asTopic(String.format("0.0.%d", idCounter.incrementAndGet()));
		}

		@Override
		public AccountID newAccountId(final AccountID newAccountSponsor) {
			return IdUtils.asAccount(String.format("0.0.%d", idCounter.incrementAndGet()));
		}

		@Override
		public ContractID newContractId(final AccountID newContractSponsor) {
			return null;
		}

		@Override
		public FileID newFileId(final AccountID newFileSponsor) {
			return null;
		}

		@Override
		public TokenID newTokenId(final AccountID sponsor) {
			return IdUtils.asToken(String.format("0.0.%d", idCounter.incrementAndGet()));
		}

		@Override
		public ScheduleID newScheduleId(final AccountID sponsor) {
			return null;
		}

		@Override
		public void reclaimLastId() {}

		@Override
		public void reclaimProvisionalIds() {}

		@Override
		public void resetProvisionalIds() {}
	}
}
