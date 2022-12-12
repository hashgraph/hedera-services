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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class EvmEncodingFacadeTest {

    private final EvmEncodingFacade subject = new EvmEncodingFacade();

    public static final Address senderAddress = Address.ALTBN128_PAIRING;

    private static final Bytes RETURN_DECIMALS_10 =
            Bytes.fromHexString(
                    "0x000000000000000000000000000000000000000000000000000000000000000a");

    private static final Bytes RETURN_3 =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000003");

    private static final Bytes RETURN_TOTAL_SUPPLY_FOR_50_TOKENS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000032");

    private static final Bytes RETURN_TRUE =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000001");

    private static final Bytes RETURN_GET_TOKEN_TYPE =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000001");

    private static final Bytes RETURN_IS_TOKEN_FROZEN =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000"
                + "000000000000000000000000001");

    private static final Bytes RETURN_SUCCESS_TRUE =
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000016"
                + "0000000000000000000000000000000000000000000000000000000000000001");

    private static final Bytes RETURN_IS_TOKEN =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000001");


    private static final Bytes RETURN_NAME_TOKENA =
            Bytes.fromHexString(
                    "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000"
                        + "00000000000000000000006546f6b656e410000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_SYMBOL_F =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000"
                        + "00000000000000000000000014600000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_TOKEN_URI_FIRST =
            Bytes.fromHexString(
                    "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000"
                        + "000000000000000000000000054649525354000000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_ADDRESS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000008");

    @Test
    void decodeReturnResultForDecimals() {
        final var decodedResult = subject.encodeDecimals(10);
        assertEquals(RETURN_DECIMALS_10, decodedResult);
    }

    @Test
    void decodeReturnResultForAllowanceERC() {
        final var decodedResult = subject.encodeAllowance(3);
        assertEquals(RETURN_3, decodedResult);
    }

    @Test
    void decodeReturnResultForTotalSupply() {
        final var decodedResult = subject.encodeTotalSupply(50);
        assertEquals(RETURN_TOTAL_SUPPLY_FOR_50_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForBalance() {
        final var decodedResult = subject.encodeBalance(3);
        assertEquals(RETURN_3, decodedResult);
    }

    @Test
    void decodeReturnResultForIsApprovedForAllERC() {
        final var decodedResult = subject.encodeIsApprovedForAll(true);
        assertEquals(RETURN_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForGetTokenType() {
        final var decodedResult = subject.encodeGetTokenType(1);
        assertEquals(RETURN_GET_TOKEN_TYPE, decodedResult);
    }

    @Test
    void decodeReturnResultForIsFrozen() {
        final var decodedResult = subject.encodeIsFrozen(true);
        assertEquals(RETURN_IS_TOKEN_FROZEN, decodedResult);
    }

    @Test
    void decodeReturnResultForGetTokenDefaultFreezeStatus() {
        final var decodedResult = subject.encodeGetTokenDefaultFreezeStatus(true);
        assertEquals(RETURN_SUCCESS_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForGetTokenDefaultKycStatus() {
        final var decodedResult = subject.encodeGetTokenDefaultKycStatus(true);
        assertEquals(RETURN_SUCCESS_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForIsKyc() {
        final var decodedResult = subject.encodeIsKyc(true);
        assertEquals(RETURN_SUCCESS_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForIsToken() {
        final var decodedResult = subject.encodeIsToken(true);
        assertEquals(RETURN_IS_TOKEN, decodedResult);
    }

    @Test
    void decodeReturnResultForName() {
        final var decodedResult = subject.encodeName("TokenA");
        assertEquals(RETURN_NAME_TOKENA, decodedResult);
    }

    @Test
    void decodeReturnResultForSymbol() {
        final var decodedResult = subject.encodeSymbol("F");
        assertEquals(RETURN_SYMBOL_F, decodedResult);
    }

    @Test
    void decodeReturnResultForTokenUri() {
        final var decodedResult = subject.encodeTokenUri("FIRST");
        assertEquals(RETURN_TOKEN_URI_FIRST, decodedResult);
    }

    @Test
    void decodeReturnResultForOwner() {
        final var decodedResult = subject.encodeOwner(senderAddress);
        assertEquals(RETURN_ADDRESS, decodedResult);
    }

    @Test
    void decodeReturnResultForGetApprovedERC() {
        final var decodedResult = subject.encodeGetApproved(senderAddress);
        assertEquals(RETURN_ADDRESS, decodedResult);
    }
}
