package com.hedera.services.state.initialization;

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
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.keys.LegacyEd25519KeyReader;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.swirlds.common.system.Address;
import com.swirlds.common.system.AddressBook;
import com.swirlds.common.utility.CommonUtils;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(LogCaptureExtension.class)
class BackedSystemAccountsCreatorTest {
	private long shard = 0;
	private long realm = 0;
	private long totalBalance = 100l;
	private long expiry = Instant.now().getEpochSecond() + 1_234_567L;
	private int numAccounts = 4;
	private String b64Loc = "somewhere";
	private String legacyId = "CURSED";
	private String hexedABytes = "447dc6bdbfc64eb894851825194744662afcb70efb8b23a6a24af98f0c1fd8ad";
	private JKey genesisKey;

	HederaNumbers hederaNums;
	AccountNumbers accountNums;
	PropertySource properties;
	LegacyEd25519KeyReader legacyReader;

	AddressBook book;
	BackingStore<AccountID, MerkleAccount> backingAccounts;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private BackedSystemAccountsCreator subject;

	@BeforeEach
	void setup() throws DecoderException, NegativeAccountBalanceException, IllegalArgumentException {
		genesisKey = JKey.mapKey(Key.newBuilder()
				.setKeyList(KeyList.newBuilder()
						.addKeys(Key.newBuilder()
										.setEd25519(ByteString.copyFrom(CommonUtils.unhex(hexedABytes)))
						)).build());

		hederaNums = mock(HederaNumbers.class);
		given(hederaNums.realm()).willReturn(realm);
		given(hederaNums.shard()).willReturn(shard);
		accountNums = mock(AccountNumbers.class);
		given(accountNums.treasury()).willReturn(2L);
		given(accountNums.stakingRewardAccount()).willReturn(800L);
		given(accountNums.nodeRewardAccount()).willReturn(801L);
		properties = mock(PropertySource.class);
		legacyReader = mock(LegacyEd25519KeyReader.class);

		given(properties.getIntProperty("ledger.numSystemAccounts"))
				.willReturn(numAccounts);
		given(properties.getLongProperty("ledger.totalTinyBarFloat"))
				.willReturn(totalBalance);
		given(properties.getStringProperty("bootstrap.genesisB64Keystore.keyName"))
				.willReturn(legacyId);
		given(properties.getStringProperty("bootstrap.genesisB64Keystore.path"))
				.willReturn(b64Loc);
		given(properties.getLongProperty("bootstrap.system.entityExpiry"))
				.willReturn(expiry);

		var address = mock(Address.class);
		given(address.getMemo()).willReturn("0.0.3");
		book = mock(AddressBook.class);
		given(book.getSize()).willReturn(1);
		given(book.getAddress(0L)).willReturn(address);

		backingAccounts = (BackingStore<AccountID, MerkleAccount>)mock(BackingStore.class);
		given(backingAccounts.idSet()).willReturn(Set.of(
				accountWith(1),
				accountWith(2),
				accountWith(3),
				accountWith(4)));
		given(backingAccounts.getImmutableRef(accountWith(1))).willReturn(withExpectedBalance(0));
		given(backingAccounts.getImmutableRef(accountWith(2))).willReturn(withExpectedBalance(totalBalance));
		given(backingAccounts.getImmutableRef(accountWith(3))).willReturn(withExpectedBalance(0));
		given(backingAccounts.getImmutableRef(accountWith(4))).willReturn(withExpectedBalance(0));

		subject = new BackedSystemAccountsCreator(
				accountNums,
				properties,
				legacyReader);
	}

