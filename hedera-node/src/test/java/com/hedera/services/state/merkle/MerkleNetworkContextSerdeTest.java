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

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.serde.SerializedForms;
import com.hedera.test.utils.SeededPropertySource;

import java.util.Optional;
import java.util.function.BiConsumer;

public class MerkleNetworkContextSerdeTest extends SelfSerializableDataTest<MerkleNetworkContext> {
	public static final int NUM_TEST_CASES = 3 * MIN_TEST_CASES_PER_VERSION;

	@Override
	protected Class<MerkleNetworkContext> getType() {
		return MerkleNetworkContext.class;
	}

	@Override
	protected Optional<BiConsumer<MerkleNetworkContext, MerkleNetworkContext>> customAssertEquals() {
		return Optional.of(MerkleNetworkContextTest::assertEqualContexts);
	}

	@Override
	protected int getNumTestCasesFor(final int version) {
		return version == MerkleNetworkContext.RELEASE_0200_VERSION ? MIN_TEST_CASES_PER_VERSION : NUM_TEST_CASES;
	}

	@Override
	protected byte[] getSerializedForm(final int version, final int testCaseNo) {
		return SerializedForms.loadForm(MerkleNetworkContext.class, version, testCaseNo);
	}

	@Override
	protected MerkleNetworkContext getExpectedObject(final int version, final int testCaseNo) {
		final var seeded = SeededPropertySource.forSerdeTest(version, testCaseNo).nextNetworkContext();
		if (version == MerkleNetworkContext.RELEASE_0200_VERSION) {
			seeded.setMigrationRecordsStreamed(false);
		}
		return seeded;
	}

	@Override
	protected MerkleNetworkContext getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextNetworkContext();
	}
}
