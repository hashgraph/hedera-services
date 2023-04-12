package com.hedera.node.app.integration;

import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
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
    private final ReplayAssetRecording assetRecording;

    @Inject
    public ReplayFacilityHandleContext(@NonNull final ReplayAssetRecording assetRecording) {
        this.assetRecording = assetRecording;
    }

    @Override
    public Instant consensusNow() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public LongSupplier newEntityNumSupplier() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public AttributeValidator attributeValidator() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public ExpiryValidator expiryValidator() {
        throw new AssertionError("Not implemented");
    }
}
