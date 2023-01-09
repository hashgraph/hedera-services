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
package com.hedera.node.app.service.mono.store.contracts.precompile.proxy;

import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_KEY;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_TYPE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.proxy.RedirectViewExecutor.MINIMUM_TINYBARS_COST;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FixedFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenDefaultFreezeStatusWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenDefaultKycStatusWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenExpiryInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenGetCustomFeesWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenKeyWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.FungibleTokenInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenDefaultFreezeStatus;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenDefaultKycStatus;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenExpiryInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenKeyPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenTypePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsFrozenPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsKycPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsTokenPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.NonFungibleTokenInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenGetCustomFeesPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenInfoPrecompile;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ViewExecutorTest {

    @Mock private MessageFrame frame;
    @Mock private EncodingFacade encodingFacade;
    @Mock private EvmEncodingFacade evmEncodingFacade;
    @Mock private ViewGasCalculator viewGasCalculator;
    @Mock private BlockValues blockValues;
    @Mock private StateView stateView;
    @Mock private WorldLedgers ledgers;
    @Mock private JKey key;
    @Mock private NetworkInfo networkInfo;
    @Mock private HederaStackedWorldStateUpdater updater;

    public static final AccountID account = IdUtils.asAccount("0.0.777");
    public static final AccountID spender = IdUtils.asAccount("0.0.888");
    public static final TokenID fungible = IdUtils.asToken("0.0.888");
    public static final TokenID nonfungibletoken = IdUtils.asToken("0.0.999");
    public static final Id fungibleId = Id.fromGrpcToken(fungible);
    public static final Id accountId = Id.fromGrpcAccount(account);
    public static final Id nonfungibleId = Id.fromGrpcToken(nonfungibletoken);
    public static final Address fungibleTokenAddress = fungibleId.asEvmAddress();
    public static final Address nonfungibleTokenAddress = nonfungibleId.asEvmAddress();
    public static final AccountID treasury =
            EntityIdUtils.accountIdFromEvmAddress(
                    Bytes.fromHexString("0x00000000000000000000000000000000000005cc").toArray());
    public static final AccountID autoRenewAccount =
            EntityIdUtils.accountIdFromEvmAddress(Address.ZERO);

    private static final long timestamp = 10L;
    private static final Timestamp resultingTimestamp =
            Timestamp.newBuilder().setSeconds(timestamp).build();
    private static final long gas = 100L;
    private static final ByteString ledgerId = ByteString.copyFromUtf8("0xff");
    private TokenInfo tokenInfo;
    private EvmTokenInfo evmTokenInfo;
    private Bytes tokenInfoEncoded;
    private Bytes isFrozenEncoded;

    ViewExecutor subject;
    private MockedStatic<GetTokenDefaultFreezeStatus> getTokenDefaultFreezeStatus;
    private MockedStatic<GetTokenDefaultKycStatus> getTokenDefaultKycStatus;
    private MockedStatic<IsKycPrecompile> isKycPrecompile;
    private MockedStatic<TokenInfoPrecompile> tokenInfoPrecompile;
    private MockedStatic<FungibleTokenInfoPrecompile> fungibleTokenInfoPrecompile;
    private MockedStatic<NonFungibleTokenInfoPrecompile> nonFungibleTokenInfoPrecompile;
    private MockedStatic<IsFrozenPrecompile> isFrozenPrecompile;
    private MockedStatic<TokenGetCustomFeesPrecompile> tokenGetCustomFeesPrecompile;
    private MockedStatic<GetTokenKeyPrecompile> getTokenKeyPrecompile;
    private MockedStatic<IsTokenPrecompile> isTokenPrecompile;
    private MockedStatic<GetTokenTypePrecompile> getTokenTypePrecompile;
    private MockedStatic<GetTokenExpiryInfoPrecompile> getTokenExpiryInfoPrecompile;

    private static final Bytes RETURN_SUCCESS_TRUE =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                            + "0000000000000000000000000000000000000000000000000000000000000001");

    @BeforeEach
    void setUp() {
        tokenInfo =
                TokenInfo.newBuilder()
                        .setLedgerId(fromString("0x03"))
                        .setSupplyTypeValue(1)
                        .setTokenId(fungible)
                        .setDeleted(false)
                        .setSymbol("FT")
                        .setName("NAME")
                        .setMemo("MEMO")
                        .setTreasury(
                                EntityIdUtils.accountIdFromEvmAddress(
                                        Address.wrap(
                                                Bytes.fromHexString(
                                                        "0x00000000000000000000000000000000000005cc"))))
                        .setTotalSupply(1L)
                        .setMaxSupply(1000L)
                        .build();
        evmTokenInfo =
                new EvmTokenInfo(
                        fromString("0x03").toByteArray(),
                        1,
                        false,
                        "FT",
                        "NAME",
                        "MEMO",
                        Address.wrap(
                                Bytes.fromHexString("0x00000000000000000000000000000000000005cc")),
                        1L,
                        1000L,
                        0,
                        0L);

        tokenInfoEncoded =
                Bytes.fromHexString(
                        "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000360000000000000000000000000000000000000000000000000000000000000038000000000000000000000000000000000000000000000000000000000000003a000000000000000000000000000000000000000000000000000000000000003c0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a000000000000000000000000000000000000000000000000000000000000005cc00000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003e80000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044e414d45000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002465400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044d454d4f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043078303300000000000000000000000000000000000000000000000000000000");
        isFrozenEncoded =
                Bytes.fromHexString(
                        "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000"
                            + "000000000000000000000000001");
        getTokenDefaultFreezeStatus = Mockito.mockStatic(GetTokenDefaultFreezeStatus.class);
        getTokenDefaultKycStatus = Mockito.mockStatic(GetTokenDefaultKycStatus.class);
        isKycPrecompile = Mockito.mockStatic(IsKycPrecompile.class);
        tokenInfoPrecompile = Mockito.mockStatic(TokenInfoPrecompile.class);
        fungibleTokenInfoPrecompile = Mockito.mockStatic(FungibleTokenInfoPrecompile.class);
        nonFungibleTokenInfoPrecompile = Mockito.mockStatic(NonFungibleTokenInfoPrecompile.class);
        isFrozenPrecompile = Mockito.mockStatic(IsFrozenPrecompile.class);
        tokenGetCustomFeesPrecompile = Mockito.mockStatic(TokenGetCustomFeesPrecompile.class);
        getTokenKeyPrecompile = Mockito.mockStatic(GetTokenKeyPrecompile.class);
        isTokenPrecompile = Mockito.mockStatic(IsTokenPrecompile.class);
        getTokenTypePrecompile = Mockito.mockStatic(GetTokenTypePrecompile.class);
        getTokenExpiryInfoPrecompile = Mockito.mockStatic(GetTokenExpiryInfoPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        getTokenDefaultFreezeStatus.close();
        getTokenDefaultKycStatus.close();
        isKycPrecompile.close();
        tokenInfoPrecompile.close();
        fungibleTokenInfoPrecompile.close();
        nonFungibleTokenInfoPrecompile.close();
        isFrozenPrecompile.close();
        tokenGetCustomFeesPrecompile.close();
        getTokenKeyPrecompile.close();
        isTokenPrecompile.close();
        getTokenTypePrecompile.close();
        getTokenExpiryInfoPrecompile.close();
    }

    private ByteString fromString(final String value) {
        return ByteString.copyFrom(Bytes.fromHexString(value).toArray());
    }

    @Test
    void computeGetTokenDefaultFreezeStatus() {
        final var input =
                prerequisites(ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS, fungibleTokenAddress);

        final var wrapper = new GetTokenDefaultFreezeStatusWrapper(fungible);
        getTokenDefaultFreezeStatus
                .when(() -> GetTokenDefaultFreezeStatus.decodeTokenDefaultFreezeStatus(input))
                .thenReturn(wrapper);
        given(ledgers.isTokenAddress(fungibleTokenAddress)).willReturn(true);
        given(evmEncodingFacade.encodeGetTokenDefaultFreezeStatus(anyBoolean()))
                .willReturn(RETURN_SUCCESS_TRUE);

        assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
    }

    @Test
    void computeGetTokenDefaultKycStatus() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS, fungibleTokenAddress);

        final var wrapper = new GetTokenDefaultKycStatusWrapper<>(fungible);
        getTokenDefaultKycStatus
                .when(() -> GetTokenDefaultKycStatus.decodeTokenDefaultKycStatus(input))
                .thenReturn(wrapper);
        given(ledgers.isTokenAddress(fungibleTokenAddress)).willReturn(true);
        given(evmEncodingFacade.encodeGetTokenDefaultKycStatus(anyBoolean()))
                .willReturn(RETURN_SUCCESS_TRUE);

        assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
    }

    @Test
    void computeIsKyc() {
        final var input = prerequisites(ABI_ID_IS_KYC, fungibleTokenAddress);

        final var wrapper = new GrantRevokeKycWrapper<>(fungible, account);
        isKycPrecompile.when(() -> IsKycPrecompile.decodeIsKyc(any(), any())).thenReturn(wrapper);
        given(ledgers.isTokenAddress(fungibleTokenAddress)).willReturn(true);
        given(evmEncodingFacade.encodeIsKyc(anyBoolean())).willReturn(RETURN_SUCCESS_TRUE);

        assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
    }

    @Test
    void computeGetTokenInfo() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_INFO, fungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forToken(fungible);
        tokenInfoPrecompile
                .when(() -> TokenInfoPrecompile.decodeGetTokenInfo(input))
                .thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        given(ledgers.evmInfoForToken(fungible, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));
        given(evmEncodingFacade.encodeGetTokenInfo(any())).willReturn(tokenInfoEncoded);

        assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
    }

    @Test
    void computeGetFungibleTokenInfo() {
        final var input = prerequisites(ABI_ID_GET_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forFungibleToken(fungible);
        fungibleTokenInfoPrecompile
                .when(() -> FungibleTokenInfoPrecompile.decodeGetFungibleTokenInfo(input))
                .thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        given(ledgers.evmInfoForToken(fungible, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));

        given(evmEncodingFacade.encodeGetFungibleTokenInfo(any())).willReturn(tokenInfoEncoded);

        assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
    }

    @Test
    void computeGetNonFungibleTokenInfo() {
        final var input =
                prerequisites(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO, nonfungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forNonFungibleToken(nonfungibletoken, 1L);
        nonFungibleTokenInfoPrecompile
                .when(() -> NonFungibleTokenInfoPrecompile.decodeGetNonFungibleTokenInfo(input))
                .thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        given(ledgers.evmInfoForToken(nonfungibletoken, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));
        given(
                        ledgers.evmNftInfo(
                                NftID.newBuilder()
                                        .setTokenID(nonfungibletoken)
                                        .setSerialNumber(1L)
                                        .build(),
                                networkInfo.ledgerId()))
                .willReturn(Optional.of(new EvmNftInfo()));
        given(evmEncodingFacade.encodeGetNonFungibleTokenInfo(any(), any()))
                .willReturn(tokenInfoEncoded);

        assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
    }

    @Test
    void computeIsFrozen() {
        final var input = prerequisites(ABI_ID_IS_FROZEN, fungibleTokenAddress);

        final var isFrozenWrapper = TokenFreezeUnfreezeWrapper.forIsFrozen(fungible, account);
        isFrozenPrecompile
                .when(() -> IsFrozenPrecompile.decodeIsFrozen(any(), any()))
                .thenReturn(isFrozenWrapper);
        given(ledgers.isFrozen(account, fungible)).willReturn(true);
        given(ledgers.isTokenAddress(fungibleTokenAddress)).willReturn(true);
        given(evmEncodingFacade.encodeIsFrozen(true)).willReturn(isFrozenEncoded);

        assertEquals(Pair.of(gas, isFrozenEncoded), subject.computeCosted());
    }

    @Test
    void computeGetTokenCustomFees() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_CUSTOM_FEES, fungibleTokenAddress);
        final var tokenCustomFeesEncoded =
                Bytes.fromHexString(
                        "0x000000000000000000000000000000000000000000000000000000000000001600000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000280000000000000000000000000000000000000000000000000000000000000036000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000064000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000900000000000000000000000000000000000000000000000000000000000000640000000000000000000000000000000000000000000000000000000000000378000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000009000000000000000000000000000000000000000000000000000000000000003200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000090000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000f0000000000000000000000000000000000000000000000000000000000000064000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000032000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000090000000000000000000000000000000000000000000000000000000000000000");

        tokenGetCustomFeesPrecompile
                .when(() -> TokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(input))
                .thenReturn(new TokenGetCustomFeesWrapper<>(fungible));
        given(ledgers.infoForTokenCustomFees(fungible))
                .willReturn(Optional.ofNullable(customFees()));
        given(evmEncodingFacade.encodeTokenGetCustomFees(any())).willReturn(tokenCustomFeesEncoded);

        assertEquals(Pair.of(gas, tokenCustomFeesEncoded), subject.computeCosted());
    }

    @Test
    void computeGetTokenKey() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_KEY, fungibleTokenAddress);
        final var getTokenKeyEncoded =
                Bytes.fromHexString(
                        "0x000000000000000000000000000000000000000000000000000000000000001600000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000209e417334d2ea6be459624060e3efdc1b459a884bc6a9c232349af35e9060ed620000000000000000000000000000000000000000000000000000000000000000");
        getTokenKeyPrecompile
                .when(() -> GetTokenKeyPrecompile.decodeGetTokenKey(input))
                .thenReturn(new GetTokenKeyWrapper(fungible, 1));
        given(ledgers.isTokenAddress(fungibleTokenAddress)).willReturn(true);
        given(ledgers.keyOf(fungible, TokenProperty.ADMIN_KEY)).willReturn(key);
        given(evmEncodingFacade.encodeGetTokenKey(any())).willReturn(getTokenKeyEncoded);

        assertEquals(Pair.of(gas, getTokenKeyEncoded), subject.computeCosted());
    }

    @Test
    void computeGetTokenCustomFeesThrowsWhenTokenDoesNotExists() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_CUSTOM_FEES, fungibleTokenAddress);

        tokenGetCustomFeesPrecompile
                .when(() -> TokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(input))
                .thenReturn(new TokenGetCustomFeesWrapper<>(fungible));
        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    @Test
    void computeIsToken() {
        final var input = prerequisites(ABI_ID_IS_TOKEN, fungibleTokenAddress);
        final var isTokenWrapper = TokenInfoWrapper.forToken(fungible);
        isTokenPrecompile
                .when(() -> IsTokenPrecompile.decodeIsToken(input))
                .thenReturn(isTokenWrapper);
        given(ledgers.isTokenAddress(fungibleTokenAddress)).willReturn(true);
        given(evmEncodingFacade.encodeIsToken(true)).willReturn(RETURN_SUCCESS_TRUE);
        assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
    }

    @Test
    void computeGetTokenType() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_TYPE, fungibleTokenAddress);
        final var wrapper = TokenInfoWrapper.forToken(fungible);
        getTokenTypePrecompile
                .when(() -> GetTokenTypePrecompile.decodeGetTokenType(input))
                .thenReturn(wrapper);
        given(ledgers.isTokenAddress(fungibleTokenAddress)).willReturn(true);
        given(ledgers.typeOf(fungible)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(evmEncodingFacade.encodeGetTokenType(TokenType.FUNGIBLE_COMMON.ordinal()))
                .willReturn(RETURN_SUCCESS_TRUE);
        assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
    }

    @Test
    void computeCostedNOT_SUPPORTED() {
        prerequisites(0xffffffff, fungibleTokenAddress);
        assertNull(subject.computeCosted().getRight());
    }

    @Test
    void getTokenInfoRevertsFrameAndReturnsNullOnRevertingException() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_INFO, fungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forToken(fungible);
        tokenInfoPrecompile
                .when(() -> TokenInfoPrecompile.decodeGetTokenInfo(input))
                .thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    @Test
    void getFungibleTokenInfoRevertsFrameAndReturnsNullOnRevertingException() {
        final var input = prerequisites(ABI_ID_GET_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forFungibleToken(fungible);
        fungibleTokenInfoPrecompile
                .when(() -> FungibleTokenInfoPrecompile.decodeGetFungibleTokenInfo(input))
                .thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    @Test
    void getNonFungibleTokenInfoRevertsFrameAndReturnsNullOnRevertingException() {
        final var input =
                prerequisitesForNft(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress, 1L);

        final var tokenInfoWrapper = TokenInfoWrapper.forNonFungibleToken(nonfungibletoken, 1L);
        nonFungibleTokenInfoPrecompile
                .when(() -> NonFungibleTokenInfoPrecompile.decodeGetNonFungibleTokenInfo(input))
                .thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    @Test
    void getNonFungibleTokenInfoRevertsFrameAndReturnsNullOnRevertingExceptionForInvalidId() {
        final var input =
                prerequisitesForNft(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress, 1L);

        final var tokenInfoWrapper = TokenInfoWrapper.forNonFungibleToken(nonfungibletoken, 1L);
        nonFungibleTokenInfoPrecompile
                .when(() -> NonFungibleTokenInfoPrecompile.decodeGetNonFungibleTokenInfo(input))
                .thenReturn(tokenInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    @Test
    void computeGetTokenExpiryInfo() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_EXPIRY_INFO, fungibleTokenAddress);
        final var tokenExpiryInfoEncoded =
                Bytes.fromHexString(
                        "0x0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000006370f6ca00000000000000000000000000000000000000000000000000000000000004c2000000000000000000000000000000000000000000000000000000000076a700");

        getTokenExpiryInfoPrecompile
                .when(() -> GetTokenExpiryInfoPrecompile.decodeGetTokenExpiryInfo(input))
                .thenReturn(new GetTokenExpiryInfoWrapper<>(fungible));
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        given(ledgers.infoForToken(fungible, networkInfo.ledgerId()))
                .willReturn(Optional.of(tokenInfo));
        given(evmEncodingFacade.encodeGetTokenExpiryInfo(any())).willReturn(tokenExpiryInfoEncoded);

        assertEquals(Pair.of(gas, tokenExpiryInfoEncoded), subject.computeCosted());
    }

    @Test
    void computeGetTokenExpiryInfoFailsAsExpected() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_EXPIRY_INFO, fungibleTokenAddress);

        getTokenExpiryInfoPrecompile
                .when(() -> GetTokenExpiryInfoPrecompile.decodeGetTokenExpiryInfo(input))
                .thenReturn(new GetTokenExpiryInfoWrapper<>(fungible));
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        given(ledgers.infoForToken(fungible, networkInfo.ledgerId())).willReturn(Optional.empty());

        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    Bytes prerequisites(final int descriptor, final Bytes tokenAddress) {
        Bytes input = Bytes.concatenate(Bytes.of(Integers.toBytes(descriptor)), tokenAddress);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(timestamp);
        given(viewGasCalculator.compute(resultingTimestamp, MINIMUM_TINYBARS_COST)).willReturn(gas);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.trackingLedgers()).willReturn(ledgers);
        this.subject =
                new ViewExecutor(input, frame, evmEncodingFacade, viewGasCalculator, stateView);
        return input;
    }

    Bytes prerequisitesForNft(
            final int descriptor, final Bytes tokenAddress, final long serialNumber) {
        Bytes input =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(descriptor)),
                        tokenAddress,
                        Bytes.of(Integers.toBytes(serialNumber)));
        given(frame.getBlockValues()).willReturn(blockValues);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.trackingLedgers()).willReturn(ledgers);
        given(blockValues.getTimestamp()).willReturn(timestamp);
        given(viewGasCalculator.compute(resultingTimestamp, MINIMUM_TINYBARS_COST)).willReturn(gas);
        this.subject =
                new ViewExecutor(input, frame, evmEncodingFacade, viewGasCalculator, stateView);
        return input;
    }

    private List<com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee>
            customFees() {
        final var payerAccountId = asAccount("0.0.9");
        List<com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee>
                customFees = new ArrayList<>();
        FixedFee fixedFeeInHbar =
                new FixedFee(
                        100, null, true, false, EntityIdUtils.asTypedEvmAddress(payerAccountId));
        FixedFee fixedFeeInHts =
                new FixedFee(
                        100,
                        EntityIdUtils.asTypedEvmAddress(fungible),
                        false,
                        false,
                        EntityIdUtils.asTypedEvmAddress(payerAccountId));
        FixedFee fixedFeeSameToken =
                new FixedFee(
                        50, null, true, false, EntityIdUtils.asTypedEvmAddress(payerAccountId));
        FractionalFee fractionalFee =
                new FractionalFee(
                        15, 100, 10, 50, false, EntityIdUtils.asTypedEvmAddress(payerAccountId));

        com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee customFee1 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee1.setFixedFee(fixedFeeInHbar);
        com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee customFee2 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee2.setFixedFee(fixedFeeInHts);
        com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee customFee3 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee3.setFixedFee(fixedFeeSameToken);
        com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee customFee4 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee4.setFractionalFee(fractionalFee);

        return List.of(customFee1, customFee2, customFee3, customFee4);
    }
}
