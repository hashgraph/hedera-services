package com.hedera.services.state.merkle.internals;

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

import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FilePartTest {
	private static final byte[] SOME_DATA = "abcdefgh".getBytes(StandardCharsets.UTF_8);

	private FilePart subject;

	@Test
	void metaAsExpected() {
		subject = new FilePart();
		assertEquals(1, subject.getVersion());
		assertEquals(0xd1b1fc6b87447a02L, subject.getClassId());
	}

	@Test
	void copyReturnsSelf() {
		subject = new FilePart(SOME_DATA);
		assertSame(subject, subject.copy());
	}

	@Test
	void canManageHash() {
		subject = new FilePart(SOME_DATA);

		final var literal = CommonUtils.noThrowSha384HashOf(SOME_DATA);
		final var hash = new Hash(literal, DigestType.SHA_384);

		subject.setHash(hash);

		assertSame(hash, subject.getHash());
	}

	@Test
	void liveFireSerdeWorksWithNonEmpty() throws IOException {
		final var baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);
		final var sha384 =

		subject = new FilePart(SOME_DATA);

		subject.serialize(dos);
		dos.flush();
		final var bytes = baos.toByteArray();
		final var bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		final var newSubject = new FilePart();
		newSubject.deserialize(din, 1);

		assertArrayEquals(subject.getData(), newSubject.getData());
	}
}
