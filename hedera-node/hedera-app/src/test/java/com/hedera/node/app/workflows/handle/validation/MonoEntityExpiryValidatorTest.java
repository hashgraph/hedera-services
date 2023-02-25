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

import static com.hedera.node.app.spi.validation.EntityExpiryMetadata.NA;
import static com.hedera.node.app.spi.validation.UpdateEntityExpiryMetadata.invalidMetadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.spi.validation.EntityExpiryMetadata;
import com.hedera.node.app.spi.validation.UpdateEntityExpiryMetadata;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoEntityExpiryValidatorTest {
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

    private MonoEntityExpiryValidator subject;

    @BeforeEach
    void setUp() {
        subject = new MonoEntityExpiryValidator(validator, txnCtx);
    }

    @Test
    void onCreationRequiresEitherExplicitValueOrFullAutoRenewMetaIfNotSelfFunding() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        assertEquals(
                INVALID_EXPIRATION_TIME,
                subject.validateCreationAttempt(false, new EntityExpiryMetadata(NA, NA, anAutoRenewNum)));
        assertSame(
                INVALID_EXPIRATION_TIME,
                subject.validateCreationAttempt(false, new EntityExpiryMetadata(NA, aPeriod, NA)));
    }

    @Test
    void onCreationRequiresValidExpiryIfExplicit() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(aTime)).willReturn(false);
        assertEquals(
                INVALID_EXPIRATION_TIME,
                subject.validateCreationAttempt(false, new EntityExpiryMetadata(aTime, NA, anAutoRenewNum)));
    }

    @Test
    void onCreationUsesAutoRenewPeriodEvenWithoutFullSpecIfSelfFunding() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(now + aPeriod)).willReturn(true);
        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);
        assertEquals(OK, subject.validateCreationAttempt(true, new EntityExpiryMetadata(NA, aPeriod, NA)));
    }

    @Test
    void onCreationRequiresValidExpiryIfImplicit() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(now + aPeriod)).willReturn(false);
        assertSame(
                INVALID_EXPIRATION_TIME,
                subject.validateCreationAttempt(false, new EntityExpiryMetadata(NA, aPeriod, anAutoRenewNum)));
    }

    @Test
    void validatesAutoRenewPeriodIfSet() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(aTime)).willReturn(true);
        assertSame(
                AUTORENEW_DURATION_NOT_IN_RANGE,
                subject.validateCreationAttempt(false, new EntityExpiryMetadata(aTime, aPeriod, NA)));
    }

    @Test
    void validatesImpliedExpiry() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(aTime)).willReturn(true);
        assertSame(
                AUTORENEW_DURATION_NOT_IN_RANGE,
                subject.validateCreationAttempt(false, new EntityExpiryMetadata(aTime, aPeriod, NA)));
    }

    @Test
    void summarizesExpiryOnlyCase() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(aTime)).willReturn(true);
        assertEquals(OK, subject.validateCreationAttempt(false, new EntityExpiryMetadata(aTime, NA, NA)));
    }

    @Test
    void summarizesExpiryAndAutoRenewNumCase() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));

        given(validator.isValidExpiry(aTime)).willReturn(true);

        assertEquals(OK, subject.validateCreationAttempt(false, new EntityExpiryMetadata(aTime, NA, anAutoRenewNum)));
    }

    @Test
    void summarizesExpiryAndValidAutoRenewPeriodCase() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
        given(validator.isValidExpiry(aTime)).willReturn(true);
        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);

        assertEquals(OK, subject.validateCreationAttempt(false, new EntityExpiryMetadata(aTime, aPeriod, NA)));
    }

    @Test
    void summarizesFullAutoRenewSpecPeriodCase() {
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);
        given(validator.isValidExpiry(now + aPeriod)).willReturn(true);

        assertEquals(
                OK,
                subject.validateCreationAttempt(
                        false, new EntityExpiryMetadata(now + aPeriod, aPeriod, anAutoRenewNum)));
    }

    @Test
    void updateCannotExplicitlyReduceExpiry() {
        final var current = new EntityExpiryMetadata(aTime, NA, NA);
        final var update = new EntityExpiryMetadata(aTime - 1, NA, NA);

        assertEquals(
                invalidMetadata(EXPIRATION_REDUCTION_NOT_ALLOWED),
                subject.resolveAndValidateUpdateAttempt(current, update));
    }

    @Test
    void explicitExpiryExtensionMustBeValid() {
        final var current = new EntityExpiryMetadata(aTime, NA, NA);
        final var update = new EntityExpiryMetadata(aTime - 1, NA, NA);

        assertEquals(
                invalidMetadata(EXPIRATION_REDUCTION_NOT_ALLOWED),
                subject.resolveAndValidateUpdateAttempt(current, update));
    }

    @Test
    void ifJustSettingAutoRenewAccountThenNetPeriodMustBeValid() {
        final var current = new EntityExpiryMetadata(aTime, 0, NA);
        final var update = new EntityExpiryMetadata(NA, NA, anAutoRenewNum);

        assertEquals(
                invalidMetadata(AUTORENEW_DURATION_NOT_IN_RANGE),
                subject.resolveAndValidateUpdateAttempt(current, update));
    }

    @Test
    void ifSettingAutoRenewPeriodThenMustBeValid() {
        final var current = new EntityExpiryMetadata(aTime, 0, NA);
        final var update = new EntityExpiryMetadata(NA, bPeriod, anAutoRenewNum);

        assertEquals(
                invalidMetadata(AUTORENEW_DURATION_NOT_IN_RANGE),
                subject.resolveAndValidateUpdateAttempt(current, update));
    }

    @Test
    void canSetEverythingValidly() {
        final var current = new EntityExpiryMetadata(aTime, 0, NA);
        final var update = new EntityExpiryMetadata(bTime, bPeriod, anAutoRenewNum);

        given(validator.isValidExpiry(bTime)).willReturn(true);
        given(validator.isValidAutoRenewPeriod(bPeriod)).willReturn(true);

        final var expected = UpdateEntityExpiryMetadata.validMetadata(update);

        assertEquals(expected, subject.resolveAndValidateUpdateAttempt(current, update));
    }
}
