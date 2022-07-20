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
import static com.hedera.services.parsing.ParsingConstants.isApprovedForAllType;
import static com.hedera.services.parsing.ParsingConstants.mintReturnType;
import static com.hedera.services.parsing.ParsingConstants.nameType;
import static com.hedera.services.parsing.ParsingConstants.notSpecifiedType;
import static com.hedera.services.parsing.ParsingConstants.ownerOfType;
import static com.hedera.services.parsing.ParsingConstants.symbolType;
import static com.hedera.services.parsing.ParsingConstants.tokenUriType;
import static com.hedera.services.parsing.ParsingConstants.totalSupplyType;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.parsing.ParsingConstants.FunctionType;
import org.junit.jupiter.api.Test;

class ParsingConstantsTest {

    @Test
    void functionTypeValidation() {
        assertEquals("ALLOWANCE", FunctionType.ALLOWANCE.name());
        assertEquals("APPROVE", FunctionType.APPROVE.name());
        assertEquals("BALANCE", FunctionType.BALANCE.name());
        assertEquals("BURN", FunctionType.BURN.name());
        assertEquals("CREATE", FunctionType.CREATE.name());
        assertEquals("DECIMALS", FunctionType.DECIMALS.name());
        assertEquals("ERC_TRANSFER", FunctionType.ERC_TRANSFER.name());
        assertEquals("GET_APPROVED", FunctionType.GET_APPROVED.name());
        assertEquals("GET_FUNGIBLE_TOKEN_INFO", FunctionType.GET_FUNGIBLE_TOKEN_INFO.name());
        assertEquals(
                "GET_NON_FUNGIBLE_TOKEN_INFO", FunctionType.GET_NON_FUNGIBLE_TOKEN_INFO.name());
        assertEquals("GET_TOKEN_INFO", FunctionType.GET_TOKEN_INFO.name());
        assertEquals("IS_APPROVED_FOR_ALL", FunctionType.IS_APPROVED_FOR_ALL.name());
        assertEquals("MINT", FunctionType.MINT.name());
        assertEquals("NAME", FunctionType.NAME.name());
        assertEquals("NOT_SPECIFIED", FunctionType.NOT_SPECIFIED.name());
        assertEquals("OWNER", FunctionType.OWNER.name());
        assertEquals("SYMBOL", FunctionType.SYMBOL.name());
        assertEquals("TOTAL_SUPPLY", FunctionType.TOTAL_SUPPLY.name());
        assertEquals("TOKEN_URI", FunctionType.TOKEN_URI.name());
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
        assertEquals(totalSupplyType, TupleType.parse(UINT256));
    }
}
