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

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.legacy.core.jproto.JKeyUtils.genSampleComplexKey;
import static com.hedera.services.legacy.core.jproto.JKeyUtils.genSingleECDSASecp256k1Key;
import static com.hedera.services.legacy.core.jproto.JKeyUtils.getSpecificJKeysMade;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class JKeySerializerTest {
	@Test
	void throwsOnNullObjectType() {
		assertThrows(IllegalStateException.class,
				() -> JKeySerializer.pack(null, null, null));
		assertThrows(IllegalStateException.class,
				() -> JKeySerializer.unpack(null, null, 0));
	}

	@Test
	void throwsAsExpectedWhenDeserializingLegacyVersions() throws IOException {
		final var in = mock(DataInputStream.class);
		given(in.readLong()).willReturn(1L);
		assertThrows(IllegalArgumentException.class, () -> JKeySerializer.deserialize(in));
	}

	@Test
	void throwsAsExpectedWhenDeserializingIllegalKeyType() throws IOException {
		final var in = mock(DataInputStream.class);
		given(in.readLong()).willReturn(2L);
		assertThrows(IllegalStateException.class, () -> JKeySerializer.deserialize(in));
	}

	@Test
	void canSerializeAndDeserializeJEcdsa384Key() throws IOException {
		final var mockPk = "NONSENSE".getBytes();
		final var subject = new JECDSA_384Key(mockPk);
		final var baos = new ByteArrayOutputStream();
		baos.write(JKeySerializer.serialize(subject));
		baos.flush();

		final var in = new ByteArrayInputStream(baos.toByteArray());
		final JECDSA_384Key result = JKeySerializer.deserialize(new DataInputStream(in));
		assertArrayEquals(subject.getECDSA384(), result.getECDSA384());
	}

	@Test
	void canSerializeAndDeserializeJDelegateContractIdKey() throws IOException {
		final var subject = new JDelegatableContractIDKey(IdUtils.asContract("1.2.3"));
		final var baos = new ByteArrayOutputStream();
		baos.write(JKeySerializer.serialize(subject));
		baos.flush();

		final var in = new ByteArrayInputStream(baos.toByteArray());
		final JDelegatableContractIDKey result = JKeySerializer.deserialize(new DataInputStream(in));
		assertEquals(subject.getContractID(), result.getContractID());
	}

	@Test
	void canSerializeAndDeserializeJContractIdKey() throws IOException {
		final var subject = new JDelegatableContractIDKey(IdUtils.asContract("1.2.3"));
		final var baos = new ByteArrayOutputStream();
		baos.write(JKeySerializer.serialize(subject));
		baos.flush();

		final var in = new ByteArrayInputStream(baos.toByteArray());
		final JDelegatableContractIDKey result = JKeySerializer.deserialize(new DataInputStream(in));
		assertEquals(subject.getContractID(), result.getContractID());
	}

	@Test
	void jThresholdSerDes() throws IOException {
		final var threshold = getSpecificJKeysMade("JThresholdKey", 3, 3);
		final var beforeKeyList = threshold.getThresholdKey().getKeys();
		final var beforeJKeyListSize = beforeKeyList.getKeysList().size();
		byte[] serializedThresholdKey = threshold.serialize();

		assertNotNull(serializedThresholdKey);
		// Now take the bytearray and build it back

		try (final var in = new ByteArrayInputStream(serializedThresholdKey);
			 final var dis = new DataInputStream(in)
		) {
			final JKey jKeyReborn = JKeySerializer.deserialize(dis);
			assertAll("JKeyRebornChecks1",
					() -> assertNotNull(jKeyReborn),
					() -> assertTrue(jKeyReborn instanceof JThresholdKey),
					() -> assertTrue(jKeyReborn.hasThresholdKey()),
					() -> assertEquals(3, jKeyReborn.getThresholdKey().getThreshold())
			);

			final var afterJKeysList = jKeyReborn.getThresholdKey().getKeys();
			assertAll("JKeyRebornChecks2",
					() -> assertNotNull(afterJKeysList),
					() -> assertNotNull(afterJKeysList.getKeysList())
			);

			final int afterJKeysListSize = afterJKeysList.getKeysList().size();
			assertAll("JKeyRebornChecks2",
					() -> assertEquals(beforeJKeyListSize, afterJKeysListSize));
		} catch (Exception e) {
			throw new IllegalStateException(String.format("Failed to deserialize!", e));
		}
	}

	@Test
	void jKeyListSerDes() throws IOException {
		final var jKeyList = getSpecificJKeysMade("JKeyList", 3, 3);
		final var beforeJKeyListSize = jKeyList.getKeyList().getKeysList().size();

		byte[] serializedJKey = jKeyList.serialize();

		assertNotNull(serializedJKey);

		try (final var in = new ByteArrayInputStream(serializedJKey);
			 final var dis = new DataInputStream(in)
		) {
			final JKey jKeyReborn = JKeySerializer.deserialize(dis);
			//Write Assertions Here
			assertAll("JKeyRebornChecks1",
					() -> assertNotNull(jKeyReborn),
					() -> assertTrue(jKeyReborn instanceof JKeyList),
					() -> assertTrue(jKeyReborn.hasKeyList()),
					() -> assertFalse(jKeyReborn.hasThresholdKey())
			);

			final var afterJKeysList = jKeyReborn.getKeyList();
			assertAll("JKeyRebornChecks2",
					() -> assertNotNull(afterJKeysList),
					() -> assertNotNull(afterJKeysList.getKeysList())
			);

			final var afterJKeysListSize = afterJKeysList.getKeysList().size();
			assertAll("JKeyRebornChecks2",
					() -> assertEquals(beforeJKeyListSize, afterJKeysListSize));
		} catch (Exception e) {
			throw new IllegalStateException(String.format("Failed to deserialize!", e));
		}
	}


	@Test
	void jKeyProtoSerDes() throws IOException {
		final Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		Key protoKey;
		JKey jkey = null;
		List<JKey> jListBefore = null;
		//Jkey will have JEd25519Key,JECDSASecp256K1Key,JThresholdKey,JKeyList
		try {
			protoKey = genSampleComplexKey(2, pubKey2privKeyMap);
			jkey = JKey.mapKey(protoKey);
			jListBefore = jkey.getKeyList().getKeysList();

		} catch (DecoderException ignore) {
		}

		byte[] serializedJKey = jkey.serialize();

		try (final var in = new ByteArrayInputStream(serializedJKey);
			 final var dis = new DataInputStream(in)
		) {
			final JKey jKeyReborn = JKeySerializer.deserialize(dis);
			//Write Top Assertions Here
			assertAll("JKeyRebornChecks-Top Level",
					() -> assertNotNull(jKeyReborn),
					() -> assertTrue(jKeyReborn instanceof JKeyList),
					() -> assertTrue(jKeyReborn.hasKeyList()),
					() -> assertFalse(jKeyReborn.hasThresholdKey())
			);

			final var jListAfter = jKeyReborn.getKeyList().getKeysList();
			assertEquals(jListBefore.size(), jListAfter.size());
			for (int i = 0; i < jListBefore.size(); i++) {
				assertTrue(equalUpToDecodability(jListBefore.get(i), jListAfter.get(i)));
			}
		} catch (Exception e) {
			throw new IllegalStateException(String.format("Failed to deserialize!", e));
		}
	}

	@Test
	void jKeyECDSASecp256k1KeySerDes() throws Exception {
		final Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		Key protoKey;
		protoKey = genSingleECDSASecp256k1Key(pubKey2privKeyMap);
		JKey jkey = JKey.mapKey(protoKey);
		byte[] serializedJKey = null;
		try {
			serializedJKey = jkey.serialize();
		} catch (IOException ignore) {
		}

		try (final var in = new ByteArrayInputStream(serializedJKey);
			 final var dis = new DataInputStream(in)
		) {
			final JKey jKeyReborn = JKeySerializer.deserialize(dis);
			assertAll("JKeyRebornChecks-Top Level",
					() -> assertNotNull(jKeyReborn),
					() -> assertTrue(jKeyReborn instanceof JECDSASecp256k1Key),
					() -> assertFalse(jKeyReborn.hasKeyList()),
					() -> assertFalse(jKeyReborn.hasThresholdKey())
			);
		} catch (Exception e) {
			throw new IllegalStateException(String.format("Failed to deserialize!", e));
		}
	}
}
