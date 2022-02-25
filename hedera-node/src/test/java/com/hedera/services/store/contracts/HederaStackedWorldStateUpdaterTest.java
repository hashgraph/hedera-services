package com.hedera.services.store.contracts;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.HederaWorldState.WorldStateAccount;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HederaStackedWorldStateUpdaterTest {
	private static final Address alias = Address.fromHexString("0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb");
	private static final Address sponsor = Address.fromHexString("0xcba");
	private static final Address address = Address.fromHexString("0xabc");
	private static final Address otherAddress = Address.fromHexString("0xdef");
	private static final ContractID addressId = EntityIdUtils.contractIdFromEvmAddress(address);

	@Mock
	private ContractAliases aliases;
	@Mock
	private WorldLedgers trackingLedgers;
	@Mock(extraInterfaces = { HederaWorldUpdater.class })
	private AbstractLedgerWorldUpdater<HederaMutableWorldState, WorldStateAccount> updater;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	@Mock
	private HederaMutableWorldState worldState;
	@Mock
	private HederaWorldState.WorldStateAccount account;
	@Mock
	private SideEffectsTracker sideEffectsTracker;

	private HederaStackedWorldStateUpdater subject;

	@BeforeEach
	void setUp() {
		subject = new HederaStackedWorldStateUpdater(updater, worldState, trackingLedgers, sideEffectsTracker);
	}

	@Test
	void usesAliasesForDecodingHelp() {
		given(aliases.resolveForEvm(alias)).willReturn(sponsor);
		given(trackingLedgers.aliases()).willReturn(aliases);

		final var resolved = subject.unaliased(alias.toArrayUnsafe());
		assertArrayEquals(sponsor.toArrayUnsafe(), resolved);
	}

	@Test
	void linksAliasWhenReservingNewContractId() {
		given(worldState.newContractAddress(sponsor)).willReturn(address);
		given(trackingLedgers.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(sponsor)).willReturn(sponsor);

		final var created = subject.newAliasedContractAddress(sponsor, alias);

		assertSame(address, created);
		assertEquals(sponsor, subject.getSponsorMap().get(address));
		assertEquals(addressId, subject.idOfLastNewAddress());
		verify(aliases).link(alias, address);
	}

	@Test
	void usesCanonicalAddressFromTrackingLedgers() {
		given(trackingLedgers.canonicalAddress(sponsor)).willReturn(alias);

		assertSame(alias, subject.priorityAddress(sponsor));
	}

	@Test
	void doesntRelinkAliasIfActiveAndExtant() {
		final var targetId = EntityIdUtils.accountIdFromEvmAddress(otherAddress);
		given(worldState.newContractAddress(sponsor)).willReturn(address);
		given(trackingLedgers.accounts()).willReturn(accountsLedger);
		given(trackingLedgers.aliases()).willReturn(aliases);
		given(aliases.isInUse(alias)).willReturn(true);
		given(aliases.resolveForEvm(sponsor)).willReturn(sponsor);
		given(aliases.resolveForEvm(alias)).willReturn(otherAddress);
		given(accountsLedger.exists(targetId)).willReturn(true);

		final var created = subject.newAliasedContractAddress(sponsor, alias);

		assertSame(address, created);
		assertEquals(sponsor, subject.getSponsorMap().get(address));
		assertEquals(addressId, subject.idOfLastNewAddress());
		verify(aliases, never()).link(alias, address);
	}

	@Test
	void doesRelinkAliasIfActiveButWithMissingTarget() {
		given(worldState.newContractAddress(sponsor)).willReturn(address);
		given(trackingLedgers.accounts()).willReturn(accountsLedger);
		given(trackingLedgers.aliases()).willReturn(aliases);
		given(aliases.isInUse(alias)).willReturn(true);
		given(aliases.resolveForEvm(sponsor)).willReturn(sponsor);
		given(aliases.resolveForEvm(alias)).willReturn(otherAddress);

		final var created = subject.newAliasedContractAddress(sponsor, alias);

		assertSame(address, created);
		assertEquals(sponsor, subject.getSponsorMap().get(address));
		assertEquals(addressId, subject.idOfLastNewAddress());
		verify(aliases).link(alias, address);
	}

	@Test
	void allocatesNewContractAddress() {
		final var sponsoredId = ContractID.newBuilder().setContractNum(2).build();
		final var sponsorAddr = Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(
				ContractID.newBuilder().setContractNum(1).build())));
		given(trackingLedgers.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(sponsorAddr)).willReturn(sponsorAddr);

		final var sponsoredAddr = Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(sponsoredId)));
		given(worldState.newContractAddress(sponsorAddr)).willReturn(sponsoredAddr);
		final var allocated = subject.newContractAddress(sponsorAddr);
		final var sponsorAid = EntityIdUtils.accountIdFromEvmAddress(sponsorAddr.toArrayUnsafe());
		final var allocatedAid = EntityIdUtils.accountIdFromEvmAddress(allocated.toArrayUnsafe());

		assertEquals(sponsorAid.getRealmNum(), allocatedAid.getRealmNum());
		assertEquals(sponsorAid.getShardNum(), allocatedAid.getShardNum());
		assertEquals(sponsorAid.getAccountNum() + 1, allocatedAid.getAccountNum());
		assertEquals(1, subject.getSponsorMap().size());
		assertTrue(subject.getSponsorMap().containsKey(sponsoredAddr));
		assertTrue(subject.getSponsorMap().containsValue(sponsorAddr));
		assertEquals(sponsoredId, subject.idOfLastNewAddress());
	}

	@Test
	void canSponsorWithAlias() {
		final var sponsoredId = ContractID.newBuilder().setContractNum(2).build();
		final var sponsorAddr = Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(
				ContractID.newBuilder().setContractNum(1).build())));
		given(aliases.resolveForEvm(alias)).willReturn(sponsorAddr);
		given(trackingLedgers.aliases()).willReturn(aliases);

		final var sponsoredAddr = Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(sponsoredId)));
		given(worldState.newContractAddress(sponsorAddr)).willReturn(sponsoredAddr);

		final var allocated = subject.newContractAddress(alias);
		final var sponsorAid = EntityIdUtils.accountIdFromEvmAddress(sponsorAddr.toArrayUnsafe());
		final var allocatedAid = EntityIdUtils.accountIdFromEvmAddress(allocated.toArrayUnsafe());

		assertEquals(sponsorAid.getRealmNum(), allocatedAid.getRealmNum());
		assertEquals(sponsorAid.getShardNum(), allocatedAid.getShardNum());
		assertEquals(sponsorAid.getAccountNum() + 1, allocatedAid.getAccountNum());
		assertEquals(1, subject.getSponsorMap().size());
		assertTrue(subject.getSponsorMap().containsKey(sponsoredAddr));
		assertTrue(subject.getSponsorMap().containsValue(sponsorAddr));
		assertEquals(sponsoredId, subject.idOfLastNewAddress());
	}

	@Test
	void revert() {
		subject.getSponsorMap().put(Address.ECREC, Address.RIPEMD160);
		subject.addSbhRefund(Gas.of(123L));
		assertEquals(1, subject.getSponsorMap().size());
		assertEquals(123L, subject.getSbhRefund().toLong());
		subject.revert();
		assertEquals(0, subject.getSponsorMap().size());
		assertEquals(0, subject.getSbhRefund().toLong());
	}

	@Test
	void updater() {
		var updater = subject.updater();
		assertEquals(HederaStackedWorldStateUpdater.class, updater.getClass());
	}

	@Test
	void getHederaAccountReturnsNullIfNotPresentInParent() {
		given(trackingLedgers.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(address)).willReturn(address);
		given(((HederaWorldUpdater) updater).getHederaAccount(address)).willReturn(null);

		final var result = subject.getHederaAccount(address);

		// then:
		assertNull(result);
		// and:
		verify((HederaWorldUpdater) updater).getHederaAccount(address);
	}

	@Test
	void getHederaAccountReturnsValueIfPresentInParent() {
		given(trackingLedgers.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(address)).willReturn(address);
		given(((HederaWorldUpdater) updater).getHederaAccount(address)).willReturn(account);

		final var result = subject.getHederaAccount(address);

		// then:
		assertEquals(account, result);
		// and:
		verify((HederaWorldUpdater) updater).getHederaAccount(address);
	}
}