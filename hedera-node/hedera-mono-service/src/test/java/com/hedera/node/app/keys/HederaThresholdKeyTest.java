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

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HederaThresholdKeyTest {
	private static final int ED25519_BYTE_LENGTH = 32;

	private final ByteString aPrimitiveKey = ByteString.copyFromUtf8("01234567890123456789012345678901");
	private int threshold = 1;
	private HederaEd25519Key k1;
	private HederaEd25519Key k2;
	private HederaKeyList keys;

	private HederaThresholdKey subject;

	@BeforeEach
	void setUp(){
		k1 = new HederaEd25519Key(aPrimitiveKey.toByteArray());
		k2 = new HederaEd25519Key(aPrimitiveKey.toByteArray());
		keys = new HederaKeyList(List.of(k1, k2));

		subject = new HederaThresholdKey(threshold, keys);
	}

	@Test
	void nullKeyListInConstructorIsNotAllowed() {
		assertThrows(NullPointerException.class, () -> new HederaThresholdKey( null));
	}

	@Test
	void emptyKeyListIsNotValid() {
		keys = new HederaKeyList(List.of());

		subject = new HederaThresholdKey(threshold, keys);
		assertTrue(subject.isEmpty());
		assertFalse(subject.isValid());
	}

	@Test
	void invalidKeyInKeyListFails() {
		k1 = new HederaEd25519Key(new byte[1]);
		k2 = new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH]);
		keys = new HederaKeyList(List.of(k1, k2));

		subject = new HederaThresholdKey(threshold, keys);
		assertFalse(subject.isEmpty());
		assertFalse(subject.isValid());

		k1 = new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH - 1]);
		keys = new HederaKeyList(List.of(k1, k2));
		subject = new HederaThresholdKey(threshold, keys);
		assertFalse(subject.isEmpty());
		assertFalse(subject.isValid());
	}

	@Test
	void validKeyInKeyListWorks() {
		assertFalse(subject.isEmpty());
		assertTrue(subject.isValid());
	}
	@Test
	void testsIsPrimitive(){
		subject = new HederaThresholdKey(threshold, keys);
		assertFalse(subject.isPrimitive());
	}

	@Test
	void copyAndAsReadOnlyWorks(){
		final var copy = subject.copy();
		assertEquals(subject, copy);
		assertNotSame(subject, copy);
	}

	@Test
	void equalsAndHashCodeWorks() {
		final var key1 = subject;
		final var key2 = new HederaThresholdKey(threshold,
				new HederaKeyList(List.of(k1, new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH]))));
		final var key3 = subject.copy();

		assertNotEquals(key1, key2);
		assertNotEquals(key1.hashCode(), key2.hashCode());
		assertEquals(key1, key3);
		assertEquals(key1.hashCode(), key3.hashCode());
		assertEquals(key1, key1);
		assertFalse(key1.equals(null));
	}

	@Test
	void toStringWorks(){
		final var expectedString = "HederaThresholdKey[threshold=1,keys=HederaKeyList[keys=[HederaEd25519Key[key=3031323334353637383930313233343536373839303132333435363738393031], HederaEd25519Key[key=3031323334353637383930313233343536373839303132333435363738393031]]]]";
		assertEquals(expectedString, subject.toString());
	}
}
