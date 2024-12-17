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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator.ERC_GET_APPROVED;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator.HAPI_GET_APPROVED;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractRevertibleTokenViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Implements the token redirect {@code approved()} call of the HTS system contract.
 */
public class GetApprovedCall extends AbstractRevertibleTokenViewCall {

    private final long serialNo;
    private final boolean isErcCall;
    private final boolean isStaticCall;

    /**
     * @param gasCalculator the gas calculator to be used
     * @param enhancement the enhancement to be used
     * @param token the token against which the call executed
     * @param serialNo the serial number of the token
     * @param isErcCall true whether the call is ERC call
     * @param isStaticCall true whether the call is static call
     */
    public GetApprovedCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull HederaWorldUpdater.Enhancement enhancement,
            @Nullable Token token,
            final long serialNo,
            final boolean isErcCall,
            final boolean isStaticCall) {
        super(gasCalculator, enhancement, token);
        this.serialNo = serialNo;
        this.isErcCall = isErcCall;
        this.isStaticCall = isStaticCall;
    }

    @Override
    protected @NonNull PricedResult resultOfViewingToken(@NonNull final Token token) {
        requireNonNull(token);
        if (token.tokenType() != TokenType.NON_FUNGIBLE_UNIQUE) {
            if (!isStaticCall) {
                return gasOnly(
                        revertResult(INVALID_TOKEN_NFT_SERIAL_NUMBER, gasCalculator.viewGasRequirement()),
                        INVALID_TOKEN_NFT_SERIAL_NUMBER,
                        true);
            } else {
                return gasOnly(
                        revertResult(INVALID_TOKEN_ID, gasCalculator.viewGasRequirement()), INVALID_TOKEN_ID, true);
            }
        }
        final var nft = nativeOperations().getNft(token.tokenId().tokenNum(), serialNo);
        if (nft == null || !nft.hasNftId()) {
            return gasOnly(
                    revertResult(INVALID_TOKEN_NFT_SERIAL_NUMBER, gasCalculator.viewGasRequirement()),
                    INVALID_TOKEN_NFT_SERIAL_NUMBER,
                    true);
        }
        var spenderAddress = asHeadlongAddress(new byte[20]);
        if (nft.spenderId() != null) {
            final var spender = nativeOperations().getAccount(nft.spenderIdOrThrow());
            if (spender != null) {
                spenderAddress = headlongAddressOf(spender);
            }
        }
        return isErcCall
                ? gasOnly(
                        successResult(
                                ERC_GET_APPROVED.getOutputs().encodeElements(spenderAddress),
                                gasCalculator.viewGasRequirement()),
                        SUCCESS,
                        true)
                : gasOnly(
                        successResult(
                                HAPI_GET_APPROVED.getOutputs().encodeElements(SUCCESS.protoOrdinal(), spenderAddress),
                                gasCalculator.viewGasRequirement()),
                        SUCCESS,
                        true);
    }
}
