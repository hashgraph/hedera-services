/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.utils;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_KEY;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_TYPE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.utils.EntityIdUtils.tokenIdFromEvmAddress;

import com.hedera.services.store.contracts.precompile.proxy.RedirectTarget;
import org.apache.tuweni.bytes.Bytes;

public class DescriptorUtils {
    public static boolean isTokenProxyRedirect(final Bytes input) {
        return ABI_ID_REDIRECT_FOR_TOKEN == input.getInt(0);
    }

    public static boolean isViewFunction(final Bytes input) {
        int functionId = input.getInt(0);
        return switch (functionId) {
            case ABI_ID_GET_TOKEN_INFO,
                    ABI_ID_GET_FUNGIBLE_TOKEN_INFO,
                    ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO,
                    ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS,
                    ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS,
                    ABI_ID_IS_FROZEN,
                    ABI_ID_IS_KYC,
                    ABI_ID_GET_TOKEN_CUSTOM_FEES,
                    ABI_ID_GET_TOKEN_KEY,
                    ABI_ID_IS_TOKEN,
                    ABI_ID_GET_TOKEN_TYPE,
                    ABI_ID_GET_TOKEN_EXPIRY_INFO -> true;
            default -> false;
        };
    }

    public static RedirectTarget getRedirectTarget(final Bytes input) {
        final var tokenAddress = input.slice(4, 20);
        final var tokenId = tokenIdFromEvmAddress(tokenAddress.toArrayUnsafe());
        final var nestedInput = input.slice(24);
        return new RedirectTarget(nestedInput.getInt(0), tokenId);
    }

    private DescriptorUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
