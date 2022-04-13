package com.hedera.services.state.submerkle;

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
import com.swirlds.common.io.SerializableDataInputStream;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static com.hedera.services.state.submerkle.ExpirableTxnRecord.RELEASE_0230_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpirableTxnRecordSerdeTest extends SelfSerializableDataTest<ExpirableTxnRecord> {
	public static final int NUM_TEST_CASES = 4 * MIN_TEST_CASES_PER_VERSION;

	@Override
	protected Class<ExpirableTxnRecord> getType() {
		return ExpirableTxnRecord.class;
	}

	@Override
	protected int getNumTestCasesFor(int version) {
		return version == RELEASE_0230_VERSION ? MIN_TEST_CASES_PER_VERSION : NUM_TEST_CASES;
	}

	@Override
	protected byte[] getSerializedForm(final int version, final int testCaseNo) {
		return SerializedForms.loadForm(ExpirableTxnRecord.class, version, testCaseNo);
	}

	@Override
	protected ExpirableTxnRecord getExpectedObject(final int version, final int testCaseNo) {
		return SeededPropertySource.forSerdeTest(version, testCaseNo).nextRecord();
	}

	@Override
	protected ExpirableTxnRecord getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextRecord();
	}

	@Test
	void testDeserializingMultipleExpirableTxnRecords() throws IOException {
		byte[] objectBytes = getSerializedForm(RELEASE_0230_VERSION, 1);
		byte[] bytes = Arrays.concatenate(objectBytes, objectBytes);
		SerializableDataInputStream inputStream = new SerializableDataInputStream(new ByteArrayInputStream(bytes));
		ExpirableTxnRecord record1 = new ExpirableTxnRecord();
		ExpirableTxnRecord record2 = new ExpirableTxnRecord();
		record1.deserialize(inputStream, RELEASE_0230_VERSION);
		record2.deserialize(inputStream, RELEASE_0230_VERSION);

		ExpirableTxnRecord expected = getExpectedObject(RELEASE_0230_VERSION, 1);
		assertEquals(expected, record1);
		assertEquals(expected, record2);
	}
}
