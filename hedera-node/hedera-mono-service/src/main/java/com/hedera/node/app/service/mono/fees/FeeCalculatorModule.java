package com.hedera.node.app.service.mono.fees;

import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import dagger.Binds;
import dagger.Module;

import javax.inject.Singleton;

@Module
public interface FeeCalculatorModule {
    @Binds
    @Singleton
    FeeCalculator bindFeeCalculator(UsageBasedFeeCalculator usageBasedFeeCalculator);
}
