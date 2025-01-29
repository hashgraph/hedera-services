/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.EXPIRY;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.EXPIRY_V2;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateDecoder.FAILURE_CUSTOMIZER;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.metrics.ContractMetrics;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethod;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethod.Category;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethod.Variant;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethodRegistry;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates ERC-721 {@code updateTokenExpiryInfo()} calls to the HTS system contract.
 */
@Singleton
public class UpdateExpiryTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    /**
     * Selector for updateTokenExpiryInfo(address, EXPIRY) method.
     */
    public static final SystemContractMethod UPDATE_TOKEN_EXPIRY_INFO_V1 = SystemContractMethod.declare(
                    "updateTokenExpiryInfo(address," + EXPIRY + ")", ReturnTypes.INT)
            .withVariant(Variant.V1)
            .withCategories(Category.UPDATE);
    /**
     * Selector for updateTokenExpiryInfo(address, EXPIRY_V2) method.
     */
    public static final SystemContractMethod UPDATE_TOKEN_EXPIRY_INFO_V2 = SystemContractMethod.declare(
                    "updateTokenExpiryInfo(address," + EXPIRY_V2 + ")", ReturnTypes.INT)
            .withVariant(Variant.V2)
            .withCategories(Category.UPDATE);

    private final UpdateDecoder decoder;

    /**
     * @param decoder the decoder used for token update calls
     */
    @Inject
    public UpdateExpiryTranslator(
            UpdateDecoder decoder,
            @NonNull final SystemContractMethodRegistry systemContractMethodRegistry,
            @NonNull final ContractMetrics contractMetrics) {
        super(SystemContractMethod.SystemContract.HTS, systemContractMethodRegistry, contractMetrics);
        this.decoder = decoder;

        registerMethods(UPDATE_TOKEN_EXPIRY_INFO_V1, UPDATE_TOKEN_EXPIRY_INFO_V2);
    }

    @Override
    public @NonNull Optional<SystemContractMethod> identifyMethod(@NonNull final HtsCallAttempt attempt) {
        return attempt.isMethod(UPDATE_TOKEN_EXPIRY_INFO_V1, UPDATE_TOKEN_EXPIRY_INFO_V2);
    }

    @Override
    public Call callFrom(@NonNull HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall(
                attempt, nominalBodyFor(attempt), UpdateTranslator::gasRequirement, FAILURE_CUSTOMIZER);
    }

    /**
     * @param body                          the transaction body to be dispatched
     * @param systemContractGasCalculator   the gas calculator for the system contract
     * @param enhancement                   the enhancement to use
     * @param payerId                       the payer of the transaction
     * @return the required gas
     */
    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.UPDATE, payerId);
    }

    private TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), UPDATE_TOKEN_EXPIRY_INFO_V1.selector())) {
            return decoder.decodeTokenUpdateExpiryV1(attempt);
        } else {
            return decoder.decodeTokenUpdateExpiryV2(attempt);
        }
    }
}
