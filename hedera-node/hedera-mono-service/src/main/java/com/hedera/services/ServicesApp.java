/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services;

import com.hedera.services.context.ContextModule;
import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.annotations.BootstrapProps;
import com.hedera.services.context.annotations.StaticAccountMemo;
import com.hedera.services.context.init.ServicesInitFlow;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.PropertiesModule;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.contracts.ContractsModule;
import com.hedera.services.fees.FeesModule;
import com.hedera.services.files.FilesModule;
import com.hedera.services.grpc.GrpcModule;
import com.hedera.services.grpc.GrpcServerManager;
import com.hedera.services.grpc.GrpcStarter;
import com.hedera.services.keys.KeysModule;
import com.hedera.services.ledger.LedgerModule;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.queries.QueriesModule;
import com.hedera.services.records.RecordsModule;
import com.hedera.services.sigs.EventExpansion;
import com.hedera.services.sigs.SigsModule;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.StateModule;
import com.hedera.services.state.expiry.ExpiryModule;
import com.hedera.services.state.exports.AccountsExporter;
import com.hedera.services.state.exports.BalancesExporter;
import com.hedera.services.state.forensics.HashLogger;
import com.hedera.services.state.initialization.SystemAccountsCreator;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.initialization.TreasuryCloner;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.MigrationRecordsManager;
import com.hedera.services.state.tasks.TaskModule;
import com.hedera.services.state.validation.LedgerValidator;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.stats.ServicesStatsManager;
import com.hedera.services.stats.StatsModule;
import com.hedera.services.store.StoresModule;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.throttling.ThrottlingModule;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.txns.TransactionsModule;
import com.hedera.services.txns.network.UpgradeActions;
import com.hedera.services.txns.prefetch.PrefetchProcessor;
import com.hedera.services.txns.submission.SubmissionModule;
import com.hedera.services.utils.NamedDigestFactory;
import com.hedera.services.utils.Pause;
import com.hedera.services.utils.SystemExits;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import dagger.BindsInstance;
import dagger.Component;
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
            ExpiryModule.class
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

    MutableStateChildren workingState();

    PrefetchProcessor prefetchProcessor();

    MigrationRecordsManager migrationRecordsManager();

    /* Needed by ServicesMain */
    Pause pause();

    NodeId nodeId();

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

    SystemFilesManager sysFilesManager();

    ServicesStatsManager statsManager();

    CurrentPlatformStatus platformStatus();

    SystemAccountsCreator sysAccountsCreator();

    Optional<PrintStream> consoleOut();

    ReconnectCompleteListener reconnectListener();

    StateWriteToDiskCompleteListener stateWriteToDiskListener();

    IssListener issListener();

    NewSignedStateListener newSignedStateListener();

    Supplier<NotificationEngine> notificationEngine();

    BackingStore<AccountID, HederaAccount> backingAccounts();

    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder crypto(Cryptography engine);

        @BindsInstance
        Builder initialHash(Hash initialHash);

        @BindsInstance
        Builder platform(Platform platform);

        @BindsInstance
        Builder selfId(long selfId);

        @BindsInstance
        Builder staticAccountMemo(@StaticAccountMemo String accountMemo);

        @BindsInstance
        Builder bootstrapProps(@BootstrapProps PropertySource bootstrapProps);

        ServicesApp build();
    }
}
