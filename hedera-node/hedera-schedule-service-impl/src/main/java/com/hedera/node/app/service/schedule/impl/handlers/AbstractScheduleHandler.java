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

package com.hedera.node.app.service.schedule.impl.handlers;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.ScheduleRecordBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Provides some implementation support needed for both the {@link ScheduleCreateHandler} and {@link
 * ScheduleSignHandler}.
 */
abstract class AbstractScheduleHandler {
    protected static final String NULL_CONTEXT_MESSAGE =
            "Dispatcher called the schedule handler with a null context; probable internal data corruption.";

    /**
     * A simple record to return both "deemed valid" signatories and remaining primitive keys that must sign.
     * @param updatedSignatories a Set of "deemed valid" signatories, possibly updated with new entries
     * @param remainingRequiredKeys A Set of Key entries that have not yet signed the scheduled transaction, but
     *     must sign that transaction before it can be executed.
     */
    protected static record ScheduleKeysResult(Set<Key> updatedSignatories, Set<Key> remainingRequiredKeys) {}

    @NonNull
    protected Set<Key> allKeysForTransaction(
            @NonNull final Schedule scheduleInState, @NonNull final PreHandleContext context) throws PreCheckException {
        final TransactionBody scheduledAsOrdinary = HandlerUtility.childAsOrdinary(scheduleInState);
        // note, payerAccount should never be null, but we're dealing with Sonar here.
        final AccountID payerForNested =
                scheduleInState.payerAccountIdOrElse(scheduleInState.schedulerAccountIdOrThrow());
        final TransactionKeys keyStructure = context.allKeysForTransaction(scheduledAsOrdinary, payerForNested);
        return getKeySetFromTransactionKeys(keyStructure);
    }

