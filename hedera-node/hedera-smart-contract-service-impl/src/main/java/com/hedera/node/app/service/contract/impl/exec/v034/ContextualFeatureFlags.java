package com.hedera.node.app.service.contract.impl.exec.v034;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Objects;

import static com.hedera.node.app.service.contract.impl.exec.TransactionProcessor.CONFIG_CONTEXT_VARIABLE;

public class ContextualFeatureFlags implements FeatureFlags {
    @Override
    public boolean isImplicitCreationEnabled(@NonNull MessageFrame frame) {
        final Configuration config = Objects.requireNonNull(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE));
        return config.getConfigData(AutoCreationConfig.class).enabled()
                && config.getConfigData(LazyCreationConfig.class).enabled();
    }
}
