// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.test.fixtures.builder;

import com.swirlds.component.framework.model.WiringModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.SecureRandom;
import java.util.Map;
import org.hiero.consensus.ConsensusLayerFactory;
import org.hiero.consensus.ConsensusLayerInputs;

/**
 * A {@link ConsensusLayerFactory} for tests that allows overriding the wiring model, the source of randomness, and the
 * properties passed to the modules.
 */
public class TestConsensusLayerFactory extends ConsensusLayerFactory {

    /**
     * Creates a new factory with the given inputs and overrides.
     *
     * @param inputs               inputs for the consensus layer
     * @param wiringModel          the wiring model to use, or {@code null} to create a new one
     * @param secureRandom         the source of randomness to use, or {@code null} to use a strong {@link SecureRandom}
     * @param additionalProperties additional properties passed to the modules
     */
    public TestConsensusLayerFactory(
            @NonNull final ConsensusLayerInputs inputs,
            @Nullable final WiringModel wiringModel,
            @Nullable final SecureRandom secureRandom,
            @NonNull final Map<String, Object> additionalProperties) {
        super(inputs, wiringModel, secureRandom, additionalProperties);
    }
}
