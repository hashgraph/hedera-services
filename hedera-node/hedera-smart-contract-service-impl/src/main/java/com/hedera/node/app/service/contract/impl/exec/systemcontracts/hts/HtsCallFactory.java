/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.List;
import java.util.function.Function;

/**
 * Factory to create a new {@link HtsCallAttempt} for a given input and message frame.
 */
@Singleton
public class HtsCallFactory {
    private final SyntheticIds syntheticIds;
    private final HtsCallAddressChecks addressChecks;
    private final DecodingStrategies decodingStrategies;
    private final VerificationStrategies verificationStrategies;
    private final List<Function<HtsCallAttempt, HtsCall>> callAttemptTranslators;

    @Inject
    public HtsCallFactory(
            @NonNull final SyntheticIds syntheticIds,
            @NonNull final HtsCallAddressChecks addressChecks,
            @NonNull final DecodingStrategies decodingStrategies,
            @NonNull final VerificationStrategies verificationStrategies,
            @NonNull final List<Function<HtsCallAttempt, HtsCall>> callAttemptTranslators) {
        this.syntheticIds = requireNonNull(syntheticIds);
        this.addressChecks = requireNonNull(addressChecks);
        this.decodingStrategies = requireNonNull(decodingStrategies);
        this.verificationStrategies = requireNonNull(verificationStrategies);
        this.callAttemptTranslators = requireNonNull(callAttemptTranslators);
    }

    /**
     * Creates a new {@link HtsCall} for the given input and message frame.
     *
     * @param input the input
     * @param frame the message frame
     * @return the new attempt
     * @throws RuntimeException if the call cannot be created
     */
    public @NonNull HtsCall createCallFrom(@NonNull final Bytes input, @NonNull final MessageFrame frame) {
        requireNonNull(input);
        requireNonNull(frame);
        final var enhancement = proxyUpdaterFor(frame).enhancement();
        final var attempt = new HtsCallAttempt(
                input,
                frame.getSenderAddress(),
                addressChecks.hasParentDelegateCall(frame),
                enhancement,
                configOf(frame),
                decodingStrategies,
                syntheticIds.converterFor(enhancement.nativeOperations()),
                verificationStrategies, callAttemptTranslators);
        return requireNonNull(attempt.asExecutableCall());
    }
}
