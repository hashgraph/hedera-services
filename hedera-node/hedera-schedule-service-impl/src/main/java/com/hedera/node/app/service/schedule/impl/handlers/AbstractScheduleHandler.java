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

import static com.hedera.node.app.spi.workflows.HandleContext.ConsensusThrottling.ON;

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
import com.hedera.node.app.service.schedule.ScheduleStreamBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.key.KeyComparator;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Predicate;

/**
 * Provides some implementation support needed for both the {@link ScheduleCreateHandler} and {@link
 * ScheduleSignHandler}.
 */
abstract class AbstractScheduleHandler {
    protected static final String NULL_CONTEXT_MESSAGE =
            "Dispatcher called the schedule handler with a null context; probable internal data corruption.";

    /**
     * A simple record to return both "deemed valid" signatories and remaining primitive keys that must sign.
     *
     * @param updatedSignatories a Set of "deemed valid" signatories, possibly updated with new entries
     * @param remainingRequiredKeys A Set of Key entries that have not yet signed the scheduled transaction, but
     *     must sign that transaction before it can be executed.
     */
    protected static record ScheduleKeysResult(Set<Key> updatedSignatories, Set<Key> remainingRequiredKeys) {}

    /**
     * Gets the set of all the keys required to sign a transaction.
     *
     * @param scheduleInState the schedule in state
     * @param context the Prehandle context
     * @return the set of keys required to sign the transaction
     * @throws PreCheckException if the transaction cannot be handled successfully due to a validation failure of the
     * dispatcher related to signer requirements or other pre-validation criteria.
     */
    @NonNull
    protected Set<Key> allKeysForTransaction(
            @NonNull final Schedule scheduleInState, @NonNull final PreHandleContext context) throws PreCheckException {
        final TransactionBody scheduledAsOrdinary = HandlerUtility.childAsOrdinary(scheduleInState);
        final AccountID originalCreatePayer =
                scheduleInState.originalCreateTransaction().transactionID().accountID();
        // note, payerAccount will never be null, but we're dealing with Sonar here.
        final AccountID payerForNested = scheduleInState.payerAccountIdOrElse(originalCreatePayer);
        final TransactionKeys keyStructure = context.allKeysForTransaction(scheduledAsOrdinary, payerForNested);
        return getKeySetFromTransactionKeys(keyStructure);
    }

    /**
     * Get the schedule keys result to sign the transaction
     *
     * @param scheduleInState the schedule in state
     * @param context         the Prehandle context
     * @return the schedule keys result containing the updated signatories and the remaining required keys
     * @throws HandleException if any validation check fails when getting the keys for the transaction
     */
    @NonNull
    protected ScheduleKeysResult allKeysForTransaction(
            @NonNull final Schedule scheduleInState, @NonNull final HandleContext context) throws HandleException {
        final AccountID originalCreatePayer =
                scheduleInState.originalCreateTransaction().transactionID().accountID();
        // note, payerAccount should never be null, but we're playing it safe here.
        final AccountID payer = scheduleInState.payerAccountIdOrElse(originalCreatePayer);
        final TransactionBody scheduledAsOrdinary = HandlerUtility.childAsOrdinary(scheduleInState);
        final TransactionKeys keyStructure;
        try {
            keyStructure = context.allKeysForTransaction(scheduledAsOrdinary, payer);
            // @todo('9447') We have an issue here.  Currently, allKeysForTransaction fails in many cases where a
            //     key is currently unavailable, but could be in the future.  We need the keys, even
            //     if the transaction is currently invalid, because we may create and sign schedules for
            //     invalid transactions, then only fail when the transaction is executed.  This would allow
            //     (e.g.) scheduling the transfer of a dApp service fee from a newly created account to be
            //     set up before the account (or key) is created; then the new account, once funded, signs
            //     the scheduled transaction and the funds are immediately transferred.  Currently that
            //     would fail on create.  Long-term we should fix that.
        } catch (final PreCheckException translated) {
            throw new HandleException(translated.responseCode());
        }
        final Set<Key> scheduledRequiredKeys = getKeySetFromTransactionKeys(keyStructure);
        // Ensure the *custom* payer is required; some rare corner cases may not require it otherwise.
        final Key payerKey = getKeyForAccount(context, payer);
        if (hasCustomPayer(scheduleInState) && payerKey != null) scheduledRequiredKeys.add(payerKey);
        final Set<Key> currentSignatories = setOfKeys(scheduleInState.signatories());
        final Set<Key> remainingRequiredKeys =
                filterRemainingRequiredKeys(context, scheduledRequiredKeys, currentSignatories, originalCreatePayer);
        // Mono doesn't store extra signatures, so for now we mustn't either.
        // This is structurally wrong for long term schedules, so we must remove this later.
        // @todo('9447') Stop removing currently unused signatures, just store all the verified signatures until
        //     there are enough to execute, so we don't discard a signature now that would be required later.
        HandlerUtility.filterSignatoriesToRequired(currentSignatories, scheduledRequiredKeys);
        return new ScheduleKeysResult(currentSignatories, remainingRequiredKeys);
    }

