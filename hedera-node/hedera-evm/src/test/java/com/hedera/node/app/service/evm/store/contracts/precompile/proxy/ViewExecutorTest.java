/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.store.contracts.precompile.proxy;

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_KEY;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.MINIMUM_TINYBARS_COST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenDefaultFreezeStatusWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenDefaultKycStatusWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenExpiryInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenKeyWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenGetCustomFeesWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenKeyType;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmFungibleTokenInfoPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenDefaultFreezeStatus;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenDefaultKycStatus;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenExpiryInfoPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenKeyPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenTypePrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsFrozenPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsKycPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsTokenPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmNonFungibleTokenInfoPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmTokenGetCustomFeesPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmTokenInfoPrecompile;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ViewExecutorTest {
    @Mock
    private MessageFrame frame;

    @Mock
    private BlockValues blockValues;

    @Mock
    private EvmEncodingFacade evmEncodingFacade;

    @Mock
    private ViewGasCalculator viewGasCalculator;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private EvmKey key;

    ViewExecutor subject;

    private static final long timestamp = 10L;
    private static final Timestamp resultingTimestamp =
            Timestamp.newBuilder().setSeconds(timestamp).build();
    private static final long gas = 100L;

    private EvmTokenInfo evmTokenInfo;
    private Bytes tokenInfoEncoded;
    private Bytes isFrozenEncoded;

    public static final Address fungibleTokenAddress =
            Address.fromHexString("0x000000000000000000000000000000000000077e");
    public static final Address nonfungibleTokenAddress =
            Address.fromHexString("0x000000000000000000000000000000000000077c");
    public static final Address accountAddress = Address.fromHexString("0x000000000000000000000000000000000000077a");
    public static final Address spenderAddress = Address.fromHexString("0x000000000000000000000000000000000000077b");

    private static final Bytes RETURN_SUCCESS_TRUE =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000016"
                    + "0000000000000000000000000000000000000000000000000000000000000001");

    private ByteString fromString(final String value) {
        return ByteString.copyFrom(Bytes.fromHexString(value).toArray());
    }

    @BeforeEach
    void setUp() {
        evmTokenInfo = new EvmTokenInfo(
                fromString("0x03").toByteArray(),
                1,
                false,
                "FT",
                "NAME",
                "MEMO",
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005cc")),
                1L,
                1000L,
                0,
                0L);

        isFrozenEncoded = Bytes.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000"
                        + "000000000000000000000000001");

        tokenInfoEncoded = Bytes.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000360000000000000000000000000000000000000000000000000000000000000038000000000000000000000000000000000000000000000000000000000000003a000000000000000000000000000000000000000000000000000000000000003c0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a000000000000000000000000000000000000000000000000000000000000005cc00000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003e80000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044e414d45000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002465400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044d454d4f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043078303300000000000000000000000000000000000000000000000000000000");
    }

    @Test
    void computeGetTokenDefaultFreezeStatus() {
        prerequisites(ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS, fungibleTokenAddress);

        try (MockedStatic<EvmGetTokenDefaultFreezeStatus> utilities =
                Mockito.mockStatic(EvmGetTokenDefaultFreezeStatus.class)) {
            final var wrapper = new GetTokenDefaultFreezeStatusWrapper<>(fungibleTokenAddress.toArrayUnsafe());
            utilities
                    .when(() -> EvmGetTokenDefaultFreezeStatus.decodeTokenDefaultFreezeStatus(any()))
                    .thenReturn(wrapper);

            given(tokenAccessor.isTokenAddress(fungibleTokenAddress)).willReturn(true);
            given(evmEncodingFacade.encodeGetTokenDefaultFreezeStatus(anyBoolean()))
                    .willReturn(RETURN_SUCCESS_TRUE);

            assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
        }
    }

    @Test
    void computeGetTokenDefaultKycStatus() {
        prerequisites(ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS, fungibleTokenAddress);

        try (MockedStatic<EvmGetTokenDefaultKycStatus> utilities =
                Mockito.mockStatic(EvmGetTokenDefaultKycStatus.class)) {
            final var wrapper = new GetTokenDefaultKycStatusWrapper<>(fungibleTokenAddress.toArrayUnsafe());
            utilities
                    .when(() -> EvmGetTokenDefaultKycStatus.decodeTokenDefaultKycStatus(any()))
                    .thenReturn(wrapper);

            given(tokenAccessor.isTokenAddress(fungibleTokenAddress)).willReturn(true);
            given(evmEncodingFacade.encodeGetTokenDefaultKycStatus(anyBoolean()))
                    .willReturn(RETURN_SUCCESS_TRUE);

            assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
        }
    }

    @Test
    void computeIsKyc() {
        prerequisites(ABI_ID_IS_KYC, fungibleTokenAddress);

        try (MockedStatic<EvmIsKycPrecompile> utilities = Mockito.mockStatic(EvmIsKycPrecompile.class)) {
            final var wrapper =
                    new GrantRevokeKycWrapper<>(fungibleTokenAddress.toArrayUnsafe(), accountAddress.toArrayUnsafe());
            utilities.when(() -> EvmIsKycPrecompile.decodeIsKyc(any())).thenReturn(wrapper);

            given(tokenAccessor.isTokenAddress(fungibleTokenAddress)).willReturn(true);
            given(evmEncodingFacade.encodeIsKyc(anyBoolean())).willReturn(RETURN_SUCCESS_TRUE);

            assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
        }
    }

    @Test
    void computeGetTokenInfo() {
        prerequisites(ABI_ID_GET_TOKEN_INFO, fungibleTokenAddress);

        try (MockedStatic<EvmTokenInfoPrecompile> utilities = Mockito.mockStatic(EvmTokenInfoPrecompile.class)) {
            final var tokenInfoWrapper = TokenInfoWrapper.forToken(fungibleTokenAddress.toArrayUnsafe());
            utilities
                    .when(() -> EvmTokenInfoPrecompile.decodeGetTokenInfo(any()))
                    .thenReturn(tokenInfoWrapper);

            given(tokenAccessor.evmInfoForToken(fungibleTokenAddress)).willReturn(Optional.of(evmTokenInfo));
            given(evmEncodingFacade.encodeGetTokenInfo(any())).willReturn(tokenInfoEncoded);

            assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
        }
    }

    @Test
    void computeGetFungibleTokenInfo() {
        prerequisites(ABI_ID_GET_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress);

        try (MockedStatic<EvmFungibleTokenInfoPrecompile> utilities =
                Mockito.mockStatic(EvmFungibleTokenInfoPrecompile.class)) {
            final var tokenInfoWrapper = TokenInfoWrapper.forFungibleToken(fungibleTokenAddress.toArrayUnsafe());
            utilities
                    .when(() -> EvmFungibleTokenInfoPrecompile.decodeGetFungibleTokenInfo(any()))
                    .thenReturn(tokenInfoWrapper);

            given(tokenAccessor.evmInfoForToken(fungibleTokenAddress)).willReturn(Optional.of(evmTokenInfo));

            given(evmEncodingFacade.encodeGetFungibleTokenInfo(any())).willReturn(tokenInfoEncoded);

            assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
        }
    }

    @Test
    void computeGetNonFungibleTokenInfo() {
        prerequisites(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO, nonfungibleTokenAddress);

        try (MockedStatic<EvmNonFungibleTokenInfoPrecompile> utilities =
                Mockito.mockStatic(EvmNonFungibleTokenInfoPrecompile.class)) {
            final var tokenInfoWrapper =
                    TokenInfoWrapper.forNonFungibleToken(nonfungibleTokenAddress.toArrayUnsafe(), 1L);
            utilities
                    .when(() -> EvmNonFungibleTokenInfoPrecompile.decodeGetNonFungibleTokenInfo(any()))
                    .thenReturn(tokenInfoWrapper);
            given(tokenAccessor.evmInfoForToken(nonfungibleTokenAddress)).willReturn(Optional.of(evmTokenInfo));
            given(tokenAccessor.evmNftInfo(nonfungibleTokenAddress, 1L)).willReturn(Optional.of(new EvmNftInfo()));
            given(evmEncodingFacade.encodeGetNonFungibleTokenInfo(any(), any())).willReturn(tokenInfoEncoded);

            assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
        }
    }

    @Test
    void computeIsFrozen() {
        prerequisites(ABI_ID_IS_FROZEN, fungibleTokenAddress);

        try (MockedStatic<EvmIsFrozenPrecompile> utilities = Mockito.mockStatic(EvmIsFrozenPrecompile.class)) {
            final var isFrozenWrapper = TokenFreezeUnfreezeWrapper.forIsFrozen(
                    fungibleTokenAddress.toArrayUnsafe(), accountAddress.toArrayUnsafe());
            utilities.when(() -> EvmIsFrozenPrecompile.decodeIsFrozen(any())).thenReturn(isFrozenWrapper);

            given(tokenAccessor.isFrozen(accountAddress, fungibleTokenAddress)).willReturn(true);
            given(tokenAccessor.isTokenAddress(fungibleTokenAddress)).willReturn(true);
            given(evmEncodingFacade.encodeIsFrozen(true)).willReturn(isFrozenEncoded);

            assertEquals(Pair.of(gas, isFrozenEncoded), subject.computeCosted());
        }
    }

    @Test
    void computeGetTokenCustomFees() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_CUSTOM_FEES, fungibleTokenAddress);
        final var tokenCustomFeesEncoded = Bytes.fromHexString(
                "0x000000000000000000000000000000000000000000000000000000000000001600000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000280000000000000000000000000000000000000000000000000000000000000036000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000064000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000900000000000000000000000000000000000000000000000000000000000000640000000000000000000000000000000000000000000000000000000000000378000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000009000000000000000000000000000000000000000000000000000000000000003200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000090000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000f0000000000000000000000000000000000000000000000000000000000000064000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000032000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000090000000000000000000000000000000000000000000000000000000000000000");

        try (MockedStatic<EvmTokenGetCustomFeesPrecompile> utilities =
                Mockito.mockStatic(EvmTokenGetCustomFeesPrecompile.class)) {
            utilities
                    .when(() -> EvmTokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(input))
                    .thenReturn(new TokenGetCustomFeesWrapper<>(fungibleTokenAddress.toArrayUnsafe()));

            given(tokenAccessor.infoForTokenCustomFees(fungibleTokenAddress))
                    .willReturn(Optional.of(List.of(new CustomFee())));
            given(evmEncodingFacade.encodeTokenGetCustomFees(any())).willReturn(tokenCustomFeesEncoded);

            assertEquals(Pair.of(gas, tokenCustomFeesEncoded), subject.computeCosted());
        }
    }

    @Test
    void computeGetTokenKey() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_KEY, fungibleTokenAddress);
        final var getTokenKeyEncoded = Bytes.fromHexString(
                "0x000000000000000000000000000000000000000000000000000000000000001600000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000209e417334d2ea6be459624060e3efdc1b459a884bc6a9c232349af35e9060ed620000000000000000000000000000000000000000000000000000000000000000");

        try (MockedStatic<EvmGetTokenKeyPrecompile> utilities = Mockito.mockStatic(EvmGetTokenKeyPrecompile.class)) {
            utilities
                    .when(() -> EvmGetTokenKeyPrecompile.decodeGetTokenKey(input))
                    .thenReturn(new GetTokenKeyWrapper<>(fungibleTokenAddress.toArrayUnsafe(), 1));

            given(tokenAccessor.isTokenAddress(fungibleTokenAddress)).willReturn(true);
            given(tokenAccessor.keyOf(fungibleTokenAddress, TokenKeyType.ADMIN_KEY))
                    .willReturn(key);
            given(evmEncodingFacade.encodeGetTokenKey(any())).willReturn(getTokenKeyEncoded);

            assertEquals(Pair.of(gas, getTokenKeyEncoded), subject.computeCosted());
        }
    }

    @Test
    void computeIsToken() {
        final var input = prerequisites(ABI_ID_IS_TOKEN, fungibleTokenAddress);

        try (MockedStatic<EvmIsTokenPrecompile> utilities = Mockito.mockStatic(EvmIsTokenPrecompile.class)) {
            final var isTokenWrapper = TokenInfoWrapper.forToken(fungibleTokenAddress.toArrayUnsafe());
            utilities.when(() -> EvmIsTokenPrecompile.decodeIsToken(input)).thenReturn(isTokenWrapper);

            given(tokenAccessor.isTokenAddress(fungibleTokenAddress)).willReturn(true);
            given(evmEncodingFacade.encodeIsToken(true)).willReturn(RETURN_SUCCESS_TRUE);
            assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
        }
    }

    @Test
    void computeGetTokenType() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_TYPE, fungibleTokenAddress);

        try (MockedStatic<EvmGetTokenTypePrecompile> utilities = Mockito.mockStatic(EvmGetTokenTypePrecompile.class)) {
            final var wrapper = TokenInfoWrapper.forToken(fungibleTokenAddress.toArrayUnsafe());
            utilities
                    .when(() -> EvmGetTokenTypePrecompile.decodeGetTokenType(input))
                    .thenReturn(wrapper);

            given(tokenAccessor.isTokenAddress(fungibleTokenAddress)).willReturn(true);
            given(tokenAccessor.typeOf(fungibleTokenAddress)).willReturn(TokenType.FUNGIBLE_COMMON);
            given(evmEncodingFacade.encodeGetTokenType(TokenType.FUNGIBLE_COMMON.ordinal()))
                    .willReturn(RETURN_SUCCESS_TRUE);
            assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
        }
    }

    @Test
    void computeGetTokenExpiryInfo() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_EXPIRY_INFO, fungibleTokenAddress);
        final var tokenExpiryInfoEncoded = Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000006370f6ca00000000000000000000000000000000000000000000000000000000000004c2000000000000000000000000000000000000000000000000000000000076a700");

        try (MockedStatic<EvmGetTokenExpiryInfoPrecompile> utilities =
                Mockito.mockStatic(EvmGetTokenExpiryInfoPrecompile.class)) {
            utilities
                    .when(() -> EvmGetTokenExpiryInfoPrecompile.decodeGetTokenExpiryInfo(input))
                    .thenReturn(new GetTokenExpiryInfoWrapper<>(fungibleTokenAddress.toArrayUnsafe()));

            given(tokenAccessor.evmInfoForToken(fungibleTokenAddress)).willReturn(Optional.of(evmTokenInfo));
            given(evmEncodingFacade.encodeGetTokenExpiryInfo(any())).willReturn(tokenExpiryInfoEncoded);

            assertEquals(Pair.of(gas, tokenExpiryInfoEncoded), subject.computeCosted());
        }
    }

    @Test
    void computeGetTokenCustomFeesThrowsWhenTokenDoesNotExists() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_CUSTOM_FEES, fungibleTokenAddress);

        try (MockedStatic<EvmTokenGetCustomFeesPrecompile> utilities =
                Mockito.mockStatic(EvmTokenGetCustomFeesPrecompile.class)) {
            utilities
                    .when(() -> EvmTokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(input))
                    .thenReturn(new TokenGetCustomFeesWrapper<>(fungibleTokenAddress.toArrayUnsafe()));

            assertEquals(Pair.of(gas, null), subject.computeCosted());
            verify(frame).setState(MessageFrame.State.REVERT);
        }
    }

    @Test
    void computeCostedNOT_SUPPORTED() {
        prerequisites(0xffffffff, fungibleTokenAddress);
        assertNull(subject.computeCosted().getRight());
    }

    Bytes prerequisites(final int descriptor, final Bytes tokenAddress) {
        Bytes input = Bytes.concatenate(Bytes.of(Integers.toBytes(descriptor)), tokenAddress);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(timestamp);
        given(viewGasCalculator.compute(resultingTimestamp, MINIMUM_TINYBARS_COST))
                .willReturn(gas);
        this.subject = new ViewExecutor(input, frame, evmEncodingFacade, viewGasCalculator, tokenAccessor);
        return input;
    }
}
