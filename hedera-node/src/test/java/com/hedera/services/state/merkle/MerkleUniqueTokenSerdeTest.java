package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class MerkleUniqueTokenSerdeTest extends SelfSerializableDataTest<MerkleUniqueToken> {
	@Override
	protected Class<MerkleUniqueToken> getType() {
		return MerkleUniqueToken.class;
	}

	@Override
	protected MerkleUniqueToken getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextMerkleUniqueToken();
	}

	@Override
	protected MerkleUniqueToken getExpectedObject(final int version, final int testCaseNo) {
		var expected = super.getExpectedObject(version, testCaseNo);
		if (version < MerkleUniqueToken.RELEASE_0250_VERSION) {
			expected.setSpender(EntityId.MISSING_ENTITY_ID);
		}
		return expected;
	}
}
