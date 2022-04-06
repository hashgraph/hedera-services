package com.hedera.test.serde;

import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.legacy.core.jproto.TxnReceiptSerdeTest;
import com.hedera.test.utils.SeededPropertySource;
import com.hedera.test.utils.SerdeUtils;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SelfSerializable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import static com.hedera.test.serde.SelfSerializableDataTest.MIN_TEST_CASES_PER_VERSION;
import static com.hedera.test.utils.SerdeUtils.serializeToHex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SerializedForms {
	private static final String SERIALIZED_FORMS_LOC = "src/test/resources/serdes";
	private static final String FORM_TPL = "%s-v%d-sn%d.hex";

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

	public static <T extends SelfSerializable> void assertStableSerialization(
			final Class<T> type,
			final Function<SeededPropertySource, T> factory,
			final int version,
			final int numTestCases
	) {
		for (int i = 0; i < numTestCases; i++) {
			final var propertySource = SeededPropertySource.forSerdeTest(version, i);
			final var example = factory.apply(propertySource);
			final var actual = SerdeUtils.serialize(example);
			final var expected = loadForm(type, version, i);
			assertArrayEquals(
					expected, actual,
					"Regression in serializing test case #" + i);
		}
	}

	public static void main(String... args)	 {
		saveTxnReceipts(2 * MIN_TEST_CASES_PER_VERSION);
	}

	private static void saveTxnReceipts(final int n) {
		saveForCurrentVersion(TxnReceipt.class, TxnReceiptSerdeTest::receiptFactory, n);
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
