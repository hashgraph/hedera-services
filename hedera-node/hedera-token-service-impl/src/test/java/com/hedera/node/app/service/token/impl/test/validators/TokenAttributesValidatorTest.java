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

package com.hedera.node.app.service.token.impl.test.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.METADATA_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.test.utils.TxnUtils;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenAttributesValidatorTest {
    private TokenAttributesValidator subject;
    private TokensConfig tokensConfig;

    @BeforeEach
    void setUp() {
        subject = new TokenAttributesValidator();
        final var configuration = HederaTestConfigBuilder.create()
                .withValue("tokens.maxTokenNameUtf8Bytes", "10")
                .withValue("tokens.maxSymbolUtf8Bytes", "10")
                .withValue("tokens.maxMetadataBytes", "100")
                .getOrCreateConfig();
        tokensConfig = configuration.getConfigData(TokensConfig.class);
    }

    @Test
    void validatesMetadataWithRandomBytes() {
        byte[] randomBytes = TxnUtils.randomUtf8Bytes(48);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        for (byte b : randomBytes) {
            if (b != 0) {
                byteArrayOutputStream.write(b);
            }
        }
        byte[] randomNonNullBytes = byteArrayOutputStream.toByteArray();
        assertThatCode(() -> subject.validateTokenMetadata(Bytes.wrap(randomNonNullBytes), tokensConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void validatesMetadataWithUtf8TextIncludingEmojis() {
        String utfEmojiString = "Hello, World! 😁";
        assertThatCode(() -> subject.validateTokenMetadata(Bytes.wrap(utfEmojiString.getBytes()), tokensConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void doesntFailMetadataWithEmptyBytes() {
        byte[] emptyBytes = new byte[0];
        assertThatCode(() -> subject.validateTokenMetadata(Bytes.wrap(emptyBytes), tokensConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void doesntFailMetadataWithZeroBytes() {
        byte[] zeroLengthBytes = Bytes.wrap(new byte[0]).toByteArray();
        assertThatCode(() -> subject.validateTokenMetadata(Bytes.wrap(zeroLengthBytes), tokensConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void failsMetadataForVeryLongValue() {
        byte[] randomLongBytes = TxnUtils.randomUtf8Bytes(101);
        assertThatThrownBy(() -> subject.validateTokenMetadata(Bytes.wrap(randomLongBytes), tokensConfig))
                .isInstanceOf(HandleException.class)
                .has(responseCode(METADATA_TOO_LONG));
    }

    @Test
    void failsForZeroLengthSymbol() {
        assertThatThrownBy(() -> subject.validateTokenSymbol("", tokensConfig))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MISSING_TOKEN_SYMBOL));
    }

    @Test
    void failsForNullSymbol() {
        assertThatThrownBy(() -> subject.validateTokenSymbol(null, tokensConfig))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MISSING_TOKEN_SYMBOL));
    }

    @Test
    void failsForVeryLongSymbol() {
        assertThatThrownBy(() -> subject.validateTokenSymbol(
                        "12345678901234567890123456789012345678901234567890123456789012345678901234567890",
                        tokensConfig))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_SYMBOL_TOO_LONG));
    }

    @Test
    void failsForZeroByteInSymbol() {
        assertThatThrownBy(() -> subject.validateTokenSymbol("\0", tokensConfig))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ZERO_BYTE_IN_STRING));
    }

    @Test
    void failsForZeroByteInName() {
        assertThatThrownBy(() -> subject.validateTokenName("\0", tokensConfig))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ZERO_BYTE_IN_STRING));
    }

    @Test
    void failsForZeroLengthName() {
        final var configuration = HederaTestConfigBuilder.create()
                .withValue("tokens.maxTokenNameUtf8Bytes", "10")
                .getOrCreateConfig();
        final var tokensConfig = configuration.getConfigData(TokensConfig.class);

        assertThatThrownBy(() -> subject.validateTokenName("", tokensConfig))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MISSING_TOKEN_NAME));
    }

    @Test
    void failsForNullName() {
        assertThatThrownBy(() -> subject.validateTokenName(null, tokensConfig))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MISSING_TOKEN_NAME));
    }

    @Test
    void failsForVeryLongName() {
        assertThatThrownBy(() -> subject.validateTokenName(
                        "12345678901234567890123456789012345678901234567890123456789012345678901234567890",
                        tokensConfig))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_NAME_TOO_LONG));
    }

    @Test
    void validatesKeys() {
        assertThatThrownBy(() -> subject.validateTokenKeys(
                        true,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ADMIN_KEY));
        assertThatThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_KYC_KEY));
        assertThatThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_WIPE_KEY));
        assertThatThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_SUPPLY_KEY));
        assertThatThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_FREEZE_KEY));
        assertThatThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_CUSTOM_FEE_SCHEDULE_KEY));
        assertThatThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_PAUSE_KEY));
    }

    @Test
    void validatesKeysWithNulls() {
        assertThatNoException()
                .isThrownBy(() -> subject.validateTokenKeys(
                        false,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY));
        assertThatNoException()
                .isThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        false,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY));
        assertThatNoException()
                .isThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        false,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY));
        assertThatNoException()
                .isThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        false,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY));
        assertThatNoException()
                .isThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        false,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY));
        assertThatNoException()
                .isThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        false,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY));
        assertThatNoException()
                .isThrownBy(() -> subject.validateTokenKeys(
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        true,
                        A_COMPLEX_KEY,
                        false,
                        Key.DEFAULT,
                        true,
                        A_COMPLEX_KEY));
    }
}
