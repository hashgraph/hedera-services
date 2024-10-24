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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.rejecttokens;

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
import javax.inject.Inject;

public class RejectTokensTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    public static final Function TOKEN_REJECT =
            new Function("rejectTokens(address,address[],(address,int64)[])", ReturnTypes.INT_64);
    public static final Function HRC_TOKEN_REJECT_FT = new Function("rejectTokenFT()", ReturnTypes.INT_64);
    public static final Function HRC_TOKEN_REJECT_NFT = new Function("rejectTokenNFTs(int64[])", ReturnTypes.INT_64);

    private final RejectTokensDecoder decoder;

    @Inject
    public RejectTokensTranslator(@NonNull final RejectTokensDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        final var rejectEnabled =
                attempt.configuration().getConfigData(ContractsConfig.class).systemContractRejectTokensEnabled();
        return attempt.isTokenRedirect()
                ? attempt.isSelectorIfConfigEnabled(HRC_TOKEN_REJECT_FT, rejectEnabled)
                        || attempt.isSelectorIfConfigEnabled(HRC_TOKEN_REJECT_NFT, rejectEnabled)
                : attempt.isSelectorIfConfigEnabled(TOKEN_REJECT, rejectEnabled);
    }

    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall(attempt, bodyFor(attempt), RejectTokensTranslator::gasRequirement);
    }

    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.TOKEN_REJECT, payerId);
    }

    private TransactionBody bodyFor(@NonNull HtsCallAttempt attempt) {
        return attempt.isSelector(TOKEN_REJECT) ? bodyForClassic(attempt) : bodyForHRC(attempt);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        return decoder.decodeTokenRejects(attempt);
    }

    private TransactionBody bodyForHRC(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(HRC_TOKEN_REJECT_FT)) {
            return decoder.decodeHrcTokenRejectFT(attempt);
        } else {
            return decoder.decodeHrcTokenRejectNFT(attempt);
        }
    }
}
