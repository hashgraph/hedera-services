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
package com.hedera.services.txns.util;

import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenUpdateValidator {

    private TokenUpdateValidator() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ResponseCodeEnum validate(TransactionBody txnBody, OptionValidator validator) {
        TokenUpdateTransactionBody op = txnBody.getTokenUpdate();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        var validity = !op.hasMemo() ? OK : validator.memoCheck(op.getMemo().getValue());
        if (validity != OK) {
            return validity;
        }

        var hasNewSymbol = op.getSymbol().length() > 0;
        if (hasNewSymbol) {
            validity = validator.tokenSymbolCheck(op.getSymbol());
            if (validity != OK) {
                return validity;
            }
        }

        var hasNewTokenName = op.getName().length() > 0;
        if (hasNewTokenName) {
            validity = validator.tokenNameCheck(op.getName());
            if (validity != OK) {
                return validity;
            }
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

        return validity;
    }
}
