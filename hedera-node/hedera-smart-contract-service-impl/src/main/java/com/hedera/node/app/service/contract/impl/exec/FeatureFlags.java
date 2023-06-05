package com.hedera.node.app.service.contract.impl.exec;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Provides feature flags used to customize behavior of Hedera {@link org.hyperledger.besu.evm.operation.Operation} overrides.
 */
public interface FeatureFlags {
    boolean isImplicitCreationEnabled(@NonNull MessageFrame frame);
}
