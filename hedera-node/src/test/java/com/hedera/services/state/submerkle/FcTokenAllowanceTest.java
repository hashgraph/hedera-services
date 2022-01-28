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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class FcTokenAllowanceTest {
	private Long allowance = 100L;
	private boolean approvedForAll = true;
	private List<Long> serialNums = List.of(1L, 2L);
	private FcTokenAllowance subject;

	@BeforeEach
	void setup() {
		subject = FcTokenAllowance.from(allowance, approvedForAll, serialNums);
	}

	@Test
	void objectContractWorks() {
		final var one = subject;
		final var two = FcTokenAllowance.from(3L, false, new ArrayList<>());
		final var three = FcTokenAllowance.from(100L, true, serialNums);

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
				"FcTokenAllowance{allowance=" + allowance.longValue() + ", approvedForAll="
						+ approvedForAll + ", serialNumbers=" + serialNums + "}",
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
		final var newSubject = new FcTokenAllowance();
		given(in.readLong()).willReturn(allowance);
		given(in.readBoolean()).willReturn(allowance != null).willReturn(approvedForAll);
		given(in.readLongList(Integer.MAX_VALUE)).willReturn(serialNums);

		newSubject.deserialize(in, FcTokenAllowance.CURRENT_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeBoolean(serialNums != null);
		inOrder.verify(out).writeLong(allowance);
		inOrder.verify(out).writeBoolean(approvedForAll);
		inOrder.verify(out).writeLongList(serialNums);
	}

	@Test
	void serializeWorksWithAllowanceSet() throws IOException {
		final var subject = FcTokenAllowance.from(10L);
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeBoolean(false);
		inOrder.verify(out).writeLong(10L);
		inOrder.verify(out).writeBoolean(false);
	}

	@Test
	void deserializeWorksWithAllowanceSet() throws IOException {
		final var subject = FcTokenAllowance.from(10L);
		final var in = mock(SerializableDataInputStream.class);
		final var newSubject = new FcTokenAllowance();
		given(in.readLong()).willReturn(10L);
		given(in.readBoolean()).willReturn(false).willReturn(false);

		newSubject.deserialize(in, FcTokenAllowance.CURRENT_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void serializeWorksWithApprovalSet() throws IOException {
		final var subject = FcTokenAllowance.from(true);
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(out).writeLong(0L);
		inOrder.verify(out).writeBoolean(false);
	}

	@Test
	void deserializeWorksWithApprovalSet() throws IOException {
		final var subject = FcTokenAllowance.from(true);
		final var in = mock(SerializableDataInputStream.class);
		final var newSubject = new FcTokenAllowance();
		given(in.readBoolean()).willReturn(true).willReturn(false);
		given(in.readLong()).willReturn(0L);

		newSubject.deserialize(in, FcTokenAllowance.CURRENT_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void serializeWorksWithSerialNumsSet() throws IOException {
		final var subject = FcTokenAllowance.from(serialNums);
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeBoolean(false);
		inOrder.verify(out).writeLong(0L);
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(out).writeLongList(serialNums);
	}

	@Test
	void deserializeWorksWithSerialNumsSet() throws IOException {
		final var subject = FcTokenAllowance.from(serialNums);
		final var in = mock(SerializableDataInputStream.class);
		final var newSubject = new FcTokenAllowance();
		given(in.readBoolean()).willReturn(false).willReturn(true);
		given(in.readLong()).willReturn(0L);
		given(in.readLongList(Integer.MAX_VALUE)).willReturn(serialNums);

		newSubject.deserialize(in, FcTokenAllowance.CURRENT_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void constructorWorks() {
		final var withAllowance = FcTokenAllowance.from(3L);
		final var withApprovalForAll = FcTokenAllowance.from(true);
		final var withSerials = FcTokenAllowance.from(List.of(1L, 2L));

		assertEquals(0, withApprovalForAll.getAllowance());
		assertEquals(true, withApprovalForAll.isApprovedForAll());
		assertEquals(null, withAllowance.getSerialNumbers());


		assertEquals(3L, withAllowance.getAllowance());
		assertEquals(false, withAllowance.isApprovedForAll());
		assertEquals(null, withAllowance.getSerialNumbers());

		assertEquals(List.of(1L, 2L), withSerials.getSerialNumbers());
		assertEquals(false, withSerials.isApprovedForAll());
		assertEquals(0, withSerials.getAllowance());
	}

	@Test
	void serializableDetWorks() {
		assertEquals(FcTokenAllowance.RELEASE_023X_VERSION, subject.getVersion());
		assertEquals(FcTokenAllowance.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}
}
