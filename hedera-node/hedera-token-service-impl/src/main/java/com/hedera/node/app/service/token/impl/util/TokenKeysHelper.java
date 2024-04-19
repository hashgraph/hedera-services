/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.util;

import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_METADATA_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hedera.node.app.spi.validation.AttributeValidator.isKeyRemoval;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;

public class TokenKeysHelper {

    public enum TokenKeys {
        ADMIN_KEY {
            @Override
            public boolean isPresentInUpdate(TokenUpdateTransactionBody update) {
                return update.hasAdminKey();
            }

            @Override
            public boolean isPresentInitially(Token originalToken) {
                return originalToken.hasAdminKey();
            }

            @Override
            public void setOn(final Token.Builder builder, TokenUpdateTransactionBody update) {
                builder.adminKey(getNewKeyValue(update.adminKey()));
            }

            @Override
            ResponseCodeEnum tokenHasNoKeyStatus() {
                return TOKEN_HAS_NO_ADMIN_KEY;
            }
        },

        WIPE_KEY {
            @Override
            public boolean isPresentInUpdate(TokenUpdateTransactionBody update) {
                return update.hasWipeKey();
            }

            @Override
            public boolean isPresentInitially(Token originalToken) {
                return originalToken.hasWipeKey();
            }

            @Override
            public void setOn(final Token.Builder builder, TokenUpdateTransactionBody update) {
                builder.wipeKey(getNewKeyValue(update.wipeKey()));
            }

            @Override
            ResponseCodeEnum tokenHasNoKeyStatus() {
                return TOKEN_HAS_NO_WIPE_KEY;
            }
        },

        KYC_KEY {
            @Override
            public boolean isPresentInUpdate(TokenUpdateTransactionBody update) {
                return update.hasKycKey();
            }

            @Override
            public boolean isPresentInitially(Token originalToken) {
                return originalToken.hasKycKey();
            }

            @Override
            public void setOn(final Token.Builder builder, TokenUpdateTransactionBody update) {
                builder.kycKey(getNewKeyValue(update.kycKey()));
            }

            @Override
            ResponseCodeEnum tokenHasNoKeyStatus() {
                return TOKEN_HAS_NO_KYC_KEY;
            }
        },

        SUPPLY_KEY {
            @Override
            public boolean isPresentInUpdate(TokenUpdateTransactionBody update) {
                return update.hasSupplyKey();
            }

            @Override
            public boolean isPresentInitially(Token originalToken) {
                return originalToken.hasSupplyKey();
            }

            @Override
            public void setOn(final Token.Builder builder, TokenUpdateTransactionBody update) {
                builder.supplyKey(getNewKeyValue(update.supplyKey()));
            }

            @Override
            ResponseCodeEnum tokenHasNoKeyStatus() {
                return TOKEN_HAS_NO_SUPPLY_KEY;
            }
        },

        FREEZE_KEY {
            @Override
            public boolean isPresentInUpdate(TokenUpdateTransactionBody update) {
                return update.hasFreezeKey();
            }

            @Override
            public boolean isPresentInitially(Token originalToken) {
                return originalToken.hasFreezeKey();
            }

            @Override
            public void setOn(final Token.Builder builder, TokenUpdateTransactionBody update) {
                builder.freezeKey(getNewKeyValue(update.freezeKey()));
            }

            @Override
            ResponseCodeEnum tokenHasNoKeyStatus() {
                return TOKEN_HAS_NO_FREEZE_KEY;
            }
        },

        FEE_SCHEDULE_KEY {
            @Override
            public boolean isPresentInUpdate(TokenUpdateTransactionBody update) {
                return update.hasFeeScheduleKey();
            }

            @Override
            public boolean isPresentInitially(Token originalToken) {
                return originalToken.hasFeeScheduleKey();
            }

            @Override
            public void setOn(final Token.Builder builder, TokenUpdateTransactionBody update) {
                builder.feeScheduleKey(getNewKeyValue(update.feeScheduleKey()));
            }

            @Override
            ResponseCodeEnum tokenHasNoKeyStatus() {
                return TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
            }
        },

        PAUSE_KEY {
            @Override
            public boolean isPresentInUpdate(TokenUpdateTransactionBody update) {
                return update.hasPauseKey();
            }

            @Override
            public boolean isPresentInitially(Token originalToken) {
                return originalToken.hasPauseKey();
            }

            @Override
            public void setOn(final Token.Builder builder, TokenUpdateTransactionBody update) {
                builder.pauseKey(getNewKeyValue(update.pauseKey()));
            }

            @Override
            ResponseCodeEnum tokenHasNoKeyStatus() {
                return TOKEN_HAS_NO_PAUSE_KEY;
            }
        },

        METADATA_KEY {
            @Override
            public boolean isPresentInUpdate(TokenUpdateTransactionBody update) {
                return update.hasMetadataKey();
            }

            @Override
            public boolean isPresentInitially(Token originalToken) {
                return originalToken.hasMetadataKey();
            }

            @Override
            public void setOn(final Token.Builder builder, TokenUpdateTransactionBody update) {
                builder.metadataKey(getNewKeyValue(update.metadataKey()));
            }

            @Override
            ResponseCodeEnum tokenHasNoKeyStatus() {
                return TOKEN_HAS_NO_METADATA_KEY;
            }
        };

        public abstract boolean isPresentInUpdate(TokenUpdateTransactionBody update);

        public abstract boolean isPresentInitially(Token originalToken);

        public abstract void setOn(final Token.Builder builder, TokenUpdateTransactionBody update);

        abstract ResponseCodeEnum tokenHasNoKeyStatus();

        private static Key getNewKeyValue(Key newKey) {
            return isKeyRemoval(newKey) ? null : newKey;
        }

        public void updateKey(TokenUpdateTransactionBody update, Token originalToken, final Token.Builder builder) {
            if (isPresentInUpdate(update)) {
                validateTrue(isPresentInitially(originalToken), tokenHasNoKeyStatus());
                setOn(builder, update);
            }
        }
    }
}
