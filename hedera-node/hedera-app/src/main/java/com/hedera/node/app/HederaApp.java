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

package com.hedera.node.app;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.annotations.MaxSignedTxnSize;
import com.hedera.node.app.annotations.NodeSelfId;
import com.hedera.node.app.authorization.AuthorizerModule;
import com.hedera.node.app.components.IngestComponent;
import com.hedera.node.app.components.QueryComponent;
import com.hedera.node.app.fees.FeesModule;
import com.hedera.node.app.info.InfoModule;
import com.hedera.node.app.metrics.MetricsModule;
import com.hedera.node.app.service.mono.LegacyMonoModule;
import com.hedera.node.app.service.mono.ServicesApp;
import com.hedera.node.app.service.mono.context.annotations.BootstrapProps;
import com.hedera.node.app.service.mono.context.annotations.StaticAccountMemo;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.state.StateModule;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.hedera.node.app.services.ServicesModule;
import com.hedera.node.app.solvency.SolvencyModule;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.HederaStateModule;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.throttle.ThrottleModule;
import com.hedera.node.app.workflows.WorkflowsModule;
import com.hedera.node.app.workflows.prehandle.AdaptedMonoEventExpansion;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import dagger.BindsInstance;
import dagger.Component;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * The infrastructure used to implement the platform contract for a Hedera Services node. This is needed for adding
 * dagger subcomponents. Currently, it extends {@link com.hedera.node.app.service.mono.ServicesApp}. But, in the future
 * this class will be cleaned up to not have multiple module dependencies
 */
@Singleton
@Component(
        modules = {
                LegacyMonoModule.class,
                ServicesModule.class,
                WorkflowsModule.class,
                HederaStateModule.class,
                FeesModule.class,
                MetricsModule.class,
                AuthorizerModule.class,
                InfoModule.class,
                ThrottleModule.class,
                SolvencyModule.class
        })
public interface HederaApp extends ServicesApp {
    /* Needed by ServicesState */
    Provider<QueryComponent.Factory> queryComponentFactory();

    Provider<IngestComponent.Factory> ingestComponentFactory();

    WorkingStateAccessor workingStateAccessor();

    AdaptedMonoEventExpansion adaptedMonoEventExpansion();

    NonAtomicReference<HederaState> mutableState();

    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder initTrigger(InitTrigger initTrigger);

        @BindsInstance
        Builder crypto(Cryptography engine);

        @BindsInstance
        Builder initialHash(Hash initialHash);

        @BindsInstance
        Builder platform(@NonNull Platform platform);

        @BindsInstance
        Builder consoleCreator(StateModule.ConsoleCreator consoleCreator);

        @BindsInstance
        Builder selfId(@NodeSelfId final AccountID selfId);

        @BindsInstance
        Builder staticAccountMemo(@StaticAccountMemo String accountMemo);

        @BindsInstance
        Builder bootstrapProps(@BootstrapProps PropertySource bootstrapProps);

        @BindsInstance
        Builder maxSignedTxnSize(@MaxSignedTxnSize final int maxSignedTxnSize);

        HederaApp build();
    }
}
