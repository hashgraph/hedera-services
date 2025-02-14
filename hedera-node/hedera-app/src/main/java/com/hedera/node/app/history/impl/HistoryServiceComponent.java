// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.history.impl;

import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.node.app.history.handlers.HistoryHandlers;
import com.hedera.node.app.spi.AppContext;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import dagger.BindsInstance;
import dagger.Component;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Singleton
@Component(modules = HistoryModule.class)
public interface HistoryServiceComponent {
    @Component.Factory
    interface Factory {
        HistoryServiceComponent create(
                @BindsInstance HistoryLibrary library,
                @BindsInstance HistoryLibraryCodec codec,
                @BindsInstance AppContext appContext,
                @BindsInstance Executor executor,
                @BindsInstance Metrics metrics,
                @BindsInstance Consumer<HistoryProof> proofConsumer);
    }

    HistoryHandlers handlers();

    ProofControllers controllers();

    @Deprecated
    Supplier<Configuration> configSupplier();
}
