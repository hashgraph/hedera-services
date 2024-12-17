/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state;

import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.state.recordcache.DeduplicationCacheImpl;
import com.hedera.node.app.state.recordcache.RecordCacheImpl;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public interface HederaStateInjectionModule {
    @Binds
    RecordCache provideRecordCache(RecordCacheImpl cache);

    @Binds
    HederaRecordCache provideHederaRecordCache(RecordCacheImpl cache);

    @Binds
    DeduplicationCache provideDeduplicationCache(DeduplicationCacheImpl cache);

    @Provides
    @Singleton
    static WorkingStateAccessor provideWorkingStateAccessor() {
        return new WorkingStateAccessor();
    }
}
