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

import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.utils.TokenTypesMapper;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.node.app.service.mono.txns.validation.TokenListChecks.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@Singleton
public class TokenCreateValidator {
    @Inject
    public TokenCreateValidator() {
    }

    public void pureChecks(@NonNull final TokenCreateTransactionBody op){

    }

    public void validate(
            final ReadableAccountStore accountStore,
            final WritableTokenStore tokenStore,
            final TokenCreateTransactionBody op,
            final TokensConfig config) {
        final com.hederahashgraph.api.proto.java.TokenCreateTransactionBody op = txnBody.getTokenCreation();

        final var domainType = TokenTypesMapper.mapToDomain(op.getTokenType());
        if (domainType == TokenType.NON_FUNGIBLE_UNIQUE && !dynamicProperties.areNftsEnabled()) {
            return NOT_SUPPORTED;
        }

        var validity = validator.memoCheck(op.getMemo());
        if (validity != OK) {
            return validity;
        }

        validity = validator.tokenSymbolCheck(op.getSymbol());
        if (validity != OK) {
            return validity;
        }

        validity = validator.tokenNameCheck(op.getName());
        if (validity != OK) {
            return validity;
        }

        validity = typeCheck(op.getTokenType(), op.getInitialSupply(), op.getDecimals());
        if (validity != OK) {
            return validity;
        }

        validity = supplyTypeCheck(op.getSupplyType(), op.getMaxSupply());
        if (validity != OK) {
            return validity;
        }

        validity = suppliesCheck(op.getInitialSupply(), op.getMaxSupply());
        if (validity != OK) {
            return validity;
        }

        validity = nftSupplyKeyCheck(op.getTokenType(), op.hasSupplyKey());
        if (validity != OK) {
            return validity;
        }

        if (!op.hasTreasury()) {
            return INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
        }

        validity = checkKeys(
                op.hasAdminKey(), op.getAdminKey(),
                op.hasKycKey(), op.getKycKey(),
                op.hasWipeKey(), op.getWipeKey(),
                op.hasSupplyKey(), op.getSupplyKey(),
                op.hasFreezeKey(), op.getFreezeKey(),
                op.hasFeeScheduleKey(), op.getFeeScheduleKey(),
                op.hasPauseKey(), op.getPauseKey());
        if (validity != OK) {
            return validity;
        }

        if (op.getFreezeDefault() && !op.hasFreezeKey()) {
            return TOKEN_HAS_NO_FREEZE_KEY;
        }
        return validateAutoRenewAccount(op, validator, curConsTime);
    }
}
