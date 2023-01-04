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
package com.hedera.node.app.workflows.onset;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.records.RecordCache;
import com.hedera.node.app.service.mono.stats.HapiOpCounters;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

/** This class preprocess transactions by parsing them and checking for syntax errors. */
public class OnsetChecker {

    private final int maxSignedTxnSize;
    private final RecordCache recordCache;
    private final GlobalDynamicProperties dynamicProperties;
    private final HapiOpCounters counters;

    /**
     * Constructor of an {@code OnsetChecker}
     *
     * @param maxSignedTxnSize the maximum transaction size
     * @param recordCache the {@link RecordCache}
     * @param dynamicProperties the {@link GlobalDynamicProperties}
     * @param counters metrics related to workflows
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public OnsetChecker(
            final int maxSignedTxnSize,
            @NonNull final RecordCache recordCache,
            @NonNull final GlobalDynamicProperties dynamicProperties,
            @NonNull final HapiOpCounters counters) {
        if (maxSignedTxnSize <= 0) {
            throw new IllegalArgumentException("maxSignedTxnSize must be > 0");
        }
        this.maxSignedTxnSize = maxSignedTxnSize;
        this.recordCache = requireNonNull(recordCache);
        this.dynamicProperties = requireNonNull(dynamicProperties);
        this.counters = requireNonNull(counters);
    }

    /**
     * Validates a {@link Transaction}
     *
     * @param tx the {@code Transaction}
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if {@code tx} is {@code null}
     */
    @SuppressWarnings("deprecation")
    public void checkTransaction(@NonNull final Transaction tx) throws PreCheckException {
        requireNonNull(tx);

        final var hasSignedTxnBytes = !tx.getSignedTransactionBytes().isEmpty();
        final var hasDeprecatedSigMap = tx.hasSigMap();
        final var hasDeprecatedBodyBytes = !tx.getBodyBytes().isEmpty();
        final var hasDeprecatedBody = tx.hasBody();
        final var hasDeprecatedSigs = tx.hasSigs();

        if (hasDeprecatedBody
                || hasDeprecatedSigs
                || hasDeprecatedSigMap
                || hasDeprecatedBodyBytes) {
            counters.countDeprecatedTxnReceived();
        }

        if (hasSignedTxnBytes) {
            if (hasDeprecatedBodyBytes || hasDeprecatedSigMap) {
                throw new PreCheckException(INVALID_TRANSACTION);
            }
        } else if (!hasDeprecatedBodyBytes) {
            throw new PreCheckException(INVALID_TRANSACTION_BODY);
        }

        if (tx.getSerializedSize() > maxSignedTxnSize) {
            throw new PreCheckException(TRANSACTION_OVERSIZE);
        }

        if (MiscUtils.hasUnknownFields(tx)) {
            throw new PreCheckException(TRANSACTION_HAS_UNKNOWN_FIELDS);
        }
    }

    /**
     * Validates a {@link SignedTransaction}
     *
     * @param tx the {@code SignedTransaction} to check
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if {@code tx} is {@code null}
     */
    public void checkSignedTransaction(@NonNull final SignedTransaction tx)
            throws PreCheckException {
        requireNonNull(tx);

        if (MiscUtils.hasUnknownFields(tx)) {
            throw new PreCheckException(TRANSACTION_HAS_UNKNOWN_FIELDS);
        }
    }

    /**
     * Validates a {@link TransactionBody}
     *
     * @param txBody the {@code TransactionBody} to check
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    @NonNull
    public ResponseCodeEnum checkTransactionBody(@NonNull final TransactionBody txBody)
            throws PreCheckException {
        requireNonNull(txBody);

        if (MiscUtils.hasUnknownFields(txBody)) {
            throw new PreCheckException(TRANSACTION_HAS_UNKNOWN_FIELDS);
        }

        if (!txBody.hasTransactionID()) {
            throw new PreCheckException(INVALID_TRANSACTION_ID);
        }

        var txnId = txBody.getTransactionID();
        if (!isPlausibleAccount(txnId.getAccountID())) {
            throw new PreCheckException(PAYER_ACCOUNT_NOT_FOUND);
        }

        checkMemo(txBody.getMemo());

        if (recordCache.isReceiptPresent(txnId)) {
            return DUPLICATE_TRANSACTION;
        }

        if (!isPlausibleTxnFee(txBody.getTransactionFee())) {
            return INSUFFICIENT_TX_FEE;
        }

        return checkTimebox(txnId.getTransactionValidStart(), txBody.getTransactionValidDuration());
    }

    private static boolean isPlausibleTxnFee(final long transactionFee) {
        return transactionFee >= 0;
    }

    private static boolean isPlausibleAccount(final AccountID accountID) {
        return accountID.getAccountNum() > 0
                && accountID.getRealmNum() >= 0
                && accountID.getShardNum() >= 0;
    }

    private void checkMemo(final String memo) throws PreCheckException {
        final var buffer = memo == null ? new byte[0] : memo.getBytes(StandardCharsets.UTF_8);
        if (buffer.length > dynamicProperties.maxMemoUtf8Bytes()) {
            throw new PreCheckException(MEMO_TOO_LONG);
        }
        for (final byte b : buffer) {
            if (b == 0) {
                throw new PreCheckException(INVALID_ZERO_BYTE_IN_STRING);
            }
        }
    }

    private ResponseCodeEnum checkTimebox(final Timestamp start, final Duration duration) {
        final var validForSecs = duration.getSeconds();
        if (validForSecs < dynamicProperties.minTxnDuration()
                || validForSecs > dynamicProperties.maxTxnDuration()) {
            return INVALID_TRANSACTION_DURATION;
        }

        final var validStart = safeguardedInstant(start);
        final var validDuration = safeguardedDuration(validForSecs, validStart);
        final var consensusTime = Instant.now(Clock.systemUTC());
        if (validStart.plusSeconds(validDuration).isBefore(consensusTime)) {
            return TRANSACTION_EXPIRED;
        }
        if (!validStart.isBefore(consensusTime)) {
            return INVALID_TRANSACTION_START;
        }

        return OK;
    }

    /**
     * This method converts a {@link Timestamp} to an {@link Instant} limited between {@link
     * Instant#MIN} and {@link Instant#MAX}
     *
     * @param timestamp the {@code Timestamp} that should be converted
     * @return the resulting {@code Instant}
     */
    private static Instant safeguardedInstant(final Timestamp timestamp) {
        return Instant.ofEpochSecond(
                Math.min(
                        Math.max(Instant.MIN.getEpochSecond(), timestamp.getSeconds()),
                        Instant.MAX.getEpochSecond()),
                Math.min(
                        Math.max(Instant.MIN.getNano(), timestamp.getNanos()),
                        Instant.MAX.getNano()));
    }

    /**
     * This method calculates the valid duration given in seconds, which is the provided number of
     * seconds minus a buffer defined in {@link GlobalDynamicProperties}. The result is limited to a
     * value that, if added to the {@code validStart}, will not exceed {@link Instant#MAX}.
     *
     * @param validForSecs the duration in seconds
     * @param validStart the {@link Instant} that is used to calculate the maximum
     * @return the valid duration given in seconds
     */
    private long safeguardedDuration(final long validForSecs, final Instant validStart) {
        return Math.min(
                validForSecs - dynamicProperties.minValidityBuffer(),
                Instant.MAX.getEpochSecond() - validStart.getEpochSecond());
    }
}
