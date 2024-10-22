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

package com.hedera.node.app.service.schedule.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.app.service.schedule.impl.handlers.HandlerUtility.childAsOrdinary;
import static com.hedera.node.app.spi.workflows.HandleContext.ConsensusThrottling.ON;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.ScheduleStreamBuilder;
import com.hedera.node.app.spi.key.KeyComparator;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides some implementation support needed for both the {@link ScheduleCreateHandler} and {@link
 * ScheduleSignHandler}.
 */
@Singleton
public class ScheduleManager {

    @Inject
    public ScheduleManager() {}

    private static final Comparator<Key> KEY_COMPARATOR = new KeyComparator();

    @FunctionalInterface
    public interface TransactionKeysFn {
        TransactionKeys apply(@NonNull TransactionBody body, @NonNull AccountID payerId) throws PreCheckException;
    }

    /**
     * Gets the {@link TransactionKeys} summarizing a schedule's signing requirements.
     * @param schedule the schedule
     * @param fn the function to get required keys by category
     * @return the schedule's signing requirements
     * @throws HandleException if the signing requirements cannot be determined
     */
    public @NonNull TransactionKeys getTransactionKeysOrThrow(
            @NonNull final Schedule schedule, @NonNull final TransactionKeysFn fn) throws HandleException {
        requireNonNull(schedule);
        requireNonNull(fn);
        try {
            return getRequiredKeys(schedule, fn);
        } catch (final PreCheckException e) {
            throw new HandleException(e.responseCode());
        }
    }

    /**
     * Gets the {@link TransactionKeys} summarizing a schedule's signing requirements.
     * @param schedule the schedule
     * @param fn the function to get required keys by category
     * @return the schedule's signing requirements
     * @throws PreCheckException if the signing requirements cannot be determined
     */
    @NonNull
    protected TransactionKeys getRequiredKeys(@NonNull final Schedule schedule, @NonNull final TransactionKeysFn fn)
            throws PreCheckException {
        requireNonNull(schedule);
        requireNonNull(fn);
        final var body = childAsOrdinary(schedule);
        final var creatorId = schedule.originalCreateTransactionOrThrow()
                .transactionIDOrThrow()
                .accountIDOrThrow();
        final var payerId = schedule.payerAccountIdOrElse(creatorId);
        final var transactionKeys = fn.apply(body, payerId);
        // We do not currently support scheduling transactions that would need to complete hollow accounts
        if (!transactionKeys.requiredHollowAccounts().isEmpty()) {
            throw new PreCheckException(UNRESOLVABLE_REQUIRED_SIGNERS);
        }
        return transactionKeys;
    }

    /**
     * Gets all required keys for a transaction, including the payer key and all non-payer keys.
     * @param keys the transaction keys
     * @return the required keys
     */
    public @NonNull List<Key> allRequiredKeys(@NonNull final TransactionKeys keys) {
        final var all = new ArrayList<Key>();
        all.add(keys.payerKey());
        all.addAll(keys.requiredNonPayerKeys());
        return all;
    }

    /**
     * Given a set of signing crypto keys, a list of signatories, and a list of required keys, returns a new list of
     * signatories that includes all the original signatories and any crypto keys that are both constituents of the
     * required keys and in the signing crypto keys set.
     * @param signingCryptoKeys the signing crypto keys
     * @param signatories the original signatories
     * @param requiredKeys the required keys
     * @return the new signatories
     */
    protected @NonNull List<Key> newSignatories(
            @NonNull final SortedSet<Key> signingCryptoKeys,
            @NonNull final List<Key> signatories,
            @NonNull final List<Key> requiredKeys) {
        requireNonNull(signingCryptoKeys);
        requireNonNull(signatories);
        requireNonNull(requiredKeys);
        final var newSignatories = new ConcurrentSkipListSet<>(KEY_COMPARATOR);
        newSignatories.addAll(signatories);
        requiredKeys.forEach(k -> accumulateNewSignatories(newSignatories, signingCryptoKeys, k));
        return new ArrayList<>(newSignatories);
    }

    /**
     * Either returns a schedule from the given store with the given id, ready to be modified, or throws a
     * {@link PreCheckException} if the schedule is not found or is not in a valid state.
     *
     * @param scheduleId the schedule to get and validate
     * @param scheduleStore the schedule store
     * @throws PreCheckException if the schedule is not found or is not in a valid state
     */
    @NonNull
    protected Schedule getValidated(
            @NonNull final ScheduleID scheduleId,
            @NonNull final ReadableScheduleStore scheduleStore,
            final boolean isLongTermEnabled)
            throws PreCheckException {
        requireNonNull(scheduleId);
        requireNonNull(scheduleStore);
        final var schedule = scheduleStore.get(scheduleId);
        final var validationResult = validate(schedule, null, isLongTermEnabled);
        if (validationResult == OK) {
            return requireNonNull(schedule);
        } else {
            throw new PreCheckException(validationResult);
        }
    }

