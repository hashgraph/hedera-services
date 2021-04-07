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

import com.hedera.services.legacy.util.ComplexKeyManager;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Test;

import static com.hedera.services.legacy.proto.utils.KeyExpansion.KEY_EXPANSION_DEPTH;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JKeyTest {
	@Test
	public void positiveConvertKeyTest() throws Exception {
		//given
		Key accountKey = ComplexKeyManager
				.genComplexKey(ComplexKeyManager.SUPPORTED_KEY_TYPES.single.name());

		//expect
		assertDoesNotThrow(() -> JKey.convertKey(accountKey, 1));
	}

	@Test
	public void negativeConvertKeyTest() {
		// given:
		var keyTooDeep = nestKeys(Key.newBuilder(), KEY_EXPANSION_DEPTH).build();

		// expect:
		assertThrows(
				DecoderException.class,
				() -> JKey.convertKey(keyTooDeep, 1),
				"Exceeding max expansion depth of " + KEY_EXPANSION_DEPTH);
	}

	private Key.Builder nestKeys(Key.Builder builder, int additionalKeysToNest) {
		if (additionalKeysToNest == 0) {
			builder.setEd25519(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey().getEd25519());
			return builder;
		} else {
			var nestedBuilder = Key.newBuilder();
			nestKeys(nestedBuilder, additionalKeysToNest - 1);
			builder.setKeyList(KeyList.newBuilder().addKeys(nestedBuilder));
			return builder;
		}
	}

	@Test
	public void rejectsEmptyKey() {
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
			public void setForScheduledTxn(boolean flag) { }

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
}
