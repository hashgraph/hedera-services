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
package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_UINT256_RAW_TYPE;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenKeyWrapper;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;

public interface EvmGetTokenKeyPrecompile {
    Function GET_TOKEN_KEYS_FUNCTION = new Function("getTokenKey(address,uint256)");
    Bytes GET_TOKEN_KEYS_SELECTOR = Bytes.wrap(GET_TOKEN_KEYS_FUNCTION.selector());
    ABIType<Tuple> GET_TOKEN_KEYS_DECODER = TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);

    static GetTokenKeyWrapper<byte[]> decodeGetTokenKey(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, GET_TOKEN_KEYS_SELECTOR, GET_TOKEN_KEYS_DECODER);
        final var tokenType = ((BigInteger) decodedArguments.get(1)).longValue();
        return new GetTokenKeyWrapper<>(decodedArguments.get(0), tokenType);
    }
}
