package com.hedera.services.state.submerkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class FcAssessedCustomFeeSerdeTest extends SelfSerializableDataTest<FcAssessedCustomFee> {
	@Override
	protected Class<FcAssessedCustomFee> getType() {
		return FcAssessedCustomFee.class;
	}

	@Override
	protected FcAssessedCustomFee getExpectedObject(final SeededPropertySource propertySource) {
		return propertySource.nextAssessedFee();
	}

	@Override
	protected FcAssessedCustomFee getExpectedObject(final int version, final int testCaseNo) {
		final var result = super.getExpectedObject(version, testCaseNo);
		if (version < FcAssessedCustomFee.RELEASE_0171_VERSION) {
			// Need to drop the last field.
			return new FcAssessedCustomFee(
					result.account(),
					result.token(),
					result.units(),
					FcAssessedCustomFee.UNKNOWN_EFFECTIVE_PAYER_ACCOUNT_NUMS);
		}
		return result;
	}
}
