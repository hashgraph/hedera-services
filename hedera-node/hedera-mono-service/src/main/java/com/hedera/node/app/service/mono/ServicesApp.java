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

package com.hedera.node.app.service.mono;

import com.hedera.node.app.service.mono.config.ConfigModule;
import com.hedera.node.app.service.mono.context.ContextModule;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.context.annotations.BootstrapProps;
import com.hedera.node.app.service.mono.context.annotations.StaticAccountMemo;
import com.hedera.node.app.service.mono.context.init.ServicesInitFlow;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.GlobalStaticProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.context.properties.PropertiesModule;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.contracts.ContractsModule;
import com.hedera.node.app.service.mono.fees.FeesModule;
import com.hedera.node.app.service.mono.files.FilesModule;
import com.hedera.node.app.service.mono.grpc.GrpcModule;
import com.hedera.node.app.service.mono.grpc.GrpcServerManager;
import com.hedera.node.app.service.mono.grpc.GrpcStarter;
import com.hedera.node.app.service.mono.keys.KeysModule;
import com.hedera.node.app.service.mono.ledger.LedgerModule;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeStartupHelper;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.queries.QueriesModule;
import com.hedera.node.app.service.mono.records.RecordsModule;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.service.mono.sigs.SigsModule;
import com.hedera.node.app.service.mono.state.DualStateAccessor;
import com.hedera.node.app.service.mono.state.StateModule;
import com.hedera.node.app.service.mono.state.expiry.ExpiryModule;
import com.hedera.node.app.service.mono.state.exports.AccountsExporter;
import com.hedera.node.app.service.mono.state.exports.BalancesExporter;
import com.hedera.node.app.service.mono.state.forensics.HashLogger;
import com.hedera.node.app.service.mono.state.initialization.SystemAccountsCreator;
import com.hedera.node.app.service.mono.state.initialization.SystemFilesManager;
import com.hedera.node.app.service.mono.state.initialization.TreasuryCloner;
import com.hedera.node.app.service.mono.state.logic.LastStepModule;
import com.hedera.node.app.service.mono.state.logic.NetworkCtxManager;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.MigrationRecordsManager;
import com.hedera.node.app.service.mono.state.tasks.TaskModule;
import com.hedera.node.app.service.mono.state.validation.LedgerValidator;
import com.hedera.node.app.service.mono.state.virtual.VirtualMapFactory;
import com.hedera.node.app.service.mono.stats.ServicesStatsManager;
import com.hedera.node.app.service.mono.stats.StatsModule;
import com.hedera.node.app.service.mono.store.StoresModule;
import com.hedera.node.app.service.mono.stream.RecordStreamManager;
import com.hedera.node.app.service.mono.throttling.ThrottlingModule;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.txns.TransactionsModule;
import com.hedera.node.app.service.mono.txns.network.UpgradeActions;
import com.hedera.node.app.service.mono.txns.prefetch.PrefetchProcessor;
import com.hedera.node.app.service.mono.txns.submission.SubmissionModule;
import com.hedera.node.app.service.mono.utils.NamedDigestFactory;
import com.hedera.node.app.service.mono.utils.Pause;
import com.hedera.node.app.service.mono.utils.SystemExits;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import dagger.BindsInstance;
import dagger.Component;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Singleton;

/** The infrastructure used to implement the platform contract for a Hedera Services node. */
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
            LastStepModule.class
        })
public interface ServicesApp {
    /* Needed by ServicesState */
    HashLogger hashLogger();

    ProcessLogic logic();

    EventExpansion eventExpansion();

    ServicesInitFlow initializationFlow();

    DualStateAccessor dualStateAccessor();

    VirtualMapFactory virtualMapFactory();

    RecordStreamManager recordStreamManager();

    NodeLocalProperties nodeLocalProperties();

    GlobalDynamicProperties globalDynamicProperties();

    GlobalStaticProperties globalStaticProperties();

    MutableStateChildren workingState();

    PrefetchProcessor prefetchProcessor();

    MigrationRecordsManager migrationRecordsManager();

    /* Needed by ServicesMain */
    Pause pause();

    NodeId nodeId();

    @NonNull
    Platform platform();

    NodeInfo nodeInfo();

    SystemExits systemExits();

    GrpcStarter grpcStarter();

    UpgradeActions upgradeActions();

    TreasuryCloner treasuryCloner();

    LedgerValidator ledgerValidator();

    AccountsExporter accountsExporter();

    BalancesExporter balancesExporter();

    Supplier<Charset> nativeCharset();

    NetworkCtxManager networkCtxManager();

    GrpcServerManager grpc();

    NamedDigestFactory digestFactory();

    StakeStartupHelper stakeStartupHelper();

    SystemFilesManager sysFilesManager();

    ServicesStatsManager statsManager();

    CurrentPlatformStatus platformStatus();

    SystemAccountsCreator sysAccountsCreator();

    Optional<PrintStream> consoleOut();

    ReconnectCompleteListener reconnectListener();

    StateWriteToDiskCompleteListener stateWriteToDiskListener();

    PlatformStatusChangeListener statusChangeListener();

    IssListener issListener();

    NewSignedStateListener newSignedStateListener();

    Supplier<NotificationEngine> notificationEngine();

    BackingStore<AccountID, HederaAccount> backingAccounts();

    @BootstrapProps
    PropertySource bootstrapProps();

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

        ServicesApp build();
    }
}
