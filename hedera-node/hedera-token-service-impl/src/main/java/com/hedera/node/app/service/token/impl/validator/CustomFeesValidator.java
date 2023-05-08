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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;

import javax.inject.Inject;

public class CustomFeesValidator {
    @Inject
    public CustomFeesValidator(){

    }

    public void validate(@NonNull final List<CustomFee> customFees,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final WritableTokenStore tokenStore,
            final boolean isCreation) {
        for(final var fee : customFees){
            final var collector = accountStore.getAccountById(fee.feeCollectorAccountId());
            validateTrue(collector != null, INVALID_CUSTOM_FEE_COLLECTOR);

            switch(fee.fee().kind()){
                case FIXED_FEE -> {
                    final var fixedFee = fee.fixedFee();
                    if(isCreation){
                        if (fixedFee.denominatingTokenId() != null) {
                            if (fixedFee.denominatingTokenId().tokenNum() == 0L) {
                                final var token = tokenStore.get(fixedFee.denominatingTokenId().tokenNum());
                                validateTrue(token.isPresent(), INVALID_TOKEN_ID);
                                validateTrue(token.get().tokenType().equals(TokenType.FUNGIBLE_COMMON), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
                                tokenDenomination.setNum(provisionalToken.getId().num());
                                usedDenomWildcard = true;
                            } else {
                                validateExplicitlyDenominatedWith(feeCollector, tokenStore);
                            }
                        }
                    }
                }
                case FRACTIONAL_FEE -> {

                }
                case ROYALTY_FEE -> {

                }
            }
        }
    }

    private void validateCreation(){

    }

    private void validateNonCreations(){

    }
}
