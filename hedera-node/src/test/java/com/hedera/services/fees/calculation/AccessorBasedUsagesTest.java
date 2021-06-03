package com.hedera.services.fees.calculation;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.utils.AccessorBasedUsages;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.utils.TxnAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessorBasedUsagesTest {
	private final int multiplier = 30;
	private final SigUsage sigUsage = new SigUsage(1, 2, 3);

	@Mock
	private TxnAccessor txnAccessor;
	@Mock
	private CryptoOpsUsage cryptoOpsUsage;
	@Mock
	private GlobalDynamicProperties dynamicProperties;

	private AccessorBasedUsages subject;

	@BeforeEach
	void setUp() {
		subject = new AccessorBasedUsages(cryptoOpsUsage, dynamicProperties);
	}

	@Test
	void throwsIfNotForCryptoTransfer() {
		given(txnAccessor.getFunction()).willReturn(CryptoCreate);

		// expect:
		assertThrows(IllegalArgumentException.class, () ->
				subject.assess(sigUsage, txnAccessor, new UsageAccumulator()));
	}

	@Test
	void worksAsExpectedForCryptoTransfer() {
		// setup:
		final var baseMeta = new BaseTransactionMeta(100, 2);
		final var xferMeta = new CryptoTransferMeta(1, 3, 7);
		final var usageAccumulator = new UsageAccumulator();

		given(dynamicProperties.feesTokenTransferUsageMultiplier()).willReturn(multiplier);
		given(txnAccessor.getFunction()).willReturn(CryptoTransfer);
		given(txnAccessor.availXferUsageMeta()).willReturn(xferMeta);
		given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);

		// when:
		subject.assess(sigUsage, txnAccessor, usageAccumulator);

		// then:
		verify(cryptoOpsUsage).cryptoTransferUsage(sigUsage, xferMeta, baseMeta, usageAccumulator);
		// and:
		assertEquals(multiplier, xferMeta.getTokenMultiplier());
	}
}
