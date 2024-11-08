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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isauthorized;

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

@Singleton
public class IsAuthorizedTranslator extends AbstractCallTranslator<HasCallAttempt> {

    public static final Function IS_AUTHORIZED =
            new Function("isAuthorized(address,bytes,bytes)", ReturnTypes.RESPONSE_CODE64_BOOL);
    private static final int ADDRESS_ARG = 0;
    private static final int MESSAGE_ARG = 1;
    private static final int SIGNATURE_BLOB_ARG = 2;

    private final CustomGasCalculator customGasCalculator;

    @Inject
    public IsAuthorizedTranslator(
            @ServicesV051 @NonNull final FeatureFlags featureFlags,
            @NonNull final CustomGasCalculator customGasCalculator) {
        requireNonNull(featureFlags, "featureFlags");
        this.customGasCalculator = requireNonNull(customGasCalculator);
    }

    @Override
    public boolean matches(@NonNull HasCallAttempt attempt) {
        requireNonNull(attempt, "attempt");

        final boolean callEnabled = attempt.configuration()
                .getConfigData(ContractsConfig.class)
                .systemContractAccountServiceIsAuthorizedEnabled();
        return callEnabled && attempt.isSelector(IS_AUTHORIZED);
    }

    @Override
    public Call callFrom(@NonNull HasCallAttempt attempt) {
        requireNonNull(attempt, "attempt");

        if (attempt.isSelector(IS_AUTHORIZED)) {

            final var call = IS_AUTHORIZED.decodeCall(attempt.inputBytes());
            final var address = (Address) call.get(ADDRESS_ARG);
            final var message = (byte[]) call.get(MESSAGE_ARG);
            final var signatureBlob = (byte[]) call.get(SIGNATURE_BLOB_ARG);

            return new IsAuthorizedCall(attempt, address, message, signatureBlob, customGasCalculator);
        }
        return null;
    }
}
