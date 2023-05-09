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

package com.hedera.node.app.service.token.impl.validator;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenCustomFee;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.evm.utils.ValidationUtils;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class CustomFeesValidator {
    @Inject
    public CustomFeesValidator(){

    }

    public void validateCreation(@NonNull final Token token,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final List<CustomFee> customFees){
        final List<TokenCustomFee> fees = new ArrayList<>();
        final var tokenType = token.tokenType();
        for(final var fee : customFees){
            final var collector = accountStore.getAccountById(fee.feeCollectorAccountId());
            validateTrue(collector != null, INVALID_CUSTOM_FEE_COLLECTOR);

            switch(fee.fee().kind()){
                case FIXED_FEE -> {
                    final var fixedFee = fee.fixedFee();
                    if (fixedFee.denominatingTokenId() != null) {
                        // If the denominating token id is set to sentinel value 0.0.0, then the fee is
                        // denominated in the same token as the token being created.
                        if (fixedFee.denominatingTokenId().tokenNum() == 0L) {
                            validateTrue(token.tokenType().equals(TokenType.FUNGIBLE_COMMON), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
                            tokenDenomination.setNum(token.tokenNumber());
                            usedDenomWildcard = true;
                        } else {
                            validateExplicitTokenDenomination(
                                        fee.feeCollectorAccountId().accountNum(),
                                        fixedFee.denominatingTokenId().tokenNum(),
                                        tokenRelationStore);
                            }
                        }
                }
                case FRACTIONAL_FEE -> {
                    validateTrue(tokenType.equals(TokenType.FUNGIBLE_COMMON), CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
                }
                case ROYALTY_FEE -> {
                    validateTrue(tokenType.equals(TokenType.NON_FUNGIBLE_UNIQUE), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
                    if (fee.royaltyFee().hasFallbackFee()) {
                        final var fallbackFee = fee.royaltyFee().fallbackFee();
                        fallbackFee.validateAndFinalizeWith(token, feeCollector, tokenStore);
                    }
                }
            }
        }
    }

    private void validateForFeeScheduleUpdate(@NonNull final Token token,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final List<CustomFee> customFees){
        final List<TokenCustomFee> fees = new ArrayList<>();
        final var tokenType = token.tokenType();
        for(final var fee : customFees){
            final var collector = accountStore.getAccountById(fee.feeCollectorAccountId());
            validateTrue(collector != null, INVALID_CUSTOM_FEE_COLLECTOR);

            switch(fee.fee().kind()){
                case FIXED_FEE -> {
                    final var fixedFee = fee.fixedFee();
                    if(fixedFee.hasDenominatingTokenId()){
                        validateExplicitTokenDenomination(tokenType,
                                fee.feeCollectorAccountId().accountNum(),
                                fixedFee.denominatingTokenId().tokenNum(),
                                tokenRelationStore,
                                tokenStore);
                    }
                }
                case FRACTIONAL_FEE -> {
                    validateTrue(tokenType.equals(TokenType.FUNGIBLE_COMMON), CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
                    validateTrue(tokenRelationStore.get(token.tokenNumber(), collector.accountNumber()).isPresent(),
                                TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
                }
                case ROYALTY_FEE -> {
                    validateTrue(tokenType.equals(TokenType.NON_FUNGIBLE_UNIQUE), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
                    if (fee.royaltyFee().hasFallbackFee()) {
                        validateExplicitTokenDenomination(tokenType,
                                fee.feeCollectorAccountId().accountNum(),
                                fee.royaltyFee().fallbackFee().denominatingTokenId().tokenNum(),
                                tokenRelationStore,
                                tokenStore);
                    }
                }
            }
    }

    private List<TokenCustomFee> validate(@NonNull final Token token,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final List<CustomFee> customFees,
            final boolean isCreation) {
        final List<TokenCustomFee> fees = new ArrayList<>();
        final var tokenType = token.tokenType();
        for(final var fee : customFees){
            final var collector = accountStore.getAccountById(fee.feeCollectorAccountId());
            validateTrue(collector != null, INVALID_CUSTOM_FEE_COLLECTOR);

            switch(fee.fee().kind()){
                case FIXED_FEE -> {
                    final var fixedFee = fee.fixedFee();
                    if(isCreation){
                        if (fixedFee.denominatingTokenId() != null) {
                            // If the denominating token id is set to sentinel value 0.0.0, then the fee is
                            // denominated in the same token as the token being created.
                            if (fixedFee.denominatingTokenId().tokenNum() == 0L) {
                                validateTrue(token.tokenType().equals(TokenType.FUNGIBLE_COMMON), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
                                tokenDenomination.setNum(token.tokenNumber());
                                usedDenomWildcard = true;
                            } else {
                                validateExplicitTokenDenomination(
                                        fee.feeCollectorAccountId().accountNum(),
                                        fixedFee.denominatingTokenId().tokenNum(),
                                        tokenRelationStore);
                            }
                        }
                    }
                }
                case FRACTIONAL_FEE -> {
                    validateTrue(tokenType.equals(TokenType.FUNGIBLE_COMMON), CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
                    if (!isCreation) {
                        validateTrue(tokenRelationStore.get(token.tokenNumber(), collector.accountNumber()).isPresent(),
                                TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
                    }
                    break;
                }
                case ROYALTY_FEE -> {
                    validateTrue(tokenType.equals(TokenType.NON_FUNGIBLE_UNIQUE), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
                    if (fee.royaltyFee().hasFallbackFee()) {
                        final var fallbackFee = fee.royaltyFee().fallbackFee();
                        if (isCreation) {
                            fallbackFee.validateAndFinalizeWith(token, feeCollector, tokenStore);
                        } else {
                            fallbackFee.validateWith(feeCollector, tokenStore);
                        }
                    }
                }
            }
        }
    }
        }

    final void validateExplicitTokenDenomination(TokenType tokenType,
            long feeCollectorNum,
            long tokenNum,
            ReadableTokenRelationStore tokenRelationStore,
            WritableTokenStore tokenStore) {
        final var denomToken = tokenStore.get(tokenNum);
       validateTrue(denomToken != null, INVALID_TOKEN_ID_IN_CUSTOM_FEES);
       validateTrue(tokenType.equals(TokenType.FUNGIBLE_COMMON), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
       validateTrue(tokenRelationStore.get(tokenNum, feeCollectorNum).isPresent(), TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
    }
}
