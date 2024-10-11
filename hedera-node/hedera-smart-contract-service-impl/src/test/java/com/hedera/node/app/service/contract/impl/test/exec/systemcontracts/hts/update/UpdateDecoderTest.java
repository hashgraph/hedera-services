/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.update;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ID;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateExpiryTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateNFTsMetadataTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateTranslator;
import java.time.Instant;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateDecoderTest {
    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private AddressIdConverter addressIdConverter;

    private final UpdateDecoder subject = new UpdateDecoder();
    private final String newName = "NEW NAME";
    private static final long EXPIRY_TIMESTAMP = Instant.now().plusSeconds(3600).toEpochMilli() / 1000;
    private static final long AUTO_RENEW_PERIOD = 8_000_000L;
    private final Tuple expiry = Tuple.of(EXPIRY_TIMESTAMP, OWNER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD);
    private final Tuple hederaToken = Tuple.of(
            newName,
            "symbol",
            OWNER_HEADLONG_ADDRESS,
            "memo",
            true,
            1000L,
            false,
            // TokenKey
            new Tuple[] {},
            // Expiry
            expiry);

    @Nested
    class UpdateTokenInfoAndExpiry {
        @BeforeEach
        void setUp() {
            given(attempt.addressIdConverter()).willReturn(addressIdConverter);
            given(addressIdConverter.convert(OWNER_HEADLONG_ADDRESS)).willReturn(OWNER_ID);
        }

        @Test
        void updateV1Works() {
            final var encoded = Bytes.wrapByteBuffer(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1.encodeCallWithArgs(
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS, hederaToken));
            given(attempt.input()).willReturn(encoded);

            final var body = subject.decodeTokenUpdateV1(attempt);
            final var tokenUpdate = body.tokenUpdateOrThrow();
            assertEquals(tokenUpdate.name(), newName);
        }

        @Test
        void updateV2Works() {
            final var encoded = Bytes.wrapByteBuffer(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2.encodeCallWithArgs(
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS, hederaToken));
            given(attempt.input()).willReturn(encoded);

            final var body = subject.decodeTokenUpdateV2(attempt);
            final var tokenUpdate = body.tokenUpdateOrThrow();
            assertEquals(tokenUpdate.name(), newName);
        }

        @Test
        void updateV3Works() {
            final var encoded = Bytes.wrapByteBuffer(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3.encodeCallWithArgs(
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS, hederaToken));
            given(attempt.input()).willReturn(encoded);

            final var body = subject.decodeTokenUpdateV3(attempt);
            final var tokenUpdate = body.tokenUpdateOrThrow();
            assertEquals(tokenUpdate.name(), newName);
        }

        @Test
        void updateExpiryV1Works() {
            final var encoded =
                    Bytes.wrapByteBuffer(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V1.encodeCallWithArgs(
                            FUNGIBLE_TOKEN_HEADLONG_ADDRESS, expiry));
            given(attempt.input()).willReturn(encoded);

            final var body = subject.decodeTokenUpdateExpiryV1(attempt);
            final var tokenUpdate = body.tokenUpdateOrThrow();

            assertNotNull(tokenUpdate.expiry());
            assertNotNull(tokenUpdate.autoRenewPeriod());

            assertEquals(EXPIRY_TIMESTAMP, tokenUpdate.expiry().seconds());
            assertEquals(AUTO_RENEW_PERIOD, tokenUpdate.autoRenewPeriod().seconds());
            assertEquals(OWNER_ID, tokenUpdate.autoRenewAccount());
        }

        @Test
        void updateExpiryV2Works() {
            final var encoded =
                    Bytes.wrapByteBuffer(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V2.encodeCallWithArgs(
                            FUNGIBLE_TOKEN_HEADLONG_ADDRESS, expiry));
            given(attempt.input()).willReturn(encoded);

            final var body = subject.decodeTokenUpdateExpiryV2(attempt);
            final var tokenUpdate = body.tokenUpdateOrThrow();

            assertNotNull(tokenUpdate.expiry());
            assertNotNull(tokenUpdate.autoRenewPeriod());

            assertEquals(EXPIRY_TIMESTAMP, tokenUpdate.expiry().seconds());
            assertEquals(AUTO_RENEW_PERIOD, tokenUpdate.autoRenewPeriod().seconds());
            assertEquals(OWNER_ID, tokenUpdate.autoRenewAccount());
        }
    }

    @Test
    void updateNFTsMetadataWorks() {
        final var encoded = Bytes.wrapByteBuffer(UpdateNFTsMetadataTranslator.UPDATE_NFTs_METADATA.encodeCallWithArgs(
                NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, new long[] {1, 2, 3}, "Jerry".getBytes()));
        given(attempt.input()).willReturn(encoded);

        final var body = subject.decodeUpdateNFTsMetadata(attempt);
        final var tokenUpdate = requireNonNull(body).tokenUpdateNftsOrThrow();

        assertNotNull(tokenUpdate.metadata());
        assertEquals("Jerry", tokenUpdate.metadata().asUtf8String());
        assertEquals(3, tokenUpdate.serialNumbers().size());
    }
}
