package com.hedera.services.context;

import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Supplier;

@Module
public abstract class ContextModule {
	@Provides
	@Singleton
	public static Supplier<Instant> provideConsensusTime(TransactionContext txnCtx) {
		return txnCtx::consensusTime;
	}
}
