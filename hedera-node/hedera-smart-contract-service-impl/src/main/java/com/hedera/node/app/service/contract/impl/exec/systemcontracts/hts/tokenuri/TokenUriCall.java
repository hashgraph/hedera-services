/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractNftViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Implements the token redirect {@code tokenURI()} call of the HTS system contract.
 */
public class TokenUriCall extends AbstractNftViewCall {

    public static final String URI_QUERY_NON_EXISTING_TOKEN_ERROR = "ERC721Metadata: URI query for nonexistent token";

    public TokenUriCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            final long serialNo) {
        super(gasCalculator, enhancement, token, serialNo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull FullResult resultOfViewingNft(@NonNull final Token token, final Nft nft) {
        requireNonNull(token);
        // #10568 - We add this check to match mono behavior
        if (token.tokenType() == TokenType.FUNGIBLE_COMMON) {
            return revertResult(ResponseCodeEnum.INVALID_TOKEN_ID, gasCalculator.viewGasRequirement());
        }

        String metadata;
        if (nft != null) {
            metadata = new String(nft.metadata().toByteArray());
        } else {
            metadata = URI_QUERY_NON_EXISTING_TOKEN_ERROR;
        }
        return successResult(
                TokenUriTranslator.TOKEN_URI.getOutputs().encodeElements(metadata), gasCalculator.viewGasRequirement());
    }

    @Override
    protected @NonNull FullResult resultOfViewingToken(@NonNull final Token token) {
        requireNonNull(token);
        final var nft = nativeOperations().getNft(token.tokenIdOrThrow().tokenNum(), serialNo);

        return resultOfViewingNft(token, nft);
    }
}
