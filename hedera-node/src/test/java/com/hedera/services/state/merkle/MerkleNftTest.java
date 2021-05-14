/*
 * Hedera Services Node
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.state.merkle;

import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.time.Instant;

import static com.hedera.services.state.merkle.MerkleTopic.serdes;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class MerkleNftTest {

	private MerkleNft subject;

	private EntityId owner;
	private EntityId otherOwner;
	private String memo;
	private String otherMemo;
	private Instant timestamp;
	private Instant otherTimestamp;

	@BeforeEach
	public void setup() {
		owner = new EntityId(1, 2, 3);
		otherOwner = new EntityId(1, 2, 4);
		memo = "Test NFT";
		otherMemo = "Test NFT2";
		timestamp = Instant.ofEpochSecond(1_234_567L);
		otherTimestamp = Instant.ofEpochSecond(1_234_568L);

		subject = new MerkleNft(owner, memo, RichInstant.fromJava(timestamp));

		serdes = mock(DomainSerdes.class);
		MerkleNft.serdes = serdes;
	}

	@AfterEach
	public void cleanup() {
		MerkleToken.serdes = new DomainSerdes();
	}

	@Test
	public void equalsContractWorks() {
		// given
		var other = new MerkleNft(owner, memo, RichInstant.fromJava(otherTimestamp));
		var other2 = new MerkleNft(owner, otherMemo, RichInstant.fromJava(timestamp));
		var other3 = new MerkleNft(otherOwner, memo, RichInstant.fromJava(timestamp));
		var identical = new MerkleNft(owner, memo, RichInstant.fromJava(timestamp));

		// expect
		assertNotEquals(subject, other);
		assertNotEquals(subject, other2);
		assertNotEquals(subject, other3);
		assertEquals(subject, identical);
	}

	@Test
	public void hashCodeWorks() {
		// given:
		var identical = new MerkleNft(owner, memo, RichInstant.fromJava(timestamp));
		var other = new MerkleNft(otherOwner, otherMemo, RichInstant.fromJava(otherTimestamp));

		// expect:
		assertNotEquals(subject.hashCode(), other.hashCode());
		assertEquals(subject.hashCode(), identical.hashCode());
	}

	@Test
	public void toStringWorks() {
		// given:
		assertEquals("MerkleNft{" +
						"owner=" + owner + ", " +
						"creationTime=" + RichInstant.fromJava(timestamp) + ", " +
						"memo=" + memo + "}",
				subject.toString());
	}

	@Test
	public void copyWorks() {
		// given:
		var copyNft = subject.copy();
		var other = new Object();

		// expect:
		assertNotSame(copyNft, subject);
		assertEquals(subject, copyNft);
		assertEquals(subject, subject);
		assertNotEquals(subject, other);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = inOrder(serdes, out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).writeSerializable(owner, true);
		inOrder.verify(serdes).serializeTimestamp(RichInstant.fromJava(timestamp), out);
		inOrder.verify(out).writeNormalisedString(memo);

	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream in = mock(SerializableDataInputStream.class);

		given(in.readSerializable()).willReturn(owner);
		given(serdes.deserializeTimestamp(in)).willReturn(RichInstant.fromJava(timestamp));
		given(in.readNormalisedString(anyInt())).willReturn(memo);

		// and:
		var read = new MerkleNft();

		// when:
		read.deserialize(in, MerkleNft.MERKLE_VERSION);

		// then:
		assertEquals(subject, read);
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleNft.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleNft.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}
}
