package com.hedera.services.state.merkle.internals;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.utils.EntityNum;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class BaseMapValueLinkedListTest {
	private static final int stringLen = 32;
	private static final int baseListSize = 64;
	private static final SplittableRandom r = new SplittableRandom();

	private MerkleMap<EntityNum, StringNode> host = new MerkleMap<>();

	private BaseMapValueLinkedList<EntityNum, StringNode, String> subject = new BaseMapValueLinkedList<>();

	@Test
	void canJustAppend() {
		final List<String> expected = new ArrayList<>();
		final List<EntityNum> expectedKeys = new ArrayList<>();
		for (int i = 1; i <= baseListSize; i++) {
			final var key = EntityNum.fromInt(i);
			addRandom(key, expected, expectedKeys);
		}

		assertExpected(expected, expectedKeys);
	}

	@Test
	void cannotAppendAtSizeLimit() {
		final var key = EntityNum.fromInt(123);
		final var node = randNode();
		addLast(key, node);

		final var badKey = EntityNum.fromInt(321);
		final var badNode = randNode();
		final var verdict = subject.addLast(badKey, badNode, 1, host::put, host::getForModify);
		assertFalse(verdict);
	}

	@Test
	void canRemoveOnlyItem() {
		final var key = EntityNum.fromInt(123);
		final var node = randNode();
		addLast(key, node);

		subject.remove(host.get(key), host::remove, host::get);

		assertSame(Collections.emptyList(), listedValues());
	}

	@Test
	void canRemoveFirstItem() {
		final var aKey = EntityNum.fromInt(123);
		final var aNode = randNode();
		addLast(aKey, aNode);
		final var bKey = EntityNum.fromInt(321);
		final var bNode = randNode();
		addLast(bKey, bNode);

		remove(host.get(aKey));

		assertExpected(List.of(bNode.getValue()), List.of(bKey));
		final var cKey = EntityNum.fromInt(666);
		final var cNode = randNode();
		addLast(cKey, cNode);
		assertExpected(List.of(bNode.getValue(), cNode.getValue()), List.of(bKey, cKey));
	}

	@Test
	void canRemoveLastItem() {
		final var aKey = EntityNum.fromInt(123);
		final var aNode = randNode();
		addLast(aKey, aNode);
		final var bKey = EntityNum.fromInt(321);
		final var bNode = randNode();
		addLast(bKey, bNode);

		remove(host.get(bKey));

		assertExpected(List.of(aNode.getValue()), List.of(aKey));
	}

	@Test
	void canPerformRandomValidOperations() {
		final List<String> expected = new ArrayList<>();
		final List<EntityNum> expectedKeys = new ArrayList<>();
		for (int i = 1; i <= 100 * baseListSize; i++) {
			if (r.nextBoolean()) {
				final var key = EntityNum.fromInt(i);
				addRandom(key, expected, expectedKeys);
			} else {
				if (!expected.isEmpty()) {
					final var n = expected.size();
					final var tbd = r.nextInt(n);
					expected.remove(tbd);
					final var tbdKey = expectedKeys.get(tbd);
					expectedKeys.remove(tbd);
					remove(host.get(tbdKey));
				}
			}
		}

		assertExpected(expected, expectedKeys);
	}


	@Test
	void canListEmptyList() {
		assertSame(Collections.emptyList(), listedValues());
	}

	private static class StringNode extends AbstractMerkleMapValueListNode<EntityNum, StringNode> {
		private String value;

		public StringNode() {
			/* RuntimeConstructable */
		}

		public StringNode(String value) {
			this.value = value;
		}

		public StringNode(StringNode that) {
			this.value = that.value;
		}

		public String getValue() {
			return value;
		}

		@Override
		public void serializeValueTo(final SerializableDataOutputStream out) throws IOException {
			out.writeNormalisedString(value);
		}

		@Override
		public void deserializeValueFrom(final SerializableDataInputStream in, final int version) throws IOException {
			value = in.readNormalisedString(Integer.MAX_VALUE);
		}

		@Override
		public long getClassId() {
			return 1L;
		}

		@Override
		public int getVersion() {
			return 1;
		}

		@Override
		public StringNode newValueCopyOf(StringNode that) {
			return new StringNode(that);
		}

		@Override
		public StringNode self() {
			return this;
		}
	}

	/* --- Internal helpers --- */
	private void remove(StringNode node) {
		subject.remove(node, host::remove, host::getForModify);
	}

	private void addLast(EntityNum k, StringNode node) {
		subject.addLast(k, node, Integer.MAX_VALUE, host::put, host::getForModify);
	}

	private List<String> listedValues() {
		return subject.listValues(host::get, StringNode::getValue);
	}

	private StringNode randNode() {
		final var data = new byte[stringLen / 2];
		r.nextBytes(data);
		return new StringNode(CommonUtils.hex(data));
	}

	private void addRandom(final EntityNum key, final List<String> expected, final List<EntityNum> expectedKeys) {
		final var node = randNode();
		addLast(key, node);
		expected.add(node.getValue());
		expectedKeys.add(key);
	}

	private void assertExpected(final List<String> expected, final List<EntityNum> expectedKeys) {
		final List<String> actual = listedValues();
		assertEquals(expected, actual);
		for (int i = 0, n = expectedKeys.size(); i < n; i++) {
			final var key = expectedKeys.get(i);
			assertEquals(expected.get(i), host.get(key).getValue());
		}
	}
}
