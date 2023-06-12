/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.validators;

import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.node.app.service.mono.utils.TokenTypesMapper;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.hapi.node.base.TokenSupplyType.FINITE;
import static com.hedera.hapi.node.base.TokenSupplyType.INFINITE;
import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.txns.validation.TokenListChecks.*;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@Singleton
public class TokenCreateValidator {
    private final TokenFieldsValidator tokenFieldsValidator;
    @Inject
    public TokenCreateValidator(@NonNull final TokenFieldsValidator tokenFieldsValidator) {
        this.tokenFieldsValidator = tokenFieldsValidator;
    }


    public void pureChecks(@NonNull final TokenCreateTransactionBody op) throws PreCheckException {
        final var initialSupply = op.initialSupply();
        final var maxSupply = op.maxSupply();
        final var decimals = op.decimals();
        final var supplyType = op.supplyType();
        final var tokenType = op.tokenType();


        validateTokenType(tokenType, initialSupply, decimals);
        validateSupplyType(supplyType, maxSupply);

        validateFalsePreCheck(maxSupply > 0 && initialSupply > maxSupply, INVALID_TOKEN_INITIAL_SUPPLY);
        validateTruePreCheck(op.hasTreasury(), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

        if (tokenType == NON_FUNGIBLE_UNIQUE) {
            validateTruePreCheck(op.hasSupplyKey(), TOKEN_HAS_NO_SUPPLY_KEY);
        }
        if (op.freezeDefault()) {
            validateTruePreCheck(op.hasFreezeKey(), TOKEN_HAS_NO_FREEZE_KEY);
        }
    }

    public void validate(
            final TokenCreateTransactionBody op,
            final TokensConfig config) {
        final var nftsAreEnabled = config.nftsAreEnabled();
        if (op.tokenType().equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
            validateTrue(nftsAreEnabled, NOT_SUPPORTED);
        }

        tokenFieldsValidator.validateMemo(op.memo());
        tokenFieldsValidator.validateTokenSymbol(op.symbol());
        tokenFieldsValidator.validateTokenName(op.name());

        tokenFieldsValidator.checkKeys(
                op.hasAdminKey(), op.adminKey(),
                op.hasKycKey(), op.kycKey(),
                op.hasWipeKey(), op.wipeKey(),
                op.hasSupplyKey(), op.supplyKey(),
                op.hasFreezeKey(), op.freezeKey(),
                op.hasFeeScheduleKey(), op.feeScheduleKey(),
                op.hasPauseKey(), op.pauseKey());
    }

    private void validateTokenType(final TokenType type, final long initialSupply, final int decimals) throws PreCheckException {
        validateTruePreCheck(type == FUNGIBLE_COMMON || type == NON_FUNGIBLE_UNIQUE, NOT_SUPPORTED);
        if (type == FUNGIBLE_COMMON) {
            validateTruePreCheck(initialSupply >= 0, INVALID_TOKEN_INITIAL_SUPPLY);
            validateTruePreCheck(decimals >= 0, INVALID_TOKEN_DECIMALS);
        } else {
            validateTruePreCheck(initialSupply == 0, INVALID_TOKEN_INITIAL_SUPPLY);
            validateTruePreCheck(decimals == 0, INVALID_TOKEN_DECIMALS);
        }
    }

    private void validateSupplyType(final TokenSupplyType supplyType, final long maxSupply) throws PreCheckException {
        validateTruePreCheck(supplyType == INFINITE || supplyType == FINITE, NOT_SUPPORTED);
        if (supplyType == INFINITE) {
            validateTruePreCheck(maxSupply == 0, INVALID_TOKEN_MAX_SUPPLY);
        } else {
            validateTruePreCheck(maxSupply > 0, INVALID_TOKEN_MAX_SUPPLY);
        }
    }
}
