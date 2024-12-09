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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.INT64_INT64;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnDecoder.BURN_OUTPUT_FN;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/**
 * Translator class for burn calls
 */
public class BurnTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    /**
     * Selector for burnToken(address,uint64,int64[]) method.
     */
    public static final Function BURN_TOKEN_V1 = new Function("burnToken(address,uint64,int64[])", INT64_INT64);
    /**
     * Selector for burnToken(address,int64,int64[]) method.
     */
    public static final Function BURN_TOKEN_V2 = new Function("burnToken(address,int64,int64[])", INT64_INT64);

    BurnDecoder decoder;

    /**
     * Constructor for injection.
     * @param decoder the decoder to use for decoding burn calls
     */
    @Inject
    public BurnTranslator(@NonNull final BurnDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return attempt.isSelector(BURN_TOKEN_V1, BURN_TOKEN_V2);
    }

    @Override
    public Call callFrom(@NonNull HtsCallAttempt attempt) {
        final var body = bodyForClassic(attempt);
        final var isFungibleMint = body.tokenBurnOrThrow().serialNumbers().isEmpty();
        return new DispatchForResponseCodeHtsCall(
                attempt,
                body,
                isFungibleMint ? BurnTranslator::fungibleBurnGasRequirement : BurnTranslator::nftBurnGasRequirement,
                BURN_OUTPUT_FN);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(BURN_TOKEN_V1)) {
            return decoder.decodeBurn(attempt);
        } else {
            return decoder.decodeBurnV2(attempt);
        }
    }

    /**
     * @param body                          the transaction body to be dispatched
     * @param systemContractGasCalculator   the gas calculator for the system contract
     * @param enhancement                   the enhancement to use
     * @param payerId                       the payer of the transaction
     * @return                              the gas requirement
     */
    public static long fungibleBurnGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.BURN_FUNGIBLE, payerId);
    }

    /**
     * @param body                          the transaction body to be dispatched
     * @param systemContractGasCalculator   the gas calculator for the system contract
     * @param enhancement                   the enhancement to use
     * @param payerId                       the payer of the transaction
     * @return                              the gas requirement
     */
    public static long nftBurnGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.BURN_NFT, payerId);
    }
}
