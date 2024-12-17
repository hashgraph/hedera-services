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

package com.hedera.node.app.spi.validation;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A utility class, similar in concept {@link java.util.Objects#requireNonNull(Object)}, to validate or throw
 * exceptions.
 */
public final class Validations {

    /** No instantiation permitted */
    private Validations() {}

    /**
     * Checks that the given subject is not null. If it is, then a {@link PreCheckException} is thrown with the
     * given {@link ResponseCodeEnum}.
     *
     * @param subject The object to check.
     * @param code The {@link ResponseCodeEnum} to use if the subject is null.
     * @return The subject if it is not null.
     * @param <T> The type of the subject.
     * @throws PreCheckException If the subject is null, a {@link PreCheckException} is thrown with the given
     * {@link ResponseCodeEnum}.
     */
    public static <T> T mustExist(@Nullable final T subject, @NonNull final ResponseCodeEnum code)
            throws PreCheckException {
        if (subject == null) {
            throw new PreCheckException(code);
        }

        return subject;
    }

    /**
     * Common validation of an {@link AccountID} that it is internally consistent. A valid ID must not be null,
     * must have either an alias or an account number, and if it has an account number, it must be positive. And
     * if there is an alias, it must have at least one byte.
     *
     * @param subject The {@link AccountID} to validate.
     * @return The {@link AccountID} if valid.
     * @throws PreCheckException If the account ID is not valid, {@link ResponseCodeEnum#INVALID_ACCOUNT_ID} will
     * be thrown.
     */
    @NonNull
    public static AccountID validateAccountID(
            @Nullable final AccountID subject, @Nullable ResponseCodeEnum responseCodeEnum) throws PreCheckException {
        AccountID result = null;
        try {
            result = validateNullableAccountID(subject);
            // Cannot be null
            if (result == null) {
                throw new PreCheckException(
                        responseCodeEnum == null ? ResponseCodeEnum.INVALID_ACCOUNT_ID : responseCodeEnum);
            }
        } catch (PreCheckException e) {
            if (responseCodeEnum != null) {
                throw new PreCheckException(responseCodeEnum);
            } else throw e;
        }
        return result;
    }

    /**
     * Common validation of an {@link AccountID} that it is internally consistent, but which permits an account ID to
     * be null. A valid ID must be null, or have either an alias or an account number, and if it has an account number,
     * it must be positive. And if there is an alias instead, it must have at least one byte.
     *
     * @param subject The {@link AccountID} to validate.
     * @return The {@link AccountID} if valid.
     * @throws PreCheckException If the account ID is not valid, {@link ResponseCodeEnum#INVALID_ACCOUNT_ID} will
     * be thrown.
     */
    @Nullable
    public static AccountID validateNullableAccountID(@Nullable final AccountID subject) throws PreCheckException {
        // We'll permit it to be null.
        if (subject == null) {
            return null;
        }

        // The account ID must have the account type (number or alias) set. It cannot be UNSET.
        if (subject.account().kind() == AccountID.AccountOneOfType.UNSET) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        }

        // You cannot have negative or zero account numbers. Those just aren't allowed!
        if (subject.hasAccountNum() && subject.accountNumOrThrow() <= 0) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        }

        // And if you have an alias, it has to have at least a byte.
        if (subject.hasAlias()) {
            final var alias = subject.aliasOrThrow();
            if (alias.length() < 1) {
                throw new PreCheckException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
            }
        }

        // FUTURE: the shard and realm need to match the configuration for this node. But we have to be careful. In
        // theory, shard and realm are config properties, and are network-wide, so they would be in the network-wide
        // configuration. And that configuration is dynamic. But if the shard/realm where to ever change, that would
        // be very bad. And we wouldn't want to look up shard and realm over and over every time we validated an
        // account, that would be terrible for performance. So we need to think this through.
        return subject;
    }
}
