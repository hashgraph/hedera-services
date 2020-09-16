package com.hedera.services.keys;

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

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.test.factories.accounts.MapValueFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.services.legacy.core.jproto.JKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.MatcherAssert.assertThat;
import static com.hedera.test.factories.keys.NodeFactory.*;

@RunWith(JUnitPlatform.class)
public class HederaKeyTraversalTest {
	static KeyTree kt;

	@BeforeAll
	private static void setupAll() {
		kt = KeyTree.withRoot(
				list(
						ed25519(),
						threshold(1, list(list(ed25519(), ed25519()), ed25519()), ed25519()),
						ed25519(),
						list(threshold(2, ed25519(), ed25519(), ed25519(), ed25519()))
				)
		);
	}

	@Test
	public void visitsAllSimpleKeys() throws Exception {
		// given:
		JKey jKey = kt.asJKey();
		List<ByteString> expectedEd25519 = ed25519KeysFromKt(kt);

		// when:
		List<ByteString> visitedEd25519 = new ArrayList<>();
		HederaKeyTraversal.visitSimpleKeys(jKey, simpleJKey ->
				visitedEd25519.add(ByteString.copyFrom(simpleJKey.getEd25519())));

		// expect:
		assertThat(visitedEd25519, contains(expectedEd25519.toArray(new ByteString[0])));
	}

	@Test
	public void countsSimpleKeys() throws Exception {
		// given:
		JKey jKey = kt.asJKey();

		// expect:
		assertEquals(10, HederaKeyTraversal.numSimpleKeys(jKey));
	}

	@Test
	public void countsSimpleKeysForValidAccount() throws Exception {
		// given:
		JKey jKey = kt.asJKey();
		MerkleAccount account = MapValueFactory.newAccount().accountKeys(jKey).get();

		// expect:
		assertEquals(10, HederaKeyTraversal.numSimpleKeys(account));
	}

	@Test
	public void countsZeroSimpleKeysForWeirdAccount() throws Exception {
		// given:
		JKey jKey = kt.asJKey();
		MerkleAccount account = MapValueFactory.newAccount().get();

		// expect:
		assertEquals(0, HederaKeyTraversal.numSimpleKeys(account));
	}


	private List<ByteString> ed25519KeysFromKt(KeyTree kt) {
		List<ByteString> keys = new ArrayList<>();
		kt.traverseLeaves(leaf -> keys.add(leaf.asKey().getEd25519()));
		return keys;
	}
}
