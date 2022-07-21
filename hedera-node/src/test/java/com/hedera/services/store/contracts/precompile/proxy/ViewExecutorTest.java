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
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.proxy.RedirectViewExecutor.MINIMUM_TINYBARS_COST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.config.NetworkInfo;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.Expiry;
import com.hedera.services.store.contracts.precompile.codec.HederaToken;
import com.hedera.services.store.contracts.precompile.codec.TokenInfo;
import com.hedera.services.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.services.store.contracts.precompile.utils.TokenInfoRetrievalUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.ArrayList;
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
    @Mock private DecodingFacade decodingFacade;
    @Mock private ViewGasCalculator viewGasCalculator;
    @Mock private HederaStackedWorldStateUpdater stackedWorldStateUpdater;
    @Mock private WorldLedgers worldLedgers;
    @Mock private BlockValues blockValues;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private NetworkInfo networkInfo;

    public static final AccountID account = IdUtils.asAccount("0.0.777");
    public static final AccountID spender = IdUtils.asAccount("0.0.888");
    public static final TokenID fungible = IdUtils.asToken("0.0.888");
    public static final TokenID nonfungibletoken = IdUtils.asToken("0.0.999");
    public static final NftId nonfungible = new NftId(0, 0, 999, 1);
    public static final Id fungibleId = Id.fromGrpcToken(fungible);
    public static final Id nonfungibleId = Id.fromGrpcToken(nonfungibletoken);
    public static final Address fungibleTokenAddress = fungibleId.asEvmAddress();
    public static final Address nonfungibleTokenAddress = nonfungibleId.asEvmAddress();
    public static final AccountID treasury =
            EntityIdUtils.accountIdFromEvmAddress(
                    Bytes.fromHexString("0x00000000000000000000000000000000000005cc").toArray());
    public static final AccountID autoRenewAccount =
            EntityIdUtils.accountIdFromEvmAddress(Address.ZERO);
    public static final EntityId treasuryEntityId = EntityId.fromGrpcAccountId(treasury);
    public static final EntityId autoRenewEntityId = EntityId.fromGrpcAccountId(autoRenewAccount);

    private static final long timestamp = 10L;
    private static final Timestamp resultingTimestamp =
            Timestamp.newBuilder().setSeconds(timestamp).build();
    private static final long gas = 100L;
    private static final Bytes answer = Bytes.of(1);
    private TokenInfo tokenInfo;
    private Bytes tokenInfoEncoded;

    ViewExecutor subject;
    MockedStatic<TokenInfoRetrievalUtils> tokenInfoRetrievalUtils;

    @BeforeEach
    void setUp() {
        final var token =
                new HederaToken(
                        "NAME",
                        "FT",
                        Address.wrap(
                                Bytes.fromHexString("0x00000000000000000000000000000000000005cc")),
                        "MEMO",
                        false,
                        1000L,
                        false,
                        new ArrayList<>(),
                        new Expiry(0L, Address.ZERO, 0L));
        tokenInfo =
                new TokenInfo(
                        token,
                        1L,
                        false,
                        false,
                        false,
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        "0x03");
        tokenInfoEncoded =
                Bytes.fromHexString(
                        "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000360000000000000000000000000000000000000000000000000000000000000038000000000000000000000000000000000000000000000000000000000000003a000000000000000000000000000000000000000000000000000000000000003c0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a000000000000000000000000000000000000000000000000000000000000005cc00000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003e80000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044e414d45000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002465400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044d454d4f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043078303300000000000000000000000000000000000000000000000000000000");

        tokenInfoRetrievalUtils = Mockito.mockStatic(TokenInfoRetrievalUtils.class);
        tokenInfoRetrievalUtils
                .when(
                        () ->
                                TokenInfoRetrievalUtils.getTokenInfo(
                                        fungible, wrappedLedgers, networkInfo))
                .thenReturn(tokenInfo);
    }

    @AfterEach
    void closeMocks() {
        tokenInfoRetrievalUtils.close();
    }

    @Test
    void computeGetTokenInfo() {
        final var input = prerequisites(ABI_ID_GET_TOKEN_INFO, fungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forToken(fungible);
        given(decodingFacade.decodeGetTokenInfo(input)).willReturn(tokenInfoWrapper);
        given(encodingFacade.encodeGetTokenInfo(any())).willReturn(tokenInfoEncoded);

        assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
    }

    @Test
    void computeGetFungibleTokenInfo() {
        final var input = prerequisites(ABI_ID_GET_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forToken(fungible);
        given(decodingFacade.decodeGetFungibleTokenInfo(input)).willReturn(tokenInfoWrapper);
        given(encodingFacade.encodeGetFungibleTokenInfo(any())).willReturn(tokenInfoEncoded);

        assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
    }

    @Test
    void computeGetNonFungibleTokenInfo() {
        final var input = prerequisites(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO, fungibleTokenAddress);

        final var tokenInfoWrapper = TokenInfoWrapper.forToken(fungible);
        given(decodingFacade.decodeGetNonFungibleTokenInfo(input)).willReturn(tokenInfoWrapper);
        given(encodingFacade.encodeGetNonFungibleTokenInfo(any())).willReturn(tokenInfoEncoded);

        assertEquals(Pair.of(gas, tokenInfoEncoded), subject.computeCosted());
    }

    Bytes prerequisites(final int descriptor, final Bytes tokenAddress) {
        given(frame.getWorldUpdater()).willReturn(stackedWorldStateUpdater);
        given(stackedWorldStateUpdater.trackingLedgers()).willReturn(worldLedgers);
        Bytes input = Bytes.concatenate(Bytes.of(Integers.toBytes(descriptor)), tokenAddress);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(timestamp);
        given(viewGasCalculator.compute(resultingTimestamp, MINIMUM_TINYBARS_COST)).willReturn(gas);
        given(frame.getWorldUpdater()).willReturn(stackedWorldStateUpdater);
        given(stackedWorldStateUpdater.trackingLedgers()).willReturn(worldLedgers);
        this.subject =
                new ViewExecutor(
                        input,
                        frame,
                        encodingFacade,
                        decodingFacade,
                        viewGasCalculator,
                        networkInfo);
        return input;
    }
}
