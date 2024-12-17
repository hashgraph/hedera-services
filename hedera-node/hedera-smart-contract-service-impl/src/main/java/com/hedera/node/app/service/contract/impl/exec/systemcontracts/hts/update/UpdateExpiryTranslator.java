/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;

/**
 * Translates ERC-721 {@code updateTokenExpiryInfo()} calls to the HTS system contract.
 */
public class UpdateExpiryTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    /**
     * Selector for updateTokenExpiryInfo(address, EXPIRY) method.
     */
    public static final Function UPDATE_TOKEN_EXPIRY_INFO_V1 =
            new Function("updateTokenExpiryInfo(address," + EXPIRY + ")", ReturnTypes.INT);
    /**
     * Selector for updateTokenExpiryInfo(address, EXPIRY_V2) method.
     */
    public static final Function UPDATE_TOKEN_EXPIRY_INFO_V2 =
            new Function("updateTokenExpiryInfo(address," + EXPIRY_V2 + ")", ReturnTypes.INT);

    private final UpdateDecoder decoder;

    /**
     * @param decoder the decoder used for token update calls
     */
    @Inject
    public UpdateExpiryTranslator(UpdateDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return attempt.isSelector(UPDATE_TOKEN_EXPIRY_INFO_V1, UPDATE_TOKEN_EXPIRY_INFO_V2);
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
