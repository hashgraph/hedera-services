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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenuri;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.haltResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;

import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Implements the token redirect {@code tokenURI()} call of the HTS system contract.
 */
public class TokenUriCall extends AbstractHtsCall {
    public static final String URI_QUERY_NON_EXISTING_TOKEN_ERROR = "ERC721Metadata: URI query for nonexistent token";

    private final long serialNo;

    @Nullable
    private final Token token;

    public TokenUriCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            final long serialNo) {
        super(gasCalculator, enhancement, true);
        this.token = token;
        this.serialNo = serialNo;
    }

    @Override
    public boolean allowsStaticFrame() {
        return true;
    }

    @Override
    public @NonNull PricedResult execute(MessageFrame frame) {
        var metadata = URI_QUERY_NON_EXISTING_TOKEN_ERROR;
        if (token != null) {
            if (token.tokenType() == FUNGIBLE_COMMON) {
                // (FUTURE) consider removing this pattern, but for now match
                // mono-service by halting on an invalid token type
                return gasOnly(
                        haltResult(
                                HederaExceptionalHaltReason.ERROR_DECODING_PRECOMPILE_INPUT,
                                gasCalculator.viewGasRequirement()),
                        INVALID_TOKEN_ID,
                        false);
            }
            final var nft = nativeOperations().getNft(token.tokenIdOrThrow().tokenNum(), serialNo);
            if (nft != null) {
                metadata = new String(nft.metadata().toByteArray());
            }
        }
        return gasOnly(
                successResult(
                        TokenUriTranslator.TOKEN_URI.getOutputs().encodeElements(metadata),
                        gasCalculator.viewGasRequirement()),
                SUCCESS,
                true);
    }
}