    private boolean hasCustomPayer(final Schedule scheduleToCheck) {
        final AccountID originalCreatePayer =
                scheduleToCheck.originalCreateTransaction().transactionID().accountID();
        final AccountID assignedPayer = scheduleToCheck.payerAccountId();
        // Will never be null, but Sonar doesn't know that.
        return assignedPayer != null && !assignedPayer.equals(originalCreatePayer);
    }

    /**
     * Verify that at least one "new" required key signed the transaction.
     * <p>
     * If there exists a {@link Key} nKey, a member of newSignatories, such that nKey is <em>not
     * in</em> existingSignatories, then a new key signed.  Otherwise an {@link HandleException} is
     * thrown with status {@link ResponseCodeEnum#NO_NEW_VALID_SIGNATURES}.
     *
     * @param existingSignatories a List of signatories representing all prior signatures before the current
     *        ScheduleSign transaction.
     * @param newSignatories a Set of signatories representing all signatures following the current ScheduleSign
     *        transaction.
     * @throws HandleException if there are no new signatures compared to the prior state.
     */
    protected void verifyHasNewSignatures(
            @NonNull final List<Key> existingSignatories, @NonNull final Set<Key> newSignatories)
            throws HandleException {
        SortedSet<Key> preExisting = setOfKeys(existingSignatories);
        if (preExisting.containsAll(newSignatories))
            throw new HandleException(ResponseCodeEnum.NO_NEW_VALID_SIGNATURES);
    }

