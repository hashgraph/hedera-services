package com.hedera.node.app.service.contract.impl.exec;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleContextScope;
import com.hedera.node.app.service.contract.impl.exec.scope.Scope;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.swirlds.config.api.Configuration;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

@Module
interface TransactionModule {
    @Binds
    @TransactionScope
    Scope bindScope(@NonNull HandleContextScope handleContextScope);

    @Provides
    @TransactionScope
    static Configuration configuration(@NonNull final HandleContext context) {
        return requireNonNull(context).configuration();
    }

    @Provides
    @TransactionScope
    static Instant consensusTime(@NonNull final HandleContext context) {
        return requireNonNull(context).consensusNow();
    }
}
