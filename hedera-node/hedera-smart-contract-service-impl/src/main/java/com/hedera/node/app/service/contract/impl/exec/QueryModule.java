package com.hedera.node.app.service.contract.impl.exec;

import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.contract.impl.exec.scope.ExtFrameScope;
import com.hedera.node.app.service.contract.impl.exec.scope.ExtWorldScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleExtFrameScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleExtWorldScope;
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

import static java.util.Objects.requireNonNull;

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
    ExtWorldScope bindExtWorldScope(HandleExtWorldScope handleExtWorldScope);

    @Binds
    @QueryScope
    ExtFrameScope bindExtFrameScope(HandleExtFrameScope handleExtFrameScope);
}
