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

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.ScheduleID.Builder;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Basic utility class for Schedule Handlers.
 */
public final class ScheduleUtility {
    private ScheduleUtility() {}

    @NonNull
    static TransactionBody asOrdinary(@NonNull final Schedule scheduleInState) {
        final TransactionID scheduledTransactionId = transactionIdForScheduled(scheduleInState);
        final SchedulableTransactionBody scheduledTransaction = scheduleInState.scheduledTransaction();
        final TransactionBody.Builder ordinary = TransactionBody.newBuilder();
        if (scheduledTransaction != null) {
            ordinary.transactionFee(scheduledTransaction.transactionFee())
                    .memo(scheduledTransaction.memo())
                    .transactionID(scheduledTransactionId);
            switch (scheduledTransaction.data().kind()) {
                case CONSENSUS_CREATE_TOPIC -> ordinary.consensusCreateTopic(
                        scheduledTransaction.consensusCreateTopicOrThrow());
                case CONSENSUS_UPDATE_TOPIC -> ordinary.consensusUpdateTopic(
                        scheduledTransaction.consensusUpdateTopicOrThrow());
                case CONSENSUS_DELETE_TOPIC -> ordinary.consensusDeleteTopic(
                        scheduledTransaction.consensusDeleteTopicOrThrow());
                case CONSENSUS_SUBMIT_MESSAGE -> ordinary.consensusSubmitMessage(
                        scheduledTransaction.consensusSubmitMessageOrThrow());
                case CRYPTO_CREATE_ACCOUNT -> ordinary.cryptoCreateAccount(
                        scheduledTransaction.cryptoCreateAccountOrThrow());
                case CRYPTO_UPDATE_ACCOUNT -> ordinary.cryptoUpdateAccount(
                        scheduledTransaction.cryptoUpdateAccountOrThrow());
                case CRYPTO_TRANSFER -> ordinary.cryptoTransfer(scheduledTransaction.cryptoTransferOrThrow());
                case CRYPTO_DELETE -> ordinary.cryptoDelete(scheduledTransaction.cryptoDeleteOrThrow());
                case FILE_CREATE -> ordinary.fileCreate(scheduledTransaction.fileCreateOrThrow());
                case FILE_APPEND -> ordinary.fileAppend(scheduledTransaction.fileAppendOrThrow());
                case FILE_UPDATE -> ordinary.fileUpdate(scheduledTransaction.fileUpdateOrThrow());
                case FILE_DELETE -> ordinary.fileDelete(scheduledTransaction.fileDeleteOrThrow());
                case CONTRACT_CREATE_INSTANCE -> ordinary.contractCreateInstance(
                        scheduledTransaction.contractCreateInstanceOrThrow());
                case CONTRACT_UPDATE_INSTANCE -> ordinary.contractUpdateInstance(
                        scheduledTransaction.contractUpdateInstanceOrThrow());
                case CONTRACT_CALL -> ordinary.contractCall(scheduledTransaction.contractCallOrThrow());
                case CONTRACT_DELETE_INSTANCE -> ordinary.contractDeleteInstance(
                        scheduledTransaction.contractDeleteInstanceOrThrow());
                case SYSTEM_DELETE -> ordinary.systemDelete(scheduledTransaction.systemDeleteOrThrow());
                case SYSTEM_UNDELETE -> ordinary.systemUndelete(scheduledTransaction.systemUndeleteOrThrow());
                case FREEZE -> ordinary.freeze(scheduledTransaction.freezeOrThrow());
                case TOKEN_CREATION -> ordinary.tokenCreation(scheduledTransaction.tokenCreationOrThrow());
                case TOKEN_FREEZE -> ordinary.tokenFreeze(scheduledTransaction.tokenFreezeOrThrow());
                case TOKEN_UNFREEZE -> ordinary.tokenUnfreeze(scheduledTransaction.tokenUnfreezeOrThrow());
                case TOKEN_GRANT_KYC -> ordinary.tokenGrantKyc(scheduledTransaction.tokenGrantKycOrThrow());
                case TOKEN_REVOKE_KYC -> ordinary.tokenRevokeKyc(scheduledTransaction.tokenRevokeKycOrThrow());
                case TOKEN_DELETION -> ordinary.tokenDeletion(scheduledTransaction.tokenDeletionOrThrow());
                case TOKEN_UPDATE -> ordinary.tokenUpdate(scheduledTransaction.tokenUpdateOrThrow());
                case TOKEN_MINT -> ordinary.tokenMint(scheduledTransaction.tokenMintOrThrow());
                case TOKEN_BURN -> ordinary.tokenBurn(scheduledTransaction.tokenBurnOrThrow());
                case TOKEN_WIPE -> ordinary.tokenWipe(scheduledTransaction.tokenWipeOrThrow());
                case TOKEN_ASSOCIATE -> ordinary.tokenAssociate(scheduledTransaction.tokenAssociateOrThrow());
                case TOKEN_DISSOCIATE -> ordinary.tokenDissociate(scheduledTransaction.tokenDissociateOrThrow());
                case SCHEDULE_DELETE -> ordinary.scheduleDelete(scheduledTransaction.scheduleDeleteOrThrow());
                case TOKEN_PAUSE -> ordinary.tokenPause(scheduledTransaction.tokenPauseOrThrow());
                case TOKEN_UNPAUSE -> ordinary.tokenUnpause(scheduledTransaction.tokenUnpauseOrThrow());
                case CRYPTO_APPROVE_ALLOWANCE -> ordinary.cryptoApproveAllowance(
                        scheduledTransaction.cryptoApproveAllowanceOrThrow());
                case CRYPTO_DELETE_ALLOWANCE -> ordinary.cryptoDeleteAllowance(
                        scheduledTransaction.cryptoDeleteAllowanceOrThrow());
                case TOKEN_FEE_SCHEDULE_UPDATE -> ordinary.tokenFeeScheduleUpdate(
                        scheduledTransaction.tokenFeeScheduleUpdateOrThrow());
                case UTIL_PRNG -> ordinary.utilPrng(scheduledTransaction.utilPrngOrThrow());
                case UNSET -> throw new HandleException(ResponseCodeEnum.INVALID_TRANSACTION);
            }
        }
        return ordinary.build();
    }

