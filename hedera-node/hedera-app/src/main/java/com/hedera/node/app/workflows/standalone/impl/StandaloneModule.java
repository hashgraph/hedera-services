/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.standalone.impl;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.Hedera;
import com.hedera.node.app.annotations.MaxSignedTxnSize;
import com.hedera.node.app.annotations.NodeSelfId;
import com.hedera.node.app.metrics.StoreMetricsServiceImpl;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.state.spi.info.NetworkInfo;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.InstantSource;
import java.util.function.IntSupplier;
import javax.inject.Singleton;

@Module
public interface StandaloneModule {
    @Binds
    @Singleton
    NetworkInfo bindNetworkInfo(@NonNull StandaloneNetworkInfo simulatedNetworkInfo);

    @Binds
    @Singleton
    StoreMetricsService bindStoreMetricsService(@NonNull StoreMetricsServiceImpl storeMetricsServiceImpl);

    @Provides
    @Singleton
    static IntSupplier provideFrontendThrottleSplit() {
        return () -> 1;
    }

    @Provides
    @Singleton
    static PlatformStateAccessor providePlatformState() {
        return new PlatformState();
    }

    @Provides
    @Singleton
    static InstantSource provideInstantSource() {
        return InstantSource.system();
    }

    @Provides
    @Singleton
    @MaxSignedTxnSize
    static int maxSignedTxnSize() {
        return Hedera.MAX_SIGNED_TXN_SIZE;
    }

    @Provides
    @Singleton
    @NodeSelfId
    static AccountID provideNodeSelfId() {
        // This is only used to check the shard and realm of account ids
        return AccountID.DEFAULT;
    }

    @Provides
    @Singleton
    static Cryptography provideCryptography() {
        return CryptographyHolder.get();
    }
}
