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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.EntityType;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoExpiryValidatorTest {
    private static final long now = 1_234_567;
    private static final long aTime = 666_666_666L;
    private static final long bTime = 777_777_777L;
    private static final long aPeriod = 666_666L;
    private static final long bPeriod = 777_777L;
    private static final AccountID anAutoRenewId =
            AccountID.newBuilder().accountNum(888).build();
    private static final long DEFAULT_CONFIG_VERSION = 1;

    @Mock
    private AttributeValidator attributeValidator;

    @Mock
    private AccountStore accountStore;

    @Mock
    private LongSupplier consensusSecondNow;

    @Mock
    private HederaNumbers numbers;

    @Mock(strictness = Strictness.LENIENT)
    private ConfigProvider configProvider;

    private MonoExpiryValidator subject;

    @BeforeEach
    void setUp() {
        subject =
                new MonoExpiryValidator(accountStore, attributeValidator, consensusSecondNow, numbers, configProvider);
        final var configuration =
                new VersionedConfigImpl(HederaTestConfigBuilder.createConfig(), DEFAULT_CONFIG_VERSION);
        given(configProvider.getConfiguration()).willReturn(configuration);
    }

    @Test
    void onCreationRequiresEitherExplicitValueOrFullAutoRenewMetaIfNotSelfFunding() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(anyLong());
        assertFailsWith(
                ResponseCodeEnum.INVALID_EXPIRATION_TIME,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(NA, NA, anAutoRenewId), false));
        assertFailsWith(
                ResponseCodeEnum.INVALID_EXPIRATION_TIME,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(NA, aPeriod, null), false));
    }

    @Test
    void validatesShard() {
        given(numbers.shard()).willReturn(1L);
        final var newMeta = new ExpiryMeta(
                aTime,
                aPeriod,
                AccountID.newBuilder().shardNum(2L).realmNum(2L).accountNum(888).build());

        final var failure =
                assertThrows(HandleException.class, () -> subject.resolveCreationAttempt(false, newMeta, false));
        assertEquals(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT, failure.getStatus());
    }

    @Test
    void validatesRealm() {
        given(numbers.shard()).willReturn(1L);
        given(numbers.realm()).willReturn(2L);
        final var newMeta = new ExpiryMeta(
                aTime,
                aPeriod,
                AccountID.newBuilder().shardNum(1L).realmNum(3L).accountNum(888).build());

        final var failure =
                assertThrows(HandleException.class, () -> subject.resolveCreationAttempt(false, newMeta, false));
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
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, NA, anAutoRenewId), false));
    }

    @Test
    void translatesFailureOnExplicitAutoRenewAccount() {
        given(accountStore.loadAccountOrFailWith(new Id(0, 0, anAutoRenewId.accountNum()), INVALID_AUTORENEW_ACCOUNT))
                .willThrow(new InvalidTransactionException(INVALID_AUTORENEW_ACCOUNT));

        assertFailsWith(
                ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, anAutoRenewId), false));
    }

    @Test
    void onCreationUsesAutoRenewPeriodEvenWithoutFullSpecIfSelfFunding() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        assertDoesNotThrow(() -> subject.resolveCreationAttempt(true, new ExpiryMeta(NA, aPeriod, null), false));
    }

    @Test
    void onCreationRequiresValidExpiryIfImplicit() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(now + aPeriod);
        assertFailsWith(
                ResponseCodeEnum.INVALID_EXPIRATION_TIME,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(NA, aPeriod, anAutoRenewId), false));
    }

    @Test
    void validatesAutoRenewPeriodIfSet() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        willThrow(new HandleException(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(aPeriod);
        assertFailsWith(
                ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, null), false));
    }

    @Test
    void validatesImpliedExpiry() {
        given(consensusSecondNow.getAsLong()).willReturn(now);
        willThrow(new HandleException(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(aPeriod);
        assertFailsWith(
                ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE,
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, null), false));
    }

    @Test
    void summarizesExpiryOnlyCase() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        assertDoesNotThrow(() -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, NA, null), false));
    }

    @Test
    void summarizesExpiryAndAutoRenewNumCase() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        assertDoesNotThrow(
                () -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, NA, anAutoRenewId), false));
    }

    @Test
    void summarizesExpiryAndValidAutoRenewPeriodCase() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        assertDoesNotThrow(() -> subject.resolveCreationAttempt(false, new ExpiryMeta(aTime, aPeriod, null), false));
    }

    @Test
    void summarizesFullAutoRenewSpecPeriodCase() {
        given(consensusSecondNow.getAsLong()).willReturn(now);

        assertDoesNotThrow(() ->
                subject.resolveCreationAttempt(false, new ExpiryMeta(now + aPeriod, aPeriod, anAutoRenewId), false));
    }

    @Test
    void updateCannotExplicitlyReduceExpiry() {
        final var current = new ExpiryMeta(aTime, NA, null);
        final var update = new ExpiryMeta(aTime - 1, NA, null);

        assertFailsWith(
                ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void explicitExpiryExtensionMustBeValid() {
        final var current = new ExpiryMeta(aTime, NA, null);
        final var update = new ExpiryMeta(aTime - 1, NA, null);

        assertFailsWith(
                ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void ifJustSettingAutoRenewAccountThenNetPeriodMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, null);
        final var update = new ExpiryMeta(NA, NA, anAutoRenewId);

        willThrow(new HandleException(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(0L);

        assertFailsWith(
                ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void ifSettingAutoRenewPeriodThenMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, null);
        final var update = new ExpiryMeta(NA, bPeriod, anAutoRenewId);

        willThrow(new HandleException(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(bPeriod);

        assertFailsWith(
                ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void ifUpdatingAutoRenewNumMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, null);
        final var update = new ExpiryMeta(NA, bPeriod, anAutoRenewId);

        given(accountStore.loadAccountOrFailWith(new Id(0, 0, anAutoRenewId.accountNum()), INVALID_AUTORENEW_ACCOUNT))
                .willThrow(new InvalidTransactionException(INVALID_AUTORENEW_ACCOUNT));

        assertFailsWith(
                ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void ifUpdatingExpiryMustBeValid() {
        final var current = new ExpiryMeta(aTime, 0, null);
        final var update = new ExpiryMeta(bTime, bPeriod, anAutoRenewId);

        willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(bTime);

        assertFailsWith(ResponseCodeEnum.INVALID_EXPIRATION_TIME, () -> subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void canSetEverythingValidly() {
        final var current = new ExpiryMeta(aTime, 0, null);
        final var update = new ExpiryMeta(bTime, bPeriod, anAutoRenewId);

        assertEquals(update, subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void canUseWildcardForRemovingAutoRenewAccount() {
        final var current = new ExpiryMeta(aTime, 0, null);
        final var update = new ExpiryMeta(
                bTime, bPeriod, AccountID.newBuilder().accountNum(0).build());

        assertEquals(update, subject.resolveUpdateAttempt(current, update));
    }

    @Test
    void checksIfAccountIsDetachedIfBalanceZero() {
        assertEquals(OK, subject.expirationStatus(EntityType.ACCOUNT, false, 0));
        assertFalse(subject.isDetached(EntityType.ACCOUNT, false, 0));
    }

    @Test
    void failsIfAccountExpiredAndPendingRemoval() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("autoRenew.targetTypes", "CONTRACT,ACCOUNT")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 1));

        assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, subject.expirationStatus(EntityType.ACCOUNT, true, 0L));
        assertTrue(subject.isDetached(EntityType.ACCOUNT, true, 0));

        assertEquals(CONTRACT_EXPIRED_AND_PENDING_REMOVAL, subject.expirationStatus(EntityType.CONTRACT, true, 0L));
        assertTrue(subject.isDetached(EntityType.CONTRACT, true, 0));
    }

    @Test
    void notDetachedIfAccountNotExpired() {
        assertEquals(OK, subject.expirationStatus(EntityType.ACCOUNT, false, 0L));
        assertFalse(subject.isDetached(EntityType.ACCOUNT, false, 10));
    }

    @Test
    void notDetachedIfAutoRenewDisabled() {
        assertEquals(OK, subject.expirationStatus(EntityType.ACCOUNT, false, 0L));
        assertFalse(subject.isDetached(EntityType.ACCOUNT, false, 0));
    }

    private static void assertFailsWith(final ResponseCodeEnum expected, final Runnable runnable) {
        final var e = assertThrows(HandleException.class, runnable::run);
        assertEquals(expected, e.getStatus());
    }
}