    /**
     * Given a Schedule, return a copy of that schedule with the executed flag and resolution time set.
     * @param schedule a {@link Schedule} to mark executed.
     * @param consensusTime the current consensus time, used to set {@link Schedule#resolutionTime()}.
     * @return a new Schedule which matches the input, except that the execute flag is set and the resolution time
     *     is set to the consensusTime provided.
     */
    @NonNull
    static Schedule markExecuted(@NonNull final Schedule schedule, @NonNull final Instant consensusTime) {
        final Timestamp consensusTimestamp = new Timestamp(consensusTime.getEpochSecond(), consensusTime.getNano());
        return new Schedule(
                schedule.scheduleId(),
                schedule.deleted(),
                true,
                schedule.waitForExpiry(),
                schedule.memo(),
                schedule.schedulerAccountId(),
                schedule.payerAccountId(),
                schedule.adminKey(),
                schedule.scheduleValidStart(),
                schedule.providedExpirationSecond(),
                schedule.calculatedExpirationSecond(),
                consensusTimestamp,
                schedule.scheduledTransaction(),
                schedule.originalCreateTransaction(),
                schedule.signatories());
    }

    @NonNull
    static Schedule replaceSignatories(@NonNull final Schedule schedule, @NonNull final Set<Key> newSignatories) {
        return new Schedule(
                schedule.scheduleId(),
                schedule.deleted(),
                schedule.executed(),
                schedule.waitForExpiry(),
                schedule.memo(),
                schedule.schedulerAccountId(),
                schedule.payerAccountId(),
                schedule.adminKey(),
                schedule.scheduleValidStart(),
                schedule.providedExpirationSecond(),
                schedule.calculatedExpirationSecond(),
                schedule.resolutionTime(),
                schedule.scheduledTransaction(),
                schedule.originalCreateTransaction(),
                List.copyOf(newSignatories));
    }

    @NonNull
    static Schedule replaceSignatoriesAndMarkExecuted(
            @NonNull final Schedule schedule,
            @NonNull final Set<Key> newSignatories,
            @NonNull final Instant consensusTime) {
        final Timestamp consensusTimestamp = new Timestamp(consensusTime.getEpochSecond(), consensusTime.getNano());
        return new Schedule(
                schedule.scheduleId(),
                schedule.deleted(),
                true,
                schedule.waitForExpiry(),
                schedule.memo(),
                schedule.schedulerAccountId(),
                schedule.payerAccountId(),
                schedule.adminKey(),
                schedule.scheduleValidStart(),
                schedule.providedExpirationSecond(),
                schedule.calculatedExpirationSecond(),
                consensusTimestamp,
                schedule.scheduledTransaction(),
                schedule.originalCreateTransaction(),
                List.copyOf(newSignatories));
    }

