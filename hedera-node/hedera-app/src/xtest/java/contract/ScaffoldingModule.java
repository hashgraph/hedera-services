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

package contract;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.VersionedConfigImpl;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.records.impl.BlockRecordManagerImpl;
import com.hedera.node.app.records.impl.BlockRecordStreamProducer;
import com.hedera.node.app.records.impl.producers.BlockRecordFormat;
import com.hedera.node.app.records.impl.producers.BlockRecordWriterFactory;
import com.hedera.node.app.records.impl.producers.StreamFileProducerSingleThreaded;
import com.hedera.node.app.records.impl.producers.formats.BlockRecordWriterFactoryImpl;
import com.hedera.node.app.records.impl.producers.formats.v6.BlockRecordFormatV6;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.fixtures.info.FakeNetworkInfo;
import com.hedera.node.app.spi.fixtures.numbers.FakeHederaNumbers;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.SelfNodeInfo;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleDispatcher;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.recordcache.DeduplicationCacheImpl;
import com.hedera.node.app.state.recordcache.RecordCacheImpl;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.handle.verifier.BaseHandleContextVerifier;
import com.hedera.node.app.workflows.prehandle.DummyPreHandleDispatcher;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.stream.Signer;
import com.swirlds.config.api.Configuration;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import javax.inject.Singleton;

@Module
public interface ScaffoldingModule {
    @Binds
    @Singleton
    DeduplicationCache bindDeduplicationCache(DeduplicationCacheImpl cacheImpl);

    @Binds
    @Singleton
    RecordCache bindRecordCache(RecordCacheImpl cacheImpl);

    @Binds
    @Singleton
    BlockRecordStreamProducer bindBlockRecordStreamProducer(StreamFileProducerSingleThreaded producer);

    @Binds
    @Singleton
    PreHandleDispatcher bindPreHandleDispatcher(DummyPreHandleDispatcher dispatcher);

    @Binds
    @Singleton
    BlockRecordManager bindBlockRecordManager(BlockRecordManagerImpl manager);

    @Binds
    @Singleton
    BlockRecordWriterFactory bindBlockRecordWriterFactory(BlockRecordWriterFactoryImpl factory);

    @Provides
    @Singleton
    static Signer provideSigner() {
        return bytes -> new Signature();
    }

    @Provides
    @Singleton
    static FileSystem provideFileSystem() {
        return FileSystems.getDefault();
    }

    @Provides
    @Singleton
    static BlockRecordFormat provideBlockRecordFormat() {
        return BlockRecordFormatV6.INSTANCE;
    }

    @Provides
    @Singleton
    static Configuration provideConfiguration() {
        return HederaTestConfigBuilder.create().getOrCreateConfig();
    }

    @Provides
    @Singleton
    static NetworkInfo provideNetworkInfo() {
        return new FakeNetworkInfo();
    }

    @Provides
    @Singleton
    static SelfNodeInfo provideSelfNodeInfo(@NonNull final NetworkInfo networkInfo) {
        return networkInfo.selfNodeInfo();
    }

    @Provides
    @Singleton
    static CryptoSignatureWaivers provideCryptoSignatureWaivers() {
        return new CryptoSignatureWaiversImpl(new FakeHederaNumbers());
    }

    @Provides
    @Singleton
    static HederaState provideState() {
        return new FakeHederaState();
    }

    @Provides
    @Singleton
    static ConfigProvider provideConfigProvider(@NonNull final Configuration configuration) {
        return () -> new VersionedConfigImpl(configuration, 1L);
    }

    @Provides
    @Singleton
    static SavepointStackImpl provideSavepointStackImpl(
            @NonNull final Configuration configuration, @NonNull final HederaState state) {
        return new SavepointStackImpl(state, configuration);
    }

    @Provides
    @Singleton
    static Function<TransactionBody, HandleContext> provideHandleContextCreator(
            @NonNull final Metrics metrics,
            @NonNull final RecordCache recordCache,
            @NonNull final Configuration configuration,
            @NonNull final ConfigProvider configProvider,
            @NonNull final ServiceScopeLookup scopeLookup,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final SavepointStackImpl savepointStack) {
        final var parentRecordBuilder = new SingleTransactionRecordBuilderImpl(Instant.now());
        return body -> new HandleContextImpl(
                body,
                body.transactionIDOrThrow().accountIDOrThrow(),
                Key.DEFAULT,
                USER,
                parentRecordBuilder,
                savepointStack,
                new BaseHandleContextVerifier(configuration.getConfigData(HederaConfig.class), Map.of()),
                new RecordListBuilder(parentRecordBuilder),
                new TransactionChecker(6192, AccountID.DEFAULT, configProvider, metrics),
                dispatcher,
                scopeLookup,
                blockRecordManager,
                recordCache);
    }
}
