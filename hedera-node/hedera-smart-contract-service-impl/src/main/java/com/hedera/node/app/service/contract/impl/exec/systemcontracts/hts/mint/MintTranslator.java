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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintDecoder.MINT_OUTPUT_FN;

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
import javax.inject.Singleton;

/**
 * Translates {@code mintToken()} calls to the HTS system contract.
 */
@Singleton
public class MintTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    /** Selector for mintToken(address,uint64,bytes[]) method. */
    public static final Function MINT = new Function("mintToken(address,uint64,bytes[])", "(int64,int64,int64[])");
    /** Selector for mintToken(address,int64,bytes[]) method. */
    public static final Function MINT_V2 = new Function("mintToken(address,int64,bytes[])", "(int64,int64,int64[])");

    private final MintDecoder decoder;

    /**
     * @param decoder the decoder to use for mint calls
     */
    @Inject
    public MintTranslator(@NonNull final MintDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isSelector(MINT, MINT_V2);
    }

    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        final var body = bodyForClassic(attempt);
        final var isFungibleMint = body.tokenMintOrThrow().metadata().isEmpty();
        return new DispatchForResponseCodeHtsCall(
                attempt,
                body,
                isFungibleMint ? MintTranslator::fungibleMintGasRequirement : MintTranslator::nftMintGasRequirement,
                MINT_OUTPUT_FN);
    }

    public static long nftMintGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.MINT_NFT, payerId);
    }

    public static long fungibleMintGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.MINT_FUNGIBLE, payerId);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(MINT)) {
            return decoder.decodeMint(attempt);
        } else {
            return decoder.decodeMintV2(attempt);
        }
    }
}
