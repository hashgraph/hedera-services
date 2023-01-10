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
package com.hedera.node.app.service.evm.store.contracts.utils;

import com.esaulpaugh.headlong.abi.TupleType;

public class EvmParsingConstants {

    private EvmParsingConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String ARRAY_BRACKETS = "[]";

    public static final String INT_BOOL_PAIR_RETURN_TYPE = "(int32,bool)";
    public static final String INT32 = "(int32)";
    public static final String INT = "(int)";
    public static final String BYTES32 = "(bytes32)";
    public static final String UINT256 = "(uint256)";
    public static final String BOOL = "(bool)";

    public static final String UINT8 = "(uint8)";
    public static final TupleType intPairTuple = TupleType.parse("(int32,int32)");
    public static final String STRING = "(string)";
    public static final String ADDRESS = "(address)";
    public static final String ADDRESS_UINT256_RAW_TYPE = "(bytes32,uint256)";
    public static final String INT_BOOL_PAIR = "(int,bool)";
    public static final String ADDRESS_TRIO_RAW_TYPE = "(bytes32,bytes32,bytes32)";

    public static final String ADDRESS_PAIR_RAW_TYPE = "(bytes32,bytes32)";

    public static final String EXPIRY = "(uint32,address,uint32)";
    public static final String KEY_VALUE = "(bool,address,bytes,bytes,address)";
    public static final String TOKEN_KEY = "(uint256," + KEY_VALUE + ")";
    public static final String HEDERA_TOKEN =
            "("
                    + "string,string,address,string,bool,int64,bool,"
                    + TOKEN_KEY
                    + ARRAY_BRACKETS
                    + ","
                    + EXPIRY
                    + ")";

    public static final String RESPONSE_STATUS_AT_BEGINNING = "(int32,";
    public static final String FIXED_FEE = "(uint32,address,bool,bool,address)";
    public static final String FRACTIONAL_FEE = "(uint32,uint32,uint32,uint32,bool,address)";
    public static final String ROYALTY_FEE = "(uint32,uint32,uint32,address,bool,address)";
    public static final String TOKEN_INFO =
            "("
                    + HEDERA_TOKEN
                    + ",int64,bool,bool,bool,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE
                    + ARRAY_BRACKETS
                    + ",string"
                    + ")";

    public static final TupleType bigIntegerTuple = TupleType.parse(UINT256);
    public static final TupleType decimalsType = TupleType.parse(UINT8);
    public static final TupleType booleanTuple = TupleType.parse(BOOL);
    public static final TupleType intBoolTuple = TupleType.parse(INT_BOOL_PAIR_RETURN_TYPE);
    public static final TupleType stringTuple = TupleType.parse(STRING);
    public static final TupleType addressTuple = TupleType.parse(ADDRESS);
    public static final TupleType notSpecifiedType = TupleType.parse(INT32);
    public static final TupleType getTokenInfoType =
            TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + TOKEN_INFO + ")");

    public enum FunctionType {
        ERC_DECIMALS,
        HAPI_GET_TOKEN_TYPE,
        ERC_ALLOWANCE,
        ERC_TOTAL_SUPPLY,
        ERC_BALANCE,
        ERC_IS_APPROVED_FOR_ALL,
        HAPI_IS_FROZEN,
        GET_TOKEN_DEFAULT_FREEZE_STATUS,
        GET_TOKEN_DEFAULT_KYC_STATUS,
        HAPI_IS_KYC,
        HAPI_IS_TOKEN,
        ERC_NAME,
        ERC_SYMBOL,
        ERC_TOKEN_URI,
        ERC_OWNER,
        ERC_GET_APPROVED,
        HAPI_GET_TOKEN_INFO,

        NOT_SPECIFIED
    }
}
