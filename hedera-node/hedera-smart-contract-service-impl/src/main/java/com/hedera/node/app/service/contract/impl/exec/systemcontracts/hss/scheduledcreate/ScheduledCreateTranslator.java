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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ScheduledCreateTranslator extends AbstractCallTranslator<HssCallAttempt> {
    public static final Function SCHEDULED_CREATE_FUNGIBLE = new Function(
            "scheduleCreateFungibleToken((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)),int64,int32)",
            ReturnTypes.RESPONSE_CODE_ADDRESS);
    public static final Function SCHEDULED_CREATE_NON_FUNGIBLE = new Function(
            "scheduleCreateNonFungibleToken((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)))",
            ReturnTypes.RESPONSE_CODE_ADDRESS);

    private static final Map<Function, ScheduledCreateDecoderFunction> scheduledCreateSelectors = new HashMap<>();

    @Inject
    public ScheduledCreateTranslator(@NonNull final ScheduledCreateDecoder decoder) {
        scheduledCreateSelectors.put(SCHEDULED_CREATE_FUNGIBLE, decoder::decodeScheduledCreateFT);
        scheduledCreateSelectors.put(SCHEDULED_CREATE_NON_FUNGIBLE, decoder::decodeScheduledCreateNFT);
    }

    @Override
    public boolean matches(@NonNull final HssCallAttempt attempt) {
        final var config = attempt.configuration().getConfigData(ContractsConfig.class);
        final var enabledScheduledTxn =
                config.scheduledTransactions().functionalitySet().contains(HederaFunctionality.TOKEN_CREATE);
        return attempt.isSelectorIfConfigEnabled(
                enabledScheduledTxn, SCHEDULED_CREATE_FUNGIBLE, SCHEDULED_CREATE_NON_FUNGIBLE);
    }

    @Override
    public Call callFrom(@NonNull final HssCallAttempt attempt) {
        return new ScheduledCreateCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                attempt.defaultVerificationStrategy(),
                requireNonNull(bodyForClassic(attempt)),
                attempt.senderId(),
                ScheduledCreateTranslator::gasRequirement,
                attempt.keySetFor());
    }

    /**
     * Calculates the gas requirement for a {@code SCHEDULE_CREATE} call.
     *
     * @param body the transaction body
     * @param systemContractGasCalculator the gas calculator
     * @param enhancement the enhancement
     * @param payerId the payer account ID
     * @return the gas requirement
     */
    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.SCHEDULE_CREATE, payerId);
    }

    private TransactionBody bodyForClassic(@NonNull final HssCallAttempt attempt) {
        return scheduledCreateSelectors.entrySet().stream()
                .filter(entry -> attempt.isSelector(entry.getKey()))
                .map(entry -> entry.getValue().decode(attempt))
                .findFirst()
                .orElse(TransactionBody.DEFAULT);
    }
}
