/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.state.submerkle;

import com.hedera.services.utils.EntityNum;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class FcAllowanceIdTest {
	private EntityNum tokenNum = EntityNum.fromLong(1L);
	private EntityNum spenderNum = EntityNum.fromLong(2L);

	private FcAllowanceId subject;

	@BeforeEach
	void setup() {
		subject = new FcAllowanceId(tokenNum, spenderNum);
	}

	@Test
	void objectContractWorks() {
		final var one = subject;
		final var two = new FcAllowanceId(EntityNum.fromLong(3L), EntityNum.fromLong(4L));
		final var three = new FcAllowanceId(EntityNum.fromLong(1L), EntityNum.fromLong(2L));

		assertNotEquals(null, one);
		assertNotEquals(new Object(), one);
		assertNotEquals(two, one);
		assertEquals(one, three);

		assertEquals(one.hashCode(), three.hashCode());
		assertNotEquals(one.hashCode(), two.hashCode());
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"FcAllowanceId{tokenNum=" + tokenNum.longValue() + ", spenderNum=" + spenderNum.longValue() + "}",
				subject.toString());
	}

	@Test
	void gettersWork() {
		assertEquals(1L, subject.getTokenNum().longValue());
		assertEquals(2L, subject.getSpenderNum().longValue());
	}

	@Test
	void deserializeWorks() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var newSubject = new FcAllowanceId();
		given(in.readLong())
				.willReturn(tokenNum.longValue())
				.willReturn(spenderNum.longValue());

		newSubject.deserialize(in, FcAllowanceId.CURRENT_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeLong(tokenNum.longValue());
		inOrder.verify(out).writeLong(spenderNum.longValue());
	}

	@Test
	void serializableDetWorks() {
		assertEquals(FcAllowanceId.RELEASE_023X_VERSION, subject.getVersion());
		assertEquals(FcAllowanceId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}
}
