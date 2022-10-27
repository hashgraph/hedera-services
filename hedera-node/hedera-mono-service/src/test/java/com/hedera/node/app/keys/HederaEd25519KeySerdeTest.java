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

import com.hedera.node.app.keys.impl.HederaEd25519Key;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class HederaEd25519KeySerdeTest extends SelfSerializableDataTest<HederaEd25519Key> {
	@Override
	protected Class<HederaEd25519Key> getType() {
		return HederaEd25519Key.class;
	}

	@Override
	protected int getNumTestCasesFor(final int version) {
		return MIN_TEST_CASES_PER_VERSION;
	}

	@Override
	protected HederaEd25519Key getExpectedObject(
			final SeededPropertySource propertySource) {
		return propertySource.nextHederaEd25519Key();
	}
}
