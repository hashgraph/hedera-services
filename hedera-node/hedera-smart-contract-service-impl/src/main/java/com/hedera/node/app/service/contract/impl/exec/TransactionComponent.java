package com.hedera.node.app.service.contract.impl.exec;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.state.BaseProxyWorldUpdater;
import com.hedera.node.app.spi.meta.bni.Scope;
import com.hedera.node.config.data.ContractsConfig;
import dagger.BindsInstance;
import dagger.Subcomponent;
import edu.umd.cs.findbugs.annotations.NonNull;

@TransactionScope
@Subcomponent
public interface TransactionComponent {
    @Subcomponent.Factory
    interface Factory {
        TransactionComponent create(
                @BindsInstance @NonNull Scope scope,
                @BindsInstance @NonNull ContractsConfig contractsConfig);
    }

    BaseProxyWorldUpdater baseProxyWorldUpdater();
}
