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
package com.hedera.services.txns.validation;

import static com.hedera.services.txns.validation.ExpiryMeta.UNUSED_FIELD_SENTINEL;
import static com.hedera.services.txns.validation.SummarizedExpiryMeta.EXPIRY_REDUCTION_SUMMARY;
import static com.hedera.services.txns.validation.SummarizedExpiryMeta.INVALID_EXPIRY_SUMMARY;
import static com.hedera.services.txns.validation.SummarizedExpiryMeta.INVALID_PERIOD_SUMMARY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.services.utils.EntityNum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiryValidatorTest {
    @Mock private OptionValidator validator;

    private ExpiryValidator subject;

    @BeforeEach
    void setUp() {
        subject = new ExpiryValidator(validator);
    }

    @Test
    void onCreationRequiresEitherExplicitValueOrFullAutoRenewMetaIfNotSelfFunding() {
        assertSame(
                INVALID_EXPIRY_SUMMARY,
                subject.summarizeCreationAttempt(
                        now,
                        false,
                        new ExpiryMeta(
                                UNUSED_FIELD_SENTINEL, UNUSED_FIELD_SENTINEL, anAutoRenewNum)));
        assertSame(
                INVALID_EXPIRY_SUMMARY,
                subject.summarizeCreationAttempt(
                        now, false, new ExpiryMeta(UNUSED_FIELD_SENTINEL, aPeriod, null)));
    }

    @Test
    void onCreationRequiresValidExpiryIfExplicit() {
        given(validator.isValidExpiry(aTime)).willReturn(false);
        assertSame(
                INVALID_EXPIRY_SUMMARY,
                subject.summarizeCreationAttempt(
                        now, false, new ExpiryMeta(aTime, UNUSED_FIELD_SENTINEL, anAutoRenewNum)));
    }

    @Test
    void onCreationUsesAutoRenewPeriodEvenWithoutFullSpecIfSelfFunding() {
        given(validator.isValidExpiry(now + aPeriod)).willReturn(true);
        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);
        final var actual =
                subject.summarizeCreationAttempt(
                        now, true, new ExpiryMeta(UNUSED_FIELD_SENTINEL, aPeriod, null));
        assertTrue(actual.isValid());
    }

    @Test
    void onCreationRequiresValidExpiryIfImplicit() {
        given(validator.isValidExpiry(now + aPeriod)).willReturn(false);
        assertSame(
                INVALID_EXPIRY_SUMMARY,
                subject.summarizeCreationAttempt(
                        now,
                        false,
                        new ExpiryMeta(UNUSED_FIELD_SENTINEL, aPeriod, anAutoRenewNum)));
    }

    @Test
    void validatesAutoRenewPeriodIfSet() {
        given(validator.isValidExpiry(aTime)).willReturn(true);
        assertSame(
                INVALID_PERIOD_SUMMARY,
                subject.summarizeCreationAttempt(now, false, new ExpiryMeta(aTime, aPeriod, null)));
    }

    @Test
    void validatesImpliedExpiry() {
        given(validator.isValidExpiry(aTime)).willReturn(true);
        assertSame(
                INVALID_PERIOD_SUMMARY,
                subject.summarizeCreationAttempt(now, false, new ExpiryMeta(aTime, aPeriod, null)));
    }

    @Test
    void summarizesExpiryOnlyCase() {
        given(validator.isValidExpiry(aTime)).willReturn(true);
        final var request = ExpiryMeta.withExplicitExpiry(aTime);
        final var expected = SummarizedExpiryMeta.forValid(request);

        assertEquals(expected, subject.summarizeCreationAttempt(now, false, request));
    }

    @Test
    void summarizesExpiryAndAutoRenewNumCase() {
        given(validator.isValidExpiry(aTime)).willReturn(true);
        final var request = new ExpiryMeta(aTime, UNUSED_FIELD_SENTINEL, anAutoRenewNum);
        final var expected = SummarizedExpiryMeta.forValid(request);

        assertEquals(expected, subject.summarizeCreationAttempt(now, false, request));
    }

    @Test
    void summarizesExpiryAndValidAutoRenewPeriodCase() {
        given(validator.isValidExpiry(aTime)).willReturn(true);
        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);
        final var request = new ExpiryMeta(aTime, aPeriod, null);
        final var expected = SummarizedExpiryMeta.forValid(request);

        assertEquals(expected, subject.summarizeCreationAttempt(now, false, request));
    }

    @Test
    void summarizesFullAutoRenewSpecPeriodCase() {
        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);
        given(validator.isValidExpiry(now + aPeriod)).willReturn(true);
        final var implicitRequest = new ExpiryMeta(now + aPeriod, aPeriod, anAutoRenewNum);
        final var expected = SummarizedExpiryMeta.forValid(implicitRequest);

        assertEquals(
                expected,
                subject.summarizeCreationAttempt(
                        now,
                        false,
                        ExpiryMeta.withAutoRenewSpecNotSelfFunding(aPeriod, anAutoRenewNum)));
    }

    @Test
    void updateCannotExplicitlyReduceExpiry() {
        final var current = ExpiryMeta.withExplicitExpiry(aTime);
        final var update = ExpiryMeta.withExplicitExpiry(aTime - 1);

        assertSame(EXPIRY_REDUCTION_SUMMARY, subject.summarizeUpdateAttempt(current, update));
    }

    @Test
    void explicitExpiryExtensionMustBeValid() {
        final var current = ExpiryMeta.withExplicitExpiry(aTime);
        final var update = ExpiryMeta.withExplicitExpiry(bTime);

        assertSame(INVALID_EXPIRY_SUMMARY, subject.summarizeUpdateAttempt(current, update));
    }

    @Test
    void ifJustSettingAutoRenewAccountThenNetPeriodMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, null);
        final var update =
                new ExpiryMeta(UNUSED_FIELD_SENTINEL, UNUSED_FIELD_SENTINEL, anAutoRenewNum);

        assertSame(INVALID_PERIOD_SUMMARY, subject.summarizeUpdateAttempt(current, update));
    }

    @Test
    void ifSettingAutoRenewPeriodThenMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, null);
        final var update = new ExpiryMeta(UNUSED_FIELD_SENTINEL, bPeriod, anAutoRenewNum);

        assertSame(INVALID_PERIOD_SUMMARY, subject.summarizeUpdateAttempt(current, update));
    }

    @Test
    void canSetEverythingValidly() {
        final var current = new ExpiryMeta(aTime, 0, null);
        final var update = new ExpiryMeta(bTime, bPeriod, anAutoRenewNum);

        given(validator.isValidExpiry(bTime)).willReturn(true);
        given(validator.isValidAutoRenewPeriod(bPeriod)).willReturn(true);

        final var expected = SummarizedExpiryMeta.forValid(update);

        assertEquals(expected, subject.summarizeUpdateAttempt(current, update));
    }

    private static final long now = 1_234_567;
    private static final long aTime = 666_666_666L;
    private static final long bTime = 777_777_777L;
    private static final long aPeriod = 666_666L;
    private static final long bPeriod = 777_777L;
    private static final EntityNum anAutoRenewNum = EntityNum.fromLong(888);
}
