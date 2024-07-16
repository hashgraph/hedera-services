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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.updatetokencustomfees;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FIXED_FEE_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE_V2;

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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;

public class UpdateTokenCustomFeesTranslator extends AbstractCallTranslator<HtsCallAttempt> {

    private static final String UPDATE_FUNGIBLE_TOKEN_CUSTOM_FEES_STRING = "updateFungibleTokenCustomFees(address,"
            + FIXED_FEE_V2 + ARRAY_BRACKETS + "," + FRACTIONAL_FEE_V2 + ARRAY_BRACKETS + ")";
    private static final String UPDATE_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_STRING =
            "updateNonFungibleTokenCustomFees(address," + FIXED_FEE_V2 + ARRAY_BRACKETS + "," + ROYALTY_FEE_V2
                    + ARRAY_BRACKETS + ")";
    public static final Function UPDATE_FUNGIBLE_TOKEN_CUSTOM_FEES_FUNCTION =
            new Function(UPDATE_FUNGIBLE_TOKEN_CUSTOM_FEES_STRING, ReturnTypes.INT);
    public static final Function UPDATE_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_FUNCTION =
            new Function(UPDATE_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_STRING, ReturnTypes.INT);

    private UpdateTokenCustomFeesDecoder decoder;

    @Inject
    public UpdateTokenCustomFeesTranslator(UpdateTokenCustomFeesDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), UPDATE_FUNGIBLE_TOKEN_CUSTOM_FEES_FUNCTION.selector())
                || Arrays.equals(attempt.selector(), UPDATE_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_FUNCTION.selector());
    }

    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.UPDATE_TOKEN_CUSTOM_FEES, payerId);
    }

    @Override
    public Call callFrom(@NonNull HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall(attempt, nominalBodyFor(attempt), UpdateTranslator::gasRequirement);
    }

    private TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), UPDATE_FUNGIBLE_TOKEN_CUSTOM_FEES_FUNCTION.selector())) {
            return decoder.decodeUpdateFungibleTokenCustomFees(attempt);
        } else {
            return decoder.decodeUpdateNonFungibleTokenCustomFees(attempt);
        }
    }
}
