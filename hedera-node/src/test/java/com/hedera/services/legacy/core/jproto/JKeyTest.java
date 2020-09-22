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

import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hedera.services.legacy.util.ComplexKeyManager;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
public class JKeyTest {
	@Test
	public void positiveConvertKeyTest() throws Exception {
		//given
		Key accountKey = ComplexKeyManager
				.genComplexKey(ComplexKeyManager.SUPPORTE_KEY_TYPES.single.name());

		//expect
		assertDoesNotThrow(() -> JKey.convertKey(accountKey,1));
	}

	@Test
	public void negativeConvertKeyTest() throws Exception {
		//given
		Key accountKey = ComplexKeyManager
				.genComplexKey(ComplexKeyManager.SUPPORTE_KEY_TYPES.thresholdKey.name());

		//expect
		assertThrows(DecoderException.class, () -> JKey.convertKey(accountKey, KeyExpansion.KEY_EXPANSION_DEPTH+1),
				"Exceeding max expansion depth of " + KeyExpansion.KEY_EXPANSION_DEPTH);
	}
}
