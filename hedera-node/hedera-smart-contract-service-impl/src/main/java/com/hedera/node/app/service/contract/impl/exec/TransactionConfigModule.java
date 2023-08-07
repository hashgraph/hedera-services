package com.hedera.node.app.service.contract.impl.exec;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.swirlds.config.api.Configuration;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

import static java.util.Objects.requireNonNull;

@Module
public interface TransactionConfigModule {
    @Provides
    @TransactionScope
    static Configuration provideConfiguration(@NonNull final HandleContext context) {
        return requireNonNull(context).configuration();
    }

    @Provides
    @TransactionScope
    static ContractsConfig provideContractsConfig(@NonNull final Configuration configuration) {
        return requireNonNull(configuration).getConfigData(ContractsConfig.class);
    }

    @Provides
    @TransactionScope
    static LedgerConfig provideLedgerConfig(@NonNull final Configuration configuration) {
        return requireNonNull(configuration).getConfigData(LedgerConfig.class);
    }

    @Provides
    @TransactionScope
    static StakingConfig provideStakingConfig(@NonNull final Configuration configuration) {
        return requireNonNull(configuration).getConfigData(StakingConfig.class);
    }

}
