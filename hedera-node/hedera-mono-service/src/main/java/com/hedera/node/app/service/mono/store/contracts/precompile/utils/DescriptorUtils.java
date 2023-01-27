/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile.utils;

import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_KEY;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_TYPE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.getSlicedAddressBytes;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.tokenIdFromEvmAddress;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.proxy.RedirectTarget;
import org.apache.tuweni.bytes.Bytes;

public class DescriptorUtils {

    public static final Function REDIRECT_FOR_TOKEN_FUNCTION =
            new Function("redirectForToken(address,bytes)");
    public static final Bytes REDIRECT_FOR_TOKEN_SELECTOR =
            Bytes.wrap(REDIRECT_FOR_TOKEN_FUNCTION.selector());
    public static final ABIType<Tuple> REDIRECT_FOR_TOKEN_DECODER =
            TypeFactory.create("(bytes32,bytes)");

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

    public static RedirectTarget getRedirectTarget(Bytes input) {
        final var finalInput = getFinalInput(input);
        final var tokenAddress = finalInput.slice(4, 20);
        final var tokenId = tokenIdFromEvmAddress(tokenAddress.toArrayUnsafe());
        final var nestedInput = finalInput.slice(24);
        return new RedirectTarget(nestedInput.getInt(0), tokenId, finalInput);
    }

    private static Bytes getFinalInput(final Bytes input) {
        try {
            // try decoding the input to see if redirectForToken() was called explicitly, using
            // normal encoding
            // if so, massage it to our expected "packed" redirect input
            final var tuple =
                    EvmDecodingFacade.decodeFunctionCall(
                            input, REDIRECT_FOR_TOKEN_SELECTOR, REDIRECT_FOR_TOKEN_DECODER);
            return Bytes.concatenate(
                    Bytes.ofUnsignedInt(ABI_ID_REDIRECT_FOR_TOKEN),
                    getSlicedAddressBytes(tuple.get(0)),
                    Bytes.of((byte[]) tuple.get(1)));
        } catch (Exception e) {
            // exception from the decoder means the input is already in "packed" form
            return input;
        }
    }

    private DescriptorUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
