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


import com.hedera.services.store.contracts.HederaWorldState.WorldStateAccount;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HederaStackedWorldStateUpdaterTest {
	@Mock
	private WorldLedgers trackingLedgers;
	@Mock(extraInterfaces = { HederaWorldUpdater.class })
	private AbstractLedgerWorldUpdater<HederaMutableWorldState, WorldStateAccount> updater;
	@Mock
	private HederaMutableWorldState worldState;
	@Mock
	private HederaWorldState.WorldStateAccount account;

	private Address address;
	private HederaStackedWorldStateUpdater subject;

	@BeforeEach
	void setUp() {
		address = Address.fromHexString("0xabc");
		subject = new HederaStackedWorldStateUpdater(updater, worldState, trackingLedgers);
	}

	@AfterEach
	void tearDown() {
		subject.getSponsorMap().clear();
	}

	@Test
	void allocateNewContractAddress() {
		final var sponsorAddr = Address.wrap(Bytes.wrap(EntityIdUtils.asSolidityAddress(
				ContractID.newBuilder().setContractNum(1).build())));

		final var sponsoredAddr = Address.wrap(Bytes.wrap(EntityIdUtils.asSolidityAddress(
				ContractID.newBuilder().setContractNum(2).build())));
		given(worldState.newContractAddress(sponsorAddr)).willReturn(sponsoredAddr);
		final var allocated = subject.allocateNewContractAddress(sponsorAddr);
		final var sponsorAid = EntityIdUtils.accountParsedFromSolidityAddress(sponsorAddr.toArrayUnsafe());
		final var allocatedAid = EntityIdUtils.accountParsedFromSolidityAddress(allocated.toArrayUnsafe());

		assertEquals(sponsorAid.getRealmNum(), allocatedAid.getRealmNum());
		assertEquals(sponsorAid.getShardNum(), allocatedAid.getShardNum());
		assertEquals(sponsorAid.getAccountNum() + 1, allocatedAid.getAccountNum());
		assertEquals(1, subject.getSponsorMap().size());
		assertTrue(subject.getSponsorMap().containsKey(sponsoredAddr));
		assertTrue(subject.getSponsorMap().containsValue(sponsorAddr));
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
	void getHederaAccountReturnsNull() {
		given(((HederaWorldUpdater) updater).getHederaAccount(address)).willReturn(null);

		final var result = subject.getHederaAccount(address);

		// then:
		assertNull(result);
		// and:
		verify((HederaWorldUpdater) updater).getHederaAccount(address);
	}

	@Test
	void getHederaAccountReturnsValue() {
		given(((HederaWorldUpdater) updater).getHederaAccount(address)).willReturn(account);

		final var result = subject.getHederaAccount(address);

		// then:
		assertEquals(account, result);
		// and:
		verify((HederaWorldUpdater) updater).getHederaAccount(address);
	}
}