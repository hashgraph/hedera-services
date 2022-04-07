package com.hedera.services.state.merkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.serde.SerializedForms;
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
	protected byte[] getSerializedForm(final int version, final int testCaseNo) {
		return SerializedForms.loadForm(MerkleSchedule.class, version, testCaseNo);
	}

	@Override
	protected MerkleSchedule getExpectedObject(final int version, final int testCaseNo) {
		return SeededPropertySource.forSerdeTest(version, testCaseNo).nextSchedule();
	}

	@Override
	protected MerkleSchedule getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextSchedule();
	}
}