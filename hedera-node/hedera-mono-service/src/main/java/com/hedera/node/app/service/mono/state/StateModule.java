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
package com.hedera.node.app.service.mono.state;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_GENESIS_PUBLIC_KEY;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.ledger.ids.SeqNoEntityIdSource;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.exports.AccountsExporter;
import com.hedera.node.app.service.mono.state.exports.BalancesExporter;
import com.hedera.node.app.service.mono.state.exports.ServicesSignedStateListener;
import com.hedera.node.app.service.mono.state.exports.SignedStateBalancesExporter;
import com.hedera.node.app.service.mono.state.exports.ToStringAccountsExporter;
import com.hedera.node.app.service.mono.state.forensics.ServicesIssListener;
import com.hedera.node.app.service.mono.state.initialization.BackedSystemAccountsCreator;
import com.hedera.node.app.service.mono.state.initialization.HfsSystemFilesManager;
import com.hedera.node.app.service.mono.state.initialization.SystemAccountsCreator;
import com.hedera.node.app.service.mono.state.initialization.SystemFilesManager;
import com.hedera.node.app.service.mono.state.logic.HandleLogicModule;
import com.hedera.node.app.service.mono.state.logic.ReconnectListener;
import com.hedera.node.app.service.mono.state.logic.StateWriteToDiskListener;
import com.hedera.node.app.service.mono.state.logic.StatusChangeListener;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
import com.hedera.node.app.service.mono.state.submerkle.SequenceNumber;
import com.hedera.node.app.service.mono.state.validation.BasedLedgerValidator;
import com.hedera.node.app.service.mono.state.validation.LedgerValidator;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualMapFactory;
import com.hedera.node.app.service.mono.store.schedule.ScheduleStore;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.JvmSystemExits;
import com.hedera.node.app.service.mono.utils.NamedDigestFactory;
import com.hedera.node.app.service.mono.utils.Pause;
import com.hedera.node.app.service.mono.utils.SleepingPause;
import com.hedera.node.app.service.mono.utils.SystemExits;
import com.swirlds.common.Console;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module(includes = HandleLogicModule.class)
public interface StateModule {
    interface ConsoleCreator {
        @Nullable
        Console createConsole(Platform platform, boolean visible);
    }

    @Binds
    @Singleton
    IssListener bindIssListener(ServicesIssListener servicesIssListener);

    @Binds
    @Singleton
    NewSignedStateListener bindNewSignedStateListener(
            ServicesSignedStateListener servicesSignedStateListener);

    @Binds
    @Singleton
    SystemExits bindSystemExits(JvmSystemExits systemExits);

    @Binds
    @Singleton
    ReconnectCompleteListener bindReconnectListener(ReconnectListener reconnectListener);

    @Binds
    @Singleton
    StateWriteToDiskCompleteListener bindStateWrittenToDiskListener(
            StateWriteToDiskListener stateWriteToDiskListener);

    @Binds
    @Singleton
    PlatformStatusChangeListener bindStatusChangeListener(
            StatusChangeListener statusChangeListener);

    @Binds
    @Singleton
    LedgerValidator bindLedgerValidator(BasedLedgerValidator basedLedgerValidator);

    @Binds
    @Singleton
    EntityCreator bindEntityCreator(ExpiringCreations creator);

    @Provides
    @Singleton
    static BalancesExporter bindBalancesExporter(
            final SystemExits systemExits,
            final @CompositeProps PropertySource properties,
            final Function<byte[], Signature> signer,
            final GlobalDynamicProperties dynamicProperties) {
        try {
            return new SignedStateBalancesExporter(
                    systemExits, properties, signer, dynamicProperties);
        } catch (final NoSuchAlgorithmException fatal) {
            throw new IllegalStateException(
                    "Could not construct signed state balances exporter", fatal);
        }
    }

    @Binds
    @Singleton
    SystemFilesManager bindSysFilesManager(HfsSystemFilesManager hfsSystemFilesManager);

    @Binds
    @Singleton
    AccountsExporter bindAccountsExporter(ToStringAccountsExporter toStringAccountsExporter);

    @Binds
    @Singleton
    SystemAccountsCreator bindSystemAccountsCreator(BackedSystemAccountsCreator backedCreator);

    @Provides
    @Singleton
    static VirtualMapFactory provideVirtualMapFactory() {
        return new VirtualMapFactory(JasperDbBuilder::new);
    }

    @Provides
    @Singleton
    static Pause providePause() {
        return SleepingPause.SLEEPING_PAUSE;
    }

    @Provides
    @Singleton
    static Supplier<Charset> provideNativeCharset() {
        return Charset::defaultCharset;
    }

    @Provides
    @Singleton
    static NamedDigestFactory provideDigestFactory() {
        return MessageDigest::getInstance;
    }

    @Provides
    @Singleton
    static Supplier<NotificationEngine> provideNotificationEngine(final Platform platform) {
        return platform::getNotificationEngine;
    }

    @Provides
    @Singleton
    static Function<EthTxData, EthTxSigs> provideSigsFunction() {
        return EthTxSigs::extractSignatures;
    }

    @Provides
    @Singleton
    static Optional<PrintStream> providePrintStream(
            final ConsoleCreator consoleCreator, final Platform platform) {
        return Optional.ofNullable(consoleCreator.createConsole(platform, true)).map(c -> c.out);
    }

