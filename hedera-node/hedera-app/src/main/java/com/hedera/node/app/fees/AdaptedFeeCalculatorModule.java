package com.hedera.node.app.fees;

import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.node.app.workflows.handle.AdaptedMonoFeeCalculator;
import dagger.Binds;
import dagger.Module;

import javax.inject.Singleton;

@Module
public interface AdaptedFeeCalculatorModule {
    @Binds
    @Singleton
    FeeCalculator bindFeeCalculator(AdaptedMonoFeeCalculator adaptedMonoFeeCalculator);
}
