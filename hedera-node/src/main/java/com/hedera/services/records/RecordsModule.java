package com.hedera.services.records;

import com.google.common.cache.Cache;
import com.hederahashgraph.api.proto.java.TransactionID;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Module
public abstract class RecordsModule {
	@Binds
	@Singleton
	public abstract AccountRecordsHistorian bindRecordsHistorian(TxnAwareRecordsHistorian txnAwareRecordsHistorian);

	@Provides
	@Singleton
	public static Map<TransactionID, TxnIdRecentHistory> txnHistories() {
		return new ConcurrentHashMap<>();
	}

	@Provides
	@Singleton
	public static Cache<TransactionID, Boolean> provideCache(RecordCacheFactory recordCacheFactory) {
		return recordCacheFactory.getCache();
	}
}
