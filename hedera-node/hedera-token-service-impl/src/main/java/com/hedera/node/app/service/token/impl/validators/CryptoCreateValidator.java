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

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ALIAS_ALREADY_ASSIGNED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.KEY_REQUIRED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl.isMirror;
import static com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.CryptoCreateWithAliasConfig;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides validation for token fields like token type,  token supply type, token symbol etc.,.
 * It is used in pureChecks for token creation.
 */
@Singleton
public class CryptoCreateValidator {
    private static final int EVM_ADDRESS_SIZE = 20;

    @Inject
    public CryptoCreateValidator() { // Exists for injection
    }

    /**
     * Validates Key Alias and EVM Address combinations.
     *
     * @param op    the crypto create transaction body
     * @param attributeValidator   AttributeValidator
     * @param config    CryptoCreateWithAliasConfig
     * @param readableAccountStore    ReadableAccountStore
     * @throws HandleException if the inputs are not invalid
     */
    public void validateKeyAliasAndEvmAddressCombinations(
            @NonNull final CryptoCreateTransactionBody op,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final CryptoCreateWithAliasConfig config,
            @NonNull final ReadableAccountStore readableAccountStore) {
        if (op.hasKey() && op.alias().equals(Bytes.EMPTY)) {
            validateKey(op, attributeValidator);
        } else if (!op.hasKey() && !op.alias().equals(Bytes.EMPTY)) {
            final var responseCode = config.enabled() ? INVALID_ALIAS_KEY : NOT_SUPPORTED;
            throw new HandleException(responseCode);
        } else if (op.hasKey() && !op.alias().equals(Bytes.EMPTY)) {
            validateKeyAndAliasProvidedCase(op, attributeValidator, config, readableAccountStore);
        } else {
            // This is the case when no key and no alias are provided
            throw new HandleException(KEY_REQUIRED);
        }
    }

    private void validateKey(
            @NonNull final CryptoCreateTransactionBody op, @NonNull final AttributeValidator attributeValidator) {
        final var key = op.key();
        if (!key.equals(IMMUTABILITY_SENTINEL_KEY)) {
            if (isEmpty(key)) {
                throw new HandleException(KEY_REQUIRED);
            }
            if (!isValid(key)) {
                throw new HandleException(INVALID_ADMIN_KEY);
            }
            attributeValidator.validateKey(key);
        }
    }

    private void validateKeyAndAliasProvidedCase(
            @NonNull final CryptoCreateTransactionBody op,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final CryptoCreateWithAliasConfig config,
            @NonNull final ReadableAccountStore readableAccountStore) {
        validateTrue(config.enabled(), NOT_SUPPORTED);
        // TODO - to match the mono-service synthetic hollow account creation, we need an empty key list here
        validateKey(op, attributeValidator);
        validateTrue(op.alias().length() == EVM_ADDRESS_SIZE, INVALID_ALIAS_KEY);
        validateFalse(isMirror(op.alias()), INVALID_ALIAS_KEY);
        validateTrue(readableAccountStore.getAccountIDByAlias(op.alias()) == null, ALIAS_ALREADY_ASSIGNED);
    }

    /** check if the number of auto associations is too many
     * @param n number to check
     * @param ledgerConfig LedgerConfig
     * @param entitiesConfig EntitiesConfig
     * @param tokensConfig TokensConfig
     */
    public boolean tooManyAutoAssociations(
            final int n,
            @NonNull final LedgerConfig ledgerConfig,
            @NonNull final EntitiesConfig entitiesConfig,
            @NonNull final TokensConfig tokensConfig) {
        return n > ledgerConfig.maxAutoAssociations()
                || (entitiesConfig.limitTokenAssociations() && n > tokensConfig.maxPerAccount());
    }
}
