package com.hedera.services.ledger.accounts;

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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static com.swirlds.common.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasManagerTest {
	private static final ByteString alias = ByteString.copyFromUtf8("aaaa");
	private static final EntityNum num = EntityNum.fromLong(1234L);
	private static final byte[] rawNonMirrorAddress = unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
	private static final Address nonMirrorAddress = Address.wrap(Bytes.wrap(rawNonMirrorAddress));
	private static final Address mirrorAddress = num.toEvmAddress();

	private FCHashMap<ByteString, EntityNum> aliases = new FCHashMap<>();

	private AliasManager subject = new AliasManager(() -> aliases);

	@Test
	void resolvesLinkedNonMirrorAsExpected() {
		subject.link(ByteString.copyFrom(rawNonMirrorAddress), num);
		assertEquals(num.toEvmAddress(), subject.resolveForEvm(nonMirrorAddress));
	}

	@Test
	void non20ByteStringCannotBeMirror() {
		assertFalse(subject.isMirror(new byte[] { (byte) 0xab, (byte) 0xcd }));
		assertFalse(subject.isMirror(unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbbde")));
	}

	@Test
	void resolvesUnlinkedNonMirrorAsExpected() {
		assertSame(nonMirrorAddress, subject.resolveForEvm(nonMirrorAddress));
	}

	@Test
	void resolvesMirrorAsExpected() {
		assertSame(mirrorAddress, subject.resolveForEvm(mirrorAddress));
	}

	@Test
	void doesntSupportTransactionalSemantics() {
		assertThrows(UnsupportedOperationException.class, () -> subject.commit(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.filterPendingChanges(null));
		assertThrows(UnsupportedOperationException.class, subject::revert);
	}

	@Test
	void canLinkAndUnlinkAddresses() {
		subject.link(nonMirrorAddress, mirrorAddress);
		assertEquals(Map.of(ByteString.copyFrom(nonMirrorAddress.toArrayUnsafe()), num), subject.getAliases());

		subject.unlink(nonMirrorAddress);
		assertEquals(Collections.emptyMap(), subject.getAliases());
	}

	@Test
	void createAliasAddsToMap() {
		subject.link(alias, num);

		assertEquals(Map.of(alias, num), subject.getAliases());
	}

	@Test
	void forgetReturnsExpectedValues() {
		final var unusedAlias = ByteString.copyFromUtf8("bbb");
		aliases.put(alias, num);
		assertFalse(subject.forgetAlias(ByteString.EMPTY));
		assertFalse(subject.forgetAlias(unusedAlias));
		assertTrue(subject.forgetAlias(alias));
	}

	@Test
	void forgetAliasRemovesFromMap() {
		subject.getAliases().put(alias, num);

		subject.unlink(alias);

		assertFalse(subject.getAliases().containsKey(alias));
	}

	@Test
	void isAliasChecksForMapMembershipOnly() {
		assertFalse(subject.isInUse(nonMirrorAddress));
		subject.link(nonMirrorAddress, mirrorAddress);
		assertTrue(subject.isInUse(nonMirrorAddress));
	}

	@Test
	void rebuildsFromMap() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));

		final var withNum = EntityNum.fromLong(1L);
		final var withoutNum = EntityNum.fromLong(2L);
		final var contractNum = EntityNum.fromLong(3L);
		final var expiredAlias = ByteString.copyFromUtf8("zyxwvut");
		final var upToDateAlias = ByteString.copyFromUtf8("abcdefg");
		final var contractAlias = ByteString.copyFrom(rawNonMirrorAddress);

		final var accountWithAlias = new MerkleAccount();
		accountWithAlias.setAlias(upToDateAlias);
		final var accountWithNoAlias = new MerkleAccount();
		final var contractAccount = new MerkleAccount();
		contractAccount.setSmartContract(true);
		contractAccount.setAlias(contractAlias);

		final MerkleMap<EntityNum, MerkleAccount> liveAccounts = new MerkleMap<>();
		liveAccounts.put(withNum, accountWithAlias);
		liveAccounts.put(withoutNum, accountWithNoAlias);
		liveAccounts.put(contractNum, contractAccount);

		subject.getAliases().put(expiredAlias, withoutNum);
		subject.rebuildAliasesMap(liveAccounts);

		final var finalMap = subject.getAliases();
		assertEquals(2, finalMap.size());
		assertEquals(withNum, subject.getAliases().get(upToDateAlias));

		// finally when
		subject.forgetAlias(accountWithAlias.getAlias());
		assertEquals(1, subject.getAliases().size());
	}
}
