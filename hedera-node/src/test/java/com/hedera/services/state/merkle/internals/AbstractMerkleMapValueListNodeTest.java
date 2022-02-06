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
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AbstractMerkleMapValueListNodeTest {
	private static final EntityNum k = new EntityNum(123);
	private static final EntityNum n = new EntityNum(231);
	private static final EntityNum p = new EntityNum(321);

	private final SubjectListNode subject = new SubjectListNode();

	@Test
	void copyWorks() {
		givenWellKnownKeysAreSet();

		final SubjectListNode mimic = subject.copy();

		assertNotSame(subject.prevKey(), mimic.prevKey());
		assertNotSame(subject.getKey(), mimic.getKey());
		assertNotSame(subject.nextKey(), mimic.nextKey());
		assertEquals(subject.prevKey(), mimic.prevKey());
		assertEquals(subject.getKey(), mimic.getKey());
		assertEquals(subject.nextKey(), mimic.nextKey());

		assertSame(subject, mimic.thatCopied);
	}

	@Test
	void copyIsNullSafe() {
		final SubjectListNode mimic = subject.copy();

		assertNotSame(mimic, subject);
	}

	@Test
	void serializesAsExpectedWithWellKnownKeys() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);

		givenWellKnownKeysAreSet();
		subject.serialize(out);

		verify(out).writeLong(SubjectListNode.PRETEND_DATA);
		verify(out).writeSerializableList(List.of(p, k, n), true, true);
	}

	@Test
	void deserializesAsExpected() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		given(in.readLong()).willReturn(SubjectListNode.PRETEND_DATA);
		given(in.readSerializableList(3)).willReturn(List.of(p, k, n));

		final var model = new SubjectListNode();

		model.deserialize(in, 1);

		assertEquals(p, model.prevKey());
		assertEquals(k, model.getKey());
		assertEquals(n, model.nextKey());
		verify(in).readLong();
	}

	@Test
	void serializesAsExpectedWithNullKnownKeys() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		InOrder inOrder = Mockito.inOrder(out);

		subject.serialize(out);

		final List<EntityNum> expected = new ArrayList<>();
		expected.add(null);
		expected.add(null);
		expected.add(null);
		inOrder.verify(out).writeSerializableList(expected, true, true);

		inOrder.verify(out).writeLong(SubjectListNode.PRETEND_DATA);
	}

	@Test
	void canGetAndSetKeys() {
		givenWellKnownKeysAreSet();

		assertSame(k, subject.getKey());
		assertSame(p, subject.prevKey());
		assertSame(n, subject.nextKey());
	}

	private void givenWellKnownKeysAreSet() {
		subject.setKey(k);
		subject.setPrevKey(p);
		subject.setNextKey(n);
	}

	private static class SubjectListNode extends AbstractMerkleMapValueListNode<EntityNum, SubjectListNode> {
		public static final long PRETEND_DATA = 123L;

		private SubjectListNode thatCopied = null;

		@Override
		public void serializeValueTo(final SerializableDataOutputStream out) throws IOException {
			out.writeLong(PRETEND_DATA);
		}

		@Override
		public void deserializeValueFrom(final SerializableDataInputStream in, final int version) throws IOException {
			in.readLong();
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
		public SubjectListNode newValueCopyOf(SubjectListNode that) {
			final var valueCopy = new SubjectListNode();
			valueCopy.thatCopied = that;
			return valueCopy;
		}

		@Override
		public SubjectListNode self() {
			return this;
		}
	}
}
