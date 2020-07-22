package com.hedera.services.legacy.initialization;

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

import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcmap.internal.FCMInternalNode;
import com.swirlds.fcmap.internal.FCMLeaf;
import com.swirlds.fcmap.internal.FCMTree;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class NodeAccountsCreationTest {
	static String FAKE_GENESIS_KEYS_IN_LOC = "src/test/resources/genesisKey/LegacyStartUpAccount.txt";

	static String FAKE_PUBLIC_KEY_OUT_LOC = "src/test/resources/genesisKey/legacyPublicKeyOut.txt";
	static String FAKE_PRIVATE_KEY_OUT_LOC = "src/test/resources/genesisKey/legacyPrivateKeyOut.txt";
	static String FAKE_PUBLIC_KEY_A_OUT_LOC = "src/test/resources/genesisKey/legacyPublicKeyAOut.txt";

	private static final String LEGACY_ACCOUNTS_LOC = "src/test/resources/legacyBootstrapAccounts.mrk";

	NodeAccountsCreation subject = new NodeAccountsCreation();

	@Test
	public void reproducesLegacyBehavior() throws Exception {
		// setup:
		var node0Address = mock(Address.class);
		var node1Address = mock(Address.class);
		var node2Address = mock(Address.class);
		var book = mock(AddressBook.class);
		// and:
		FCMap<MerkleEntityId, MerkleAccount> pristine = new FCMap<>(
				new MerkleEntityId.Provider(),
				MerkleAccount.LEGACY_PROVIDER);
		// and:
		var merkleIn = new MerkleDataInputStream(
				Files.newInputStream(Paths.get(LEGACY_ACCOUNTS_LOC)), false);
		FCMap<MerkleEntityId, MerkleAccount> expected = merkleIn.readMerkleTree(1_234_567);
		CryptoFactory.getInstance().digestTreeSync(expected);

		given(node0Address.getMemo()).willReturn("0.0.3");
		given(node1Address.getMemo()).willReturn("0.0.4");
		given(node2Address.getMemo()).willReturn("0.0.5");
		given(book.getSize()).willReturn(3);
		given(book.getAddress(0)).willReturn(node0Address);
		given(book.getAddress(1)).willReturn(node1Address);
		given(book.getAddress(2)).willReturn(node2Address);

		// when:
		subject.initializeNodeAccounts(book, pristine);
		// and:
		CryptoFactory.getInstance().digestTreeSync(pristine);

		// then:
		Assertions.assertEquals(expected, pristine);
	}

	@BeforeAll
	public static void setupAll() throws ConstructableRegistryException {
		NodeAccountsCreation.GEN_ACCOUNT_PATH = FAKE_GENESIS_KEYS_IN_LOC;
		NodeAccountsCreation.GEN_PRIV_KEY_PATH = FAKE_PRIVATE_KEY_OUT_LOC;
		NodeAccountsCreation.GEN_PUB_KEY_PATH = FAKE_PUBLIC_KEY_OUT_LOC;
		NodeAccountsCreation.GEN_PUB_KEY_32BYTES_PATH = FAKE_PUBLIC_KEY_A_OUT_LOC;

		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMInternalNode.class, FCMInternalNode::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleLong.class, MerkleLong::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCQueue.class, FCQueue::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMap.class, FCMap::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMTree.class, FCMTree::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMLeaf.class, FCMLeaf::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleEntityId.class, MerkleEntityId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccountState.class, MerkleAccountState::new));
	}

	@AfterAll
	public static void cleanupAll() {
		NodeAccountsCreation.GEN_ACCOUNT_PATH = PropertiesLoader.getGenAccountPath();
		NodeAccountsCreation.GEN_PRIV_KEY_PATH = PropertiesLoader.getGenPrivKeyPath();
		NodeAccountsCreation.GEN_PUB_KEY_PATH = PropertiesLoader.getGenPubKeyPath();
		NodeAccountsCreation.GEN_PUB_KEY_32BYTES_PATH = PropertiesLoader.getGenPub32KeyPath();

		var f1 = new File(FAKE_PUBLIC_KEY_OUT_LOC);
		if (f1.exists()) {
			f1.delete();
		}
		var f2 = new File(FAKE_PRIVATE_KEY_OUT_LOC);
		if (f2.exists()) {
			f2.delete();
		}
		var f3 = new File(FAKE_PUBLIC_KEY_A_OUT_LOC);
		if (f3.exists()) {
			f3.delete();
		}
	}
}