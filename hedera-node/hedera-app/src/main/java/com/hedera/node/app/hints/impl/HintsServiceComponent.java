// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.hints.impl;

import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.node.app.hints.handlers.HintsHandlers;
import com.hedera.node.app.spi.AppContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import dagger.BindsInstance;
import dagger.Component;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Singleton
@Component(modules = HintsModule.class)
public interface HintsServiceComponent {
    @Component.Factory
    interface Factory {
        HintsServiceComponent create(
                @BindsInstance HintsLibrary hintsLibrary,
                @BindsInstance AppContext appContext,
                @BindsInstance Executor executor,
                @BindsInstance Metrics metrics);
    }

    HintsHandlers handlers();

    HintsControllers controllers();

    HintsContext signingContext();

    ConcurrentMap<Bytes, HintsContext.Signing> signings();

    @Deprecated
    Supplier<Configuration> configSupplier();
}
