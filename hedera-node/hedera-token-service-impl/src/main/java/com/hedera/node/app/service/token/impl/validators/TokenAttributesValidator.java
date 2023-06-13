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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides validation for token fields like token type,  token supply type, token symbol etc.,.
 * It is used in pureChecks for token creation.
 */
@Singleton
public class TokenAttributesValidator {
    private final ConfigProvider configProvider;
    public static final Key IMMUTABILITY_SENTINEL_KEY =
            Key.newBuilder().keyList(KeyList.DEFAULT).build();

    @Inject
    public TokenAttributesValidator(@NonNull final ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    public void validateTokenSymbol(@Nullable final String symbol) {
        final var tokensConfig = configProvider.getConfiguration().getConfigData(TokensConfig.class);
        tokenStringCheck(symbol, tokensConfig.maxSymbolUtf8Bytes(), MISSING_TOKEN_SYMBOL, TOKEN_SYMBOL_TOO_LONG);
    }

    public void validateTokenName(@Nullable final String name) {
        final var tokensConfig = configProvider.getConfiguration().getConfigData(TokensConfig.class);
        tokenStringCheck(name, tokensConfig.maxTokenNameUtf8Bytes(), MISSING_TOKEN_NAME, TOKEN_NAME_TOO_LONG);
    }

    private void tokenStringCheck(
            @Nullable final String s,
            final int maxLen,
            @NonNull final ResponseCodeEnum onMissing,
            @NonNull final ResponseCodeEnum onTooLong) {
        validateTrue(s != null, onMissing);
        final int numUtf8Bytes = s.getBytes(StandardCharsets.UTF_8).length;
        validateTrue(numUtf8Bytes != 0, onMissing);
        validateTrue(numUtf8Bytes <= maxLen, onTooLong);
        validateTrue(!s.contains("\u0000"), INVALID_ZERO_BYTE_IN_STRING);
    }

    public void checkKeys(
            final boolean hasAdminKey,
            @Nullable final Key adminKey,
            final boolean hasKycKey,
            @Nullable final Key kycKey,
            final boolean hasWipeKey,
            @Nullable final Key wipeKey,
            final boolean hasSupplyKey,
            @Nullable final Key supplyKey,
            final boolean hasFreezeKey,
            @Nullable final Key freezeKey,
            final boolean hasFeeScheduleKey,
            @Nullable final Key feeScheduleKey,
            final boolean hasPauseKey,
            @Nullable final Key pauseKey) {
        if (hasAdminKey && !isKeyRemoval(adminKey)) {
            validateTrue(isValid(adminKey), INVALID_ADMIN_KEY);
        }
        if (hasKycKey) {
            validateTrue(isValid(kycKey), INVALID_KYC_KEY);
        }
        if (hasWipeKey) {
            validateTrue(isValid(wipeKey), INVALID_WIPE_KEY);
        }
        if (hasSupplyKey) {
            validateTrue(isValid(supplyKey), INVALID_SUPPLY_KEY);
        }
        if (hasFreezeKey) {
            validateTrue(isValid(freezeKey), INVALID_FREEZE_KEY);
        }
        if (hasFeeScheduleKey) {
            validateTrue(isValid(feeScheduleKey), INVALID_CUSTOM_FEE_SCHEDULE_KEY);
        }
        if (hasPauseKey) {
            validateTrue(isValid(pauseKey), INVALID_PAUSE_KEY);
        }
    }

    private static boolean isKeyRemoval(@NonNull final Key source) {
        requireNonNull(source);
        return IMMUTABILITY_SENTINEL_KEY.equals(source);
    }
}
