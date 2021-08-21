package com.hedera.services.store;

import com.hedera.services.store.schedule.HederaScheduleStore;
import com.hedera.services.store.schedule.ScheduleStore;
import dagger.Binds;
import dagger.Module;

import javax.inject.Singleton;

@Module
public abstract class StoresModule {
	@Binds @Singleton
	public abstract ScheduleStore bindSchedulStore(HederaScheduleStore scheduleStore);
}
