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
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BOOL;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.IsApproveForAllWrapper;
import org.apache.tuweni.bytes.Bytes;

public interface EvmIsApprovedForAllPrecompile {

    Function ERC_IS_APPROVED_FOR_ALL = new Function("isApprovedForAll(address,address)", BOOL);
    Bytes ERC_IS_APPROVED_FOR_ALL_SELECTOR = Bytes.wrap(ERC_IS_APPROVED_FOR_ALL.selector());
    ABIType<Tuple> ERC_IS_APPROVED_FOR_ALL_DECODER = TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    static IsApproveForAllWrapper<byte[], byte[], byte[]> decodeIsApprovedForAll(final Bytes input) {
        final var tokenAddress = input.slice(4, 20).toArrayUnsafe();
        final var nestedInput = input.slice(24);
        final Tuple decodedArguments =
                decodeFunctionCall(nestedInput, ERC_IS_APPROVED_FOR_ALL_SELECTOR, ERC_IS_APPROVED_FOR_ALL_DECODER);

        return new IsApproveForAllWrapper<>(tokenAddress, decodedArguments.get(0), decodedArguments.get(1));
    }
}
