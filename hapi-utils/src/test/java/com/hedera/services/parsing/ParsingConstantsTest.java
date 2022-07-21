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

import static com.hedera.services.parsing.ParsingConstants.ADDRESS;
import static com.hedera.services.parsing.ParsingConstants.BOOL;
import static com.hedera.services.parsing.ParsingConstants.FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.parsing.ParsingConstants.INT32;
import static com.hedera.services.parsing.ParsingConstants.NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.parsing.ParsingConstants.RESPONSE_STATUS_AT_BEGINNING;
import static com.hedera.services.parsing.ParsingConstants.STRING;
import static com.hedera.services.parsing.ParsingConstants.TOKEN_INFO;
import static com.hedera.services.parsing.ParsingConstants.UINT256;
import static com.hedera.services.parsing.ParsingConstants.UINT8;
import static com.hedera.services.parsing.ParsingConstants.allowanceOfType;
import static com.hedera.services.parsing.ParsingConstants.approveOfType;
import static com.hedera.services.parsing.ParsingConstants.balanceOfType;
import static com.hedera.services.parsing.ParsingConstants.burnReturnType;
import static com.hedera.services.parsing.ParsingConstants.createReturnType;
import static com.hedera.services.parsing.ParsingConstants.decimalsType;
import static com.hedera.services.parsing.ParsingConstants.ercTransferType;
import static com.hedera.services.parsing.ParsingConstants.getApprovedType;
import static com.hedera.services.parsing.ParsingConstants.getFungibleTokenInfoType;
import static com.hedera.services.parsing.ParsingConstants.getNonFungibleTokenInfoType;
import static com.hedera.services.parsing.ParsingConstants.getTokenInfoType;
import static com.hedera.services.parsing.ParsingConstants.hapiAllowanceOfType;
import static com.hedera.services.parsing.ParsingConstants.hapiGetApprovedType;
import static com.hedera.services.parsing.ParsingConstants.hapiIsApprovedForAllType;
import static com.hedera.services.parsing.ParsingConstants.isApprovedForAllType;
import static com.hedera.services.parsing.ParsingConstants.mintReturnType;
import static com.hedera.services.parsing.ParsingConstants.nameType;
import static com.hedera.services.parsing.ParsingConstants.notSpecifiedType;
import static com.hedera.services.parsing.ParsingConstants.ownerOfType;
import static com.hedera.services.parsing.ParsingConstants.symbolType;
import static com.hedera.services.parsing.ParsingConstants.tokenUriType;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.parsing.ParsingConstants.FunctionType;
import org.junit.jupiter.api.Test;

class ParsingConstantsTest {

    @Test
    void functionTypeValidation() {
        assertEquals("ERC_ALLOWANCE", FunctionType.ERC_ALLOWANCE.name());
        assertEquals("ERC_APPROVE", FunctionType.ERC_APPROVE.name());
        assertEquals("ERC_BALANCE", FunctionType.ERC_BALANCE.name());
        assertEquals("ERC_IS_APPROVED_FOR_ALL", FunctionType.ERC_IS_APPROVED_FOR_ALL.name());
        assertEquals("ERC_DECIMALS", FunctionType.ERC_DECIMALS.name());
        assertEquals("ERC_TRANSFER", FunctionType.ERC_TRANSFER.name());
        assertEquals("ERC_GET_APPROVED", FunctionType.ERC_GET_APPROVED.name());
        assertEquals("ERC_OWNER", FunctionType.ERC_OWNER.name());
        assertEquals("ERC_SYMBOL", FunctionType.ERC_SYMBOL.name());
        assertEquals("ERC_TOTAL_SUPPLY", FunctionType.ERC_TOTAL_SUPPLY.name());
        assertEquals("ERC_TOKEN_URI", FunctionType.ERC_TOKEN_URI.name());
        assertEquals("ERC_NAME", FunctionType.ERC_NAME.name());
        assertEquals(
                "HAPI_GET_FUNGIBLE_TOKEN_INFO", FunctionType.HAPI_GET_FUNGIBLE_TOKEN_INFO.name());
        assertEquals(
                "HAPI_GET_NON_FUNGIBLE_TOKEN_INFO",
                FunctionType.HAPI_GET_NON_FUNGIBLE_TOKEN_INFO.name());
        assertEquals("HAPI_GET_TOKEN_INFO", FunctionType.HAPI_GET_TOKEN_INFO.name());
        assertEquals("HAPI_MINT", FunctionType.HAPI_MINT.name());
        assertEquals("HAPI_BURN", FunctionType.HAPI_BURN.name());
        assertEquals("HAPI_CREATE", FunctionType.HAPI_CREATE.name());
        assertEquals("HAPI_ALLOWANCE", FunctionType.HAPI_ALLOWANCE.name());
        assertEquals("HAPI_APPROVE_NFT", FunctionType.HAPI_APPROVE_NFT.name());
        assertEquals("HAPI_APPROVE", FunctionType.HAPI_APPROVE.name());
        assertEquals("HAPI_GET_APPROVED", FunctionType.HAPI_GET_APPROVED.name());
        assertEquals("HAPI_IS_APPROVED_FOR_ALL", FunctionType.HAPI_IS_APPROVED_FOR_ALL.name());
        assertEquals("NOT_SPECIFIED", FunctionType.NOT_SPECIFIED.name());
    }

    @Test
    void tupleTypesValidation() {
        assertEquals(allowanceOfType, TupleType.parse(UINT256));
        assertEquals(approveOfType, TupleType.parse(BOOL));
        assertEquals(balanceOfType, TupleType.parse(UINT256));
        assertEquals(burnReturnType, TupleType.parse("(int32,uint64)"));
        assertEquals(createReturnType, TupleType.parse("(int32,address)"));
        assertEquals(decimalsType, TupleType.parse(UINT8));
        assertEquals(ercTransferType, TupleType.parse(BOOL));
        assertEquals(getApprovedType, TupleType.parse(ADDRESS));
        assertEquals(
                getFungibleTokenInfoType,
                TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + FUNGIBLE_TOKEN_INFO + ")"));
        assertEquals(
                getTokenInfoType, TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + TOKEN_INFO + ")"));
        assertEquals(
                getNonFungibleTokenInfoType,
                TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + NON_FUNGIBLE_TOKEN_INFO + ")"));
        assertEquals(isApprovedForAllType, TupleType.parse(BOOL));
        assertEquals(mintReturnType, TupleType.parse("(int32,uint64,int64[])"));
        assertEquals(nameType, TupleType.parse(STRING));
        assertEquals(notSpecifiedType, TupleType.parse(INT32));
        assertEquals(ownerOfType, TupleType.parse(ADDRESS));
        assertEquals(symbolType, TupleType.parse(STRING));
        assertEquals(tokenUriType, TupleType.parse(STRING));
        assertEquals(hapiAllowanceOfType, TupleType.parse("(int32,uint256)"));
        assertEquals(hapiGetApprovedType, TupleType.parse("(int32,bytes32)"));
        assertEquals(hapiIsApprovedForAllType, TupleType.parse("(int32,bool)"));
    }
}
