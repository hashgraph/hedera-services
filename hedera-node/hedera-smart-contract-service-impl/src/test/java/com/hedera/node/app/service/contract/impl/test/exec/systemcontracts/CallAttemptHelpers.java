/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_LONG_ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_BESU_ADDRESS;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.CallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import com.swirlds.config.api.Configuration;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;

/**
 * Helper utility class to generate {@link HtsCallAttempt} object in different scenarios
 */
public final class CallAttemptHelpers {

    /**
     * @param function the selector to match against
     * @param translator the translator for this specific call attempt
     * @param enhancement the enhancement that is used
     * @param addressIdConverter the address ID converter for this call
     * @param verificationStrategies the verification strategy currently used
     * @param gasCalculator the gas calculator used for the system contract
     * @return the call attempt
     */
    public static HtsCallAttempt prepareHtsAttemptWithSelector(
            final Function function,
            final CallTranslator<HtsCallAttempt> translator,
            final HederaWorldUpdater.Enhancement enhancement,
            final AddressIdConverter addressIdConverter,
            final VerificationStrategies verificationStrategies,
            final SystemContractGasCalculator gasCalculator) {
        final var input = Bytes.wrap(function.selector());

        return new HtsCallAttempt(
                input,
                OWNER_BESU_ADDRESS,
                OWNER_BESU_ADDRESS,
                false,
                enhancement,
                DEFAULT_CONFIG,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                List.of(translator),
                false);
    }

    /**
     * @param function the selector to match against
     * @param translator the translator for this specific call attempt
     * @param enhancement the enhancement that is used
     * @param addressIdConverter the address ID converter for this call
     * @param verificationStrategies the verification strategy currently used
     * @param gasCalculator the gas calculator used for the system contract
     * @return the call attempt
     */
    public static HtsCallAttempt prepareHtsAttemptWithSelectorForRedirect(
            final Function function,
            final CallTranslator<HtsCallAttempt> translator,
            final HederaWorldUpdater.Enhancement enhancement,
            final AddressIdConverter addressIdConverter,
            final VerificationStrategies verificationStrategies,
            final SystemContractGasCalculator gasCalculator) {
        final var input = TestHelpers.bytesForRedirect(function.selector(), NON_SYSTEM_LONG_ZERO_ADDRESS);

        return new HtsCallAttempt(
                input,
                OWNER_BESU_ADDRESS,
                OWNER_BESU_ADDRESS,
                false,
                enhancement,
                DEFAULT_CONFIG,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                List.of(translator),
                false);
    }

    /**
     * @param function the selector to match against
     * @param translator the translator for this specific call attempt
     * @param enhancement the enhancement that is used
     * @param addressIdConverter the address ID converter for this call
     * @param verificationStrategies the verification strategy currently used
     * @param gasCalculator the gas calculator used for the system contract
     * @param config the current configuration that is used
     * @return the call attempt
     */
    public static HtsCallAttempt prepareHtsAttemptWithSelectorForRedirectWithConfig(
            final Function function,
            final CallTranslator<HtsCallAttempt> translator,
            final HederaWorldUpdater.Enhancement enhancement,
            final AddressIdConverter addressIdConverter,
            final VerificationStrategies verificationStrategies,
            final SystemContractGasCalculator gasCalculator,
            final Configuration config) {
        final var input = TestHelpers.bytesForRedirect(function.selector(), NON_SYSTEM_LONG_ZERO_ADDRESS);

        return new HtsCallAttempt(
                input,
                OWNER_BESU_ADDRESS,
                OWNER_BESU_ADDRESS,
                false,
                enhancement,
                config,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                List.of(translator),
                false);
    }

    /**
     * @param function the selector to match against
     * @param translator the translator for this specific call attempt
     * @param enhancement the enhancement that is used
     * @param addressIdConverter the address ID converter for this call
     * @param verificationStrategies the verification strategy currently used
     * @param gasCalculator the gas calculator used for the system contract
     * @param config the current configuration that is used
     * @return the call attempt
     */
    public static HtsCallAttempt prepareHtsAttemptWithSelectorAndCustomConfig(
            final Function function,
            final CallTranslator<HtsCallAttempt> translator,
            final HederaWorldUpdater.Enhancement enhancement,
            final AddressIdConverter addressIdConverter,
            final VerificationStrategies verificationStrategies,
            final SystemContractGasCalculator gasCalculator,
            final Configuration config) {
        final var input = Bytes.wrap(function.selector());

        return new HtsCallAttempt(
                input,
                OWNER_BESU_ADDRESS,
                OWNER_BESU_ADDRESS,
                false,
                enhancement,
                config,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                List.of(translator),
                false);
    }

