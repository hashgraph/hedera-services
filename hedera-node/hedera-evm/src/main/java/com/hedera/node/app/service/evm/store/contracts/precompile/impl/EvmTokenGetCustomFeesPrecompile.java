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

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.annotations.InterimSPI;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenGetCustomFeesWrapper;
import org.apache.tuweni.bytes.Bytes;

@InterimSPI
public interface EvmTokenGetCustomFeesPrecompile {

    Function TOKEN_GET_CUSTOM_FEES_FUNCTION = new Function("getTokenCustomFees(address)");
    Bytes TOKEN_GET_CUSTOM_FEES_SELECTOR = Bytes.wrap(TOKEN_GET_CUSTOM_FEES_FUNCTION.selector());
    ABIType<Tuple> TOKEN_GET_CUSTOM_FEES_DECODER = TypeFactory.create(BYTES32);

    public static TokenGetCustomFeesWrapper<byte[]> decodeTokenGetCustomFees(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_GET_CUSTOM_FEES_SELECTOR, TOKEN_GET_CUSTOM_FEES_DECODER);

        return new TokenGetCustomFeesWrapper<>(decodedArguments.get(0));
    }
}