    /**
     * Given a schedule, consensus time, and long term scheduling enabled flag, validate the transaction
     * meets minimum requirements to be handled. Returns {@link ResponseCodeEnum#OK} if the schedule is valid.
     * <p>
     * This method checks that, as of the current consensus time, the schedule,
     * <ul>
     *     <li>Is not null.</li>
     *     <li>Has a scheduled transaction.</li>
     *     <li>Has not been executed.</li>
     *     <li>Is not deleted.</li>
     *     <li>Has not expired.</li>
     * </ul>
     * @param schedule the schedule to validate
     * @param consensusNow the current consensus time
     * @param isLongTermEnabled whether long term scheduling is enabled
     * @return the validation result
     */
    @NonNull
    public ResponseCodeEnum validate(
            @Nullable final Schedule schedule, @Nullable final Instant consensusNow, final boolean isLongTermEnabled) {
        if (schedule == null) {
            return INVALID_SCHEDULE_ID;
        }
        if (!schedule.hasScheduledTransaction()) {
            return INVALID_TRANSACTION;
        }
        if (schedule.executed()) {
            return SCHEDULE_ALREADY_EXECUTED;
        }
        if (schedule.deleted()) {
            return SCHEDULE_ALREADY_DELETED;
        }
        final long expiration = schedule.calculatedExpirationSecond();
        final var calculatedExpiration = (expiration != Schedule.DEFAULT.calculatedExpirationSecond()
                ? Instant.ofEpochSecond(expiration)
                : Instant.MAX);
        final var effectiveNow = Objects.requireNonNullElse(consensusNow, Instant.MIN);
        if (calculatedExpiration.getEpochSecond() >= effectiveNow.getEpochSecond()) {
            return OK;
        } else {
            return isLongTermEnabled ? SCHEDULE_PENDING_EXPIRATION : INVALID_SCHEDULE_ID;
        }
    }

    /**
     * Indicates if the given validation result is one that may allow a validated schedule to be executed.
     * @param validationResult the validation result
     * @return if the schedule might be executable
     */
    protected boolean isMaybeExecutable(@NonNull final ResponseCodeEnum validationResult) {
        return validationResult == OK || validationResult == SUCCESS || validationResult == SCHEDULE_PENDING_EXPIRATION;
    }

    /**
     * Tries to execute a schedule, if all conditions are met. Returns true if the schedule was executed.
     * @param context the context
     * @param schedule the schedule to execute
     * @param validationResult the validation result
     * @param isLongTermEnabled the is long term enabled
     * @return if the schedule was executed
     */
    public boolean tryToExecuteSchedule(
            @NonNull final HandleContext context,
            @NonNull final Schedule schedule,
            @NonNull final List<Key> requiredKeys,
            @NonNull final ResponseCodeEnum validationResult,
            final boolean isLongTermEnabled) {
        requireNonNull(context);
        requireNonNull(schedule);
        requireNonNull(requiredKeys);
        requireNonNull(validationResult);

        final var signatories = new HashSet<>(schedule.signatories());
        final VerificationAssistant callback = (k, ignore) -> signatories.contains(k);
        final var remainingKeys = new HashSet<>(requiredKeys);
        remainingKeys.removeIf(
                k -> context.keyVerifier().verificationFor(k, callback).passed());
        final boolean isExpired = validationResult == SCHEDULE_PENDING_EXPIRATION;
        if (canExecute(schedule, remainingKeys, isExpired, isLongTermEnabled)) {
            final var body = childAsOrdinary(schedule);
            context.dispatchChildTransaction(
                            body,
                            ScheduleStreamBuilder.class,
                            signatories::contains,
                            schedule.payerAccountIdOrThrow(),
                            TransactionCategory.SCHEDULED,
                            ON)
                    .scheduleRef(schedule.scheduleId());
            context.savepointStack()
                    .getBaseBuilder(ScheduleStreamBuilder.class)
                    .scheduledTransactionID(body.transactionID());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns a version of the given schedule marked as executed at the given time.
     * @param schedule the schedule to mark as executed
     * @param consensusNow the time to mark the schedule as executed
     * @return the marked schedule
     */
    protected static @NonNull Schedule markedExecuted(
            @NonNull final Schedule schedule, @NonNull final Instant consensusNow) {
        return schedule.copyBuilder()
                .executed(true)
                .resolutionTime(asTimestamp(consensusNow))
                .build();
    }

    /**
     * Evaluates whether a schedule with given remaining signatories, validation result, and can be executed
     * in the context of long-term scheduling on or off.
     *
     * @param schedule the schedule to execute
     * @param remainingKeys the remaining keys that must sign
     * @param isExpired whether the schedule is expired
     * @param isLongTermEnabled the long term scheduling flag
     * @return boolean indicating if the schedule can be executed
     */
    private boolean canExecute(
            @NonNull final Schedule schedule,
            @NonNull final Set<Key> remainingKeys,
            final boolean isExpired,
            final boolean isLongTermEnabled) {
        // We can only execute if there are no remaining keys required to sign
        if (!remainingKeys.isEmpty()) {
            return false;
        }
        // If long-term transactions are disabled, everything executes immediately
        if (!isLongTermEnabled) {
            return true;
        }
        // Otherwise we can only execute in two cases,
        //   (1) The schedule is allowed to execute immediately, and is not expired.
        //   (2) The schedule is waiting for its expiry to execute, and is expired.
        return schedule.waitForExpiry() == isExpired;
    }

    /**
     * Accumulates the valid signatories from a key structure into a set of signatories.
     * @param signatories the set of signatories to accumulate into
     * @param signingCryptoKeys the signing crypto keys
     * @param key the key structure to accumulate signatories from
     */
    private void accumulateNewSignatories(
            @NonNull final Set<Key> signatories, @NonNull final Set<Key> signingCryptoKeys, @NonNull final Key key) {
        switch (key.key().kind()) {
            case ED25519, ECDSA_SECP256K1 -> {
                if (signingCryptoKeys.contains(key)) {
                    signatories.add(key);
                }
            }
            case KEY_LIST -> key.keyListOrThrow()
                    .keys()
                    .forEach(k -> accumulateNewSignatories(signatories, signingCryptoKeys, k));
            case THRESHOLD_KEY -> key.thresholdKeyOrThrow()
                    .keysOrThrow()
                    .keys()
                    .forEach(k -> accumulateNewSignatories(signatories, signingCryptoKeys, k));
        }
    }
}
