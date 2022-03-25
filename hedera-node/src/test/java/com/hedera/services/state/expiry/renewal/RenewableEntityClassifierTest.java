package com.hedera.services.state.expiry.renewal;

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

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.OTHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RenewableEntityClassifierTest {
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private TokenStore tokenStore;
	@Mock
	private AliasManager aliasManager;

	private RenewableEntityClassifier subject;

	@BeforeEach
	void setUp() {
		subject = new RenewableEntityClassifier(tokenStore, dynamicProps, () -> accounts);
	}

	@Test
	void classifiesNonAccount() {
		// expect:
		assertEquals(OTHER, subject.classify(EntityNum.fromLong(4L), now));
	}

	@Test
	void classifiesNonExpiredAccount() {
		givenPresent(nonExpiredAccountNum, nonExpiredAccount);

		// expect:
		assertEquals(OTHER, subject.classify(EntityNum.fromLong(nonExpiredAccountNum), now));
	}

	@Test
	void classifiesContractAccount() {
		givenPresent(nonExpiredAccountNum, contractAccount);

		// expect:
		assertEquals(OTHER, subject.classify(EntityNum.fromLong(nonExpiredAccountNum), now));
	}

	@Test
	void classifiesDeletedAccountAfterExpiration() {
		givenPresent(brokeExpiredAccountNum, expiredDeletedAccount);

		// expect:
		assertEquals(
				DETACHED_ACCOUNT_GRACE_PERIOD_OVER,
				subject.classify(EntityNum.fromLong(brokeExpiredAccountNum), now));
	}

	@Test
	void classifiesDetachedAccountAfterGracePeriod() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);

		// expect:
		assertEquals(
				DETACHED_ACCOUNT_GRACE_PERIOD_OVER,
				subject.classify(EntityNum.fromLong(brokeExpiredAccountNum), now + dynamicProps.autoRenewGracePeriod()));
	}

	@Test
	void classifiesDetachedAccountAfterGracePeriodAsOtherIfTokenNotYetRemoved() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);
		given(tokenStore.isKnownTreasury(grpcIdWith(brokeExpiredAccountNum))).willReturn(true);

		// expect:
		assertEquals(
				DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN,
				subject.classify(EntityNum.fromLong(brokeExpiredAccountNum),
						now + dynamicProps.autoRenewGracePeriod()));
	}

	@Test
	void classifiesDetachedAccount() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);

		// expect:
		assertEquals(
				DETACHED_ACCOUNT,
				subject.classify(EntityNum.fromLong(brokeExpiredAccountNum), now));
	}

	@Test
	void classifiesFundedExpiredAccount() {
		givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance);

		// expect:
		assertEquals(EXPIRED_ACCOUNT_READY_TO_RENEW, subject.classify(EntityNum.fromLong(fundedExpiredAccountNum), now));
		// and:
		assertEquals(expiredAccountNonZeroBalance, subject.getLastClassifiedAccount());
	}

	@Test
	void renewsLastClassifiedAsRequested() {
		// setup:
		var key = EntityNum.fromLong(fundedExpiredAccountNum);
		var fundingKey = EntityNum.fromInt(98);

		givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance, true);
		givenPresent(98, fundingAccount, true);

		// when:
		subject.classify(EntityNum.fromLong(fundedExpiredAccountNum), now);
		// and:
		subject.renewLastClassifiedWith(nonZeroBalance, 3600L);

		// then:
		verify(accounts).getForModify(key);
		verify(accounts).getForModify(fundingKey);
		verify(aliasManager, never()).forgetAlias(any());
	}

	@Test
	void cannotRenewIfNoLastClassified() {
		// expect:
		assertThrows(IllegalStateException.class,
				() -> subject.renewLastClassifiedWith(nonZeroBalance, 3600L));
	}

	@Test
	void rejectsAsIseIfFeeIsUnaffordable() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);

		// when:
		subject.classify(EntityNum.fromLong(brokeExpiredAccountNum), now);
		// expect:
		assertThrows(IllegalStateException.class,
				() -> subject.renewLastClassifiedWith(nonZeroBalance, 3600L));
	}

	private AccountID grpcIdWith(long num) {
		return AccountID.newBuilder().setAccountNum(num).build();
	}

	private void givenPresent(long num, MerkleAccount account) {
		givenPresent(num, account, false);
	}

	private void givenPresent(long num, MerkleAccount account, boolean modifiable) {
		var key = EntityNum.fromLong(num);
		if (num != 98) {
			given(accounts.containsKey(key)).willReturn(true);
			given(accounts.get(key)).willReturn(account);
		}
		if (modifiable) {
			given(accounts.getForModify(key)).willReturn(account);
		}
	}

	private final long now = 1_234_567L;
	private final long nonZeroBalance = 1L;
	private final MockGlobalDynamicProps dynamicProps = new MockGlobalDynamicProps();

	private final MerkleAccount nonExpiredAccount = MerkleAccountFactory.newAccount()
			.balance(0).expirationTime(now + 1)
			.alias(ByteString.copyFromUtf8("aaaa"))
			.get();
	private final MerkleAccount expiredAccountZeroBalance = MerkleAccountFactory.newAccount()
			.balance(0).expirationTime(now - 1)
			.alias(ByteString.copyFromUtf8("bbbb"))
			.get();
	private final MerkleAccount expiredDeletedAccount = MerkleAccountFactory.newAccount()
			.balance(0)
			.deleted(true)
			.alias(ByteString.copyFromUtf8("cccc"))
			.expirationTime(now - 1)
			.get();
	private final MerkleAccount expiredAccountNonZeroBalance = MerkleAccountFactory.newAccount()
			.balance(nonZeroBalance).expirationTime(now - 1)
			.alias(ByteString.copyFromUtf8("dddd"))
			.get();
	private final MerkleAccount fundingAccount = MerkleAccountFactory.newAccount()
			.balance(0)
			.alias(ByteString.copyFromUtf8("eeee"))
			.get();
	private final MerkleAccount contractAccount = MerkleAccountFactory.newAccount()
			.isSmartContract(true)
			.balance(0).expirationTime(now - 1)
			.get();
	private final long nonExpiredAccountNum = 1L;
	private final long brokeExpiredAccountNum = 2L;
	private final long fundedExpiredAccountNum = 3L;
	private final EntityId expiredTreasuryId = new EntityId(0, 0, brokeExpiredAccountNum);
	private final EntityId treasuryId = new EntityId(0, 0, 666L);
	private final MerkleToken deletedToken = new MerkleToken(
			Long.MAX_VALUE, 1L, 0,
			"GONE", "Long lost dream",
			true, true, expiredTreasuryId);
	private final long deletedTokenNum = 1234L, survivedTokenNum = 4321L;
	private final EntityNum deletedTokenId = EntityNum.fromLong(deletedTokenNum);
	private final EntityNum survivedTokenId = EntityNum.fromLong(survivedTokenNum);
	private final TokenID deletedTokenGrpcId = deletedTokenId.toGrpcTokenId();
	private final TokenID survivedTokenGrpcId = survivedTokenId.toGrpcTokenId();
	private final TokenID missingTokenGrpcId = TokenID.newBuilder().setTokenNum(5678L).build();

	{
		deletedToken.setDeleted(true);
		final var associations = new MerkleAccountTokens();
		associations.associateAll(Set.of(deletedTokenGrpcId, survivedTokenGrpcId, missingTokenGrpcId));
		expiredAccountZeroBalance.setTokens(associations);
	}
}