    /**
     * @param function the selector to match against
     * @param translator the translator for this specific call attempt
     * @param enhancement the enhancement that is used
     * @param addressIdConverter the address ID converter for this call
     * @param verificationStrategies the verification strategy currently used
     * @param gasCalculator the gas calculator used for the system contract
     * @return the call attempt
     */
    public static HasCallAttempt prepareHasAttemptWithSelector(
            final Function function,
            final CallTranslator<HasCallAttempt> translator,
            final HederaWorldUpdater.Enhancement enhancement,
            final AddressIdConverter addressIdConverter,
            final VerificationStrategies verificationStrategies,
            final SignatureVerifier signatureVerifier,
            final SystemContractGasCalculator gasCalculator) {
        return prepareHasAttemptWithSelectorAndCustomConfig(
                function,
                translator,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                signatureVerifier,
                gasCalculator,
                DEFAULT_CONFIG);
    }

    public static HasCallAttempt prepareHasAttemptWithSelectorAndCustomConfig(
            final Function function,
            final CallTranslator<HasCallAttempt> translator,
            final HederaWorldUpdater.Enhancement enhancement,
            final AddressIdConverter addressIdConverter,
            final VerificationStrategies verificationStrategies,
            final SignatureVerifier signatureVerifier,
            final SystemContractGasCalculator gasCalculator,
            final Configuration config) {
        return prepareHasAttemptWithSelectorAndInputAndCustomConfig(
                function,
                TestHelpers.bytesForRedirectAccount(function.selector(), NON_SYSTEM_LONG_ZERO_ADDRESS),
                translator,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                signatureVerifier,
                gasCalculator,
                config);
    }

    public static HasCallAttempt prepareHasAttemptWithSelectorAndInputAndCustomConfig(
            final Function function,
            final Bytes input,
            final CallTranslator<HasCallAttempt> translator,
            final HederaWorldUpdater.Enhancement enhancement,
            final AddressIdConverter addressIdConverter,
            final VerificationStrategies verificationStrategies,
            final SignatureVerifier signatureVerifier,
            final SystemContractGasCalculator gasCalculator,
            final Configuration config) {
        return new HasCallAttempt(
                input,
                OWNER_BESU_ADDRESS,
                false,
                enhancement,
                config,
                addressIdConverter,
                verificationStrategies,
                signatureVerifier,
                gasCalculator,
                List.of(translator),
                false);
    }

    /**
     * @param function the selector to match against
     * @param translator the translator for this specific call attempt
     * @param enhancement the enhancement that is used
     * @param addressIdConverter the address ID converter for this call
     * @param verificationStrategies the verification strategy currently used
     * @param gasCalculator the gas calculator used for the system contract
     * @param config the current configuration that is used
     * @return the call attempt
     */
    public static HssCallAttempt prepareHssAttemptWithSelectorAndCustomConfig(
            final Function function,
            final CallTranslator<HssCallAttempt> translator,
            final HederaWorldUpdater.Enhancement enhancement,
            final AddressIdConverter addressIdConverter,
            final VerificationStrategies verificationStrategies,
            final SystemContractGasCalculator gasCalculator,
            final Configuration config) {
        final var input = Bytes.wrap(function.selector());

        return new HssCallAttempt(
                input,
                OWNER_BESU_ADDRESS,
                false,
                enhancement,
                config,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                List.of(translator),
                false);
    }

    public static HssCallAttempt prepareHssAttemptWithBytesAndCustomConfig(
            final Bytes input,
            final CallTranslator<HssCallAttempt> translator,
            final HederaWorldUpdater.Enhancement enhancement,
            final AddressIdConverter addressIdConverter,
            final VerificationStrategies verificationStrategies,
            final SystemContractGasCalculator gasCalculator,
            final Configuration config) {

        return new HssCallAttempt(
                input,
                OWNER_BESU_ADDRESS,
                false,
                enhancement,
                config,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                List.of(translator),
                false);
    }
}
