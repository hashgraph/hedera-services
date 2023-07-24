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

import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ScopedEvmFrameStateFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.swirlds.config.api.Configuration;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

@Module
public interface QueryModule {
    @Provides
    @QueryScope
    static Configuration configuration(@NonNull final QueryContext context) {
        return requireNonNull(context).configuration();
    }

    @Provides
    @QueryScope
    static Instant consensusTime(@NonNull final HandleContext context) {
        return requireNonNull(context).consensusNow();
    }

    @Binds
    @QueryScope
    EvmFrameStateFactory bindEvmFrameStateFactory(ScopedEvmFrameStateFactory factory);

    @Binds
    @QueryScope
    HederaOperations bindExtWorldScope(HandleHederaOperations handleExtWorldScope);

    @Binds
    @QueryScope
    HederaNativeOperations bindExtFrameScope(HandleHederaNativeOperations handleExtFrameScope);
}
