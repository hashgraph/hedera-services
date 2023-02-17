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

package com.hedera.node.app.service.evm.store.contracts.utils;

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_KEY;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;

import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmRedirectForTokenPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectTarget;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class DescriptorUtils {
    public static final long MINIMUM_TINYBARS_COST = 100;
    public static final int ADDRESS_BYTES_LENGTH = 20;
    public static final int ADDRESS_SKIP_BYTES_LENGTH = 12;

    private DescriptorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

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
        final var finalInput = massageInputIfNeeded(input);
        final var tokenBytes = finalInput.slice(4, 20);
        final var tokenAddress = Address.wrap(Bytes.wrap(tokenBytes.toArrayUnsafe()));
        final var nestedInput = finalInput.slice(24);
        return new RedirectTarget(nestedInput.getInt(0), tokenAddress, finalInput != input ? finalInput : null);
    }

    public static Address addressFromBytes(final byte[] addressBytes) {
        var bytes = Bytes.wrap(addressBytes);
        if (addressBytes.length > ADDRESS_BYTES_LENGTH) {
            bytes = bytes.slice(ADDRESS_SKIP_BYTES_LENGTH, ADDRESS_BYTES_LENGTH);
        }
        return Address.wrap(bytes);
    }

    private static Bytes massageInputIfNeeded(final Bytes input) {
        try {
            // try decoding the input to see if redirectForToken() was called explicitly, using
            // normal encoding
            // if so, massage it to our expected "packed" redirect input
            final var redirectForTokenWrapper = EvmRedirectForTokenPrecompile.decodeExplicitRedirectForToken(input);
            return Bytes.concatenate(
                    Bytes.ofUnsignedInt(ABI_ID_REDIRECT_FOR_TOKEN),
                    addressFromBytes(redirectForTokenWrapper.token()),
                    Bytes.of((redirectForTokenWrapper.data())));
        } catch (Exception e) {
            // exception from the decoder means the input is already in "packed" form
            return input;
        }
    }
}
