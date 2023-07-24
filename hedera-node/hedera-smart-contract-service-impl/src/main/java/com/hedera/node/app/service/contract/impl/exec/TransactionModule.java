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

package com.hedera.node.app.service.contract.impl.exec;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.scope.ExtFrameScope;
import com.hedera.node.app.service.contract.impl.exec.scope.ExtWorldScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleExtFrameScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleExtWorldScope;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HandleContextHevmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.ScopedEvmFrameStateFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Supplier;

@Module
public interface TransactionModule {
    @Provides
    @TransactionScope
    static Configuration configuration(@NonNull final HandleContext context) {
        return requireNonNull(context).configuration();
    }

    @Provides
    @TransactionScope
    static ContractsConfig contractsConfig(@NonNull final Configuration configuration) {
        return requireNonNull(configuration).getConfigData(ContractsConfig.class);
    }

    @Provides
    @TransactionScope
    static Instant consensusTime(@NonNull final HandleContext context) {
        return requireNonNull(context).consensusNow();
    }

    @Provides
    @TransactionScope
    static ActionSidecarContentTracer provideActionSidecarContentTracer() {
        return new EvmActionTracer(new ActionStack());
    }

    @Provides
    @TransactionScope
    static HederaEvmContext provideHederaEvmContext(
            @NonNull final ExtWorldScope extWorldScope, @NonNull final HederaEvmBlocks hederaEvmBlocks) {
        return new HederaEvmContext(extWorldScope.gasPriceInTinybars(), false, hederaEvmBlocks);
    }

    @Provides
    @TransactionScope
    static Supplier<HederaWorldUpdater> feesOnlyUpdater(
            @NonNull final ExtWorldScope extWorldScope, @NonNull final EvmFrameStateFactory factory) {
        return () -> new ProxyWorldUpdater(requireNonNull(extWorldScope), requireNonNull(factory), null);
    }

    @Binds
    @TransactionScope
    EvmFrameStateFactory bindEvmFrameStateFactory(ScopedEvmFrameStateFactory factory);

    @Binds
    @TransactionScope
    ExtWorldScope bindExtWorldScope(HandleExtWorldScope handleExtWorldScope);

    @Binds
    @TransactionScope
    ExtFrameScope bindExtFrameScope(HandleExtFrameScope handleExtFrameScope);

    @Binds
    @TransactionScope
    HederaEvmBlocks bindHederaEvmBlocks(HandleContextHevmBlocks handleContextHevmBlocks);
}
