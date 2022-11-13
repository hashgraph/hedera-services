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

import com.hedera.services.files.HFileMeta;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public record ExpiryMeta(long expiry, long autoRenewPeriod, @Nullable EntityNum autoRenewNum) {
    public static final long UNUSED_FIELD_SENTINEL = Long.MIN_VALUE;
    public static final ExpiryMeta INVALID_EXPIRY_META =
            new ExpiryMeta(UNUSED_FIELD_SENTINEL, UNUSED_FIELD_SENTINEL, null);

    public static ExpiryMeta withExplicitExpiry(final long expiry) {
        return new ExpiryMeta(expiry, UNUSED_FIELD_SENTINEL, null);
    }

    public static ExpiryMeta withAutoRenewSpecNotSelfFunding(
            final long autoRenewPeriod, final EntityNum autoRenewNum) {
        return new ExpiryMeta(UNUSED_FIELD_SENTINEL, autoRenewPeriod, autoRenewNum);
    }

    public static ExpiryMeta fromCryptoCreateOp(final CryptoCreateTransactionBody op) {
        return fromOp(
                op,
                ignore -> false,
                () -> UNUSED_FIELD_SENTINEL,
                CryptoCreateTransactionBody::hasAutoRenewPeriod,
                () -> op.getAutoRenewPeriod().getSeconds(),
                CryptoCreateTransactionBody::hasAutoRenewAccount,
                () -> EntityNum.fromAccountId(op.getAutoRenewAccount()));
    }

    public static ExpiryMeta fromCryptoUpdateOp(final CryptoUpdateTransactionBody op) {
        return fromOp(
                op,
                CryptoUpdateTransactionBody::hasExpirationTime,
                () -> op.getExpirationTime().getSeconds(),
                CryptoUpdateTransactionBody::hasAutoRenewPeriod,
                () -> op.getAutoRenewPeriod().getSeconds(),
                CryptoUpdateTransactionBody::hasAutoRenewAccount,
                () -> EntityNum.fromAccountId(op.getAutoRenewAccount()));
    }

    public static ExpiryMeta fromFileCreateOp(final FileCreateTransactionBody op) {
        return fromOp(
                op,
                FileCreateTransactionBody::hasExpirationTime,
                () -> op.getExpirationTime().getSeconds(),
                FileCreateTransactionBody::hasAutoRenewPeriod,
                () -> op.getAutoRenewPeriod().getSeconds(),
                FileCreateTransactionBody::hasAutoRenewAccount,
                () -> EntityNum.fromAccountId(op.getAutoRenewAccount()));
    }

    public static ExpiryMeta fromFileUpdateOp(final FileUpdateTransactionBody op) {
        return fromOp(
                op,
                FileUpdateTransactionBody::hasExpirationTime,
                () -> op.getExpirationTime().getSeconds(),
                FileUpdateTransactionBody::hasAutoRenewPeriod,
                () -> op.getAutoRenewPeriod().getSeconds(),
                FileUpdateTransactionBody::hasAutoRenewAccount,
                () -> EntityNum.fromAccountId(op.getAutoRenewAccount()));
    }

    private static <T> ExpiryMeta fromOp(
            final T op,
            final Predicate<T> expiryTest,
            final LongSupplier expiryFn,
            final Predicate<T> autoRenewPeriodTest,
            final LongSupplier autoRenewPeriodFn,
            final Predicate<T> autoRenewNumTest,
            final Supplier<EntityNum> autoRenewNumFn) {
        final var expiry = expiryTest.test(op) ? expiryFn.getAsLong() : UNUSED_FIELD_SENTINEL;
        final var autoRenewPeriod =
                autoRenewPeriodTest.test(op)
                        ? autoRenewPeriodFn.getAsLong()
                        : UNUSED_FIELD_SENTINEL;
        // FUTURE WORK - support aliases here
        final var autoRenewNum = autoRenewNumTest.test(op) ? autoRenewNumFn.get() : null;
        return new ExpiryMeta(expiry, autoRenewPeriod, autoRenewNum);
    }

    public static ExpiryMeta fromExtantFileMeta(final HFileMeta meta) {
        final var autoRenewNum = meta.getAutoRenewId();
        return new ExpiryMeta(
                meta.getExpiry(),
                meta.getAutoRenewPeriod(),
                autoRenewNum == null ? null : autoRenewNum.asNum());
    }

    public void updateFileMeta(final HFileMeta meta) {
        assertSuitableForUpdate();
        meta.setExpiry(expiry);
        meta.setAutoRenewPeriod(autoRenewPeriod);
        meta.setAutoRenewId(effectiveAutoRenewId());
    }

    public void updateCustomizer(final HederaAccountCustomizer customizer) {
        assertSuitableForUpdate();
        customizer.expiry(expiry);
        customizer.autoRenewPeriod(autoRenewPeriod);
        customizer.autoRenewAccount(effectiveAutoRenewId());
    }

    private EntityId effectiveAutoRenewId() {
        // 0.0.0 is a sentinel value used to remove an auto-renew account
        return autoRenewNum == null || autoRenewNum.longValue() == 0
                ? null
                : autoRenewNum.toEntityId();
    }

    private void assertSuitableForUpdate() {
        if (expiry == UNUSED_FIELD_SENTINEL) {
            throw new IllegalStateException("No usable expiry is set");
        }
        if (autoRenewPeriod == UNUSED_FIELD_SENTINEL) {
            throw new IllegalStateException("No usable auto-renew period is set");
        }
    }

    @Nullable
    public EntityId autoRenewId() {
        return autoRenewNum == null ? null : autoRenewNum.toEntityId();
    }

    public long usableAutoRenewPeriod() {
        return autoRenewPeriod == UNUSED_FIELD_SENTINEL ? 0 : autoRenewPeriod;
    }

    public boolean hasExplicitExpiry() {
        return expiry != UNUSED_FIELD_SENTINEL;
    }

    public boolean hasAutoRenewPeriod() {
        return autoRenewPeriod != UNUSED_FIELD_SENTINEL;
    }

    public boolean hasAutoRenewNum() {
        return autoRenewNum != null;
    }

    public boolean hasFullAutoRenewSpec() {
        return hasAutoRenewNum() && hasAutoRenewPeriod();
    }
}
