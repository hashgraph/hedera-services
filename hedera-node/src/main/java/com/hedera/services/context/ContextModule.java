package com.hedera.services.context;

import com.hederahashgraph.api.proto.java.AccountID;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Supplier;

@Module
public abstract class ContextModule {
	@Binds
	@Singleton
	public abstract TransactionContext bindTransactionContext(BasicTransactionContext txnCtx);

	@Provides
	@Singleton
	public static Supplier<Instant> provideConsensusTime(TransactionContext txnCtx) {
		return txnCtx::consensusTime;
	}

	@Provides
	@Singleton
	public static AccountID provideEffectiveNodeAccount(NodeInfo nodeInfo) {
		return nodeInfo.hasSelfAccount() ? nodeInfo.selfAccount() : AccountID.getDefaultInstance();
	}
}
