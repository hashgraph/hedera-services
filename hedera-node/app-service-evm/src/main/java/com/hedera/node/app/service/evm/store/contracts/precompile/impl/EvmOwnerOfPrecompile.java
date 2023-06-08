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
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.UINT256;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.OwnerOfAndTokenURIWrapper;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;

public interface EvmOwnerOfPrecompile {

    Function OWNER_OF_NFT_FUNCTION = new Function("ownerOf(uint256)", INT);
    Bytes OWNER_OF_NFT_SELECTOR = Bytes.wrap(OWNER_OF_NFT_FUNCTION.selector());
    ABIType<Tuple> OWNER_OF_NFT_DECODER = TypeFactory.create(UINT256);

    public static OwnerOfAndTokenURIWrapper decodeOwnerOf(final Bytes input) {
        final Tuple decodedArguments = decodeFunctionCall(input, OWNER_OF_NFT_SELECTOR, OWNER_OF_NFT_DECODER);

        final var tokenId = (BigInteger) decodedArguments.get(0);

        return new OwnerOfAndTokenURIWrapper(tokenId.longValueExact());
    }
}
