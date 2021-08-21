package com.hedera.services.fees;

import com.hedera.services.fees.charging.NarratedCharging;
import com.hedera.services.fees.charging.NarratedLedgerCharging;
import dagger.Binds;
import dagger.Module;

import javax.inject.Singleton;

@Module
public abstract class FeesModule {
	@Binds @Singleton
	public abstract FeeExemptions bindFeeExemptions(StandardExemptions standardExemptions);

	@Binds @Singleton
	public abstract NarratedCharging bindNarratedCharging(NarratedLedgerCharging narratedLedgerCharging);

	@Binds @Singleton
	public abstract HbarCentExchange bindHbarCentExchange(ScopedHbarCentExchange scopedHbarCentExchange);
}
