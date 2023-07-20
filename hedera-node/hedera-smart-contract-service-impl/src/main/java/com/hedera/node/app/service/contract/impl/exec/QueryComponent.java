package com.hedera.node.app.service.contract.impl.exec;

import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.contract.impl.state.BaseProxyWorldUpdater;
import com.hedera.node.app.spi.workflows.QueryContext;
import dagger.BindsInstance;
import dagger.Subcomponent;
import edu.umd.cs.findbugs.annotations.NonNull;

@Subcomponent(modules = {QueryModule.class})
@QueryScope
public interface QueryComponent {
    @Subcomponent.Factory
    interface Factory {
        QueryComponent create(@BindsInstance @NonNull QueryContext context);
    }

    BaseProxyWorldUpdater baseProxyWorldUpdater();
}
