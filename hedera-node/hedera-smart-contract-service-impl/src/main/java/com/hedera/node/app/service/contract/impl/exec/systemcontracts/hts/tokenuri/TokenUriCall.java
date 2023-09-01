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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asExactLongValueOrZero;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractNftViewCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;

/**
 * Implements the token redirect {@code tokenURI()} call of the HTS system contract.
 */
public class TokenUriCall extends AbstractNftViewCall {
    public static final Function TOKEN_URI = new Function("tokenURI(uint256)", ReturnTypes.STRING);

    public TokenUriCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            final long serialNo) {
        super(enhancement, token, serialNo);
    }

    @Override
    protected @NonNull HederaSystemContract.FullResult resultOfViewingNft(
            @NonNull final Token token, @NonNull final Nft nft) {
        requireNonNull(token);
        requireNonNull(nft);

        // TODO - gas calculation
        final var metadata = new String(nft.metadata().toByteArray());
        return successResult(TOKEN_URI.getOutputs().encodeElements(metadata), 0L);
    }

    /**
     * Indicates if the given {@code selector} is a selector for {@link TokenUriCall}.
     *
     * @param selector the selector to check
     * @return {@code true} if the given {@code selector} is a selector for {@link TokenUriCall}
     */
    public static boolean matches(@NonNull final byte[] selector) {
        requireNonNull(selector);
        return Arrays.equals(selector, TOKEN_URI.selector());
    }

    /**
     * Constructs a {@link TokenUriCall} from the given {@code attempt}.
     *
     * @param attempt the attempt to construct from
     * @return the constructed {@link TokenUriCall}
     */
    public static TokenUriCall from(@NonNull final HtsCallAttempt attempt) {
        // Since zero is never a valid serial number, if we clamp the passed value, the result
        // will be a revert with INVALID_NFT_ID as reason
        final var serialNo = asExactLongValueOrZero(
                TOKEN_URI.decodeCall(attempt.input().toArrayUnsafe()).get(0));
        return new TokenUriCall(attempt.enhancement(), attempt.redirectToken(), serialNo);
    }
}
