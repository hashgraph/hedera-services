/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.store.contracts.precompile.TokenKeyType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.junit.jupiter.api.Test;

// FUTURE WORK Add tests for token info encoding methods
class EncodingFacadeTest {
    private final EncodingFacade subject = new EncodingFacade();

    private static final Bytes RETURN_FUNGIBLE_MINT_FOR_10_TOKENS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                        + "0000000000000000000000000000000000000000000000000000000000"
                        + "00000a0000000000000000000000000000000000000000000000000000"
                        + "0000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                        + "0000000000000000000000000000000000000000000000000000000000"
                        + "0000020000000000000000000000000000000000000000000000000000"
                        + "00000000006000000000000000000000000000000000000000000000000"
                        + "00000000000000002000000000000000000000000000000000000000000"
                        + "00000000000000000000010000000000000000000000000000000000000000000000000000000000000002");
    private static final Bytes RETURN_BURN_FOR_49_TOKENS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                            + "0000000000000000000000000000000000000000000000000000000000000031");
    private static final Bytes MINT_FAILURE_FROM_INVALID_TOKEN_ID =
            Bytes.fromHexString(
                    "0x00000000000000000000000000000000000000000000000000000000000000a7"
                        + "0000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000"
                        + "0000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes BURN_FAILURE_FROM_TREASURY_NOT_OWNER =
            Bytes.fromHexString(
                    "0x00000000000000000000000000000000000000000000000000000000000000fc"
                            + "0000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_TOTAL_SUPPLY_FOR_50_TOKENS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000032");

    private static final Bytes RETURN_DECIMALS_10 =
            Bytes.fromHexString(
                    "0x000000000000000000000000000000000000000000000000000000000000000a");

    private static final Bytes RETURN_3 =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000003");

    private static final Bytes RETURN_TOKEN_URI_FIRST =
            Bytes.fromHexString(
                    "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000"
                        + "000000000000000000000000054649525354000000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_NAME_TOKENA =
            Bytes.fromHexString(
                    "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000"
                        + "00000000000000000000006546f6b656e410000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_SYMBOL_F =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000"
                        + "00000000000000000000000014600000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_TRUE =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000001");

    private static final Bytes RETURN_ADDRESS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000008");

    private static final Bytes TRANSFER_EVENT =
            Bytes.fromHexString("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");

    private static final Bytes RETURN_CREATE_SUCCESS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                            + "0000000000000000000000000000000000000000000000000000000000000008");

    private static final Bytes CREATE_FAILURE_FROM_INVALID_EXPIRATION_TIME =
            Bytes.fromHexString(
                    "0x000000000000000000000000000000000000000000000000000000000000002d"
                            + "0000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_GET_TOKEN_INFO =
            Bytes.fromHexString(
                    "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000001f40000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000d000000000000000000000000000000000000000000000000000000000000000d200000000000000000000000000000000000000000000000000000000000000d400000000000000000000000000000000000000000000000000000000000000d60000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a0000000000000000000000000000000000000000000000000000000000000047000000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000003e800000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000006346bfdb0000000000000000000000000000000000000000000000000000000000000471000000000000000000000000000000000000000000000000000000000076a70000000000000000000000000000000000000000000000000000000000000000077072696d617279000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008414b44444e59444900000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044a554d5000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000700000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000000000036000000000000000000000000000000000000000000000000000000000000004a000000000000000000000000000000000000000000000000000000000000005e000000000000000000000000000000000000000000000000000000000000007200000000000000000000000000000000000000000000000000000000000000860000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020718790ef2434ae809229d631b38d231b3de7e1a26dd0f075ef7f3560a32447450000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002094905a8cd34bc0bf1e18717114942d57989350a3dd180b4d21ebbe1420c537650000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020059df7c237aa10d8de0606a3a176c8ab1f50a82d9bdc9e4d6bc16161fd981aa60000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020337935cc04c8a2ec26a0895d166056907918c39a2bcbaff53ec2f2f22cbf85260000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020631997336df6ea08202537c75687178c6e37a806a8f6a545b34e6251702440a10000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002028f0a81e25e15f18df661a587abb07165501894c8c94033e0faf1da0fb612e6a0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000209ea273a0a12ca0c2b384bab5019e7f4377b171bf60d9088e592259f4cf8020a6000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043078303300000000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_GET_FUNGIBLE_TOKEN_INFO =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000001f40000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000d000000000000000000000000000000000000000000000000000000000000000d200000000000000000000000000000000000000000000000000000000000000d400000000000000000000000000000000000000000000000000000000000000d60000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a0000000000000000000000000000000000000000000000000000000000000049d00000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000003e800000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000006346d4e0000000000000000000000000000000000000000000000000000000000000049e000000000000000000000000000000000000000000000000000000000076a700000000000000000000000000000000000000000000000000000000000000000d46756e6769626c65546f6b656e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002465400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044a554d5000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000700000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000000000036000000000000000000000000000000000000000000000000000000000000004a000000000000000000000000000000000000000000000000000000000000005e000000000000000000000000000000000000000000000000000000000000007200000000000000000000000000000000000000000000000000000000000000860000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002071fb990bbd0454ec02b8aceecf02190ddb791be338c45b5fabb69e79301f91180000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020611b0bc0fd0dc88b68ef7d411805d20c5f831deee6aa4eee6798857ec5addc0b0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020f5cc9aaead096175d36e8de65813e9b5588305b63c568d05956bb296d93947d50000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002074c8b6d4c6358b4870f91cc0c32fce984c0be6132d3247cf5824a79573a8d2880000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020ed1d693b6c9938b2f8b9c42fc7a25eeec4bf5303e6680ea3b200d1153c19cbe30000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002069d6c3d475bd24ed69fd597cd8ae809e1d4c5e06f7e123fb097e14a20924eda60000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002032061b445ecccbc10b2f5d6802d8f93ef1f395d3b6f7daf7f925c1ab2171d09e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043078303300000000000000000000000000000000000000000000000000000000");

    final Address logger = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);

    @Test
    void canEncodeEip1014Address() {
        final var literalEip1014 = "0x8ff8eb31713b9ff374d893d21f3b9eb732a307a5";
        final var besuAddress = Address.fromHexString(literalEip1014);
        final var headlongAddress = EncodingFacade.convertBesuAddressToHeadlongAddress(besuAddress);
        assertEquals(literalEip1014, ("" + headlongAddress).toLowerCase());
    }

    @Test
    void decodeGetTokenInfo() {
        final var expiry =
                new Expiry(
                        1665581019,
                        Address.wrap(
                                Bytes.fromHexString("0x0000000000000000000000000000000000000471")),
                        7776000L);
        final var tokenKeys = new ArrayList<TokenKey>();
        final var adminKey =
                new TokenKey(
                        TokenKeyType.ADMIN_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    113, -121, -112, -17, 36, 52, -82, -128, -110, 41, -42, 49, -77,
                                    -115, 35, 27, 61, -25, -31, -94, 109, -48, -16, 117, -17, 127,
                                    53, 96, -93, 36, 71, 69
                                },
                                new byte[] {},
                                null));
        final var kycKey =
                new TokenKey(
                        TokenKeyType.KYC_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    -108, -112, 90, -116, -45, 75, -64, -65, 30, 24, 113, 113, 20,
                                    -108, 45, 87, -104, -109, 80, -93, -35, 24, 11, 77, 33, -21,
                                    -66, 20, 32, -59, 55, 101
                                },
                                new byte[] {},
                                null));
        final var freezeKey =
                new TokenKey(
                        TokenKeyType.FREEZE_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    5, -99, -9, -62, 55, -86, 16, -40, -34, 6, 6, -93, -95, 118,
                                    -56, -85, 31, 80, -88, 45, -101, -36, -98, 77, 107, -63, 97, 97,
                                    -3, -104, 26, -90
                                },
                                new byte[] {},
                                null));
        final var wipeKey =
                new TokenKey(
                        TokenKeyType.WIPE_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    51, 121, 53, -52, 4, -56, -94, -20, 38, -96, -119, 93, 22, 96,
                                    86, -112, 121, 24, -61, -102, 43, -53, -81, -11, 62, -62, -14,
                                    -14, 44, -65, -123, 38
                                },
                                new byte[] {},
                                null));
        final var supplyKey =
                new TokenKey(
                        TokenKeyType.SUPPLY_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    99, 25, -105, 51, 109, -10, -22, 8, 32, 37, 55, -57, 86, -121,
                                    23, -116, 110, 55, -88, 6, -88, -10, -91, 69, -77, 78, 98, 81,
                                    112, 36, 64, -95
                                },
                                new byte[] {},
                                null));
        final var feeScheduleKey =
                new TokenKey(
                        TokenKeyType.FEE_SCHEDULE_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    40, -16, -88, 30, 37, -31, 95, 24, -33, 102, 26, 88, 122, -69,
                                    7, 22, 85, 1, -119, 76, -116, -108, 3, 62, 15, -81, 29, -96, -5,
                                    97, 46, 106
                                },
                                new byte[] {},
                                null));
        final var pauseKey =
                new TokenKey(
                        TokenKeyType.PAUSE_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    -98, -94, 115, -96, -95, 44, -96, -62, -77, -124, -70, -75, 1,
                                    -98, 127, 67, 119, -79, 113, -65, 96, -39, 8, -114, 89, 34, 89,
                                    -12, -49, -128, 32, -90
                                },
                                new byte[] {},
                                null));
        tokenKeys.add(adminKey);
        tokenKeys.add(kycKey);
        tokenKeys.add(freezeKey);
        tokenKeys.add(wipeKey);
        tokenKeys.add(supplyKey);
        tokenKeys.add(feeScheduleKey);
        tokenKeys.add(pauseKey);

        final var hederaToken =
                new HederaToken(
                        "primary",
                        "AKDDNYDI",
                        Address.wrap(
                                Bytes.fromHexString("0x0000000000000000000000000000000000000470")),
                        "JUMP",
                        true,
                        1000,
                        true,
                        tokenKeys,
                        expiry);
        final TokenInfo tokenInfo =
                new TokenInfo(
                        hederaToken,
                        500,
                        false,
                        false,
                        false,
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        Bytes.fromHexString("0x03").toString());

        final var encodedResult = subject.encodeGetTokenInfo(tokenInfo);
        assertEquals(RETURN_GET_TOKEN_INFO, encodedResult);
    }

    @Test
    void decodeGetFungibleTokenInfo() {
        final var expiry =
                new Expiry(
                        1665586400,
                        Address.wrap(
                                Bytes.fromHexString("0x000000000000000000000000000000000000049e")),
                        7776000L);
        final var tokenKeys = new ArrayList<TokenKey>();
        final var adminKey =
                new TokenKey(
                        TokenKeyType.ADMIN_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    113, -5, -103, 11, -67, 4, 84, -20, 2, -72, -84, -18, -49, 2,
                                    25, 13, -37, 121, 27, -29, 56, -60, 91, 95, -85, -74, -98, 121,
                                    48, 31, -111, 24
                                },
                                new byte[] {},
                                null));
        final var kycKey =
                new TokenKey(
                        TokenKeyType.KYC_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    97, 27, 11, -64, -3, 13, -56, -117, 104, -17, 125, 65, 24, 5,
                                    -46, 12, 95, -125, 29, -18, -26, -86, 78, -18, 103, -104, -123,
                                    126, -59, -83, -36, 11
                                },
                                new byte[] {},
                                null));
        final var freezeKey =
                new TokenKey(
                        TokenKeyType.FREEZE_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    -11, -52, -102, -82, -83, 9, 97, 117, -45, 110, -115, -26, 88,
                                    19, -23, -75, 88, -125, 5, -74, 60, 86, -115, 5, -107, 107, -78,
                                    -106, -39, 57, 71, -43
                                },
                                new byte[] {},
                                null));
        final var wipeKey =
                new TokenKey(
                        TokenKeyType.WIPE_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    116, -56, -74, -44, -58, 53, -117, 72, 112, -7, 28, -64, -61,
                                    47, -50, -104, 76, 11, -26, 19, 45, 50, 71, -49, 88, 36, -89,
                                    -107, 115, -88, -46, -120
                                },
                                new byte[] {},
                                null));
        final var supplyKey =
                new TokenKey(
                        TokenKeyType.SUPPLY_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    -19, 29, 105, 59, 108, -103, 56, -78, -8, -71, -60, 47, -57,
                                    -94, 94, -18, -60, -65, 83, 3, -26, 104, 14, -93, -78, 0, -47,
                                    21, 60, 25, -53, -29
                                },
                                new byte[] {},
                                null));
        final var feeScheduleKey =
                new TokenKey(
                        TokenKeyType.FEE_SCHEDULE_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    105, -42, -61, -44, 117, -67, 36, -19, 105, -3, 89, 124, -40,
                                    -82, -128, -98, 29, 76, 94, 6, -9, -31, 35, -5, 9, 126, 20, -94,
                                    9, 36, -19, -90
                                },
                                new byte[] {},
                                null));
        final var pauseKey =
                new TokenKey(
                        TokenKeyType.PAUSE_KEY.value(),
                        new KeyValue(
                                false,
                                null,
                                new byte[] {
                                    50, 6, 27, 68, 94, -52, -53, -63, 11, 47, 93, 104, 2, -40, -7,
                                    62, -15, -13, -107, -45, -74, -9, -38, -9, -7, 37, -63, -85, 33,
                                    113, -48, -98
                                },
                                new byte[] {},
                                null));
        tokenKeys.add(adminKey);
        tokenKeys.add(kycKey);
        tokenKeys.add(freezeKey);
        tokenKeys.add(wipeKey);
        tokenKeys.add(supplyKey);
        tokenKeys.add(feeScheduleKey);
        tokenKeys.add(pauseKey);

        final var hederaToken =
                new HederaToken(
                        "FungibleToken",
                        "FT",
                        Address.wrap(
                                Bytes.fromHexString("0x000000000000000000000000000000000000049d")),
                        "JUMP",
                        true,
                        1000,
                        true,
                        tokenKeys,
                        expiry);
        final var tokenInfo =
                new TokenInfo(
                        hederaToken,
                        500,
                        false,
                        false,
                        false,
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        Bytes.fromHexString("0x03").toString());
        final var fungibleTokenInfo = new FungibleTokenInfo(tokenInfo, 1);
        final var encodedResult = subject.encodeGetFungibleTokenInfo(fungibleTokenInfo);
        assertEquals(RETURN_GET_FUNGIBLE_TOKEN_INFO, encodedResult);
    }

    @Test
    void decodeReturnResultForFungibleMint() {
        final var decodedResult = subject.encodeMintSuccess(10, null);
        assertEquals(RETURN_FUNGIBLE_MINT_FOR_10_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForNonFungibleMint() {
        final var decodedResult = subject.encodeMintSuccess(2, new long[] {1, 2});
        assertEquals(RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForBurn() {
        final var decodedResult = subject.encodeBurnSuccess(49);
        assertEquals(RETURN_BURN_FOR_49_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForCreateSuccess() {
        final var decodedResult = subject.encodeCreateSuccess(senderAddress);
        assertEquals(RETURN_CREATE_SUCCESS, decodedResult);
    }

    @Test
    void decodeReturnResultForCreateFailure() {
        final var decodedResult = subject.encodeCreateFailure(INVALID_EXPIRATION_TIME);
        assertEquals(CREATE_FAILURE_FROM_INVALID_EXPIRATION_TIME, decodedResult);
    }

    @Test
    void decodeReturnResultForTotalSupply() {
        final var decodedResult = subject.encodeTotalSupply(50);
        assertEquals(RETURN_TOTAL_SUPPLY_FOR_50_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForDecimals() {
        final var decodedResult = subject.encodeDecimals(10);
        assertEquals(RETURN_DECIMALS_10, decodedResult);
    }

    @Test
    void decodeReturnResultForBalance() {
        final var decodedResult = subject.encodeBalance(3);
        assertEquals(RETURN_3, decodedResult);
    }

    @Test
    void decodeReturnResultForTokenUri() {
        final var decodedResult = subject.encodeTokenUri("FIRST");
        assertEquals(RETURN_TOKEN_URI_FIRST, decodedResult);
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
    void decodeReturnResultForTransfer() {
        final var decodedResult = subject.encodeEcFungibleTransfer(true);
        assertEquals(RETURN_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForApprove() {
        final var decodedResult = subject.encodeApprove(true);
        assertEquals(RETURN_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForIsApprovedForAll() {
        final var decodedResult = subject.encodeIsApprovedForAll(true);
        assertEquals(RETURN_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForAllowance() {
        final var decodedResult = subject.encodeAllowance(3);
        assertEquals(RETURN_3, decodedResult);
    }

    @Test
    void decodeReturnResultForGetApproved() {
        final var decodedResult = subject.encodeGetApproved(senderAddress);
        assertEquals(RETURN_ADDRESS, decodedResult);
    }

    @Test
    void decodeReturnResultForOwner() {
        final var decodedResult = subject.encodeOwner(senderAddress);
        assertEquals(RETURN_ADDRESS, decodedResult);
    }

    @Test
    void logBuilderWithTopics() {
        final var log =
                EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(logger)
                        .forEventSignature(TRANSFER_EVENT)
                        .forIndexedArgument(senderAddress)
                        .forIndexedArgument(recipientAddress)
                        .build();

        final List<LogTopic> topics = new ArrayList<>();
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000008")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000006")));

        assertEquals(new Log(logger, Bytes.EMPTY, topics), log);
    }

    @Test
    void logBuilderWithTopicsWithDifferentTypes() {
        final var log =
                EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(logger)
                        .forEventSignature(TRANSFER_EVENT)
                        .forIndexedArgument(senderAddress)
                        .forIndexedArgument(20L)
                        .forIndexedArgument(BigInteger.valueOf(20))
                        .forIndexedArgument(Boolean.TRUE)
                        .forIndexedArgument(false)
                        .build();

        final List<LogTopic> topics = new ArrayList<>();
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000008")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000014")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000014")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000001")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000000")));

        assertEquals(new Log(logger, Bytes.EMPTY, topics), log);
    }

    @Test
    void logBuilderWithData() {
        final var tupleType = TupleType.parse("(address,uint256,uint256,bool,bool)");
        final var log =
                EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(logger)
                        .forEventSignature(TRANSFER_EVENT)
                        .forDataItem(senderAddress)
                        .forDataItem(9L)
                        .forDataItem(BigInteger.valueOf(9))
                        .forDataItem(Boolean.TRUE)
                        .forDataItem(false)
                        .build();

        final var dataItems = new ArrayList<>();
        dataItems.add(convertBesuAddressToHeadlongAddress(senderAddress));
        dataItems.add(BigInteger.valueOf(9));
        dataItems.add(BigInteger.valueOf(9));
        dataItems.add(true);
        dataItems.add(false);
        final var tuple = Tuple.of(dataItems.toArray());

        final List<LogTopic> topics = new ArrayList<>();
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));

        assertEquals(new Log(logger, Bytes.wrap(tupleType.encode(tuple).array()), topics), log);
    }

    @Test
    void createsExpectedMintFailureResult() {
        assertEquals(
                MINT_FAILURE_FROM_INVALID_TOKEN_ID, subject.encodeMintFailure(INVALID_TOKEN_ID));
    }

    @Test
    void createsExpectedBurnFailureResult() {
        assertEquals(
                BURN_FAILURE_FROM_TREASURY_NOT_OWNER,
                subject.encodeBurnFailure(TREASURY_MUST_OWN_BURNED_NFT));
    }

    private com.esaulpaugh.headlong.abi.Address convertBesuAddressToHeadlongAddress(
            final Address addressToBeConverted) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(
                        addressToBeConverted.toBigInteger()));
    }
}
