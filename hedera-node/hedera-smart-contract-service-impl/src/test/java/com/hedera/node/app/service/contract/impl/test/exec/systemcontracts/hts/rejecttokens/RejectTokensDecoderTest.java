/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.rejecttokens;

import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.token.TokenReference;
import com.hedera.hapi.node.token.TokenRejectTransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.rejecttokens.RejectTokensDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.rejecttokens.RejectTokensTranslator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.LedgerConfig;
import com.swirlds.config.api.Configuration;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RejectTokensDecoderTest {

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private Configuration configuration;

    @Mock
    private LedgerConfig ledgerConfig;

    private RejectTokensDecoder subject;

    @BeforeEach
    void setup() {
        subject = new RejectTokensDecoder();

        lenient().when(attempt.addressIdConverter()).thenReturn(addressIdConverter);
    }

    @Test
    void decodeHtsCall() {
        // given:
        given(attempt.configuration()).willReturn(configuration);
        given(configuration.getConfigData(LedgerConfig.class)).willReturn(ledgerConfig);
        given(ledgerConfig.tokenRejectsMaxLen()).willReturn(10);
        given(addressIdConverter.convert(asHeadlongAddress(SENDER_ID.accountNum())))
                .willReturn(SENDER_ID);
        final var encoded = Bytes.wrapByteBuffer(RejectTokensTranslator.TOKEN_REJECT.encodeCall(Tuple.of(
                asHeadlongAddress(SENDER_ID.accountNum()),
                new Address[] {FUNGIBLE_TOKEN_HEADLONG_ADDRESS},
                new Tuple[] {})));

        // when
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());

        final var expected = TokenRejectTransactionBody.newBuilder()
                .owner(SENDER_ID)
                .rejections(TokenReference.newBuilder()
                        .fungibleToken(FUNGIBLE_TOKEN_ID)
                        .build())
                .owner(SENDER_ID)
                .build();

        // then
        final var decoded = subject.decodeTokenRejects(attempt);

        assertNotNull(decoded);
        assertEquals(expected, decoded.tokenReject());
    }

    @Test
    void decodeHtsCallNFT() {
        // given:
        given(attempt.configuration()).willReturn(configuration);
        given(configuration.getConfigData(LedgerConfig.class)).willReturn(ledgerConfig);
        given(ledgerConfig.tokenRejectsMaxLen()).willReturn(10);
        given(addressIdConverter.convert(asHeadlongAddress(SENDER_ID.accountNum())))
                .willReturn(SENDER_ID);

        final var encoded = Bytes.wrapByteBuffer(RejectTokensTranslator.TOKEN_REJECT.encodeCall(
                Tuple.of(asHeadlongAddress(SENDER_ID.accountNum()), new Address[] {}, new Tuple[] {
                    Tuple.of(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 1L)
                })));

        // when
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());

        final var expected = TokenRejectTransactionBody.newBuilder()
                .owner(SENDER_ID)
                .rejections(TokenReference.newBuilder()
                        .nft(NftID.newBuilder()
                                .tokenId(NON_FUNGIBLE_TOKEN_ID)
                                .serialNumber(1L)
                                .build())
                        .build())
                .owner(SENDER_ID)
                .build();

        // then
        final var decoded = subject.decodeTokenRejects(attempt);

        assertNotNull(decoded);
        assertEquals(expected, decoded.tokenReject());
    }

    @Test
    void decodeFailsIfReferencesExceedLimits() {
        // given:
        given(attempt.configuration()).willReturn(configuration);
        given(configuration.getConfigData(LedgerConfig.class)).willReturn(ledgerConfig);
        given(ledgerConfig.tokenRejectsMaxLen()).willReturn(10);

        final var encoded = Bytes.wrapByteBuffer(RejectTokensTranslator.TOKEN_REJECT.encodeCall(Tuple.of(
                OWNER_HEADLONG_ADDRESS,
                new Address[] {
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS
                },
                new Tuple[] {
                    Tuple.of(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 1L),
                    Tuple.of(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 1L),
                    Tuple.of(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 1L),
                    Tuple.of(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 1L),
                    Tuple.of(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 1L),
                    Tuple.of(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 1L),
                })));

        // when
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());

        // then
        assertThatExceptionOfType(HandleException.class)
                .isThrownBy(() -> subject.decodeTokenRejects(attempt))
                .withMessage(TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED.toString());
    }

    @Test
    void decodeHRCFungible() {
        // given:
        given(attempt.senderId()).willReturn(SENDER_ID);

        // when
        given(attempt.redirectTokenId()).willReturn(FUNGIBLE_TOKEN_ID);

        final var expected = TokenRejectTransactionBody.newBuilder()
                .rejections(TokenReference.newBuilder()
                        .fungibleToken(FUNGIBLE_TOKEN_ID)
                        .build())
                .owner(SENDER_ID)
                .build();

        // then
        final var decoded = subject.decodeHrcTokenRejectFT(attempt);

        assertNotNull(decoded);
        assertEquals(expected, decoded.tokenReject());
    }

    @Test
    void decodeHRCNft() {
        // given:
        given(attempt.configuration()).willReturn(configuration);
        given(configuration.getConfigData(LedgerConfig.class)).willReturn(ledgerConfig);
        given(ledgerConfig.tokenRejectsMaxLen()).willReturn(10);

        final var encoded = Bytes.wrapByteBuffer(
                RejectTokensTranslator.HRC_TOKEN_REJECT_NFT.encodeCall(Tuple.singleton(new long[] {1L})));

        // when
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.redirectTokenId()).willReturn(NON_FUNGIBLE_TOKEN_ID);

        final var expected = TokenRejectTransactionBody.newBuilder()
                .rejections(TokenReference.newBuilder()
                        .nft(NftID.newBuilder()
                                .tokenId(NON_FUNGIBLE_TOKEN_ID)
                                .serialNumber(1L)
                                .build())
                        .build())
                .owner(SENDER_ID)
                .build();

        // then
        final var decoded = subject.decodeHrcTokenRejectNFT(attempt);

        assertNotNull(decoded);
        assertEquals(expected, decoded.tokenReject());
    }

    @Test
    void decodeFailsWhenHRCNftExceedsLimits() {
        // given:
        given(attempt.configuration()).willReturn(configuration);
        given(configuration.getConfigData(LedgerConfig.class)).willReturn(ledgerConfig);
        given(ledgerConfig.tokenRejectsMaxLen()).willReturn(2);

        final var encoded = Bytes.wrapByteBuffer(
                RejectTokensTranslator.HRC_TOKEN_REJECT_NFT.encodeCall(Tuple.singleton(new long[] {1L, 2L, 3L})));

        // when
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());

        // then
        assertThatExceptionOfType(HandleException.class)
                .isThrownBy(() -> subject.decodeHrcTokenRejectNFT(attempt))
                .withMessage(TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED.toString());
    }
}