    @NonNull
    public static ScheduleID toPbj(@NonNull com.hederahashgraph.api.proto.java.ScheduleID valueToConvert) {
        return new ScheduleID(
                valueToConvert.getShardNum(), valueToConvert.getRealmNum(), valueToConvert.getScheduleNum());
    }

    /**
     * Create a new Schedule, but without an ID or signatories.
     * This method is used to create a schedule object for processing during a ScheduleCreate, but without the
     * schedule ID, as we still need to complete validation and other processing.  Once all processing is complete,
     * a new ID is allocated and signatories are added immediately prior to storing the new object in state.
     * @param currentTransaction The transaction body of the current Schedule Create transaction.  We assume that
     *     the transaction is a ScheduleCreate, but require the less specific object so that we have access to
     *     the transaction ID via {@link TransactionBody#transactionID()} from the TransactionBody stored in
     *     the {@link Schedule#originalCreateTransaction()} attribute of the Schedule.
     * @param currentConsensusTime The current consensus time for the network.
     * @param maxLifeMilliseconds The maximum number of seconds a schedule is permitted to exist on
     *     the ledger before it expires.
     * @return a newly created Schedule with a null schedule ID.
     * @throws HandleException if the
     */
    @NonNull
    static Schedule createProvisionalSchedule(
            @NonNull final TransactionBody currentTransaction,
            @NonNull final Instant currentConsensusTime,
            final long maxLifeMilliseconds)
            throws HandleException {
        // The next three items will never be null, but Sonar is persnickety, so we force NPE if any are null.
        final TransactionID parentTransactionId = currentTransaction.transactionIDOrThrow();
        final ScheduleCreateTransactionBody createTransaction = currentTransaction.scheduleCreateOrThrow();
        final AccountID schedulerAccount = parentTransactionId.accountIDOrThrow();
        final Timestamp providedExpirationTime = createTransaction.expirationTime();
        final Timestamp calculatedExpirationTime =
                calculateExpiration(providedExpirationTime, currentConsensusTime, maxLifeMilliseconds);
        final ScheduleID nullId = null;

        Schedule.Builder builder = Schedule.newBuilder();
        builder.scheduleId(nullId).deleted(false).executed(false);
        builder.waitForExpiry(createTransaction.waitForExpiry());
        builder.adminKey(createTransaction.adminKey()).schedulerAccountId(parentTransactionId.accountID());
        builder.payerAccountId(createTransaction.payerAccountIDOrElse(schedulerAccount));
        builder.schedulerAccountId(schedulerAccount);
        builder.scheduleValidStart(parentTransactionId.transactionValidStart());
        builder.calculatedExpirationSecond(calculatedExpirationTime.seconds());
        builder.originalCreateTransaction(currentTransaction);
        builder.memo(createTransaction.memo());
        builder.scheduledTransaction(createTransaction.scheduledTransactionBody());
        return builder.build();
    }

    @NonNull
    static Schedule completeProvisionalSchedule(
            @NonNull final Schedule provisionalSchedule,
            final long newEntityNumber,
            @NonNull final Set<Key> finalSignatories)
            throws HandleException {
        final TransactionBody originalTransaction = provisionalSchedule.originalCreateTransactionOrThrow();
        final TransactionID parentTransactionId = originalTransaction.transactionIDOrThrow();
        final ScheduleID finalId = getNextScheduleID(parentTransactionId, newEntityNumber);

        Schedule.Builder build = Schedule.newBuilder();
        build.scheduleId(finalId).deleted(false).executed(false);
        build.waitForExpiry(provisionalSchedule.waitForExpiry());
        build.adminKey(provisionalSchedule.adminKey()).schedulerAccountId(parentTransactionId.accountID());
        build.payerAccountId(provisionalSchedule.payerAccountId());
        build.schedulerAccountId(provisionalSchedule.schedulerAccountId());
        build.scheduleValidStart(provisionalSchedule.scheduleValidStart());
        build.calculatedExpirationSecond(provisionalSchedule.calculatedExpirationSecond());
        build.originalCreateTransaction(provisionalSchedule.originalCreateTransaction());
        build.memo(provisionalSchedule.memo());
        build.scheduledTransaction(provisionalSchedule.scheduledTransaction());
        build.signatories(List.copyOf(finalSignatories));
        return build.build();
    }

