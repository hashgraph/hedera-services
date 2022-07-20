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
package com.hedera.services.parsing;

import com.esaulpaugh.headlong.abi.TupleType;

public final class ParsingConstants {
    private ParsingConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // data types
    public static final String ADDRESS = "(address)";
    public static final String ARRAY_BRACKETS = "[]";
    public static final String BOOL = "(bool)";
    public static final String BYTES32 = "(bytes32)";
    public static final String BYTES32_PAIR_RAW_TYPE = "(bytes32,bytes32)";
    public static final String INT = "(int)";
    public static final String STRING = "(string)";
    public static final String UINT8 = "(uint8)";
    public static final String UINT256 = "(uint256)";

    // struct types
    public static final String EXPIRY = "(uint32,address,uint32)";
    public static final String FIXED_FEE = "(uint32,address,bool,bool,address)";
    public static final String FRACTIONAL_FEE = "(uint32,uint32,uint32,uint32,bool,address)";
    public static final String KEY_VALUE = "(bool,address,bytes,bytes,address)";
    public static final String ROYALTY_FEE = "(uint32,uint32,uint32,address,bool,address)";
    public static final String TOKEN_KEY = "(uint256," + KEY_VALUE + ")";

    public static final String RESPONSE_STATUS_AT_BEGINNING = "(int32,";
    public static final String HEDERA_TOKEN =
            "("
                    + "string,string,address,string,bool,int64,bool,"
                    + TOKEN_KEY
                    + ARRAY_BRACKETS
                    + ","
                    + EXPIRY
                    + ")";
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

    public static final String TOKEN_INFO_REPLACED_ADDRESS =
            "("
                    + HEDERA_TOKEN.replace("address", "bytes32")
                    + ",int64,bool,bool,bool,"
                    + FIXED_FEE.replace("address", "bytes32")
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE.replace("address", "bytes32")
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE.replace("address", "bytes32")
                    + ARRAY_BRACKETS
                    + ",string"
                    + ")";

    public static final String FUNGIBLE_TOKEN_INFO = "(" + TOKEN_INFO + ",int32" + ")";
    public static final String FUNGIBLE_TOKEN_INFO_REPLACED_ADDRESS =
            "(" + TOKEN_INFO_REPLACED_ADDRESS + ",int32" + ")";

    public static final String NON_FUNGIBLE_TOKEN_INFO =
            "(" + TOKEN_INFO + ",int64,bytes32,int64,bytes,bytes32" + ")";
    public static final String NON_FUNGIBLE_TOKEN_INFO_REPLACED_ADDRESS =
            "(" + TOKEN_INFO_REPLACED_ADDRESS + ",int64,bytes32,int64,bytes,bytes32" + ")";

    // tuple types
    public static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
    public static final TupleType notSpecifiedType = TupleType.parse("(int32)");
    public static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");
    public static final TupleType totalSupplyType = TupleType.parse(UINT256);
    public static final TupleType balanceOfType = TupleType.parse(UINT256);
    public static final TupleType createReturnType = TupleType.parse("(int32,address)");
    public static final TupleType allowanceOfType = TupleType.parse(UINT256);
    public static final TupleType approveOfType = TupleType.parse(BOOL);
    public static final TupleType decimalsType = TupleType.parse(UINT8);
    public static final TupleType ownerOfType = TupleType.parse(ADDRESS);
    public static final TupleType getApprovedType = TupleType.parse(ADDRESS);
    public static final TupleType nameType = TupleType.parse(STRING);
    public static final TupleType symbolType = TupleType.parse(STRING);
    public static final TupleType tokenUriType = TupleType.parse(STRING);
    public static final TupleType ercTransferType = TupleType.parse(BOOL);
    public static final TupleType isApprovedForAllType = TupleType.parse(BOOL);
    public static final TupleType getTokenInfoType =
            TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + TOKEN_INFO + ")");
    public static final TupleType getTokenInfoTypeReplacedAddress =
            TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + TOKEN_INFO_REPLACED_ADDRESS + ")");
    public static final TupleType getFungibleTokenInfoType =
            TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + FUNGIBLE_TOKEN_INFO + ")");
    public static final TupleType getFungibleTokenInfoTypeReplacedAddress =
            TupleType.parse(
                    RESPONSE_STATUS_AT_BEGINNING + FUNGIBLE_TOKEN_INFO_REPLACED_ADDRESS + ")");
    public static final TupleType getNonFungibleTokenInfoType =
            TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + NON_FUNGIBLE_TOKEN_INFO + ")");
    public static final TupleType getNonFungibleTokenInfoTypeReplacedAddress =
            TupleType.parse(
                    RESPONSE_STATUS_AT_BEGINNING + NON_FUNGIBLE_TOKEN_INFO_REPLACED_ADDRESS + ")");

    public enum FunctionType {
        NOT_SPECIFIED,
        CREATE,
        MINT,
        BURN,
        TOTAL_SUPPLY,
        DECIMALS,
        BALANCE,
        OWNER,
        TOKEN_URI,
        NAME,
        SYMBOL,
        ERC_TRANSFER,
        ALLOWANCE,
        APPROVE,
        GET_APPROVED,
        IS_APPROVED_FOR_ALL,
        GET_TOKEN_INFO,
        GET_FUNGIBLE_TOKEN_INFO,
        GET_NON_FUNGIBLE_TOKEN_INFO
    }
}