    @NonNull
    protected ScheduleKeysResult allKeysForTransaction(
            @NonNull final Schedule scheduleInState, @NonNull final HandleContext context) throws HandleException {
        try {
            // note, payerAccount should never be null, but we're playing it safe here.
            final AccountID payer = scheduleInState.payerAccountIdOrElse(context.payer());
            final TransactionBody scheduledAsOrdinary = HandlerUtility.childAsOrdinary(scheduleInState);
            final TransactionKeys keyStructure = context.allKeysForTransaction(scheduledAsOrdinary, payer);
            final Set<Key> scheduledRequiredKeys = getKeySetFromTransactionKeys(keyStructure);
            final Set<Key> currentSignatories = setOfKeys(scheduleInState.signatories());
            scheduledRequiredKeys.removeAll(currentSignatories);
            // Ensure the payer is required, some rare corner cases may not require it otherwise.
            final Key payerKey = getKeyForAccount(context, payer);
            if (payerKey != null) scheduledRequiredKeys.add(payerKey);
            final Set<Key> remainingRequiredKeys = scheduledRequiredKeys.parallelStream()
                    .map(new ValidatedKeysMapping(context, currentSignatories))
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());
            // This works ONLY because ValidatedKeysMapping observes the validation process and
            // modifies currentSignatories to add any newly validated primitive keys.
            return new ScheduleKeysResult(currentSignatories, remainingRequiredKeys);
        } catch (final PreCheckException translated) {
            throw new HandleException(translated.responseCode());
        }
    }

    @Nullable
    protected Key getKeyForAccount(@NonNull final HandleContext context, @NonNull final AccountID accountToQuery) {
        final ReadableAccountStore accountStore = context.readableStore(ReadableAccountStore.class);
        final Account accountData = accountStore.getAccountById(accountToQuery);
        return (accountData != null && accountData.key() != null) ? accountData.key() : null;
    }

    /**
     * Given a transaction body and schedule store, validate the transaction meets minimum requirements to
     * be completed.
     * <p>This method checks that  the Schedule ID is not null, references a schedule in readable state,
     * and the referenced schedule has a child transaction.
     * The full set of checks in the {@link #validate(Schedule, Instant, boolean)} method must also
     * pass.
     * If all validation checks pass, the schedule metadata is returned.
     * If any checks fail, then a {@link PreCheckException} is thrown.</p>
     * @param idToValidate the ID of the schedule to validate
     * @param scheduleStore data from readable state which contains, at least, a metadata entry for the schedule
     *     that the current transaction will sign.
     * @throws PreCheckException if the ScheduleSign transaction provided fails any of the required validation
     *     checks.
     */
    @NonNull
    protected Schedule preValidate(
            @NonNull final ReadableScheduleStore scheduleStore,
            final boolean isLongTermEnabled,
            @Nullable final ScheduleID idToValidate)
            throws PreCheckException {
        if (idToValidate != null) {
            final Schedule scheduleData = scheduleStore.get(idToValidate);
            if (scheduleData != null) {
                if (scheduleData.scheduledTransaction() != null) {
                    final ResponseCodeEnum validationResult = validate(scheduleData, null, isLongTermEnabled);
                    if (validationResult == ResponseCodeEnum.OK) {
                        return scheduleData;
                    } else {
                        throw new PreCheckException(validationResult);
                    }
                } else {
                    throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION);
                }
            } else {
                throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
            }
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
        }
    }

    /**
     * Given a schedule, consensus time, and long term scheduling enabled flag, validate the transaction
     * meets minimum requirements to be handled.
     * <p>
     * This method checks that, as of the current consensus time, the schedule is
     * <ul>
     *     <li>not null</li>
     *     <li>has a scheduled transaction</li>
     *     <li>has not been executed</li>
     *     <li>is not deleted</li>
     *     <li>has not expired</li>
     * </ul>
     *
     * @param scheduleToValidate the {@link Schedule} to validate.  If this is null then
     *     {@link ResponseCodeEnum#INVALID_SCHEDULE_ID} is returned.
     * @param consensusTime the consensus time {@link Instant} applicable to this transaction.
     *     If this is null then we assume this is a pre-check and do not validate expiration.
     * @param isLongTermEnabled a flag indicating if long term scheduling is currently enabled. This modifies
     *     which response code is sent when a schedule is expired.
     * @return a response code representing the result of the validation.  This is {@link ResponseCodeEnum#OK}
     *     if all checks pass, or an appropriate failure code if any checks fail.
     */
    @NonNull
    protected ResponseCodeEnum validate(
            @Nullable final Schedule scheduleToValidate,
            @Nullable final Instant consensusTime,
            final boolean isLongTermEnabled) {
        final ResponseCodeEnum result;
        final Instant effectiveConsensusTime = Objects.requireNonNullElse(consensusTime, Instant.MIN);
        if (scheduleToValidate != null) {
            if (scheduleToValidate.hasScheduledTransaction()) {
                if (!scheduleToValidate.executed()) {
                    if (!scheduleToValidate.deleted()) {
                        final long expiration = scheduleToValidate.calculatedExpirationSecond();
                        final Instant calculatedExpiration =
                                (expiration != Schedule.DEFAULT.calculatedExpirationSecond()
                                        ? Instant.ofEpochSecond(expiration)
                                        : Instant.MAX);
                        if (effectiveConsensusTime.isBefore(calculatedExpiration)) {
                            result = ResponseCodeEnum.OK;
                        } else {
                            // We are past expiration time
                            if (!isLongTermEnabled) {
                                result = ResponseCodeEnum.INVALID_SCHEDULE_ID;
                            } else {
                                // This is not failure, it indicates the schedule should execute if it can,
                                // or be removed if it is not executable (i.e. it lacks required signatures)
                                result = ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
                            }
                        }
                    } else {
                        result = ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
                    }
                } else {
                    result = ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
                }
            } else {
                result = ResponseCodeEnum.INVALID_TRANSACTION;
            }
        } else {
            result = ResponseCodeEnum.INVALID_SCHEDULE_ID;
        }
        return result;
    }

    /**
     * Very basic transaction ID validation.
     * This just checks that the transaction is not scheduled (you cannot schedule a schedule),
     * that the account ID is not null (so we can fill in scheduler account),
     * and that the start timestamp is not null (so we can fill in schedule valid start time)
     * @param currentId a TransactionID to validate
     * @throws PreCheckException if the transaction is scheduled, the account ID is null, or the start time is null.
     */
    protected void checkValidTransactionId(@Nullable final TransactionID currentId) throws PreCheckException {
        if (currentId == null) throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_ID);
        final AccountID payer = currentId.accountID();
        final Timestamp validStart = currentId.transactionValidStart();
        final boolean isScheduled = currentId.scheduled();
        if (isScheduled) throw new PreCheckException(ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        if (payer == null) throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID);
        if (validStart == null) throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_START);
    }

    protected boolean tryToExecuteSchedule(
            @NonNull final HandleContext context,
            @NonNull final Schedule scheduleToExecute,
            @NonNull final Set<Key> remainingSignatories,
            @NonNull final Set<Key> validSignatories,
            @NonNull final ResponseCodeEnum validationResult,
            final boolean isLongTermEnabled) {
        if (canExecute(remainingSignatories, isLongTermEnabled, validationResult, scheduleToExecute)) {
            final Predicate<Key> assistant = new DispatchPredicate(validSignatories);
            final TransactionBody childTransaction = HandlerUtility.childAsOrdinary(scheduleToExecute);
            final ScheduleRecordBuilder recordBuilder =
                    context.dispatchChildTransaction(childTransaction, ScheduleRecordBuilder.class, assistant);
            // set the schedule ref for the child transaction
            recordBuilder.scheduleRef(scheduleToExecute.scheduleId());
            recordBuilder.scheduledTransactionID(childTransaction.transactionID());
            // If the child failed, we fail with the same result.
            // @note the interface below should always be implemented by all record builders,
            //       but we still need to cast it.
            if (recordBuilder instanceof SingleTransactionRecordBuilder base && !validationOk(base.status())) {
                throw new HandleException(base.status());
            }
            return true;
        } else {
            return false;
        }
    }

    protected boolean validationOk(final ResponseCodeEnum validationResult) {
        return validationResult == ResponseCodeEnum.OK
                || validationResult == ResponseCodeEnum.SUCCESS
                || validationResult == ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
    }

    @NonNull
    private Set<Key> getKeySetFromTransactionKeys(final TransactionKeys requiredKeys) {
        final Set<Key> scheduledRequiredKeys = new ConcurrentSkipListSet<>(new RecordIdentityComparator());
        scheduledRequiredKeys.addAll(requiredKeys.requiredNonPayerKeys());
        scheduledRequiredKeys.addAll(requiredKeys.optionalNonPayerKeys());
        scheduledRequiredKeys.add(requiredKeys.payerKey());
        return scheduledRequiredKeys;
    }

    /**
     * Given an arbitrary {@link Iterable<Key>}, return a <strong>modifiable</strong> {@link Set<Key>} containing
     * the same objects as the input.
     * If there are any duplicates in the input, only one of each will be in the result.
     * If there are any null values in the input, those values will be excluded from the result.
     * Note: The return value of this method is always safe to be read and written by multiple threads
     *     concurrently, and operates as close to lock-free as possible.
     * Implementation Note: we use ConcurrentSkipListSet that orders the entries by hash code.  This means that
     *   object order will be very different from a reasonable natural ordering, but it should (if hashCode is defined
     *   properly) be consistent with equals, and have sufficient diffusion to work well with the random
     *   functions inherent in the Skip List data structure. Order is not important, so this approach is reasonable.
     *   The alternative is a CopyOnWriteArraySet, which has significant GC performance issues.
     * @param keyCollection an Iterable of Key values.
     * @return a {@link Set<Key>} containing the same contents as the input.  Duplicates and null values are excluded
     * from this Set.  This Set is always a modifiable set.
     */
    @NonNull
    private Set<Key> setOfKeys(@Nullable final Iterable<Key> keyCollection) {
        // We only permit Concurrent sets for this method.
        if (keyCollection instanceof ConcurrentSkipListSet<Key>) {
            return (Set<Key>) keyCollection;
        } else if (keyCollection != null) {
            final Set<Key> results = new ConcurrentSkipListSet<>(new RecordIdentityComparator());
            for (final Key next : keyCollection) {
                if (next != null) results.add(next);
            }
            return results;
        } else {
            // cannot use Set.of() or Collections.emptySet() here because those are unmodifiable.
            return new ConcurrentSkipListSet<>(new RecordIdentityComparator());
        }
    }

    private boolean canExecute(
            final Set<Key> remainingSignatories,
            final boolean isLongTermEnabled,
            final ResponseCodeEnum validationResult,
            final Schedule scheduleToExecute) {
        return (remainingSignatories == null || remainingSignatories.isEmpty())
                && (!isLongTermEnabled
                        || (scheduleToExecute.waitForExpiry()
                                && validationResult == ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION));
    }

    /**
     * A mapping function implementation that validates a single account key and maps that to a
     * set of "primitive" keys that are needed (in whole or in part) to meet the full signature
     * requirements of that key.  This function, as a side effect, adds additional "primitive"
     * keys to the existing signatories set for each such key that has a valid signature for the
     * current transaction.
     */
    private static class ValidatedKeysMapping implements Function<Key, Set<Key>> {
        private final HandleContext context;
        private final Set<Key> signatories;

        /**
         * Create a mapping function with a given context and set of signatories.
         *
         * @param context a HandleContext for a particular ScheduleCreate or ScheduleSign transaction.
         * @param signatories a Set of "primitive" keys that were previously validated, and therefore
         *     are "deemed" to have signed the child transaction.  <strong>This set is modified by the
         *     apply method</strong> of the object created, so it must not be an unmodifiable set.
         */
        protected ValidatedKeysMapping(@NonNull final HandleContext context, @NonNull final Set<Key> signatories) {
            this.context = context;
            this.signatories = signatories;
        }

        /**
         * Apply the mapping function to a particular Account key.
         * The function here checks if the key is valid while providing a verification assistant that
         * considers all keys in "signatories" to be valid.  The mapping, as a side effect, also
         * records all newly validated "primitive" keys and adds those to "signatories".  If the
         * Account key is still not validated, then all remaining required "primitive" keys are
         * returned.
         *
         * Note, it is possible that not all values in the returned key set are required, particularly
         * in the case of threshold keys, it may be that only some of the keys are required, but all
         * of the keys returned <strong>can</strong> be used to meet the signature requirements for
         * the Account key.
         *
         * @param key a single Account key to validate against the signatures on the current transaction
         *     as well as the "signatories" set of previously validated signatures.
         * @return a {@link Set<Key>} of "primitive" keys that still must be validated
         *     (in whole or in part) to consider the Account key fully validated.
         */
        @Override
        public Set<Key> apply(final Key key) {
            final Set<Key> remainingUnverifiedKeys = new LinkedHashSet<>();
            final var assistant = new ScheduleVerificationAssistant(signatories, remainingUnverifiedKeys);
            final SignatureVerification isVerified = context.verificationFor(key, assistant);
            // unverified primitive keys only count if the top-level key failed verification.
            return isVerified.passed() ? Collections.emptySet() : remainingUnverifiedKeys;
        }
    }

    /**
     * Comparator implementation for Records that orders by hash code.  This is not useful if order actually
     * matters, but it is useful to use an ordered set when order does not matter and performance does.
     * We use this to enable storing a Record (Key) in a ConcurrentSkipListSet which is used in a highly
     * parallel operation that both reads and modifies the set on, ideally, several threads at once.
     */
    private static class RecordIdentityComparator implements Comparator<Record> {
        @Override
        public int compare(final Record left, final Record right) {
            return Objects.hashCode(left) - Objects.hashCode(right);
        }
    }
}
