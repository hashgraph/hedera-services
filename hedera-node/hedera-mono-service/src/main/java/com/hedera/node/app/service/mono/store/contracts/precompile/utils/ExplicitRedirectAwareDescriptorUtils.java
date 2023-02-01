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

import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.getSlicedAddressBytes;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade;
import com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils;
import com.hedera.node.app.service.mono.store.contracts.precompile.proxy.ExplicitRedirectAwareRedirectTarget;
import org.apache.tuweni.bytes.Bytes;

public class ExplicitRedirectAwareDescriptorUtils {

    public static final Function REDIRECT_FOR_TOKEN_FUNCTION =
            new Function("redirectForToken(address,bytes)");
    public static final Bytes REDIRECT_FOR_TOKEN_SELECTOR =
            Bytes.wrap(REDIRECT_FOR_TOKEN_FUNCTION.selector());
    public static final ABIType<Tuple> REDIRECT_FOR_TOKEN_DECODER =
            TypeFactory.create("(bytes32,bytes)");

    public static ExplicitRedirectAwareRedirectTarget getRedirectTarget(Bytes input) {
        final var finalInput = getFinalInput(input);
        final var redirectTarget = DescriptorUtils.getRedirectTarget(finalInput);
        return new ExplicitRedirectAwareRedirectTarget(redirectTarget, finalInput);
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

    private ExplicitRedirectAwareDescriptorUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
