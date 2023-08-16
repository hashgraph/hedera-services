/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.throttling.DeterministicThrottling;
import com.hedera.node.app.service.mono.throttling.TimedFunctionalityThrottling;
import com.hedera.node.app.service.mono.throttling.annotations.HandleThrottle;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.throttle.impl.NetworkUtilizationManagerImpl;
import com.swirlds.fchashmap.FCHashMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

@Module
public interface ThrottleInjectionModule {
    @Binds
    @Singleton
    ThrottleAccumulator bindThrottleAccumulator(ThrottleAccumulatorImpl throttleAccumulator);

    @Provides
    @Singleton
    @HandleThrottle
    public static TimedFunctionalityThrottling provideHandleThrottling() {
        // TODO: tmp use dummy aliases
        final FCHashMap<ByteString, EntityNum> aliases = new FCHashMap<>();
        final var tmpAliases = new AliasManager(() -> aliases);

        // TODO: tmp use dummy properties
        final var propertySource = new BootstrapProperties();
        propertySource.ensureProps();
        final var tmpProps = new GlobalDynamicProperties(new HederaNumbers(propertySource), propertySource);

        return new DeterministicThrottling(
                () -> 1,
                tmpAliases,
                tmpProps,
                DeterministicThrottling.DeterministicThrottlingMode.CONSENSUS,
                null); // TODO: should we throttle schedule transactions in handle throttling?
    }

    /** Provides an implementation of the {@link com.hedera.node.app.throttle.NetworkUtilizationManager}. */
    @Provides
    @Singleton
    public static NetworkUtilizationManager provideNetworkUtilizationManager(
            @NonNull final HandleThrottleAccumulator handleThrottling) {
        return new NetworkUtilizationManagerImpl(handleThrottling);
    }
}
