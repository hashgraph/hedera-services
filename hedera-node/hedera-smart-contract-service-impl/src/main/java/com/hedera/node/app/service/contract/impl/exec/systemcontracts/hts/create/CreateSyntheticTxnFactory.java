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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenCreateWrapper;
import javax.inject.Singleton;

@Singleton
public class CreateSyntheticTxnFactory {

    public static TokenCreateTransactionBody.Builder createToken(TokenCreateWrapper tokenCreateWrapper) {
        final var txnBodyBuilder = TokenCreateTransactionBody.newBuilder();

        txnBodyBuilder.name(tokenCreateWrapper.getName());
        txnBodyBuilder.symbol(tokenCreateWrapper.getSymbol());
        txnBodyBuilder.decimals(tokenCreateWrapper.getDecimals());
        txnBodyBuilder.tokenType(
                tokenCreateWrapper.isFungible() ? TokenType.FUNGIBLE_COMMON : TokenType.NON_FUNGIBLE_UNIQUE);
        txnBodyBuilder.supplyType(
                tokenCreateWrapper.isSupplyTypeFinite() ? TokenSupplyType.FINITE : TokenSupplyType.INFINITE);
        txnBodyBuilder.maxSupply(tokenCreateWrapper.getMaxSupply());
        txnBodyBuilder.initialSupply(tokenCreateWrapper.getInitSupply());

        // checks for treasury
        if (tokenCreateWrapper.getTreasury() != null) {
            txnBodyBuilder.treasury(tokenCreateWrapper.getTreasury());
        }

        tokenCreateWrapper.getTokenKeys().forEach(tokenKeyWrapper -> {
            final var key = tokenKeyWrapper.key().asGrpc();
            if (tokenKeyWrapper.isUsedForAdminKey()) {
                txnBodyBuilder.adminKey(key);
            }
            if (tokenKeyWrapper.isUsedForKycKey()) {
                txnBodyBuilder.kycKey(key);
            }
            if (tokenKeyWrapper.isUsedForFreezeKey()) {
                txnBodyBuilder.freezeKey(key);
            }
            if (tokenKeyWrapper.isUsedForWipeKey()) {
                txnBodyBuilder.wipeKey(key);
            }
            if (tokenKeyWrapper.isUsedForSupplyKey()) {
                txnBodyBuilder.supplyKey(key);
            }
            if (tokenKeyWrapper.isUsedForFeeScheduleKey()) {
                txnBodyBuilder.feeScheduleKey(key);
            }
            if (tokenKeyWrapper.isUsedForPauseKey()) {
                txnBodyBuilder.pauseKey(key);
            }
        });
        txnBodyBuilder.freezeDefault(tokenCreateWrapper.isFreezeDefault());
        txnBodyBuilder.memo(tokenCreateWrapper.getMemo());

        // checks for expiry
        if (tokenCreateWrapper.getExpiry().second() != 0) {
            txnBodyBuilder.expiry(Timestamp.newBuilder()
                    .seconds(tokenCreateWrapper.getExpiry().second())
                    .build());
        }
        if (tokenCreateWrapper.getExpiry().autoRenewAccount() != null) {
            txnBodyBuilder.autoRenewAccount(tokenCreateWrapper.getExpiry().autoRenewAccount());
        }
        if (tokenCreateWrapper.getExpiry().autoRenewPeriod() != null) {
            txnBodyBuilder.autoRenewPeriod(tokenCreateWrapper.getExpiry().autoRenewPeriod());
        }

        if (tokenCreateWrapper.getFixedFees() != null) {
            txnBodyBuilder.customFees(tokenCreateWrapper.getFixedFees().stream()
                    .map(TokenCreateWrapper.FixedFeeWrapper::asGrpc)
                    .toList());
        }
        if (tokenCreateWrapper.getFractionalFees() != null) {
            txnBodyBuilder.customFees(tokenCreateWrapper.getFractionalFees().stream()
                    .map(TokenCreateWrapper.FractionalFeeWrapper::asGrpc)
                    .toList());
        }
        if (tokenCreateWrapper.getFractionalFees() != null) {
            txnBodyBuilder.customFees(tokenCreateWrapper.getRoyaltyFees().stream()
                    .map(TokenCreateWrapper.RoyaltyFeeWrapper::asGrpc)
                    .toList());
        }
        return txnBodyBuilder;
    }
}
