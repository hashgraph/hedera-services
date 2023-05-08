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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import javax.inject.Inject;

public class TokenTypeValidator {
    @Inject
    public TokenTypeValidator(){

    }
    public ResponseCodeEnum typeCheck(final TokenType type, final long initialSupply, final int decimals) {
        switch (type) {
            case FUNGIBLE_COMMON -> {
                return initialSupply < 0
                        ? INVALID_TOKEN_INITIAL_SUPPLY
                        : (decimals < 0 ? INVALID_TOKEN_DECIMALS : OK);
            }
            case NON_FUNGIBLE_UNIQUE -> {
                return initialSupply != 0
                        ? INVALID_TOKEN_INITIAL_SUPPLY
                        : (decimals != 0 ? INVALID_TOKEN_DECIMALS : OK);
            }
        }
        return NOT_SUPPORTED;
    }

    public ResponseCodeEnum supplyTypeCheck(final TokenSupplyType supplyType, final long maxSupply) {
        switch (supplyType) {
            case INFINITE:
                return maxSupply != 0 ? INVALID_TOKEN_MAX_SUPPLY : ResponseCodeEnum.OK;
            case FINITE:
                return maxSupply <= 0 ? INVALID_TOKEN_MAX_SUPPLY : ResponseCodeEnum.OK;
            default:
                return NOT_SUPPORTED;
        }
    }
}
