package com.hedera.services.stats;

import com.hedera.services.context.properties.NodeLocalProperties;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public abstract class StatsModule {
	@Provides
	@Singleton
	public static MiscRunningAvgs provideMiscRunningAvgs(NodeLocalProperties nodeLocalProperties) {
		return new MiscRunningAvgs(new RunningAvgFactory() { }, nodeLocalProperties);
	}

	@Provides
	@Singleton
	public static MiscSpeedometers provideMiscSpeedometers(NodeLocalProperties nodeLocalProperties) {
		return new MiscSpeedometers(new SpeedometerFactory() { }, nodeLocalProperties);
	}
}
