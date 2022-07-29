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
package com.hedera.services.store.contracts.precompile.proxy;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.services.store.contracts.precompile.proxy.RedirectViewExecutor.MINIMUM_TINYBARS_COST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GetTokenDefaultFreezeStatusWrapper;
import com.hedera.services.store.contracts.precompile.codec.GetTokenDefaultKycStatusWrapper;
import com.hedera.services.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ViewExecutorTest {

    @Mock private MessageFrame frame;
    @Mock private EncodingFacade encodingFacade;
    @Mock private DecodingFacade decodingFacade;
    @Mock private ViewGasCalculator viewGasCalculator;
    @Mock private BlockValues blockValues;
    @Mock private StateView stateView;
    @Mock private WorldLedgers ledgers;

    public static final AccountID account = IdUtils.asAccount("0.0.777");
    public static final AccountID spender = IdUtils.asAccount("0.0.888");
    public static final TokenID fungible = IdUtils.asToken("0.0.888");
    public static final TokenID nonfungibletoken = IdUtils.asToken("0.0.999");
    public static final Id fungibleId = Id.fromGrpcToken(fungible);
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
    private static final Bytes answer = Bytes.of(1);
    private TokenInfo tokenInfo;
    private Bytes tokenInfoEncoded;

    ViewExecutor subject;

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
        tokenInfoEncoded =
                Bytes.fromHexString(
                        "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000360000000000000000000000000000000000000000000000000000000000000038000000000000000000000000000000000000000000000000000000000000003a000000000000000000000000000000000000000000000000000000000000003c0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a000000000000000000000000000000000000000000000000000000000000005cc00000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003e80000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044e414d45000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002465400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044d454d4f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043078303300000000000000000000000000000000000000000000000000000000");
    }

    private ByteString fromString(final String value) {
        return ByteString.copyFrom(Bytes.fromHexString(value).toArray());
    }

    @Test
    void computeGetTokenDefaultFreezeStatus() {
        final var input =
                prerequisites(ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS, fungibleTokenAddress);

        final var wrapper = new GetTokenDefaultFreezeStatusWrapper(fungible);
        given(decodingFacade.decodeTokenDefaultFreezeStatus(input)).willReturn(wrapper);
        given(encodingFacade.encodeGetTokenDefaultFreezeStatus(anyBoolean()))
                .willReturn(RETURN_SUCCESS_TRUE);

        assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
    }

    @Test
    void computeGetTokenDefaultKycStatus() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS, fungibleTokenAddress);

        final var wrapper = new GetTokenDefaultKycStatusWrapper(fungible);
        given(decodingFacade.decodeTokenDefaultKycStatus(input)).willReturn(wrapper);
        given(encodingFacade.encodeGetTokenDefaultKycStatus(anyBoolean()))
                .willReturn(RETURN_SUCCESS_TRUE);

        assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
    }

    @Test
    void computeIsKyc() {
        final var input = prerequisites(ABI_ID_IS_KYC, fungibleTokenAddress);

        final var wrapper = new GrantRevokeKycWrapper(fungible, account);
        given(decodingFacade.decodeIsKyc(any(), any())).willReturn(wrapper);
        given(encodingFacade.encodeIsKyc(anyBoolean())).willReturn(RETURN_SUCCESS_TRUE);

        assertEquals(Pair.of(gas, RETURN_SUCCESS_TRUE), subject.computeCosted());
    }

    @Test
    void computeGetTokenInfo() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_INFO, fungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forToken(fungible);
        given(decodingFacade.decodeGetTokenInfo(input)).willReturn(tokenInfoWrapper);

        given(stateView.infoForToken(fungible)).willReturn(Optional.of(tokenInfo));
        given(encodingFacade.encodeGetTokenInfo(any())).willReturn(tokenInfoEncoded);

        assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
    }

    @Test
    void computeGetFungibleTokenInfo() {
        final var input = prerequisites(ABI_ID_GET_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forFungibleToken(fungible);
        given(decodingFacade.decodeGetFungibleTokenInfo(input)).willReturn(tokenInfoWrapper);

        given(stateView.infoForToken(fungible)).willReturn(Optional.of(tokenInfo));
        given(encodingFacade.encodeGetFungibleTokenInfo(any())).willReturn(tokenInfoEncoded);

        assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
    }

    @Test
    void computeGetNonFungibleTokenInfo() {
        final var input =
                prerequisites(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO, nonfungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forNonFungibleToken(nonfungibletoken, 1L);
        given(decodingFacade.decodeGetNonFungibleTokenInfo(input)).willReturn(tokenInfoWrapper);

        given(stateView.infoForToken(nonfungibletoken)).willReturn(Optional.of(tokenInfo));
        given(
                        stateView.infoForNft(
                                NftID.newBuilder()
                                        .setTokenID(nonfungibletoken)
                                        .setSerialNumber(1L)
                                        .build()))
                .willReturn(Optional.of(TokenNftInfo.newBuilder().build()));
        given(encodingFacade.encodeGetNonFungibleTokenInfo(any(), any()))
                .willReturn(tokenInfoEncoded);

        assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
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
        given(decodingFacade.decodeGetTokenInfo(input)).willReturn(tokenInfoWrapper);

        given(stateView.infoForToken(any())).willReturn(Optional.empty());

        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    @Test
    void getFungibleTokenInfoRevertsFrameAndReturnsNullOnRevertingException() {
        final var input = prerequisites(ABI_ID_GET_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forFungibleToken(fungible);
        given(decodingFacade.decodeGetFungibleTokenInfo(input)).willReturn(tokenInfoWrapper);

        given(stateView.infoForToken(any())).willReturn(Optional.empty());

        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    @Test
    void getNonFungibleTokenInfoRevertsFrameAndReturnsNullOnRevertingException() {
        final var input =
                prerequisitesForNft(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress, 1L);

        final var tokenInfoWrapper = TokenInfoWrapper.forNonFungibleToken(nonfungibletoken, 1L);
        given(decodingFacade.decodeGetNonFungibleTokenInfo(input)).willReturn(tokenInfoWrapper);

        given(stateView.infoForToken(any())).willReturn(Optional.empty());

        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    @Test
    void getNonFungibleTokenInfoRevertsFrameAndReturnsNullOnRevertingExceptionForInvalidId() {
        final var input =
                prerequisitesForNft(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress, 1L);

        final var tokenInfoWrapper = TokenInfoWrapper.forNonFungibleToken(nonfungibletoken, 1L);
        given(decodingFacade.decodeGetNonFungibleTokenInfo(input)).willReturn(tokenInfoWrapper);

        given(stateView.infoForToken(any())).willReturn(Optional.of(tokenInfo));
        given(stateView.infoForNft(any())).willReturn(Optional.empty());

        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    Bytes prerequisites(final int descriptor, final Bytes tokenAddress) {
        Bytes input = Bytes.concatenate(Bytes.of(Integers.toBytes(descriptor)), tokenAddress);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(timestamp);
        given(viewGasCalculator.compute(resultingTimestamp, MINIMUM_TINYBARS_COST)).willReturn(gas);
        this.subject =
                new ViewExecutor(
                        input,
                        frame,
                        encodingFacade,
                        decodingFacade,
                        viewGasCalculator,
                        stateView,
                        ledgers);
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
        given(blockValues.getTimestamp()).willReturn(timestamp);
        given(viewGasCalculator.compute(resultingTimestamp, MINIMUM_TINYBARS_COST)).willReturn(gas);
        this.subject =
                new ViewExecutor(
                        input,
                        frame,
                        encodingFacade,
                        decodingFacade,
                        viewGasCalculator,
                        stateView,
                        ledgers);
        return input;
    }
}
