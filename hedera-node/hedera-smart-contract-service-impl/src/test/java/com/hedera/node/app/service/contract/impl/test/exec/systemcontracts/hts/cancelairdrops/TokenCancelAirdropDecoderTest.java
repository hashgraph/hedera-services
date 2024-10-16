/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.cancelairdrops;

import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_AIRDROP_ID_LIST_TOO_LONG;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.cancelairdrops.TokenCancelAirdropTranslator.CANCEL_AIRDROP;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.cancelairdrops.TokenCancelAirdropTranslator.HRC_CANCEL_AIRDROP_FT;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.cancelairdrops.TokenCancelAirdropTranslator.HRC_CANCEL_AIRDROP_NFT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ACCOUNT_AS_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.cancelairdrops.TokenCancelAirdropDecoder;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.TokensConfig;
import com.swirlds.config.api.Configuration;
import java.util.ArrayList;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenCancelAirdropDecoderTest {

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private Enhancement enhancement;

    @Mock
    private HederaNativeOperations nativeOperations;

    @Mock
    private Configuration configuration;

    @Mock
    private TokensConfig tokensConfig;

    private TokenCancelAirdropDecoder subject;

    @BeforeEach
    void setup() {
        subject = new TokenCancelAirdropDecoder();

        lenient().when(attempt.addressIdConverter()).thenReturn(addressIdConverter);
    }

    @Test
    void cancelAirdropDecoder1FTTest() {
        // given:
        given(attempt.configuration()).willReturn(configuration);
        given(attempt.enhancement()).willReturn(enhancement);
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        given(configuration.getConfigData(TokensConfig.class)).willReturn(tokensConfig);
        given(tokensConfig.maxAllowedPendingAirdropsToCancel()).willReturn(10);
        given(nativeOperations.getToken(FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(FUNGIBLE_TOKEN);
        given(addressIdConverter.convert(asHeadlongAddress(SENDER_ID.accountNum())))
                .willReturn(SENDER_ID);
        given(addressIdConverter.convert(OWNER_ACCOUNT_AS_ADDRESS)).willReturn(OWNER_ID);

        final var encoded = Bytes.wrapByteBuffer(CANCEL_AIRDROP.encodeCall(Tuple.singleton(new Tuple[] {
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L)
        })));
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());

        var expected = new ArrayList<PendingAirdropId>();
        expected.add(PendingAirdropId.newBuilder()
                .senderId(SENDER_ID)
                .receiverId(OWNER_ID)
                .fungibleTokenType(FUNGIBLE_TOKEN_ID)
                .build());
        // when:
        final var decoded = subject.decodeCancelAirdrop(attempt);

        // then:
        assertNotNull(decoded.tokenCancelAirdrop());
        assertEquals(expected, decoded.tokenCancelAirdrop().pendingAirdrops());
    }

    @Test
    void failsIfPendingAirdropsAboveLimit() {
        // given:
        given(attempt.configuration()).willReturn(configuration);
        //        given(attempt.enhancement()).willReturn(enhancement);
        //        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        given(configuration.getConfigData(TokensConfig.class)).willReturn(tokensConfig);
        given(tokensConfig.maxAllowedPendingAirdropsToCancel()).willReturn(10);
        //        given(nativeOperations.getToken(FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(FUNGIBLE_TOKEN);
        //        given(addressIdConverter.convert(asHeadlongAddress(SENDER_ID.accountNum())))
        //                .willReturn(SENDER_ID);
        //        given(addressIdConverter.convert(OWNER_ACCOUNT_AS_ADDRESS)).willReturn(OWNER_ID);

        final var encoded = Bytes.wrapByteBuffer(CANCEL_AIRDROP.encodeCall(Tuple.singleton(new Tuple[] {
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L),
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L),
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L),
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L),
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L),
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L),
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L),
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L),
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L),
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L),
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L)
        })));
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());

        assertThrows(HandleException.class, () -> subject.decodeCancelAirdrop(attempt));
    }

    @Test
    void failsIfTokenIsNull() {
        // given:
        given(attempt.configuration()).willReturn(configuration);
        given(attempt.enhancement()).willReturn(enhancement);
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        given(configuration.getConfigData(TokensConfig.class)).willReturn(tokensConfig);
        given(tokensConfig.maxAllowedPendingAirdropsToCancel()).willReturn(10);
        given(nativeOperations.getToken(FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(null);
        given(addressIdConverter.convert(asHeadlongAddress(SENDER_ID.accountNum())))
                .willReturn(SENDER_ID);
        given(addressIdConverter.convert(OWNER_ACCOUNT_AS_ADDRESS)).willReturn(OWNER_ID);

        final var encoded = Bytes.wrapByteBuffer(CANCEL_AIRDROP.encodeCall(Tuple.singleton(new Tuple[] {
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    0L)
        })));
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());

        assertThrows(
                HandleException.class,
                () -> subject.decodeCancelAirdrop(attempt),
                PENDING_AIRDROP_ID_LIST_TOO_LONG.protoName());
    }

    @Test
    void failsIfTokenIsNullHRCFT() {
        final var encoded = Bytes.wrapByteBuffer(HRC_CANCEL_AIRDROP_FT.encodeCallWithArgs(OWNER_ACCOUNT_AS_ADDRESS));
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());

        assertThrows(
                HandleException.class,
                () -> subject.decodeCancelAirdropFT(attempt),
                PENDING_AIRDROP_ID_LIST_TOO_LONG.protoName());
    }

    @Test
    void failsIfTokenIsNullHRCNFT() {
        final var encoded =
                Bytes.wrapByteBuffer(HRC_CANCEL_AIRDROP_NFT.encodeCallWithArgs(OWNER_ACCOUNT_AS_ADDRESS, 1L));
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());

        assertThrows(
                HandleException.class,
                () -> subject.decodeCancelAirdropNFT(attempt),
                PENDING_AIRDROP_ID_LIST_TOO_LONG.protoName());
    }

    @Test
    void cancelAirdropDecoder1NFTTest() {
        // given:
        given(attempt.configuration()).willReturn(configuration);
        given(attempt.enhancement()).willReturn(enhancement);
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        given(configuration.getConfigData(TokensConfig.class)).willReturn(tokensConfig);
        given(tokensConfig.maxAllowedPendingAirdropsToCancel()).willReturn(10);
        given(nativeOperations.getToken(NON_FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(NON_FUNGIBLE_TOKEN);
        given(addressIdConverter.convert(asHeadlongAddress(SENDER_ID.accountNum())))
                .willReturn(SENDER_ID);
        given(addressIdConverter.convert(OWNER_ACCOUNT_AS_ADDRESS)).willReturn(OWNER_ID);

        final var encoded = Bytes.wrapByteBuffer(CANCEL_AIRDROP.encodeCall(Tuple.singleton(new Tuple[] {
            Tuple.of(
                    asHeadlongAddress(SENDER_ID.accountNum()),
                    OWNER_ACCOUNT_AS_ADDRESS,
                    NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    1L)
        })));
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());
        var expected = new ArrayList<PendingAirdropId>();
        expected.add(PendingAirdropId.newBuilder()
                .senderId(SENDER_ID)
                .receiverId(OWNER_ID)
                .nonFungibleToken(
                        NftID.newBuilder().tokenId(NON_FUNGIBLE_TOKEN_ID).serialNumber(1L))
                .build());

        // when:
        final var decoded = subject.decodeCancelAirdrop(attempt);

        // then:
        assertNotNull(decoded.tokenCancelAirdrop());
        assertEquals(expected, decoded.tokenCancelAirdrop().pendingAirdrops());
    }

    @Test
    void cancelFTAirdropHRC() {
        // given:
        given(attempt.redirectTokenId()).willReturn(FUNGIBLE_TOKEN_ID);
        given(attempt.senderId()).willReturn(SENDER_ID);

        final var encoded = Bytes.wrapByteBuffer(HRC_CANCEL_AIRDROP_FT.encodeCallWithArgs(OWNER_ACCOUNT_AS_ADDRESS));
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());
        given(addressIdConverter.convert(OWNER_ACCOUNT_AS_ADDRESS)).willReturn(OWNER_ID);

        final var decoded = subject.decodeCancelAirdropFT(attempt);
        var expected = new ArrayList<PendingAirdropId>();
        expected.add(PendingAirdropId.newBuilder()
                .senderId(SENDER_ID)
                .receiverId(OWNER_ID)
                .fungibleTokenType(FUNGIBLE_TOKEN_ID)
                .build());

        // then:
        assertNotNull(decoded.tokenCancelAirdrop());
        assertEquals(expected, decoded.tokenCancelAirdrop().pendingAirdrops());
    }

    @Test
    void cancelNFTAirdropHRC() {
        // given:
        given(attempt.redirectTokenId()).willReturn(NON_FUNGIBLE_TOKEN_ID);
        given(attempt.senderId()).willReturn(SENDER_ID);

        final var encoded =
                Bytes.wrapByteBuffer(HRC_CANCEL_AIRDROP_NFT.encodeCallWithArgs(OWNER_ACCOUNT_AS_ADDRESS, 1L));
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());
        given(addressIdConverter.convert(OWNER_ACCOUNT_AS_ADDRESS)).willReturn(OWNER_ID);

        final var decoded = subject.decodeCancelAirdropNFT(attempt);
        var expected = new ArrayList<PendingAirdropId>();
        expected.add(PendingAirdropId.newBuilder()
                .senderId(SENDER_ID)
                .receiverId(OWNER_ID)
                .nonFungibleToken(
                        NftID.newBuilder().tokenId(NON_FUNGIBLE_TOKEN_ID).serialNumber(1L))
                .build());
        // then:
        assertNotNull(decoded.tokenCancelAirdrop());
        assertEquals(expected, decoded.tokenCancelAirdrop().pendingAirdrops());
    }
}
