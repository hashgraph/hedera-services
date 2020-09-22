package com.hedera.services.state.exports;

import com.hedera.services.ServicesState;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;

import java.time.Instant;

public class SignedStateBalancesExporter implements BalancesExporter {
	static final Instant NEVER = null;

	final long expectedFloat;
	final String balancesExportLoc;
	final GlobalDynamicProperties dynamicProperties;

	Instant periodEnd = NEVER;

	public SignedStateBalancesExporter(
			PropertySource properties,
			GlobalDynamicProperties dynamicProperties
	) {
		this.expectedFloat = properties.getLongProperty("ledger.totalTinyBarFloat");
		this.dynamicProperties = dynamicProperties;
		balancesExportLoc = "TODO";
	}

	@Override
	public boolean isTimeToExport(Instant now) {
		if (periodEnd == NEVER) {
			periodEnd = now.plusSeconds(dynamicProperties.balancesExportPeriodSecs());
		} else {
			if (now.isAfter(periodEnd)) {
				periodEnd = now.plusSeconds(dynamicProperties.balancesExportPeriodSecs());
				return true;
			}
		}
		return false;
	}

	@Override
	public void toCsvFile(ServicesState signedState, Instant when) {
		throw new AssertionError("Not implemented");
	}
}
