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

package com.hedera.node.app.service.token.impl.test.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.config.VersionedConfigImpl;
import com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenAttributesValidatorTest {
    @Mock
    private ConfigProvider configProvider;

    private TokenAttributesValidator subject;

    @BeforeEach
    void setUp() {
        subject = new TokenAttributesValidator(configProvider);
    }

    @Test
    void failsForZeroLengthSymbol() {
        final var configuration = new HederaTestConfigBuilder()
                .withValue("tokens.maxSymbolUtf8Bytes", "10")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validateTokenSymbol(""))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MISSING_TOKEN_SYMBOL));
    }

    @Test
    void failsForNullSymbol() {
        final var configuration = new HederaTestConfigBuilder()
                .withValue("tokens.maxSymbolUtf8Bytes", "10")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validateTokenSymbol(null))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MISSING_TOKEN_SYMBOL));
    }

    @Test
    void failsForVeryLongSymbol() {
        final var configuration = new HederaTestConfigBuilder()
                .withValue("tokens.maxSymbolUtf8Bytes", "10")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validateTokenSymbol(
                        "12345678901234567890123456789012345678901234567890123456789012345678901234567890"))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_SYMBOL_TOO_LONG));
    }

    @Test
    void failsForZeroByteInSymbol() {
        final var configuration = new HederaTestConfigBuilder()
                .withValue("tokens.maxSymbolUtf8Bytes", "10")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validateTokenSymbol("\0"))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ZERO_BYTE_IN_STRING));
    }

    @Test
    void failsForZeroByteInName() {
        final var configuration = new HederaTestConfigBuilder()
                .withValue("tokens.maxTokenNameUtf8Bytes", "10")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validateTokenName("\0"))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ZERO_BYTE_IN_STRING));
    }

    @Test
    void failsForZeroLengthName() {
        final var configuration = new HederaTestConfigBuilder()
                .withValue("tokens.maxTokenNameUtf8Bytes", "10")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validateTokenName(""))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MISSING_TOKEN_NAME));
    }

    @Test
    void failsForNullName() {
        final var configuration = new HederaTestConfigBuilder()
                .withValue("tokens.maxTokenNameUtf8Bytes", "10")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validateTokenName(null))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MISSING_TOKEN_NAME));
    }

    @Test
    void failsForVeryLongName() {
        final var configuration = new HederaTestConfigBuilder()
                .withValue("tokens.maxTokenNameUtf8Bytes", "10")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validateTokenName(
                        "12345678901234567890123456789012345678901234567890123456789012345678901234567890"))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_NAME_TOO_LONG));
    }

    @Test
    void validatesKeys() {
        assertThatThrownBy(() -> subject.checkKeys(
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
                .has(responseCode(INVALID_ADMIN_KEY));
        assertThatThrownBy(() -> subject.checkKeys(
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
                .has(responseCode(INVALID_KYC_KEY));
        assertThatThrownBy(() -> subject.checkKeys(
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
                .has(responseCode(INVALID_WIPE_KEY));
        assertThatThrownBy(() -> subject.checkKeys(
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
                .has(responseCode(INVALID_SUPPLY_KEY));
        assertThatThrownBy(() -> subject.checkKeys(
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
                .has(responseCode(INVALID_FREEZE_KEY));
        assertThatThrownBy(() -> subject.checkKeys(
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
                .has(responseCode(INVALID_CUSTOM_FEE_SCHEDULE_KEY));
        assertThatThrownBy(() -> subject.checkKeys(
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
                        Key.DEFAULT))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_PAUSE_KEY));
    }

    @Test
    void validatesKeysWithNulls() {
        assertThatNoException()
                .isThrownBy(() -> subject.checkKeys(
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
                .isThrownBy(() -> subject.checkKeys(
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
                .isThrownBy(() -> subject.checkKeys(
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
                .isThrownBy(() -> subject.checkKeys(
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
                .isThrownBy(() -> subject.checkKeys(
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
                .isThrownBy(() -> subject.checkKeys(
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
        assertThatNoException()
                .isThrownBy(() -> subject.checkKeys(
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
                        Key.DEFAULT));
    }
}
