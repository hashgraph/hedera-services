/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.service.mono.records;

import static com.hedera.node.app.service.mono.stream.RecordStreamManager.effectiveLogDir;

import com.google.common.cache.Cache;
import com.hedera.node.app.service.mono.context.annotations.StaticAccountMemo;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.stats.MiscRunningAvgs;
import com.hedera.node.app.service.mono.stream.CurrentRecordStreamType;
import com.hedera.node.app.service.mono.stream.RecordStreamManager;
import com.hedera.node.app.service.mono.stream.RecordStreamType;
import com.hedera.node.app.service.mono.stream.RecoveryRecordsWriter;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.Units;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;

@Module
public interface RecordsModule {
    @Binds
    @Singleton
    RecordStreamType bindRecordStreamType(CurrentRecordStreamType recordStreamType);

    @Binds
    @Singleton
    RecordsHistorian bindRecordsHistorian(TxnAwareRecordsHistorian txnAwareRecordsHistorian);

    @Provides
    @Singleton
    static Map<TransactionID, TxnIdRecentHistory> txnHistories() {
        return new ConcurrentHashMap<>();
    }

    @Provides
    @Singleton
    static Cache<TransactionID, Boolean> provideCache(RecordCacheFactory recordCacheFactory) {
        return recordCacheFactory.getCache();
    }

    @Provides
    @Singleton
    static RecordStreamManager provideRecordStreamManager(
            final Platform platform,
            final MiscRunningAvgs runningAvgs,
            final NodeLocalProperties nodeLocalProperties,
            final @StaticAccountMemo String accountMemo,
            final Hash initialHash,
            final RecordStreamType streamType,
            final GlobalDynamicProperties globalDynamicProperties,
            final InitTrigger initTrigger) {
        try {
            final RecoveryRecordsWriter recoveryRecordsWriter;
            if (initTrigger == InitTrigger.EVENT_STREAM_RECOVERY) {
                final var blockPeriodMs = nodeLocalProperties.recordLogPeriod() * Units.SECONDS_TO_MILLISECONDS;
                final var onDiskRecordsLoc = effectiveLogDir(nodeLocalProperties.recordLogDir(), accountMemo);
                recoveryRecordsWriter = new RecoveryRecordsWriter(blockPeriodMs, onDiskRecordsLoc);
            } else {
                recoveryRecordsWriter = null;
            }
            return new RecordStreamManager(
                    platform,
                    runningAvgs,
                    nodeLocalProperties,
                    accountMemo,
                    initialHash,
                    streamType,
                    globalDynamicProperties,
                    recoveryRecordsWriter);
        } catch (NoSuchAlgorithmException | IOException fatal) {
            throw new IllegalStateException("Could not construct record stream manager", fatal);
        }
    }
}
