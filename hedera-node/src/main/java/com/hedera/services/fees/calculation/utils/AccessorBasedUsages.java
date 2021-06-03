package com.hedera.services.fees.calculation.utils;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.utils.TxnAccessor;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

public class AccessorBasedUsages {
	private final CryptoOpsUsage cryptoOpsUsage;
	private final GlobalDynamicProperties dynamicProperties;

	public AccessorBasedUsages(
			CryptoOpsUsage cryptoOpsUsage,
			GlobalDynamicProperties dynamicProperties
	) {
		this.cryptoOpsUsage = cryptoOpsUsage;
		this.dynamicProperties = dynamicProperties;
	}

	public void assess(SigUsage sigUsage, TxnAccessor accessor, UsageAccumulator into) {
		final var function = accessor.getFunction();
		if (function != CryptoTransfer) {
			throw new IllegalArgumentException("Usage estimation for " + function + " not yet migrated");
		}

		final var baseMeta = accessor.baseUsageMeta();
		final var xferMeta = accessor.availXferUsageMeta();
		xferMeta.setTokenMultiplier(dynamicProperties.feesTokenTransferUsageMultiplier());
		cryptoOpsUsage.cryptoTransferUsage(sigUsage, xferMeta, baseMeta, into);
	}
}
