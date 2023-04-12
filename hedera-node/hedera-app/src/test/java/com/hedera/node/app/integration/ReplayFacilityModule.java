package com.hedera.node.app.integration;

import com.hedera.node.app.service.admin.impl.components.AdminComponent;
import com.hedera.node.app.service.consensus.impl.components.ConsensusComponent;
import com.hedera.node.app.service.contract.impl.components.ContractComponent;
import com.hedera.node.app.service.file.impl.components.FileComponent;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.hedera.node.app.service.network.impl.components.NetworkComponent;
import com.hedera.node.app.service.schedule.impl.components.ScheduleComponent;
import com.hedera.node.app.service.token.impl.components.TokenComponent;
import com.hedera.node.app.service.util.impl.components.UtilComponent;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.test.mocks.MockAccountNumbers;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Singleton;
import java.io.File;
import java.nio.file.Paths;
import java.util.Map;

@Module
public interface ReplayFacilityModule {
    String REPLAY_ASSETS_DIR = "src/test/resources/replay-assets";

    @Binds
    @Singleton
    HandleContext bindHandleContext(ReplayFacilityHandleContext context);

    @Binds
    @Singleton
    TransactionDispatcher bindTransactionDispatcher(ReplayFacilityTransactionDispatcher transactionDispatcher);

    @Provides
    @Singleton
    static HederaAccountNumbers provideHederaAccountNumbers() {
        return new MockAccountNumbers();
    }

    @Provides
    @Singleton
    static ReplayAssetRecording provideAssetRecording() {
        return new ReplayAssetRecording(new File(REPLAY_ASSETS_DIR));
    }

    @Provides
    @Singleton
    @CompositeProps
    static PropertySource provideCompositeProps() {
        return new BootstrapProperties(false);
    }

    @Provides
    @Singleton
    static MapWritableStates provideMapWritableStates() {
        final var builder = MapWritableStates.builder();

        return builder.build();
    }
}