    @NonNull
    static ScheduleID getNextScheduleID(
            @NonNull final TransactionID parentTransactionId, final long newScheduleNumber) {
        final AccountID schedulingAccount = parentTransactionId.accountIDOrThrow();
        final long shardNumber = schedulingAccount.shardNum();
        final long reamlNumber = schedulingAccount.realmNum();
        final Builder builder = ScheduleID.newBuilder().shardNum(shardNumber).realmNum(reamlNumber);
        builder.scheduleNum(newScheduleNumber);
        return builder.build();
    }

    @NonNull
    static TransactionID transactionIdForScheduled(@NonNull Schedule valueInState) {
        // original create transaction and its transaction ID will never be null, but Sonar...
        final TransactionBody originalTransaction = valueInState.originalCreateTransactionOrThrow();
        final TransactionID parentTransactionId = originalTransaction.transactionIDOrThrow();
        // payer on parent transaction ID will also never be null...
        final AccountID payerAccount = valueInState.payerAccountIdOrElse(parentTransactionId.accountIDOrThrow());
        // Scheduled transaction ID is the same as its parent except
        //     if scheduled is set true, payer *might* be modified, and the nonce is incremented.
        final TransactionID.Builder builder = TransactionID.newBuilder().accountID(payerAccount);
        builder.transactionValidStart(parentTransactionId.transactionValidStart());
        builder.scheduled(true).nonce(parentTransactionId.nonce() + 1);
        return builder.build();
    }

    @NonNull
    private static Timestamp calculateExpiration(
            @Nullable final Timestamp givenExpiration,
            @NonNull final Instant currentConsensusTime,
            final long maxLifeMilliseconds) {
        if (givenExpiration != null) {
            return givenExpiration;
        } else {
            final Instant currentPlusMaxLife = currentConsensusTime.plusMillis(maxLifeMilliseconds);
            return new Timestamp(currentPlusMaxLife.getEpochSecond(), currentPlusMaxLife.getNano());
        }
    }
    // Create issue and fill in below.
    // @todo('7773') This requires rebuilding the equality virtual map on migration,
    //      because it's different from ScheduleVirtualValue (and must be, due to PBJ shift)
    @SuppressWarnings("UnstableApiUsage")
    public static String calculateStringHash(final @NonNull Schedule scheduleToHash) {
        Objects.requireNonNull(scheduleToHash);
        final Hasher hasher = Hashing.sha256().newHasher();
        if (scheduleToHash.memo() != null) {
            hasher.putString(scheduleToHash.memo(), StandardCharsets.UTF_8);
        }
        if (scheduleToHash.adminKey() != null) {
            addToHash(hasher, scheduleToHash.adminKey());
        }
        if (scheduleToHash.scheduledTransaction() != null) {
            addToHash(hasher, scheduleToHash.scheduledTransaction());
        }
        if (scheduleToHash.providedExpirationSecond() != Schedule.DEFAULT.providedExpirationSecond()) {
            // @todo('7905') fix to not use Proto serialization
            //            hasher.putLong(scheduleToHash.providedExpirationSecond());
            addToHash(hasher, scheduleToHash.providedExpirationSecond());
        }
        hasher.putBoolean(scheduleToHash.waitForExpiry());
        return hasher.hash().toString();
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void addToHash(final Hasher hasher, final Key keyToAdd) {
        final byte[] keyBytes = Key.PROTOBUF.toBytes(keyToAdd).toByteArray();
        hasher.putInt(keyBytes.length);
        hasher.putBytes(keyBytes);
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void addToHash(final Hasher hasher, final Long timeToAdd) {
        final byte[] bytes =
                ProtoLong.PROTOBUF.toBytes(new ProtoLong(timeToAdd)).toByteArray();
        hasher.putInt(bytes.length);
        hasher.putBytes(bytes);
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void addToHash(final Hasher hasher, final SchedulableTransactionBody transactionToAdd) {
        final byte[] bytes =
                SchedulableTransactionBody.PROTOBUF.toBytes(transactionToAdd).toByteArray();
        hasher.putInt(bytes.length);
        hasher.putBytes(bytes);
    }
}
