package com.hedera.services.legacy.core.jproto;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.serde.SerializedForms;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import org.junit.jupiter.api.Test;

public class TxnReceiptSerdeTest extends SelfSerializableDataTest<TxnReceipt> {
	@Override
	protected Class<TxnReceipt> getType() {
		return TxnReceipt.class;
	}

	@Override
	protected void registerConstructables() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
	}

	@Override
	protected int getNumTestCasesFor(final int version) {
		return 2 * MIN_TEST_CASES_PER_VERSION;
	}

	@Override
	protected byte[] getSerializedForm(final int version, final int testCaseNo) {
		return SerializedForms.loadForm(TxnReceipt.class, version, testCaseNo);
	}

	@Override
	protected TxnReceipt getExpectedObject(final int version, final int testCaseNo) {
		final var propertySource = SeededPropertySource.forSerdeTest(version, testCaseNo);
		return receiptFactory(propertySource);
	}

	@Test
	void checkSerializationRegressionsForCurrentVersion() {
		SerializedForms.assertStableSerialization(
				TxnReceipt.class,
				TxnReceiptSerdeTest::receiptFactory,
				TxnReceipt.CURRENT_VERSION,
				getNumTestCasesFor(TxnReceipt.CURRENT_VERSION));
	}

	public static TxnReceipt receiptFactory(final SeededPropertySource propertySource) {
		return propertySource.nextReceipt();
	}
}