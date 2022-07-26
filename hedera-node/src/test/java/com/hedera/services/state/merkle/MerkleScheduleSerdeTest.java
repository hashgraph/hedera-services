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
import com.hedera.test.utils.SeededPropertySource;

import java.util.Optional;
import java.util.function.BiConsumer;

public class MerkleScheduleSerdeTest extends SelfSerializableDataTest<MerkleSchedule> {
	public static final int NUM_TEST_CASES = MIN_TEST_CASES_PER_VERSION;

	@Override
	protected Class<MerkleSchedule> getType() {
		return MerkleSchedule.class;
	}

	@Override
	protected int getNumTestCasesFor(final int version) {
		return NUM_TEST_CASES;
	}

	@Override
	protected Optional<BiConsumer<MerkleSchedule, MerkleSchedule>> customAssertEquals() {
		return Optional.of(MerkleScheduleTest::assertEqualSchedules);
	}

	@Override
	protected MerkleSchedule getExpectedObject(final SeededPropertySource propertySource) {
		return nextSchedule(propertySource);
	}

	public static MerkleSchedule nextSchedule(final SeededPropertySource propertySource) {
		return nextSchedule(propertySource, null, null);
	}

	public static MerkleSchedule nextSchedule(final SeededPropertySource propertySource, Long expiry,
			byte[] bodyBytes) {
		final var seeded = new MerkleSchedule();
		if (expiry != null) {
			seeded.setExpiry(expiry);
		} else {
			seeded.setExpiry(propertySource.nextUnsignedLong());
		}
		if (bodyBytes != null) {
			seeded.setBodyBytes(bodyBytes);
		} else {
			seeded.setBodyBytes(propertySource.nextSerializedTransactionBody());
		}
		if (propertySource.nextBoolean()) {
			seeded.markDeleted(propertySource.nextInstant());
		} else if (propertySource.nextBoolean()) {
			seeded.markExecuted(propertySource.nextInstant());
		}
		final var numSignatures = propertySource.nextInt(10);
		for (int i = 0; i < numSignatures; i++) {
			seeded.witnessValidSignature(propertySource.nextBytes(propertySource.nextBoolean() ? 32 : 33));
		}
		seeded.setKey(propertySource.nextNum());
		return seeded;
	}

}
