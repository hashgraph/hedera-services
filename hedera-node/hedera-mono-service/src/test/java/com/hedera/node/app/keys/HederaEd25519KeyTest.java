/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.node.app.keys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HederaEd25519KeyTest {
	private static final int ED25519_BYTE_LENGTH = 32;
	private HederaEd25519Key subject;

	@Test
	void nullInEd25519KeyConstructorIsNotAllowed() {
		assertThrows(NullPointerException.class, () -> new HederaEd25519Key((byte[]) null));
	}

	@Test
	void emptyEd25519KeyIsAllowed() {
		subject = new HederaEd25519Key(new byte[0]);
		assertTrue(subject.isEmpty());
		assertFalse(subject.isValid());
	}

	@Test
	void invalidEd25519KeyFails() {
		subject = new HederaEd25519Key(new byte[1]);
		assertFalse(subject.isEmpty());
		assertFalse(subject.isValid());

		subject = new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH - 1]);
		assertFalse(subject.isEmpty());
		assertFalse(subject.isValid());
	}

	@Test
	void validEd25519KeyWorks() {
		subject = new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH]);
		assertFalse(subject.isEmpty());
		assertTrue(subject.isValid());
	}
	@Test
	void testsIsPrimitive(){
		subject = new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH]);
		assertTrue(subject.isPrimitive());
	}

	@Test
	void copyAndAsReadOnlyWorks(){
		subject = new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH]);
		final var copy = subject.copy();
		assertEquals(subject, copy);
		assertNotSame(subject, copy);
	}

	@Test
	void equalsAndHashCodeWorks() {
		HederaEd25519Key key1 = new HederaEd25519Key("firstKey".getBytes());
		HederaEd25519Key key2 = new HederaEd25519Key("secondKey".getBytes());
		HederaEd25519Key key3 = new HederaEd25519Key("firstKey".getBytes());

		assertNotEquals(key1, key2);
		assertNotEquals(key1.hashCode(), key2.hashCode());
		assertEquals(key1, key3);
		assertEquals(key1.hashCode(), key3.hashCode());
		assertEquals(key1, key1);
		assertFalse(key1.equals(null));
	}

	@Test
	void toStringWorks(){
		subject = new HederaEd25519Key("firstKey".getBytes());
		final var expectedString = "Ed25519Key[key=66697273744b6579]";
		assertEquals(expectedString, subject.toString());
	}
}
