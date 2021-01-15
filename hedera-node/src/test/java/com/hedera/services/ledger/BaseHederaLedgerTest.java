package com.hedera.services.ledger;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcqueue.FCQueue;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.RECORDS;
import static com.hedera.services.ledger.properties.AccountProperty.TOKENS;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnitPlatform.class)
public class BaseHederaLedgerTest {
	protected long GENESIS_BALANCE = 50_000_000_000L;
	protected long NEXT_ID = 1_000_000L;
	protected AccountID genesis = AccountID.newBuilder().setAccountNum(2).build();

	protected HederaLedger subject;

	protected HederaTokenStore tokenStore;
	protected EntityIdSource ids;
	protected ExpiringCreations creator;
	protected AccountRecordsHistorian historian;
	protected TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	protected TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	protected AccountID misc = AccountID.newBuilder().setAccountNum(1_234).build();
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
	protected AccountID deletable = AccountID.newBuilder().setAccountNum(666).build();
	protected AccountID rand = AccountID.newBuilder().setAccountNum(2_345).build();
	protected AccountID deleted = AccountID.newBuilder().setAccountNum(3_456).build();

	protected void commonSetup() {
		creator = mock(ExpiringCreations.class);
		historian = mock(AccountRecordsHistorian.class);

		ids = new EntityIdSource() {
			long nextId = NEXT_ID;

			@Override
			public AccountID newAccountId(AccountID newAccountSponsor) {
				return AccountID.newBuilder().setAccountNum(nextId++).build();
			}

			@Override
			public FileID newFileId(AccountID newFileSponsor) {
				return FileID.newBuilder().setFileNum(nextId++).build();
			}

			@Override
			public TokenID newTokenId(AccountID sponsor) {
				return TokenID.newBuilder().setTokenNum(nextId++).build();
			}

			@Override
			public ScheduleID newScheduleId(AccountID sponsor) { return ScheduleID.newBuilder().setScheduleNum(nextId++).build(); }

			@Override
			public void reclaimLastId() {
				nextId--;
			}
		};
	}

	protected AccountAmount aa(AccountID account, long amount) {
		return AccountAmount.newBuilder().setAccountID(account).setAmount(amount).build();
	}

	protected FCQueue<ExpirableTxnRecord> asExpirableRecords(long... expiries) {
		FCQueue<ExpirableTxnRecord> records = new FCQueue<>(ExpirableTxnRecord.LEGACY_PROVIDER);
		for (int i = 0; i < expiries.length; i++) {
			ExpirableTxnRecord record = new ExpirableTxnRecord();
			record.setExpiry(expiries[i]);
			records.offer(record);
		}
		return records;
	}

	protected void addPayerRecords(AccountID id, FCQueue<ExpirableTxnRecord> records) {
		when(accountsLedger.get(id, RECORDS)).thenReturn(records);
	}

	protected void addToLedger(
			AccountID id,
			long balance,
			HederaAccountCustomizer customizer,
			Map<TokenID, TokenInfo> tokenInfo
	) {
		when(accountsLedger.get(id, EXPIRY)).thenReturn(1_234_567_890L);
		when(accountsLedger.get(id, BALANCE)).thenReturn(balance);
		when(accountsLedger.get(id, IS_DELETED)).thenReturn(false);
		when(accountsLedger.get(id, IS_SMART_CONTRACT)).thenReturn(false);
		when(accountsLedger.exists(id)).thenReturn(true);
		var tokens = new MerkleAccountTokens();
		tokens.associateAll(tokenInfo.keySet());
		when(accountsLedger.get(id, TOKENS)).thenReturn(tokens);
		// and:
		for (TokenID tId : tokenInfo.keySet()) {
			var info = tokenInfo.get(tId);
			var relationship = BackingTokenRels.asTokenRel(id, tId);
			when(tokenRelsLedger.get(relationship, TOKEN_BALANCE)).thenReturn(info.balance);
		}
	}

	protected void addDeletedAccountToLedger(AccountID id, HederaAccountCustomizer customizer) {
		when(accountsLedger.get(id, BALANCE)).thenReturn(0L);
		when(accountsLedger.get(id, IS_DELETED)).thenReturn(true);
	}

	protected void addToLedger(
			AccountID id,
			long balance,
			HederaAccountCustomizer customizer
	) {
		addToLedger(id, balance, customizer, Collections.emptyMap());
	}

	protected void setupWithMockLedger() {
		var freezeKey = new JEd25519Key("w/e".getBytes());

		account = mock(MerkleAccount.class);

		frozenToken = mock(MerkleToken.class);
		given(frozenToken.freezeKey()).willReturn(Optional.of(freezeKey));
		given(frozenToken.accountsAreFrozenByDefault()).willReturn(true);
		token = mock(MerkleToken.class);
		given(token.freezeKey()).willReturn(Optional.empty());

		accountsLedger = mock(TransactionalLedger.class);
		tokenRelsLedger = mock(TransactionalLedger.class);
		addToLedger(misc, MISC_BALANCE, noopCustomizer, Map.of(
				frozenId,
				new TokenInfo(miscFrozenTokenBalance, frozenToken)));
		addToLedger(deletable, MISC_BALANCE, noopCustomizer, Map.of(
				frozenId,
				new TokenInfo(0, frozenToken)));
		addToLedger(rand, RAND_BALANCE, noopCustomizer);
		addToLedger(genesis, GENESIS_BALANCE, noopCustomizer);
		addDeletedAccountToLedger(deleted, noopCustomizer);
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

		subject = new HederaLedger(tokenStore, ids, creator, historian, accountsLedger);
		subject.setTokenRelsLedger(tokenRelsLedger);
	}

	protected void givenAdjustBalanceUpdatingTokenXfers(AccountID misc, TokenID tokenId, long i) {
		given(tokenStore.adjustBalance(misc, tokenId, i))
				.willAnswer(invocationOnMock -> {
					AccountID aId = invocationOnMock.getArgument(0);
					TokenID tId = invocationOnMock.getArgument(1);
					long amount = invocationOnMock.getArgument(2);
					subject.updateTokenXfers(tId, aId, amount);
					return OK;
				});
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
