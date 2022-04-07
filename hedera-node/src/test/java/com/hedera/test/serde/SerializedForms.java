package com.hedera.test.serde;

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

import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.legacy.core.jproto.TxnReceiptSerdeTest;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleTopicSerdeTest;
import com.hedera.services.state.submerkle.EvmLog;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.test.utils.SeededPropertySource;
import com.hedera.test.utils.SerdeUtils;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SelfSerializable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.function.Function;

import static com.hedera.test.utils.SerdeUtils.serializeToHex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SerializedForms {
	private static final String SERIALIZED_FORMS_LOC = "src/test/resources/serdes";
	private static final String FORM_TPL = "%s-v%d-sn%d.hex";

	public static void main(String... args) {
//		saveAccountStates(MIN_TEST_CASES_PER_VERSION);
//		saveTxnReceipts(2 * MIN_TEST_CASES_PER_VERSION);
//		saveNetworkContexts(MerkleNetworkContextSerdeTest.MIN_TEST_CASES_PER_VERSION);
//		saveRecords(ExpirableTxnRecordSerdeTest.NUM_TEST_CASES);
//		save024xRecords(ExpirableTxnRecordSerdeTest.MIN_TEST_CASES_PER_VERSION);
//		saveSchedules(MerkleScheduleSerdeTest.NUM_TEST_CASES);
//		saveTokens(MerkleTokenSerdeTest.NUM_TEST_CASES);
//		saveLogs(EvmLogSerdeTest.NUM_TEST_CASES);
//		saveTxnIds(TxnIdSerdeTest.NUM_TEST_CASES);
		saveTopics(MerkleTopicSerdeTest.NUM_TEST_CASES);
	}

	public static <T extends SelfSerializable> byte[] loadForm(
			final Class<T> type,
			final int version,
			final int testCaseNo
	) {
		final var path = pathFor(type, version, testCaseNo);
		try {
			return CommonUtils.unhex(Files.readString(path));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static <T extends SelfSerializable> void assertSameSerialization(
			final Class<T> type,
			final Function<SeededPropertySource, T> factory,
			final int version,
			final int testCaseNo
	) {
		final var propertySource = SeededPropertySource.forSerdeTest(version, testCaseNo);
		final var example = factory.apply(propertySource);
		final var actual = SerdeUtils.serialize(example);
		final var expected = loadForm(type, version, testCaseNo);
		assertArrayEquals(
				expected, actual,
				"Regression in serializing test case #" + testCaseNo);
	}

	private static void saveTxnReceipts(final int n) {
		saveForCurrentVersion(TxnReceipt.class, TxnReceiptSerdeTest::receiptFactory, n);
	}

	private static void saveAccountStates(final int n) {
		saveForCurrentVersion(MerkleAccountState.class, SeededPropertySource::nextAccountState, n);
	}

	private static void saveTokens(final int n) {
		saveForCurrentVersion(MerkleToken.class, SeededPropertySource::nextToken, n);
	}

	private static void saveLogs(final int n) {
		saveForCurrentVersion(EvmLog.class, SeededPropertySource::nextEvmLog, n);
	}

	private static void saveTopics(final int n) {
		saveForCurrentVersion(MerkleTopic.class, SeededPropertySource::nextTopic, n);
	}

	private static void saveTxnIds(final int n) {
		saveForCurrentVersion(TxnId.class, SeededPropertySource::nextTxnId, n);
	}

	private static void saveNetworkContexts(final int n) {
		saveForCurrentVersion(MerkleNetworkContext.class, SeededPropertySource::nextNetworkContext, n);
	}

	private static void saveRecords(final int n) {
		saveForCurrentVersion(ExpirableTxnRecord.class, SeededPropertySource::nextRecord, n);
	}

	private static void save024xRecords(final int n) {
		saveForCurrentVersion(ExpirableTxnRecord.class, propertySource -> {
			final var seeded = propertySource.nextRecord();
			seeded.setCryptoAllowances(Collections.emptyMap());
			seeded.setFungibleTokenAllowances(Collections.emptyMap());
			return seeded;
		}, n);
	}

	private static void saveSchedules(final int n) {
		saveForCurrentVersion(MerkleSchedule.class, SeededPropertySource::nextSchedule, n);
	}

	private static <T extends SelfSerializable> void saveForCurrentVersion(
			final Class<T> type,
			final Function<SeededPropertySource, T> factory,
			final int numTestCases
	) {
		final var instance = SelfSerializableDataTest.instantiate(type);
		final var currentVersion = instance.getVersion();
		for (int i = 0; i < numTestCases; i++) {
			final var propertySource = SeededPropertySource.forSerdeTest(currentVersion, i);
			final var example = factory.apply(propertySource);
			saveForm(example, type, currentVersion, i);
		}
	}

	private static <T extends SelfSerializable> void saveForm(
			final T example,
			final Class<T> type,
			final int version,
			final int testCaseNo
	) {
		final var hexed = serializeToHex(example);
		final var path = pathFor(type, version, testCaseNo);
		try {
			Files.writeString(path, hexed);
			System.out.println("Please ensure " + path + " is tracked in git");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static <T extends SelfSerializable> Path pathFor(
			final Class<T> type,
			final int version,
			final int testCaseNo
	) {
		return Paths.get(SERIALIZED_FORMS_LOC + "/"
				+ String.format(FORM_TPL, type.getSimpleName(), version, testCaseNo));
	}
}
