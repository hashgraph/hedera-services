package com.hedera.node.app.integration;

import com.hedera.node.app.integration.facilities.ReplayAdvancingConsensusNow;
import com.hedera.node.app.integration.facilities.ReplayIds;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.LongSupplier;

@Singleton
public class ReplayFacilityHandleContext implements HandleContext {
    private final ReplayIds replayIds;
    private final AttributeValidator attributeValidator;
    private final ReplayAdvancingConsensusNow consensusNow;

    @Inject
    public ReplayFacilityHandleContext(
            @NonNull final ReplayIds replayIds,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final ReplayAdvancingConsensusNow consensusNow) {
        this.replayIds = replayIds;
        this.consensusNow = consensusNow;
        this.attributeValidator = attributeValidator;
    }

    @Override
    public Instant consensusNow() {
        return consensusNow.get();
    }

    @Override
    public LongSupplier newEntityNumSupplier() {
        return replayIds;
    }

    @Override
    public AttributeValidator attributeValidator() {
        return attributeValidator;
    }

    @Override
    public ExpiryValidator expiryValidator() {
        throw new AssertionError("Not implemented");
    }
}
