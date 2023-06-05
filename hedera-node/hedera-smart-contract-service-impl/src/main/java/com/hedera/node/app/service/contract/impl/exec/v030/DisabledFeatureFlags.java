package com.hedera.node.app.service.contract.impl.exec.v030;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame;

import javax.inject.Singleton;

@Singleton
public class DisabledFeatureFlags implements FeatureFlags {
    @Override
    public boolean isImplicitCreationEnabled(@NonNull MessageFrame frame) {
        return false;
    }
}
