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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isauthorizedraw;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.annotations.ServicesV051;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code isAuthorizedRaw()} calls to the HAS system contract. HIP-632.
 */
@Singleton
public class IsAuthorizedRawTranslator extends AbstractCallTranslator<HasCallAttempt> {

    public static final Function IS_AUTHORIZED_RAW =
            new Function("isAuthorizedRaw(address,bytes,bytes)", ReturnTypes.BOOL);
    private static final int ADDRESS_ARG = 0;
    private static final int HASH_ARG = 1;
    private static final int SIGNATURE_ARG = 2;

    private final CustomGasCalculator customGasCalculator;

    @Inject
    public IsAuthorizedRawTranslator(
            @ServicesV051 @NonNull final FeatureFlags featureFlags,
            @NonNull final CustomGasCalculator customGasCalculator) {
        requireNonNull(featureFlags, "featureFlags");
        this.customGasCalculator = requireNonNull(customGasCalculator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt, "attempt");

        final boolean callEnabled = attempt.configuration()
                .getConfigData(ContractsConfig.class)
                .systemContractAccountServiceIsAuthorizedRawEnabled();
        return callEnabled && attempt.isSelector(IS_AUTHORIZED_RAW);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt, "attempt");

        if (attempt.isSelector(IS_AUTHORIZED_RAW)) {

            final var call = IS_AUTHORIZED_RAW.decodeCall(attempt.inputBytes());
            final var address = (Address) call.get(ADDRESS_ARG);
            final var messageHash = (byte[]) call.get(HASH_ARG);
            final var signature = (byte[]) call.get(SIGNATURE_ARG);

            return new IsAuthorizedRawCall(attempt, address, messageHash, signature, customGasCalculator);
        }
        return null;
    }
}
