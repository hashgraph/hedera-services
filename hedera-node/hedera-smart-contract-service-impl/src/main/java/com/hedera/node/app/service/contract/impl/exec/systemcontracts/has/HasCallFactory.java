// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.systemContractGasCalculatorOf;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.CallAddressChecks;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.CallFactory;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.CallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.SyntheticIds;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethodRegistry;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Factory to create a new {@link HasCallAttempt} for a given input and message frame.
 */
@Singleton
public class HasCallFactory implements CallFactory<HasCallAttempt> {
    private final SyntheticIds syntheticIds;
    private final CallAddressChecks addressChecks;
    private final VerificationStrategies verificationStrategies;
    private final SignatureVerifier signatureVerifier;
    private final List<CallTranslator<HasCallAttempt>> callTranslators;
    private final SystemContractMethodRegistry systemContractMethodRegistry;

    @Inject
    public HasCallFactory(
            @NonNull final SyntheticIds syntheticIds,
            @NonNull final CallAddressChecks addressChecks,
            @NonNull final VerificationStrategies verificationStrategies,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull @Named("HasTranslators") final List<CallTranslator<HasCallAttempt>> callTranslators,
            @NonNull final SystemContractMethodRegistry systemContractMethodRegistry) {
        this.syntheticIds = requireNonNull(syntheticIds);
        this.addressChecks = requireNonNull(addressChecks);
        this.verificationStrategies = requireNonNull(verificationStrategies);
        this.signatureVerifier = requireNonNull(signatureVerifier);
        this.callTranslators = requireNonNull(callTranslators);
        this.systemContractMethodRegistry = requireNonNull(systemContractMethodRegistry);
    }

    /**
     * Creates a new {@link HasCallAttempt} for the given input and message frame.
     *
     * @param input the input
     * @param frame the message frame
     * @return the new attempt
     * @throws RuntimeException if the call cannot be created
     */
    @Override
    public @NonNull HasCallAttempt createCallAttemptFrom(
            @NonNull ContractID contractID,
            @NonNull final Bytes input,
            @NonNull final FrameUtils.CallType callType,
            @NonNull final MessageFrame frame) {
        requireNonNull(input);
        requireNonNull(frame);
        final var enhancement = proxyUpdaterFor(frame).enhancement();
        return new HasCallAttempt(
                contractID,
                input,
                frame.getSenderAddress(),
                addressChecks.hasParentDelegateCall(frame),
                enhancement,
                configOf(frame),
                syntheticIds.converterFor(enhancement.nativeOperations()),
                verificationStrategies,
                signatureVerifier,
                systemContractGasCalculatorOf(frame),
                callTranslators,
                systemContractMethodRegistry,
                frame.isStatic());
    }
}
