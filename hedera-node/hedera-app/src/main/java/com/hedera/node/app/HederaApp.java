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

import com.hedera.node.app.annotations.MaxSignedTxnSize;
import com.hedera.node.app.components.IngestComponent;
import com.hedera.node.app.components.QueryComponent;
import com.hedera.node.app.fees.AdaptedFeeCalculatorModule;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.mono.ServicesApp;
import com.hedera.node.app.service.mono.config.ConfigModule;
import com.hedera.node.app.service.mono.context.ContextModule;
import com.hedera.node.app.service.mono.context.annotations.BootstrapProps;
import com.hedera.node.app.service.mono.context.annotations.StaticAccountMemo;
import com.hedera.node.app.service.mono.context.properties.PropertiesModule;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.contracts.ContractsModule;
import com.hedera.node.app.service.mono.fees.FeesModule;
import com.hedera.node.app.service.mono.files.FilesModule;
import com.hedera.node.app.service.mono.grpc.GrpcModule;
import com.hedera.node.app.service.mono.keys.KeysModule;
import com.hedera.node.app.service.mono.ledger.LedgerModule;
import com.hedera.node.app.service.mono.queries.QueriesModule;
import com.hedera.node.app.service.mono.records.RecordsModule;
import com.hedera.node.app.service.mono.sigs.SigsModule;
import com.hedera.node.app.service.mono.state.StateModule;
import com.hedera.node.app.service.mono.state.expiry.ExpiryModule;
import com.hedera.node.app.service.mono.state.tasks.TaskModule;
import com.hedera.node.app.service.mono.stats.StatsModule;
import com.hedera.node.app.service.mono.store.StoresModule;
import com.hedera.node.app.service.mono.throttling.ThrottlingModule;
import com.hedera.node.app.service.mono.txns.TransactionsModule;
import com.hedera.node.app.service.mono.txns.submission.SubmissionModule;
import com.hedera.node.app.services.ServiceModule;
import com.hedera.node.app.state.HederaStateModule;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.workflows.handle.HandleWorkflowModule;
import com.hedera.node.app.workflows.prehandle.AdaptedMonoEventExpansion;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflowModule;
import com.hedera.node.app.workflows.query.QueryWorkflowModule;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
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
            TaskModule.class,
            FeesModule.class,
            KeysModule.class,
            SigsModule.class,
            GrpcModule.class,
            ConfigModule.class,
            StatsModule.class,
            StateModule.class,
            FilesModule.class,
            LedgerModule.class,
            StoresModule.class,
            ContextModule.class,
            RecordsModule.class,
            QueriesModule.class,
            ContractsModule.class,
            PropertiesModule.class,
            ThrottlingModule.class,
            SubmissionModule.class,
            TransactionsModule.class,
            ExpiryModule.class,
            ServiceModule.class,
            QueryWorkflowModule.class,
            HandleWorkflowModule.class,
            PreHandleWorkflowModule.class,
            HederaStateModule.class,
            AdaptedFeeCalculatorModule.class
        })
public interface HederaApp extends ServicesApp {
    /* Needed by ServicesState */
    Provider<QueryComponent.Factory> queryComponentFactory();

    Provider<IngestComponent.Factory> ingestComponentFactory();

    WorkingStateAccessor workingStateAccessor();

    AdaptedMonoEventExpansion adaptedMonoEventExpansion();

    FileServiceImpl fileService();

    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder crypto(Cryptography engine);

        @BindsInstance
        Builder initialHash(Hash initialHash);

        @BindsInstance
        Builder platform(@NonNull Platform platform);

        @BindsInstance
        Builder consoleCreator(StateModule.ConsoleCreator consoleCreator);

        @BindsInstance
        Builder selfId(long selfId);

        @BindsInstance
        Builder staticAccountMemo(@StaticAccountMemo String accountMemo);

        @BindsInstance
        Builder bootstrapProps(@BootstrapProps PropertySource bootstrapProps);

        @BindsInstance
        Builder maxSignedTxnSize(@MaxSignedTxnSize final int maxSignedTxnSize);

        HederaApp build();
    }
}
