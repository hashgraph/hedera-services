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

package com.hedera.node.app.handle.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoExpiryValidatorTest {
    //    private static final long now = 1_234_567;
    //    private static final long aTime = 666_666_666L;
    //    private static final long bTime = 777_777_777L;
    //    private static final long aPeriod = 666_666L;
    //    private static final long bPeriod = 777_777L;
    //    private static final long anAutoRenewNum = 888;
    //
    //    @Mock
    //    private OptionValidator validator;
    //
    //    @Mock
    //    private AccountStore accountStore;
    //
    //    @Mock
    //    private TransactionContext txnCtx;
    //
    //    @Mock
    //    private HederaNumbers numbers;
    //
    //    private MonoExpiryValidator subject;
    //
    //    @BeforeEach
    //    void setUp() {
    //        subject = new MonoExpiryValidator(accountStore, validator, txnCtx, numbers);
    //    }
    //
    //    @Test
    //    void onCreationRequiresEitherExplicitValueOrFullAutoRenewMetaIfNotSelfFunding() {
    //        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    //
    //        assertFailsWith(
    //                INVALID_EXPIRATION_TIME,
    //                () -> subject.validateCreationAttempt(false, new ExpiryMeta(NA, NA, anAutoRenewNum)));
    //        assertFailsWith(
    //                INVALID_EXPIRATION_TIME, () -> subject.validateCreationAttempt(false, new ExpiryMeta(NA, aPeriod,
    // NA)));
    //    }
    //
    //    @Test
    //    void onCreationRequiresValidExpiryIfExplicit() {
    //        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    //
    //        given(validator.isValidExpiry(aTime)).willReturn(false);
    //        assertFailsWith(
    //                INVALID_EXPIRATION_TIME,
    //                () -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, NA, anAutoRenewNum)));
    //    }
    //
    //    @Test
    //    void translatesFailureOnExplicitAutoRenewAccount() {
    //        given(accountStore.loadAccountOrFailWith(new Id(0, 0, anAutoRenewNum), INVALID_AUTORENEW_ACCOUNT))
    //                .willThrow(new InvalidTransactionException(INVALID_AUTORENEW_ACCOUNT));
    //
    //        assertFailsWith(
    //                INVALID_AUTORENEW_ACCOUNT,
    //                () -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, anAutoRenewNum)));
    //    }
    //
    //    @Test
    //    void onCreationUsesAutoRenewPeriodEvenWithoutFullSpecIfSelfFunding() {
    //        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    //
    //        given(validator.isValidExpiry(now + aPeriod)).willReturn(true);
    //        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);
    //        assertDoesNotThrow(() -> subject.validateCreationAttempt(true, new ExpiryMeta(NA, aPeriod, NA)));
    //    }
    //
    //    @Test
    //    void onCreationRequiresValidExpiryIfImplicit() {
    //        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    //
    //        given(validator.isValidExpiry(now + aPeriod)).willReturn(false);
    //        assertFailsWith(
    //                INVALID_EXPIRATION_TIME,
    //                () -> subject.validateCreationAttempt(false, new ExpiryMeta(NA, aPeriod, anAutoRenewNum)));
    //    }
    //
    //    @Test
    //    void validatesAutoRenewPeriodIfSet() {
    //        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    //
    //        given(validator.isValidExpiry(aTime)).willReturn(true);
    //        assertFailsWith(
    //                AUTORENEW_DURATION_NOT_IN_RANGE,
    //                () -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, NA)));
    //    }
    //
    //    @Test
    //    void validatesImpliedExpiry() {
    //        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    //
    //        given(validator.isValidExpiry(aTime)).willReturn(true);
    //        assertFailsWith(
    //                AUTORENEW_DURATION_NOT_IN_RANGE,
    //                () -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, NA)));
    //    }
    //
    //    @Test
    //    void summarizesExpiryOnlyCase() {
    //        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    //
    //        given(validator.isValidExpiry(aTime)).willReturn(true);
    //        assertDoesNotThrow(() -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, NA, NA)));
    //    }
    //
    //    @Test
    //    void summarizesExpiryAndAutoRenewNumCase() {
    //        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    //
    //        given(validator.isValidExpiry(aTime)).willReturn(true);
    //
    //        assertDoesNotThrow(() -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, NA,
    // anAutoRenewNum)));
    //    }
    //
    //    @Test
    //    void summarizesExpiryAndValidAutoRenewPeriodCase() {
    //        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    //        given(validator.isValidExpiry(aTime)).willReturn(true);
    //        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);
    //
    //        assertDoesNotThrow(() -> subject.validateCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, NA)));
    //    }
    //
    //    @Test
    //    void summarizesFullAutoRenewSpecPeriodCase() {
    //        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    //        given(validator.isValidAutoRenewPeriod(aPeriod)).willReturn(true);
    //        given(validator.isValidExpiry(now + aPeriod)).willReturn(true);
    //
    //        assertDoesNotThrow(
    //                () -> subject.validateCreationAttempt(false, new ExpiryMeta(now + aPeriod, aPeriod,
    // anAutoRenewNum)));
    //    }
    //
    //    @Test
    //    void updateCannotExplicitlyReduceExpiry() {
    //        final var current = new ExpiryMeta(aTime, NA, NA);
    //        final var update = new ExpiryMeta(aTime - 1, NA, NA);
    //
    //        assertFailsWith(EXPIRATION_REDUCTION_NOT_ALLOWED, () -> subject.resolveUpdateAttempt(current, update));
    //    }
    //
    //    @Test
    //    void explicitExpiryExtensionMustBeValid() {
    //        final var current = new ExpiryMeta(aTime, NA, NA);
    //        final var update = new ExpiryMeta(aTime - 1, NA, NA);
    //
    //        assertFailsWith(EXPIRATION_REDUCTION_NOT_ALLOWED, () -> subject.resolveUpdateAttempt(current, update));
    //    }
    //
    //    @Test
    //    void ifJustSettingAutoRenewAccountThenNetPeriodMustBeValid() {
    //        final var current = new ExpiryMeta(aTime, 0, NA);
    //        final var update = new ExpiryMeta(NA, NA, anAutoRenewNum);
    //
    //        assertFailsWith(AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.resolveUpdateAttempt(current, update));
    //    }
    //
    //    @Test
    //    void ifSettingAutoRenewPeriodThenMustBeValid() {
    //        final var current = new ExpiryMeta(aTime, 0, NA);
    //        final var update = new ExpiryMeta(NA, bPeriod, anAutoRenewNum);
    //
    //        assertFailsWith(AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.resolveUpdateAttempt(current, update));
    //    }
    //
    //    @Test
    //    void ifUpdatingAutoRenewNumMustBeValid() {
    //        final var current = new ExpiryMeta(aTime, 0, NA);
    //        final var update = new ExpiryMeta(NA, bPeriod, anAutoRenewNum);
    //
    //        given(accountStore.loadAccountOrFailWith(new Id(0, 0, anAutoRenewNum), INVALID_AUTORENEW_ACCOUNT))
    //                .willThrow(new InvalidTransactionException(INVALID_AUTORENEW_ACCOUNT));
    //
    //        assertFailsWith(INVALID_AUTORENEW_ACCOUNT, () -> subject.resolveUpdateAttempt(current, update));
    //    }
    //
    //    @Test
    //    void ifUpdatingExpiryMustBeValid() {
    //        final var current = new ExpiryMeta(aTime, 0, NA);
    //        final var update = new ExpiryMeta(bTime, bPeriod, anAutoRenewNum);
    //
    //        assertFailsWith(INVALID_EXPIRATION_TIME, () -> subject.resolveUpdateAttempt(current, update));
    //    }
    //
    //    @Test
    //    void canSetEverythingValidly() {
    //        final var current = new ExpiryMeta(aTime, 0, NA);
    //        final var update = new ExpiryMeta(bTime, bPeriod, anAutoRenewNum);
    //
    //        given(validator.isValidExpiry(bTime)).willReturn(true);
    //        given(validator.isValidAutoRenewPeriod(bPeriod)).willReturn(true);
    //
    //        assertEquals(update, subject.resolveUpdateAttempt(current, update));
    //    }
    //
    //    private static void assertFailsWith(final ResponseCodeEnum expected, final Runnable runnable) {
    //        final var e = assertThrows(HandleStatusException.class, runnable::run);
    //        assertEquals(expected, e.getStatus());
    //    }
}