    @Provides
    @Singleton
    static Function<byte[], Signature> provideSigner(final Platform platform) {
        return platform::sign;
    }

    @Provides
    @Singleton
    static NodeId provideNodeId(final Platform platform) {
        return platform.getSelfId();
    }

    @Provides
    @Singleton
    static StateView provideCurrentView(
            final ScheduleStore scheduleStore,
            final MutableStateChildren workingState,
            final NetworkInfo networkInfo) {
        return new StateView(scheduleStore, workingState, networkInfo);
    }

    @Provides
    @Singleton
    static Supplier<StateView> provideStateViews(
            final ScheduleStore scheduleStore,
            final MutableStateChildren workingState,
            final NetworkInfo networkInfo) {
        return () -> new StateView(scheduleStore, workingState, networkInfo);
    }

    @Provides
    @Singleton
    static MutableStateChildren provideWorkingState() {
        return new MutableStateChildren();
    }

    @Provides
    @Singleton
    static Supplier<AccountStorageAdapter> provideWorkingAccounts(
            final MutableStateChildren workingState) {
        return workingState::accounts;
    }

    @Provides
    @Singleton
    static Supplier<RecordsStorageAdapter> providePayerRecords(
            final MutableStateChildren workingState) {
        return workingState::payerRecords;
    }

    @Provides
    @Singleton
    static Supplier<MerkleMapLike<EntityNum, MerkleStakingInfo>> provideWorkingStakingInfo(
            final MutableStateChildren workingState) {
        return workingState::stakingInfo;
    }

    @Provides
    @Singleton
    static Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> provideWorkingStorage(
            final MutableStateChildren workingState) {
        return workingState::storage;
    }

    @Provides
    @Singleton
    static Supplier<MerkleMapLike<EntityNum, MerkleTopic>> provideWorkingTopics(
            final MutableStateChildren workingState) {
        return workingState::topics;
    }

    @Provides
    @Singleton
    static Supplier<MerkleMapLike<EntityNum, MerkleToken>> provideWorkingTokens(
            final MutableStateChildren workingState) {
        return workingState::tokens;
    }

    @Provides
    @Singleton
    static Supplier<TokenRelStorageAdapter> provideWorkingTokenAssociations(
            final MutableStateChildren workingState) {
        return workingState::tokenAssociations;
    }

    @Provides
    @Singleton
    static Supplier<MerkleScheduledTransactions> provideWorkingSchedules(
            final MutableStateChildren workingState) {
        return workingState::schedules;
    }

    @Provides
    @Singleton
    static Supplier<UniqueTokenMapAdapter> provideWorkingNfts(
            final MutableStateChildren workingState) {
        return workingState::uniqueTokens;
    }

    @Provides
    @Singleton
    static Supplier<MerkleSpecialFiles> provideWorkingSpecialFiles(
            final MutableStateChildren workingState) {
        return workingState::specialFiles;
    }

    @Provides
    @Singleton
    static Supplier<VirtualMapLike<ContractKey, IterableContractValue>> provideWorkingContractStorage(
            final MutableStateChildren workingState) {
        return workingState::contractStorage;
    }

    @Provides
    @Singleton
    static Supplier<MerkleNetworkContext> provideWorkingNetworkCtx(
            final MutableStateChildren workingState) {
        return workingState::networkCtx;
    }

    @Provides
    @Singleton
    static Supplier<RecordsRunningHashLeaf> provideRecordsRunningHashLeaf(
            final MutableStateChildren workingState) {
        return workingState::runningHashLeaf;
    }

    @Provides
    @Singleton
    static Supplier<AddressBook> provideWorkingAddressBook(
            final MutableStateChildren workingState) {
        return workingState::addressBook;
    }

    @Provides
    @Singleton
    static EntityIdSource provideWorkingEntityIdSource(final MutableStateChildren workingState) {
        return new SeqNoEntityIdSource(() -> workingState.networkCtx().seqNo());
    }

    @Provides
    @Singleton
    static Supplier<ExchangeRates> provideWorkingMidnightRates(
            final MutableStateChildren workingState) {
        return () -> workingState.networkCtx().midnightRates();
    }

    @Provides
    @Singleton
    static Supplier<SequenceNumber> provideWorkingSeqNo(final MutableStateChildren workingState) {
        return () -> workingState.networkCtx().seqNo();
    }

    @Provides
    @Singleton
    static Supplier<Map<ByteString, EntityNum>> provideWorkingAliases(
            final MutableStateChildren workingState) {
        return workingState::aliases;
    }

    @Provides
    @Singleton
    static Supplier<JEd25519Key> provideSystemFileKey(
            @CompositeProps final PropertySource properties) {
        return () -> {
            final var hexedEd25519Key = properties.getStringProperty(BOOTSTRAP_GENESIS_PUBLIC_KEY);
            final var ed25519Key = new JEd25519Key(CommonUtils.unhex(hexedEd25519Key));
            if (!ed25519Key.isValid()) {
                throw new IllegalStateException(
                        "'" + hexedEd25519Key + "' is not a possible Ed25519 public key");
            }
            return ed25519Key;
        };
    }
}
