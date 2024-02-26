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

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenUpdateNftsTransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

public class TokenUpdateNftValidator {
    private final TokenAttributesValidator validator;

    @Inject
    public TokenUpdateNftValidator(@NonNull final TokenAttributesValidator validator) {
        this.validator = validator;
    }

    public record ValidationResult(@NonNull Token token, @NonNull ResponseCodeEnum responseCodeEnum) {}

    @NonNull
    public ValidationResult validateSemantics(
            @NonNull final HandleContext context, @NonNull final TokenUpdateNftsTransactionBody op) {

        final var tokenStore = context.readableStore(ReadableTokenStore.class);
        final var tokenId = op.tokenOrThrow();
        final var token = getIfUsable(tokenId, tokenStore);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);

        // validate metadata
        if (op.hasMetadata()) {
            validator.validateTokenMetadata(op.metadata(), tokensConfig);
        }
        return new ValidationResult(token, OK);
    }
}
