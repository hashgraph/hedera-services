package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JKeyListTest {
	@Test
	void emptyWaclSerdeWorks() throws IOException {
		// given:
		byte[] repr = JKeySerializer.serialize(StateView.EMPTY_WACL);

		// when:
		JKey recovered = JKeySerializer.deserialize(new DataInputStream(new ByteArrayInputStream(repr)));

		// then:
		assertTrue(recovered.isEmpty());
	}

	@Test
	public void defaultConstructor() {
		final var cut = new JKeyList();

		assertEquals(0, cut.getKeysList().size());
	}

	@Test
	public void isEmptySubkeys() {
		final var cut = new JKeyList(List.of(new JEd25519Key(new byte[0])));

		assertTrue(cut.isEmpty());
	}

	@Test
	public void isNotEmpty() {
		final var cut = new JKeyList(List.of(new JECDSA_384Key(new byte[1])));

		assertFalse(cut.isEmpty());
	}

	@Test
	public void invalidJKeyListTest() throws Exception {
		Key validED25519Key = Key.newBuilder().setEd25519(
				TxnUtils.randomUtf8ByteString(JEd25519Key.ED25519_BYTE_LENGTH)
		).build();
		KeyList invalidKeyList1 = KeyList.newBuilder().build();
		Key invalidKey1 = Key.newBuilder().setKeyList(invalidKeyList1).build();
		KeyList invalidKeyList2 = KeyList.newBuilder().addKeys(validED25519Key).addKeys(invalidKey1).build();
		Key invalidKey2 = Key.newBuilder().setKeyList(invalidKeyList2).build();
		KeyList invalidKeyList3 = KeyList.newBuilder().addKeys(validED25519Key).addKeys(invalidKey2).build();
		Key invalidKey3 = Key.newBuilder().setKeyList(invalidKeyList3).build();

		JKey jKeyList1 = JKey.convertKey(invalidKey1, 1);
		assertFalse(jKeyList1.isValid());

		JKey jKeyList2 = JKey.convertKey(invalidKey2, 1);
		assertFalse(jKeyList2.isValid());

		JKey jKeyList3 = JKey.convertKey(invalidKey3, 1);
		assertFalse(jKeyList3.isValid());
	}

	@Test
	public void validJKeyListTest() throws Exception {
		Key validED25519Key = Key.newBuilder().setEd25519(
				TxnUtils.randomUtf8ByteString(JEd25519Key.ED25519_BYTE_LENGTH)
		).build();
		Key validECDSA384Key = Key.newBuilder().setECDSA384(
				TxnUtils.randomUtf8ByteString(24)
		).build();
		KeyList validKeyList1 = KeyList.newBuilder().addKeys(validECDSA384Key).addKeys(validED25519Key).build();
		Key validKey1 = Key.newBuilder().setKeyList(validKeyList1).build();
		KeyList validKeyList2 = KeyList.newBuilder().addKeys(validED25519Key).addKeys(validKey1).build();
		Key validKey2 = Key.newBuilder().setKeyList(validKeyList2).build();
		KeyList validKeyList3 = KeyList.newBuilder().addKeys(validED25519Key).addKeys(validKey2).build();
		Key validKey3 = Key.newBuilder().setKeyList(validKeyList3).build();

		JKey jKeyList1 = JKey.convertKey(validKey1, 1);
		assertTrue(jKeyList1.isValid());

		JKey jKeyList2 = JKey.convertKey(validKey2, 1);
		assertTrue(jKeyList2.isValid());

		JKey jKeyList3 = JKey.convertKey(validKey3, 1);
		assertTrue(jKeyList3.isValid());
	}
}
