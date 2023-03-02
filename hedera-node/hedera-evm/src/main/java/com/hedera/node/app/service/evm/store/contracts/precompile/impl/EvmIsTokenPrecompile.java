/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BYTES32;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT_BOOL_PAIR;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.annotations.InterimSPI;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import org.apache.tuweni.bytes.Bytes;

@InterimSPI
public interface EvmIsTokenPrecompile {
    Function IS_TOKEN_FUNCTION = new Function("isToken(address)", INT_BOOL_PAIR);
    Bytes IS_TOKEN_FUNCTION_SELECTOR = Bytes.wrap(IS_TOKEN_FUNCTION.selector());
    ABIType<Tuple> IS_TOKEN_DECODER = TypeFactory.create(BYTES32);

    public static TokenInfoWrapper<byte[]> decodeIsToken(final Bytes input) {
        final Tuple decodedArguments = decodeFunctionCall(input, IS_TOKEN_FUNCTION_SELECTOR, IS_TOKEN_DECODER);

        return TokenInfoWrapper.forToken(decodedArguments.get(0));
    }
}