    /**
     * Gets key for account.
     *
     * @param context        the handle context
     * @param accountToQuery the account to query
     * @return the key for account
     */
    @Nullable
    protected Key getKeyForAccount(@NonNull final HandleContext context, @NonNull final AccountID accountToQuery) {
        final ReadableAccountStore accountStore = context.storeFactory().readableStore(ReadableAccountStore.class);
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
                        if (calculatedExpiration.getEpochSecond() >= effectiveConsensusTime.getEpochSecond()) {
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

    /**
     * Try to execute a schedule. Will attempt to execute a schedule if the remaining signatories are empty
     * and the schedule is not waiting for expiration.
     *
     * @param context              the context
     * @param scheduleToExecute    the schedule to execute
     * @param remainingSignatories the remaining signatories
     * @param validSignatories     the valid signatories
     * @param validationResult     the validation result
     * @param isLongTermEnabled    the is long term enabled
     * @return boolean indicating if the schedule was executed
     */
    protected boolean tryToExecuteSchedule(
            @NonNull final HandleContext context,
            @NonNull final Schedule scheduleToExecute,
            @NonNull final Set<Key> remainingSignatories,
            @NonNull final Set<Key> validSignatories,
            @NonNull final ResponseCodeEnum validationResult,
            final boolean isLongTermEnabled) {
        if (canExecute(remainingSignatories, isLongTermEnabled, validationResult, scheduleToExecute)) {
            final AccountID originalPayer = scheduleToExecute
                    .originalCreateTransaction()
                    .transactionID()
                    .accountID();
            final Set<Key> acceptedSignatories = new HashSet<>();
            acceptedSignatories.addAll(validSignatories);
            acceptedSignatories.add(getKeyForAccount(context, originalPayer));
            final Predicate<Key> assistant = new DispatchPredicate(acceptedSignatories);
            // This sets the child transaction ID to scheduled.
            final TransactionBody childTransaction = HandlerUtility.childAsOrdinary(scheduleToExecute);
            final ScheduleStreamBuilder recordBuilder = context.dispatchChildTransaction(
                    childTransaction,
                    ScheduleStreamBuilder.class,
                    assistant,
                    scheduleToExecute.payerAccountId(),
                    TransactionCategory.SCHEDULED,
                    ON);
            // If the child failed, we would prefer to fail with the same result.
            //     We do not fail, however, at least mono service code does not.
            //     We succeed and the record of the child transaction is failed.
            // set the schedule ref for the child transaction to the schedule that we're executing
            recordBuilder.scheduleRef(scheduleToExecute.scheduleId());
            // also set the child transaction ID as scheduled transaction ID in the parent record.
            final ScheduleStreamBuilder parentRecordBuilder =
                    context.savepointStack().getBaseBuilder(ScheduleStreamBuilder.class);
            parentRecordBuilder.scheduledTransactionID(childTransaction.transactionID());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if the validation is OK, SUCCESS, or SCHEDULE_PENDING_EXPIRATION.
     *
     * @param validationResult the validation result
     * @return boolean indicating status of the validation
     */
    protected boolean validationOk(final ResponseCodeEnum validationResult) {
        return validationResult == ResponseCodeEnum.OK
                || validationResult == ResponseCodeEnum.SUCCESS
                || validationResult == ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
    }

    @NonNull
    private SortedSet<Key> getKeySetFromTransactionKeys(final TransactionKeys requiredKeys) {
        final SortedSet<Key> scheduledRequiredKeys = new ConcurrentSkipListSet<>(new KeyComparator());
        scheduledRequiredKeys.addAll(requiredKeys.requiredNonPayerKeys());
        scheduledRequiredKeys.addAll(requiredKeys.optionalNonPayerKeys());
        return scheduledRequiredKeys;
    }

    private SortedSet<Key> filterRemainingRequiredKeys(
            final HandleContext context,
            final Set<Key> scheduledRequiredKeys,
            final Set<Key> currentSignatories,
            final AccountID originalCreatePayer) {
        // the final output must be a sorted/ordered set.
        final KeyComparator keyMatcher = new KeyComparator();
        final SortedSet<Key> remainingKeys = new ConcurrentSkipListSet<>(keyMatcher);
        final Set<Key> currentUnverifiedKeys = new HashSet<>(1);
        final Key originalPayerKey = getKeyForAccount(context, originalCreatePayer);
        final var assistant = new ScheduleVerificationAssistant(currentSignatories, currentUnverifiedKeys);
        for (final Key next : scheduledRequiredKeys) {
            // The schedule verification assistant observes each primitive key in the tree
            final SignatureVerification isVerified = context.keyVerifier().verificationFor(next, assistant);
            // unverified primitive keys only count if the top-level key failed verification.
            // @todo('9447') The comparison to originalPayerKey here is to match monoservice
            //      "hidden default payer" behavior. We intend to remove that behavior after v1
            //      release as it is not considered fully "correct", particularly for long term schedules.
            if (!isVerified.passed() && keyMatcher.compare(next, originalPayerKey) != 0) {
                remainingKeys.addAll(currentUnverifiedKeys);
            }
            currentUnverifiedKeys.clear();
        }
        return remainingKeys;
    }

    /**
     * Given an arbitrary {@link Iterable<Key>}, return a <strong>modifiable</strong> {@link SortedSet<Key>} containing
     * the same objects as the input.
     * This set must be sorted to ensure a deterministic order of values in state.
     * If there are any duplicates in the input, only one of each will be in the result.
     * If there are any null values in the input, those values will be excluded from the result.
     * @param keyCollection an Iterable of Key values.
     * @return a {@link SortedSet<Key>} containing the same contents as the input.
     * Duplicates and null values are excluded from this Set.  This Set is always a modifiable set.
     */
    @NonNull
    private SortedSet<Key> setOfKeys(@Nullable final Iterable<Key> keyCollection) {
        if (keyCollection != null) {
            final SortedSet<Key> results = new ConcurrentSkipListSet<>(new KeyComparator());
            for (final Key next : keyCollection) {
                if (next != null) results.add(next);
            }
            return results;
        } else {
            // cannot use Set.of() or Collections.emptySet() here because those are unmodifiable and unsorted.
            return new ConcurrentSkipListSet<>(new KeyComparator());
        }
    }

    private boolean canExecute(
            final Set<Key> remainingSignatories,
            final boolean isLongTermEnabled,
            final ResponseCodeEnum validationResult,
            final Schedule scheduleToExecute) {
        // either we're waiting and pending, or not waiting and not pending
        final boolean longTermReady =
                scheduleToExecute.waitForExpiry() == (validationResult == ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION);
        final boolean allSignturesGathered = remainingSignatories == null || remainingSignatories.isEmpty();
        return allSignturesGathered && (!isLongTermEnabled || longTermReady);
    }
}
