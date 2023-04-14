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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.spi.exceptions.HandleException;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import java.util.function.LongSupplier;
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
    private AttributeValidator attributeValidator;

    @Mock
    private AccountStore accountStore;

    @Mock
    private LongSupplier consensusSecondNow;

    @Mock
    private HederaNumbers numbers;

    private MonoExpiryValidator subject;

    @BeforeEach
    void setUp() {
        subject = new MonoExpiryValidator(accountStore, attributeValidator, consensusSecondNow, numbers);
    }

    @Test
    void onCreationRequiresEitherExplicitValueOrFullAutoRenewMetaIfNotSelfFunding() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(anyLong());
        assertFailsWith(
                ResponseCodeEnum.INVALID_EXPIRATION_TIME,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(NA, NA, anAutoRenewNum)));
        assertFailsWith(
                ResponseCodeEnum.INVALID_EXPIRATION_TIME,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(NA, aPeriod, NA)));
    }

    @Test
    void validatesShard() {
        given(numbers.shard()).willReturn(1L);
        final var newMeta = new ExpiryMeta(aTime, aPeriod, 2L, 2L, anAutoRenewNum);

        final var failure = assertThrows(HandleException.class, () -> subject.resolveCreationAttempt(false, newMeta));
        assertEquals(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT, failure.getStatus());
    }

    @Test
    void validatesRealm() {
        given(numbers.shard()).willReturn(1L);
        given(numbers.realm()).willReturn(2L);
        final var newMeta = new ExpiryMeta(aTime, aPeriod, 1L, 3L, anAutoRenewNum);

        final var failure = assertThrows(HandleException.class, () -> subject.resolveCreationAttempt(false, newMeta));
        assertEquals(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT, failure.getStatus());
    }

    @Test
    void onCreationRequiresValidExpiryIfExplicit() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(aTime);
        assertFailsWith(
                ResponseCodeEnum.INVALID_EXPIRATION_TIME,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, NA, anAutoRenewNum)));
    }

    @Test
    void translatesFailureOnExplicitAutoRenewAccount() {
        given(accountStore.loadAccountOrFailWith(new Id(0, 0, anAutoRenewNum), INVALID_AUTORENEW_ACCOUNT))
                .willThrow(new InvalidTransactionException(INVALID_AUTORENEW_ACCOUNT));

        assertFailsWith(
                ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, anAutoRenewNum)));
    }

    @Test
    void onCreationUsesAutoRenewPeriodEvenWithoutFullSpecIfSelfFunding() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        assertDoesNotThrow(() -> subject.resolveCreationAttempt(true, new ExpiryMeta(NA, aPeriod, NA)));
    }

    @Test
    void onCreationRequiresValidExpiryIfImplicit() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(now + aPeriod);
        assertFailsWith(
                ResponseCodeEnum.INVALID_EXPIRATION_TIME,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(NA, aPeriod, anAutoRenewNum)));
    }

    @Test
    void validatesAutoRenewPeriodIfSet() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        willThrow(new HandleException(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(aPeriod);
        assertFailsWith(
                ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, NA)));
    }

    @Test
    void validatesImpliedExpiry() {
        given(consensusSecondNow.getAsLong()).willReturn(now);
        willThrow(new HandleException(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(aPeriod);
        assertFailsWith(
                ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, NA)));
    }

    @Test
    void summarizesExpiryOnlyCase() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        assertDoesNotThrow(() -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, NA, NA)));
    }

    @Test
    void summarizesExpiryAndAutoRenewNumCase() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        assertDoesNotThrow(() -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, NA, anAutoRenewNum)));
    }

    @Test
    void summarizesExpiryAndValidAutoRenewPeriodCase() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        assertDoesNotThrow(() -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, NA)));
    }

    @Test
    void summarizesFullAutoRenewSpecPeriodCase() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        assertDoesNotThrow(
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(now + aPeriod, aPeriod, anAutoRenewNum)));
    }

    @Test
    void updateCannotExplicitlyReduceExpiry() {
        final var current = new ExpiryMeta(aTime, NA, NA);
        final var update = new ExpiryMeta(aTime - 1, NA, NA);

        assertFailsWith(
                ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void explicitExpiryExtensionMustBeValid() {
        final var current = new ExpiryMeta(aTime, NA, NA);
        final var update = new ExpiryMeta(aTime - 1, NA, NA);

        assertFailsWith(
                ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void ifJustSettingAutoRenewAccountThenNetPeriodMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, NA);
        final var update = new ExpiryMeta(NA, NA, anAutoRenewNum);

        willThrow(new HandleException(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(0L);

        assertFailsWith(
                ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void ifSettingAutoRenewPeriodThenMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, NA);
        final var update = new ExpiryMeta(NA, bPeriod, anAutoRenewNum);

        willThrow(new HandleException(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(bPeriod);

        assertFailsWith(
                ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void ifUpdatingAutoRenewNumMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, NA);
        final var update = new ExpiryMeta(NA, bPeriod, anAutoRenewNum);

        given(accountStore.loadAccountOrFailWith(new Id(0, 0, anAutoRenewNum), INVALID_AUTORENEW_ACCOUNT))
                .willThrow(new InvalidTransactionException(INVALID_AUTORENEW_ACCOUNT));

        assertFailsWith(
                ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void ifUpdatingExpiryMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, NA);
        final var update = new ExpiryMeta(bTime, bPeriod, anAutoRenewNum);

        willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(bTime);

        assertFailsWith(ResponseCodeEnum.INVALID_EXPIRATION_TIME, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void canSetEverythingValidly() {
        final var current = new ExpiryMeta(aTime, 0, NA);
        final var update = new ExpiryMeta(bTime, bPeriod, anAutoRenewNum);

        assertEquals(update, subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void canUseWildcardForRemovingAutoRenewAccount() {
        final var current = new ExpiryMeta(aTime, 0, NA);
        final var update = new ExpiryMeta(bTime, bPeriod, 0);

        assertEquals(update, subject.resolveUpdateAttempt(current, update));
    }

    private static void assertFailsWith(final ResponseCodeEnum expected, final Runnable runnable) {
        final var e = assertThrows(HandleException.class, runnable::run);
        assertEquals(expected, e.getStatus());
    }
}
