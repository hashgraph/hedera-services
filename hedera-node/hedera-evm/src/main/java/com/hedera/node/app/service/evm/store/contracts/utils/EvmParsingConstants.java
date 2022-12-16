/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.esaulpaugh.headlong.abi.TupleType;

public class EvmParsingConstants {

    private EvmParsingConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String INT32 = "(int32)";
    public static final String INT = "(int)";
    public static final String BYTES32 = "(bytes32)";
    public static final String UINT256 = "(uint256)";
    public static final String BOOL = "(bool)";

    public static final String UINT8 = "(uint8)";
    public static final String STRING = "(string)";
    public static final String ADDRESS = "(address)";
    public static final String ADDRESS_UINT256_RAW_TYPE = "(bytes32,uint256)";
    public static final String INT_BOOL_PAIR = "(int,bool)";
    public static final String ADDRESS_TRIO_RAW_TYPE = "(bytes32,bytes32,bytes32)";

    public static final String ADDRESS_PAIR_RAW_TYPE = "(bytes32,bytes32)";

    public static final TupleType bigIntegerTuple = TupleType.parse(UINT256);
    public static final TupleType decimalsType = TupleType.parse(UINT8);
    public static final TupleType booleanTuple = TupleType.parse(BOOL);
    public static final TupleType stringTuple = TupleType.parse(STRING);
    public static final TupleType addressTuple = TupleType.parse(ADDRESS);
    public static final TupleType notSpecifiedType = TupleType.parse(INT32);

    public enum FunctionType {
        ERC_DECIMALS,
        ERC_ALLOWANCE,
        ERC_TOTAL_SUPPLY,
        ERC_BALANCE,
        ERC_IS_APPROVED_FOR_ALL,
        ERC_NAME,
        ERC_SYMBOL,
        ERC_TOKEN_URI,
        ERC_OWNER,
        ERC_GET_APPROVED,

        NOT_SPECIFIED
    }
}
