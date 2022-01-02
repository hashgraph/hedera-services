package com.hedera.services.records;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.cache.Cache;
import com.hedera.services.context.annotations.StaticAccountMemo;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.state.StateAccessor;
import com.hedera.services.state.annotations.WorkingState;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stream.RecordStreamManager;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

	@Provides
	@Singleton
	public static Consumer<RunningHash> provideRunningHashUpdate(@WorkingState StateAccessor stateAccessor) {
		return runningHash -> stateAccessor.runningHashLeaf().setRunningHash(runningHash);
	}

	@Provides
	@Singleton
	public static RecordStreamManager provideRecordStreamManager(
			final Platform platform,
			final MiscRunningAvgs runningAvgs,
			final NodeLocalProperties nodeLocalProperties,
			final @StaticAccountMemo String accountMemo,
			final Hash initialHash
	) {
		try {
			return new RecordStreamManager(
					platform,
					runningAvgs,
					nodeLocalProperties,
					accountMemo,
					initialHash);
		} catch (NoSuchAlgorithmException | IOException fatal) {
			throw new IllegalStateException("Could not construct record stream manager", fatal);
		}
	}
}
