/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token.validators;

import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hedera.services.txns.validation.TokenListChecks.nftSupplyKeyCheck;
import static com.hedera.services.txns.validation.TokenListChecks.suppliesCheck;
import static com.hedera.services.txns.validation.TokenListChecks.supplyTypeCheck;
import static com.hedera.services.txns.validation.TokenListChecks.typeCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.TokenTypesMapper;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class CreateChecks {
    private final GlobalDynamicProperties dynamicProperties;
    private final OptionValidator validator;

    @Inject
    public CreateChecks(
            final GlobalDynamicProperties dynamicProperties, final OptionValidator validator) {
        this.dynamicProperties = dynamicProperties;
        this.validator = validator;
    }

    public Function<TransactionBody, ResponseCodeEnum> validatorForConsTime(
            final Instant curConsTime) {
        return txnBody -> {
            TokenCreateTransactionBody op = txnBody.getTokenCreation();

            final var domainType = TokenTypesMapper.mapToDomain(op.getTokenType());
            if (domainType == TokenType.NON_FUNGIBLE_UNIQUE
                    && !dynamicProperties.areNftsEnabled()) {
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

            validity =
                    checkKeys(
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
        };
    }

    private ResponseCodeEnum validateAutoRenewAccount(
            final TokenCreateTransactionBody op,
            final OptionValidator validator,
            final Instant curConsTime) {
        ResponseCodeEnum validity = OK;
        if (op.hasAutoRenewAccount()) {
            validity =
                    validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())
                            ? OK
                            : INVALID_RENEWAL_PERIOD;
            return validity;
        } else {
            if (curConsTime != null
                    && op.getExpiry().getSeconds() <= curConsTime.getEpochSecond()) {
                return INVALID_EXPIRATION_TIME;
            }
        }
        return validity;
    }
}
