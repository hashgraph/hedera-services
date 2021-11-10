package com.hedera.services.legacy.core.jproto;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JECDSAsecp256k1KeyTest {
	JECDSAsecp256k1Key subject;
	byte[] bytes;

	@BeforeEach
	void setUp() {
		bytes = new byte[33];
		bytes[0] = 0x03;
		subject = new JECDSAsecp256k1Key(bytes);
	}

	@Test
	void emptyJECDSAsecp256k1KeyTest() {
		JECDSAsecp256k1Key key1 = new JECDSAsecp256k1Key(null);
		assertTrue(key1.isEmpty());
		assertFalse(key1.isValid());

		JECDSAsecp256k1Key key2 = new JECDSAsecp256k1Key(new byte[0]);
		assertTrue(key2.isEmpty());
		assertFalse(key2.isValid());
	}

	@Test
	void nonEmptyInvalidLengthJECDSAsecp256k1KeyTest() {
		JECDSAsecp256k1Key key = new JECDSAsecp256k1Key(new byte[1]);
		assertFalse(key.isEmpty());
		assertFalse(key.isValid());
	}

	@Test
	void nonEmptyValid0x02JECDSAsecp256k1KeyTest() {
		byte[] bytes = new byte[33];
		bytes[0] = 0x02;
		JECDSAsecp256k1Key key = new JECDSAsecp256k1Key(bytes);
		assertFalse(key.isEmpty());
		assertTrue(key.isValid());
	}

	@Test
	void nonEmptyValid0x03JECDSAsecp256k1KeyTest() {
		assertFalse(subject.isEmpty());
		assertTrue(subject.isValid());
	}

	@Test
	void constructorWorks() {
		var bytes = new byte[33];
		bytes[0] = 0x03;
		var subject = new JECDSAsecp256k1Key(bytes);

		assertEquals(bytes, subject.getECDSASecp256k1Key());
	}

	@Test
	void getterWorks() {
		assertEquals(bytes, subject.getECDSASecp256k1Key());
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"<JECDSAsecp256k1Key: ecdsaSecp256k1Key " +
						"hex=030000000000000000000000000000000000000000000000000000000000000000>",
				subject.toString());
	}
}
