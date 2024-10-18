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

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.EXPIRY;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.EXPIRY_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_WITH_METADATA;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.TOKEN_KEY;

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
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

public class UpdateTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    private static final String UPDATE_TOKEN_INFO_STRING = "updateTokenInfo(address,";
    private static final String HEDERA_TOKEN_STRUCT =
            "(string,string,address,string,bool,uint32,bool," + TOKEN_KEY + ARRAY_BRACKETS + "," + EXPIRY + ")";
    private static final String HEDERA_TOKEN_STRUCT_V2 =
            "(string,string,address,string,bool,int64,bool," + TOKEN_KEY + ARRAY_BRACKETS + "," + EXPIRY + ")";
    private static final String HEDERA_TOKEN_STRUCT_V3 =
            "(string,string,address,string,bool,int64,bool," + TOKEN_KEY + ARRAY_BRACKETS + "," + EXPIRY_V2 + ")";
    public static final Function TOKEN_UPDATE_INFO_FUNCTION_V1 =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT + ")", ReturnTypes.INT);
    public static final Function TOKEN_UPDATE_INFO_FUNCTION_V2 =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT_V2 + ")", ReturnTypes.INT);
    public static final Function TOKEN_UPDATE_INFO_FUNCTION_V3 =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT_V3 + ")", ReturnTypes.INT);
    public static final Function TOKEN_UPDATE_INFO_FUNCTION_WITH_METADATA =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_WITH_METADATA + ")", ReturnTypes.INT);

    private static final Map<Function, UpdateDecoderFunction> updateSelectorsMap = new HashMap<>();

    @Inject
    public UpdateTranslator(final UpdateDecoder decoder) {
        updateSelectorsMap.put(TOKEN_UPDATE_INFO_FUNCTION_V1, decoder::decodeTokenUpdateV1);
        updateSelectorsMap.put(TOKEN_UPDATE_INFO_FUNCTION_V2, decoder::decodeTokenUpdateV2);
        updateSelectorsMap.put(TOKEN_UPDATE_INFO_FUNCTION_V3, decoder::decodeTokenUpdateV3);
        updateSelectorsMap.put(TOKEN_UPDATE_INFO_FUNCTION_WITH_METADATA, decoder::decodeTokenUpdateWithMetadata);
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        final boolean metadataSupport =
                attempt.configuration().getConfigData(ContractsConfig.class).metadataKeyAndFieldEnabled();

        return updateSelectorsMap.keySet().stream()
                .anyMatch(selector -> selector.equals(TOKEN_UPDATE_INFO_FUNCTION_WITH_METADATA)
                        ? attempt.isSelectorIfConfigEnabled(selector, metadataSupport)
                        : attempt.isSelector(selector));
    }

    @Override
    public Call callFrom(@NonNull HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall(
                attempt, nominalBodyFor(attempt), UpdateTranslator::gasRequirement, UpdateDecoder.FAILURE_CUSTOMIZER);
    }

    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.UPDATE, payerId);
    }

    private TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        return updateSelectorsMap.entrySet().stream()
                .filter(entry -> attempt.isSelector(entry.getKey()))
                .map(entry -> entry.getValue().decode(attempt))
                .findFirst()
                .orElse(null);
    }
}
