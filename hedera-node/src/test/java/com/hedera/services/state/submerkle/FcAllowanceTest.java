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

public class FcAllowanceTest {
	private Long allowance = 100L;
	private boolean approvedForAll = true;
	private FcAllowance subject;

	@BeforeEach
	void setup() {
		subject = new FcAllowance(allowance, approvedForAll);
	}

	@Test
	void objectContractWorks() {
		final var one = subject;
		final var two = new FcAllowance(3L, false);
		final var three = new FcAllowance(100L, true);

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
				"FcAllowance{allowance=" + allowance.longValue() + ", approvedForAll=" + approvedForAll + "}",
				subject.toString());
	}

	@Test
	void gettersWork() {
		assertEquals(100L, subject.getAllowance());
		assertEquals(true, subject.isApprovedForAll());
	}

	@Test
	void deserializeWorksForBothSet() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var newSubject = new FcAllowance();
		given(in.readLong()).willReturn(allowance);
		given(in.readBoolean()).willReturn(allowance != null).willReturn(approvedForAll);

		newSubject.deserialize(in, FcAllowance.CURRENT_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeBoolean(allowance != null);
		inOrder.verify(out).writeLong(allowance);
		inOrder.verify(out).writeBoolean(approvedForAll);
	}

	@Test
	void serializeWorksWithAllowanceSet() throws IOException {
		final var subject = new FcAllowance(10L);
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(out).writeLong(10L);
		inOrder.verify(out).writeBoolean(false);
	}

	@Test
	void deserializeWorksWithAllowanceSet() throws IOException {
		final var subject = new FcAllowance(10L);
		final var in = mock(SerializableDataInputStream.class);
		final var newSubject = new FcAllowance();
		given(in.readLong()).willReturn(10L);
		given(in.readBoolean()).willReturn(true).willReturn(false);

		newSubject.deserialize(in, FcAllowance.CURRENT_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void serializeWorksWithApprovalSet() throws IOException {
		final var subject = new FcAllowance(true);
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeBoolean(false);
		inOrder.verify(out).writeBoolean(true);
	}

	@Test
	void deserializeWorksWithApprovalSet() throws IOException {
		final var subject = new FcAllowance(true);
		final var in = mock(SerializableDataInputStream.class);
		final var newSubject = new FcAllowance();
		given(in.readBoolean()).willReturn(false).willReturn(true);

		newSubject.deserialize(in, FcAllowance.CURRENT_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void constructorWorks() {
		final var noApprovalForAll = new FcAllowance(3L);
		final var noAllowance = new FcAllowance(true);

		assertEquals(null, noAllowance.getAllowance());
		assertEquals(true, noAllowance.isApprovedForAll());


		assertEquals(3L, noApprovalForAll.getAllowance());
		assertEquals(false, noApprovalForAll.isApprovedForAll());
	}

	@Test
	void serializableDetWorks() {
		assertEquals(FcAllowance.RELEASE_023X_VERSION, subject.getVersion());
		assertEquals(FcAllowance.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}
}
