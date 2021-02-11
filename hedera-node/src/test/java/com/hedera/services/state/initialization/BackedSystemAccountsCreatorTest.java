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
import com.hedera.services.keys.LegacyEd25519KeyReader;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BackedSystemAccountsCreatorTest {
	private long shard = 1;
	private long realm = 2;
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

	BackedSystemAccountsCreator subject;

	@BeforeEach
	public void setup() throws DecoderException, NegativeAccountBalanceException {
		genesisKey = JKey.mapKey(Key.newBuilder()
				.setKeyList(KeyList.newBuilder()
						.addKeys(Key.newBuilder()
										.setEd25519(ByteString.copyFrom(MiscUtils.commonsHexToBytes(hexedABytes)))
						)).build());

		hederaNums = mock(HederaNumbers.class);
		given(hederaNums.realm()).willReturn(realm);
		given(hederaNums.shard()).willReturn(shard);
		accountNums = mock(AccountNumbers.class);
		given(accountNums.treasury()).willReturn(2L);
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
		given(backingAccounts.getUnsafeRef(accountWith(1))).willReturn(withExpectedBalance(0));
		given(backingAccounts.getUnsafeRef(accountWith(2))).willReturn(withExpectedBalance(totalBalance));
		given(backingAccounts.getUnsafeRef(accountWith(3))).willReturn(withExpectedBalance(0));
		given(backingAccounts.getUnsafeRef(accountWith(4))).willReturn(withExpectedBalance(0));

		subject = new BackedSystemAccountsCreator(
				hederaNums,
				accountNums,
				properties,
				legacyReader);
	}

	@Test
	public void throwsOnNegativeBalance() {
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
	public void throwsOnUndecodableGenesisKeyIfCreating() {
		givenMissingTreasury();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn("This isn't hex!");

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.ensureSystemAccounts(backingAccounts, book));
	}

	@Test
	public void throwsOnUnavailableGenesisKeyIfCreating() {
		givenMissingTreasury();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willThrow(IllegalStateException.class);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.ensureSystemAccounts(backingAccounts, book));
	}

	@Test
	public void createsMissingNode() throws NegativeAccountBalanceException {
		givenMissingNode();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn(hexedABytes);

		// when:
		subject.ensureSystemAccounts(backingAccounts, book);

		// then:
		verify(backingAccounts).put(accountWith(3), withExpectedBalance(0));
	}

	@Test
	public void createsMissingSystemAccount() throws NegativeAccountBalanceException {
		givenMissingSystemAccount();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn(hexedABytes);

		// when:
		subject.ensureSystemAccounts(backingAccounts, book);

		// then:
		verify(backingAccounts).put(accountWith(4), withExpectedBalance(0));
	}

	@Test
	public void createsMissingTreasury() throws NegativeAccountBalanceException {
		givenMissingTreasury();
		// and:
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn(hexedABytes);

		// when:
		subject.ensureSystemAccounts(backingAccounts, book);

		// then:
		verify(backingAccounts).put(accountWith(2), withExpectedBalance(totalBalance));
	}

	@Test
	public void createsMissingSpecialAccounts() throws NegativeAccountBalanceException{
		givenMissingSpecialAccounts();
		given(legacyReader.hexedABytesFrom(b64Loc, legacyId)).willReturn(hexedABytes);

		subject.ensureSystemAccounts(backingAccounts, book);

		verify(backingAccounts).put(accountWith(900), withExpectedBalance(0));
		verify(backingAccounts).put(accountWith(1000), withExpectedBalance(0));
	}

	@Test
	public void createsNothingIfAllPresent() {
		// setup:
		BackedSystemAccountsCreator.log = mock(Logger.class);

		given(backingAccounts.contains(any())).willReturn(true);

		// when:
		subject.ensureSystemAccounts(backingAccounts, book);

		// then:
		verify(backingAccounts, never()).put(any(), any());
		// and:
		verify(BackedSystemAccountsCreator.log).info(String.format(
				"Ledger float is %d tinyBars in %d accounts.", totalBalance, 4));

		// cleanup:
		BackedSystemAccountsCreator.log = LogManager.getLogger(BackedSystemAccountsCreator.class);
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
				.proxy(EntityId.MISSING_ENTITY_ID)
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
