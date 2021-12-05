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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.backing.BackingTokenRels;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.MutableEntityAccess;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.ledger.properties.AccountProperty.ALREADY_USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.services.ledger.properties.AccountProperty.TOKENS;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.test.mocks.TestContextValidator.TEST_VALIDATOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BaseHederaLedgerTestHelper {
	protected OptionValidator validator = TEST_VALIDATOR;
	protected MockGlobalDynamicProps dynamicProps = new MockGlobalDynamicProps();

	protected long GENESIS_BALANCE = 50_000_000_000L;
	protected long NEXT_ID = 1_000_000L;
	protected EntityNum genesis = EntityNum.fromLong(2);

	protected HederaLedger subject;

	protected MutableEntityAccess mutableEntityAccess;
	protected SideEffectsTracker sideEffectsTracker;
	protected HederaTokenStore tokenStore;
	protected EntityIdSource ids;
	protected ExpiringCreations creator;
	protected AccountRecordsHistorian historian;
	protected TransferLogic transferLogic;
	protected TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;
	protected TransactionalLedger<EntityNum, AccountProperty, MerkleAccount> accountsLedger;
	protected TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
	protected TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	protected EntityNum misc = EntityNum.fromLong(1_234);
	protected long MISC_BALANCE = 1_234L;
	protected long RAND_BALANCE = 2_345L;
	protected long miscFrozenTokenBalance = 500L;
	protected MerkleAccount account;
	protected MerkleToken frozenToken;
	protected MerkleToken token;
	protected TokenID missingId = IdUtils.tokenWith(333);
	protected TokenID tokenId = IdUtils.tokenWith(222);
	protected TokenID frozenId = IdUtils.tokenWith(111);
	protected HederaAccountCustomizer noopCustomizer = new HederaAccountCustomizer();
	protected EntityNum deletable = EntityNum.fromLong(666);
	protected EntityNum rand = EntityNum.fromLong(2_345);
	protected EntityNum deleted = EntityNum.fromLong(3_456);
	protected EntityNum detached = EntityNum.fromLong(4_567);

	protected void commonSetup() {
		sideEffectsTracker = mock(SideEffectsTracker.class);
		creator = mock(ExpiringCreations.class);
		historian = mock(AccountRecordsHistorian.class);

		ids = new EntityIdSource() {
			long nextId = NEXT_ID;

			@Override
			public TopicID newTopicId() {
				return TopicID.newBuilder().setTopicNum(nextId++).build();
			}

			@Override
			public EntityNum newAccountId() {
				return EntityNum.fromLong(nextId++);
			}

			@Override
			public ContractID newContractId() {
				return ContractID.newBuilder().setContractNum(nextId++).build();
			}

			@Override
			public FileID newFileId() {
				return FileID.newBuilder().setFileNum(nextId++).build();
			}

			@Override
			public TokenID newTokenId() {
				return TokenID.newBuilder().setTokenNum(nextId++).build();
			}

			@Override
			public ScheduleID newScheduleId() { return ScheduleID.newBuilder().setScheduleNum(nextId++).build(); }

			@Override
			public void reclaimLastId() {
				nextId--;
			}

			@Override
			public void reclaimProvisionalIds() { }

			@Override
			public void resetProvisionalIds() { }
		};
	}

	protected void addToLedger(
			final EntityNum id,
			final long balance,
			final Map<TokenID, TokenInfo> tokenInfo
	) {
		when(accountsLedger.get(id, EXPIRY)).thenReturn(1_234_567_890L);
		when(accountsLedger.get(id, PROXY)).thenReturn(new EntityId(0, 0, 1_234L));
		when(accountsLedger.get(id, AUTO_RENEW_PERIOD)).thenReturn(7776000L);
		when(accountsLedger.get(id, BALANCE)).thenReturn(balance);
		when(accountsLedger.get(id, IS_DELETED)).thenReturn(false);
		when(accountsLedger.get(id, IS_RECEIVER_SIG_REQUIRED)).thenReturn(true);
		when(accountsLedger.get(id, IS_SMART_CONTRACT)).thenReturn(false);
		when(accountsLedger.get(id, MAX_AUTOMATIC_ASSOCIATIONS)).thenReturn(8);
		when(accountsLedger.get(id, ALREADY_USED_AUTOMATIC_ASSOCIATIONS)).thenReturn(5);
		when(accountsLedger.exists(id)).thenReturn(true);
		var tokens = new MerkleAccountTokens();
		tokens.associateAll(tokenInfo.keySet());
		when(accountsLedger.get(id, TOKENS)).thenReturn(tokens);
		// and:
		for (TokenID tId : tokenInfo.keySet()) {
			var info = tokenInfo.get(tId);
			var relationship = BackingTokenRels.asTokenRel(id.toGrpcAccountId(), tId);
			when(tokenRelsLedger.get(relationship, TOKEN_BALANCE)).thenReturn(info.balance);
		}
	}

	protected void addDeletedAccountToLedger(final EntityNum id) {
		when(accountsLedger.get(id, BALANCE)).thenReturn(0L);
		when(accountsLedger.get(id, IS_DELETED)).thenReturn(true);
	}

	protected void addToLedger(
			final EntityNum id,
			final long balance,
			final HederaAccountCustomizer customizer
	) {
		addToLedger(id, balance, Collections.emptyMap());
	}

	protected void setupWithMockLedger() {
		var freezeKey = new JEd25519Key("w/e".getBytes());

		account = mock(MerkleAccount.class);

		frozenToken = mock(MerkleToken.class);
		given(frozenToken.freezeKey()).willReturn(Optional.of(freezeKey));
		given(frozenToken.accountsAreFrozenByDefault()).willReturn(true);
		token = mock(MerkleToken.class);
		given(token.freezeKey()).willReturn(Optional.empty());

		nftsLedger = mock(TransactionalLedger.class);
		accountsLedger = mock(TransactionalLedger.class);
		tokenRelsLedger = mock(TransactionalLedger.class);
		tokensLedger = mock(TransactionalLedger.class);
		addToLedger(misc, MISC_BALANCE, Map.of(
				frozenId,
				new TokenInfo(miscFrozenTokenBalance, frozenToken)));
		addToLedger(deletable, MISC_BALANCE, Map.of(
				frozenId,
				new TokenInfo(0, frozenToken)));
		addToLedger(rand, RAND_BALANCE, noopCustomizer);
		addToLedger(genesis, GENESIS_BALANCE, noopCustomizer);
		addToLedger(detached, 0L, new HederaAccountCustomizer().expiry(1_234_567L));
		addDeletedAccountToLedger(deleted);
		given(tokenRelsLedger.isInTransaction()).willReturn(true);

		tokenStore = mock(HederaTokenStore.class);
		given(tokenStore.exists(frozenId)).willReturn(true);
		given(tokenStore.exists(tokenId)).willReturn(true);
		given(tokenStore.exists(missingId)).willReturn(false);
		given(tokenStore.resolve(missingId))
				.willReturn(TokenStore.MISSING_TOKEN);
		given(tokenStore.resolve(frozenId))
				.willReturn(frozenId);
		given(tokenStore.resolve(tokenId))
				.willReturn(tokenId);
		given(tokenStore.get(frozenId)).willReturn(frozenToken);

		mutableEntityAccess = mock(MutableEntityAccess.class);
		subject = new HederaLedger(
				tokenStore, ids, creator,
				validator, sideEffectsTracker, historian,
				dynamicProps, accountsLedger, transferLogic);
		subject.setNftsLedger(nftsLedger);
		subject.setTokenRelsLedger(tokenRelsLedger);
		subject.setMutableEntityAccess(mutableEntityAccess);
	}

	protected void givenOkTokenXfers(final EntityNum misc, final TokenID tokenId, final long i) {
		given(tokenStore.adjustBalance(misc, tokenId, i)).willReturn(OK);
	}

	protected static class TokenInfo {
		final long balance;
		final MerkleToken token;

		public TokenInfo(long balance, MerkleToken token) {
			this.balance = balance;
			this.token = token;
		}
	}
}
