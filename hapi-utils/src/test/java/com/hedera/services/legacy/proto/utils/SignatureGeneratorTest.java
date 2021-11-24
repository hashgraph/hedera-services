package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API Utilities
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

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SignatureGeneratorTest {
	@Test
	void rejectsNonEddsaKeys() {
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> SignatureGenerator.signBytes(new byte[0], null));
	}

	@Test
	void signsBytesCorrectly() throws Exception {
		final var pair = new KeyPairGenerator().generateKeyPair();
		final var sig = SignatureGenerator.signBytes("abc".getBytes(), pair.getPrivate());
		Assertions.assertEquals(64, sig.length);
	}
}
