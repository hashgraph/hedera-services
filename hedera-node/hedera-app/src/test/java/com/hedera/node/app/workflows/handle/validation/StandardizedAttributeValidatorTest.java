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

package com.hedera.node.app.workflows.handle.validation;

import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BAD_ENCODING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.node.app.spi.config.PropertyNames.ENTITIES_MAX_LIFETIME;
import static com.hedera.node.app.workflows.handle.validation.StandardizedAttributeValidator.MAX_NESTED_KEY_LEVELS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Arrays;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandardizedAttributeValidatorTest {
    private static final long maxLifetime = 3_000_000L;
    private static final byte[] MOCK_ED25519_KEY = "abcdefghabcdefghabcdefghabcdefgh".getBytes();

    @Mock
    private LongSupplier consensusSecondNow;

    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private PropertySource compositeProps;

    private StandardizedAttributeValidator subject;

    @BeforeEach
    void setUp() {
        given(compositeProps.getLongProperty(ENTITIES_MAX_LIFETIME)).willReturn(maxLifetime);

        subject = new StandardizedAttributeValidator(consensusSecondNow, compositeProps, dynamicProperties);
    }

    @Test
    void memoCheckWorks() {
        given(dynamicProperties.maxMemoUtf8Bytes()).willReturn(100);

        final char[] aaa = new char[101];
        Arrays.fill(aaa, 'a');

        assertDoesNotThrow(() -> subject.validateMemo("OK"));
        assertFailsWith(MEMO_TOO_LONG, () -> subject.validateMemo(new String(aaa)));
        assertFailsWith(INVALID_ZERO_BYTE_IN_STRING, () -> subject.validateMemo("Not s\u0000 ok!"));
    }

    @Test
    void rejectsFutureExpiryImplyingSuperMaxLifetime() {
        assertFailsWith(INVALID_EXPIRATION_TIME, () -> subject.validateExpiry(maxLifetime + 1));
    }

    @Test
    void allowsFutureExpiryBeforeMaxLifetime() {
        final var now = 1_234_567L;
        given(consensusSecondNow.getAsLong()).willReturn(now);
        assertDoesNotThrow(() -> subject.validateExpiry(now + 1));
    }

    @Test
    void rejectsAnyNonFutureExpiry() {
        final var now = 1_234_567L;
        given(consensusSecondNow.getAsLong()).willReturn(now);
        assertFailsWith(INVALID_EXPIRATION_TIME, () -> subject.validateExpiry(now));
    }

    @Test
    void rejectsBriefAutoRenewPeriod() {
        given(dynamicProperties.minAutoRenewDuration()).willReturn(1_000L);

        assertFailsWith(AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.validateAutoRenewPeriod(55L));
    }

    @Test
    void rejectsOverLongAutoRenewPeriod() {
        given(dynamicProperties.minAutoRenewDuration()).willReturn(1_000L);
        given(dynamicProperties.maxAutoRenewDuration()).willReturn(10_000L);

        assertFailsWith(AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.validateAutoRenewPeriod(10_001L));
    }

    @Test
    void rejectsOverlyNestedKey() {
        final var acceptablyNested =
                nestKeys(Key.newBuilder(), MAX_NESTED_KEY_LEVELS - 1).build();
        final var overlyNested =
                nestKeys(Key.newBuilder(), MAX_NESTED_KEY_LEVELS).build();
        assertDoesNotThrow(() -> subject.validateKey(acceptablyNested));
        assertFailsWith(BAD_ENCODING, () -> subject.validateKey(overlyNested));
    }

    @Test
    void unsetKeysAreNotValid() {
        assertFailsWith(BAD_ENCODING, () -> subject.validateKey(Key.DEFAULT));
    }

    private void assertFailsWith(final ResponseCodeEnum expected, final Executable action) {
        final var e = assertThrows(HandleException.class, action);
        // expect:
        assertEquals(expected, e.getStatus());
    }

    private static Key.Builder nestKeys(final Key.Builder builder, final int additionalLevels) {
        if (additionalLevels == 0) {
            builder.ed25519(Bytes.wrap(MOCK_ED25519_KEY));
            return builder;
        } else {
            var nestedBuilder = Key.newBuilder();
            nestKeys(nestedBuilder, additionalLevels - 1);
            if (additionalLevels % 2 == 0) {
                builder.keyList(KeyList.newBuilder().keys(nestedBuilder.build()));
            } else {
                builder.thresholdKey(ThresholdKey.newBuilder()
                        .threshold(1)
                        .keys(KeyList.newBuilder().keys(nestedBuilder.build())));
            }
            return builder;
        }
    }
}
