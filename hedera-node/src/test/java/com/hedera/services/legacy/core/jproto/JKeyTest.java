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

import com.google.protobuf.ByteString;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JKeyTest {
	@Test
	void positiveConvertKeyTest() {
		// given:
		final Key aKey = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();

		// expect:
		assertDoesNotThrow(() -> JKey.convertKey(aKey, 1));
	}

	@Test
	void negativeConvertKeyTest() {
		// given:
		var keyTooDeep = TxnUtils.nestKeys(Key.newBuilder(), JKey.MAX_KEY_DEPTH).build();

		// expect:
		assertThrows(
				DecoderException.class,
				() -> JKey.convertKey(keyTooDeep, 1),
				"Exceeding max expansion depth of " + JKey.MAX_KEY_DEPTH);
	}

	@Test
	void negativeConvertJKeyTest() {
		// given:
		var jKeyTooDeep = TxnUtils.nestJKeys(JKey.MAX_KEY_DEPTH);

		// expect:
		assertThrows(
				DecoderException.class,
				() -> JKey.convertJKey(jKeyTooDeep, 1),
				"Exceeding max expansion depth of " + JKey.MAX_KEY_DEPTH);
	}

	@Test
	void rejectsEmptyKey() {
		// expect:
		assertThrows(DecoderException.class, () -> JKey.convertJKeyBasic(new JKey() {
			@Override
			public boolean isEmpty() {
				return false;
			}

			@Override
			public boolean isValid() {
				return false;
			}

			@Override
			public void setForScheduledTxn(boolean flag) {
			}

			@Override
			public boolean isForScheduledTxn() {
				return false;
			}
		}));
	}

	@Test
	void duplicatesAsExpected() {
		// given:
		var orig = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asJKeyUnchecked();

		// when:
		var dup = orig.duplicate();

		// then:
		assertNotSame(dup, orig);
		assertEquals(asKeyUnchecked(orig), asKeyUnchecked(dup));
	}

	@Test
	void convertsECDSAsecp256k1Key() {
		ByteString edcsaSecp256K1Bytes = ByteString.copyFrom(new byte[] { 0x02 })
				.concat(TxnUtils.randomUtf8ByteString(JECDSASecp256k1Key.ECDSASECP256_COMPRESSED_BYTE_LENGTH - 1));
		final Key aKey = Key.newBuilder().setECDSASecp256K1(edcsaSecp256K1Bytes).build();

		var validEDCSAsecp256K1Key = assertDoesNotThrow(() -> JKey.convertKey(aKey, 1));
		assertTrue(validEDCSAsecp256K1Key instanceof JECDSASecp256k1Key);
		assertEquals(33, validEDCSAsecp256K1Key.getECDSASecp256k1Key().length);
		assertTrue(validEDCSAsecp256K1Key.isValid());
		assertTrue(Arrays.equals(edcsaSecp256K1Bytes.toByteArray(), validEDCSAsecp256K1Key.getECDSASecp256k1Key()));
	}

	@Test
	void negativeConvertKeyECDSAsecp256k1KeyTest() throws DecoderException {
		ByteString edcsaSecp256K1Bytes = ByteString.copyFrom(new byte[] { 0x08 })
				.concat(TxnUtils.randomUtf8ByteString(JECDSASecp256k1Key.ECDSASECP256_COMPRESSED_BYTE_LENGTH - 1));
		final Key aKey = Key.newBuilder().setECDSASecp256K1(edcsaSecp256K1Bytes).build();
		assertFalse(JKey.convertKey(aKey, 1).isValid());
	}

	@Test
	void convertsECDSAsecp256k1BasicKey() {
		ByteString edcsaSecp256K1Bytes = ByteString.copyFrom(new byte[] { 0x02 })
				.concat(TxnUtils.randomUtf8ByteString(JECDSASecp256k1Key.ECDSASECP256_COMPRESSED_BYTE_LENGTH - 1));
		JKey jkey = new JECDSASecp256k1Key(edcsaSecp256K1Bytes.toByteArray());
		var key = assertDoesNotThrow(() -> JKey.convertJKeyBasic(jkey));
		assertFalse(key.getECDSASecp256K1().isEmpty());
	}
}
