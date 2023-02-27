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

import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoExpiryValidatorTest {
    private static final long now = 1_234_567;
    private static final long aTime = 666_666_666L;
    private static final long bTime = 777_777_777L;
    private static final long aPeriod = 666_666L;
    private static final long bPeriod = 777_777L;
    private static final long anAutoRenewNum = 888;

    @Mock
    private OptionValidator validator;

    @Mock
    private TransactionContext txnCtx;

    private MonoExpiryValidator subject;

    @BeforeEach
    void setUp() {
        subject = new MonoExpiryValidator(validator, txnCtx);
    }

    @Test
    void onCreationRequiresEitherExplicitValueOrFullAutoRenewMetaIfNotSelfFunding() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        assertFailsWith(
                INVALID_EXPIRATION_TIME,
                () -> subject.validateCreationAttempt(false, new ExpiryMeta(NA, NA, anAutoRenewNum)));
        assertFailsWith(
                INVALID_EXPIRATION_TIME, () -> subject.validateCreationAttempt(false, new ExpiryMeta(NA, aPeriod, NA)));
    }

    @Test
    void onCreationRequiresValidExpiryIfExplicit() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(aTime)).willReturn(false);
        assertFailsWith(
                INVALID_EXPIRATION_TIME,
                () -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, NA, anAutoRenewNum)));
    }

    @Test
    void onCreationUsesAutoRenewPeriodEvenWithoutFullSpecIfSelfFunding() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(now + aPeriod)).willReturn(true);
        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);
        assertDoesNotThrow(() -> subject.validateCreationAttempt(true, new ExpiryMeta(NA, aPeriod, NA)));
    }

    @Test
    void onCreationRequiresValidExpiryIfImplicit() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(now + aPeriod)).willReturn(false);
        assertFailsWith(
                INVALID_EXPIRATION_TIME,
                () -> subject.validateCreationAttempt(false, new ExpiryMeta(NA, aPeriod, anAutoRenewNum)));
    }

    @Test
    void validatesAutoRenewPeriodIfSet() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(aTime)).willReturn(true);
        assertFailsWith(
                AUTORENEW_DURATION_NOT_IN_RANGE,
                () -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, NA)));
    }

    @Test
    void validatesImpliedExpiry() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(aTime)).willReturn(true);
        assertFailsWith(
                AUTORENEW_DURATION_NOT_IN_RANGE,
                () -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, NA)));
    }

    @Test
    void summarizesExpiryOnlyCase() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(aTime)).willReturn(true);
        assertDoesNotThrow(() -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, NA, NA)));
    }

    @Test
    void summarizesExpiryAndAutoRenewNumCase() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(aTime)).willReturn(true);

        assertDoesNotThrow(() -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, NA, anAutoRenewNum)));
    }

    @Test
    void summarizesExpiryAndValidAutoRenewPeriodCase() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
        given(validator.isValidExpiry(aTime)).willReturn(true);
        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);

        assertDoesNotThrow(() -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, NA)));
    }

    @Test
    void summarizesFullAutoRenewSpecPeriodCase() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);
        given(validator.isValidExpiry(now + aPeriod)).willReturn(true);

        assertDoesNotThrow(
                () -> subject.validateCreationAttempt(false, new ExpiryMeta(now + aPeriod, aPeriod, anAutoRenewNum)));
    }

    @Test
    void updateCannotExplicitlyReduceExpiry() {
        final var current = new ExpiryMeta(aTime, NA, NA);
        final var update = new ExpiryMeta(aTime - 1, NA, NA);

        assertFailsWith(EXPIRATION_REDUCTION_NOT_ALLOWED, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void explicitExpiryExtensionMustBeValid() {
        final var current = new ExpiryMeta(aTime, NA, NA);
        final var update = new ExpiryMeta(aTime - 1, NA, NA);

        assertFailsWith(EXPIRATION_REDUCTION_NOT_ALLOWED, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void ifJustSettingAutoRenewAccountThenNetPeriodMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, NA);
        final var update = new ExpiryMeta(NA, NA, anAutoRenewNum);

        assertFailsWith(AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void ifSettingAutoRenewPeriodThenMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, NA);
        final var update = new ExpiryMeta(NA, bPeriod, anAutoRenewNum);

        assertFailsWith(AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void canSetEverythingValidly() {
        final var current = new ExpiryMeta(aTime, 0, NA);
        final var update = new ExpiryMeta(bTime, bPeriod, anAutoRenewNum);

        given(validator.isValidExpiry(bTime)).willReturn(true);
        given(validator.isValidAutoRenewPeriod(bPeriod)).willReturn(true);

        assertEquals(update, subject.resolveUpdateAttempt(current, update));
    }

    private static void assertFailsWith(final ResponseCodeEnum expected, final Runnable runnable) {
        final var e = assertThrows(HandleStatusException.class, runnable::run);
        assertEquals(expected, e.getStatus());
    }
}
