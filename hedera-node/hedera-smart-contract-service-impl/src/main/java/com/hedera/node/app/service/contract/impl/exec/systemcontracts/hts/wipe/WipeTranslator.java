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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall.FailureCustomizer.NOOP_CUSTOMIZER;

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
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class WipeTranslator extends AbstractCallTranslator<HtsCallAttempt> {

    /** Selector for wipeTokenAccount(address,address,uint32) method. */
    public static final Function WIPE_FUNGIBLE_V1 =
            new Function("wipeTokenAccount(address,address,uint32)", ReturnTypes.INT);
    /** Selector for wipeTokenAccount(address,address,int64) method. */
    public static final Function WIPE_FUNGIBLE_V2 =
            new Function("wipeTokenAccount(address,address,int64)", ReturnTypes.INT);
    /** Selector for wipeTokenAccountNFT(address,address,int64[]) method. */
    public static final Function WIPE_NFT =
            new Function("wipeTokenAccountNFT(address,address,int64[])", ReturnTypes.INT);

    private final WipeDecoder decoder;

    @Inject
    public WipeTranslator(@NonNull final WipeDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isSelector(WIPE_FUNGIBLE_V1, WIPE_FUNGIBLE_V2, WIPE_NFT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        final var body = bodyForClassic(attempt);
        final var isFungibleWipe = body.tokenWipeOrThrow().serialNumbers().isEmpty();
        return new DispatchForResponseCodeHtsCall(
                attempt,
                body,
                isFungibleWipe ? WipeTranslator::fungibleWipeGasRequirement : WipeTranslator::nftWipeGasRequirement,
                NOOP_CUSTOMIZER);
    }

    public static long fungibleWipeGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.WIPE_FUNGIBLE, payerId);
    }

    public static long nftWipeGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.WIPE_NFT, payerId);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(WIPE_FUNGIBLE_V1)) {
            return decoder.decodeWipeFungibleV1(attempt);
        } else if (attempt.isSelector(WIPE_FUNGIBLE_V2)) {
            return decoder.decodeWipeFungibleV2(attempt);
        } else {
            return decoder.decodeWipeNonFungible(attempt);
        }
    }
}