	@Test
	void throwsOnNegativeBalance() {
		givenMissingTreasury();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn(hexedABytes);
		// and:
		given(properties.getLongProperty("ledger.totalTinyBarFloat"))
				.willReturn(-100L);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.ensureSystemAccounts(backingAccounts, book));
	}

	@Test
	void throwsOnUndecodableGenesisKeyIfCreating() {
		givenMissingTreasury();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn("This isn't hex!");

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.ensureSystemAccounts(backingAccounts, book));
	}

	@Test
	void throwsOnUnavailableGenesisKeyIfCreating() {
		givenMissingTreasury();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willThrow(IllegalStateException.class);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.ensureSystemAccounts(backingAccounts, book));
	}

	@Test
	void createsMissingNode() throws NegativeAccountBalanceException {
		givenMissingNode();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn(hexedABytes);

		// when:
		subject.ensureSystemAccounts(backingAccounts, book);

		// then:
		verify(backingAccounts).put(accountWith(3), withExpectedBalance(0));
	}

	@Test
	void createsMissingSystemAccount() throws NegativeAccountBalanceException {
		givenMissingSystemAccount();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn(hexedABytes);

		// when:
		subject.ensureSystemAccounts(backingAccounts, book);

		// then:
		verify(backingAccounts).put(accountWith(4), withExpectedBalance(0));
	}

	@Test
	void createsMissingTreasury() throws NegativeAccountBalanceException {
		givenMissingTreasury();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn(hexedABytes);

		// when:
		subject.ensureSystemAccounts(backingAccounts, book);

		// then:
		verify(backingAccounts).put(accountWith(2), withExpectedBalance(totalBalance));
	}

	@Test
	void createsMissingSpecialAccounts() throws NegativeAccountBalanceException{
		givenMissingSpecialAccounts();
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn(hexedABytes);

		subject.ensureSystemAccounts(backingAccounts, book);

		verify(backingAccounts).put(accountWith(900), withExpectedBalance(0));
		verify(backingAccounts).put(accountWith(1000), withExpectedBalance(0));
	}

	@Test
	void createsNothingIfAllPresent() {
		given(backingAccounts.contains(any())).willReturn(true);
		var desiredInfo = String.format("Ledger float is %d tinyBars in %d accounts.", totalBalance, 4);

		// when:
		subject.ensureSystemAccounts(backingAccounts, book);

		// then:
		verify(backingAccounts, never()).put(any(), any());
		// and:
		assertThat(logCaptor.infoLogs(), contains(desiredInfo));
	}

	@Test
	void createsStakingFundAccounts() {
		final var captor = ArgumentCaptor.forClass(MerkleAccount.class);
		final var funding801 = AccountID.newBuilder().setAccountNum(801).build();
		given(backingAccounts.contains(any())).willReturn(true);
		given(backingAccounts.contains(funding801)).willReturn(false);

		subject.ensureSystemAccounts(backingAccounts, book);

		verify(backingAccounts).put(eq(funding801), captor.capture());
		final var new801 = captor.getValue();
		assertEquals(canonicalFundingAccount(), new801);
	}

	private MerkleAccount canonicalFundingAccount() {
		final var account = new MerkleAccount();
		BackedSystemAccountsCreator.customizeAsStakingFund(account);
		return account;
	}

	private void givenMissingSystemAccount() {
		given(backingAccounts.contains(accountWith(1L))).willReturn(true);
		given(backingAccounts.contains(accountWith(2L))).willReturn(true);
		given(backingAccounts.contains(accountWith(3L))).willReturn(true);
		given(backingAccounts.contains(accountWith(4L))).willReturn(false);
		givenAllPresentSpecialAccounts();
	}

	private void givenMissingTreasury() {
		given(backingAccounts.contains(accountWith(1L))).willReturn(true);
		given(backingAccounts.contains(accountWith(2L))).willReturn(false);
		given(backingAccounts.contains(accountWith(3L))).willReturn(true);
		given(backingAccounts.contains(accountWith(4L))).willReturn(true);
		givenAllPresentSpecialAccounts();
	}

	private void givenMissingNode() {
		given(backingAccounts.contains(accountWith(1L))).willReturn(true);
		given(backingAccounts.contains(accountWith(2L))).willReturn(true);
		given(backingAccounts.contains(accountWith(3L))).willReturn(false);
		given(backingAccounts.contains(accountWith(4L))).willReturn(true);
		givenAllPresentSpecialAccounts();
	}

	private void givenAllPresentSpecialAccounts() {
		given(backingAccounts.contains(
				argThat(accountID -> (900 <= accountID.getAccountNum() && accountID.getAccountNum() <= 1000)))
		).willReturn(true);
	}

	private void givenMissingSpecialAccounts() {
		given(backingAccounts.contains(
				argThat(accountID -> (accountID.getAccountNum() != 900 && accountID.getAccountNum() != 1000)))
		).willReturn(true);
		given(backingAccounts.contains(accountWith(900L))).willReturn(false);
		given(backingAccounts.contains(accountWith(1000L))).willReturn(false);
	}

	private AccountID accountWith(long num) {
		return IdUtils.asAccount(String.format("%d.%d.%d", shard, realm, num));
	}

	private MerkleAccount withExpectedBalance(long balance) throws NegativeAccountBalanceException {
		MerkleAccount hAccount = new HederaAccountCustomizer()
				.isReceiverSigRequired(false)
				.isDeleted(false)
				.expiry(expiry)
				.memo("")
				.isSmartContract(false)
				.key(genesisKey)
				.autoRenewPeriod(expiry)
				.customizing(new MerkleAccount());
		hAccount.setBalance(balance);
		return hAccount;
	}
}
