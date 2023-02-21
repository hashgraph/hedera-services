/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.RecordCache;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static java.util.Objects.requireNonNull;

/** This class preprocess transactions by parsing them and checking for syntax errors. */
public class OnsetChecker {
    private static final String COUNTER_DEPRECATED_TXNS_NAME = "DeprTxnsRcv";
    private static final String COUNTER_RECEIVED_DEPRECATED_DESC = "number of deprecated txns received";

    private final RecordCache recordCache;
    private final GlobalDynamicProperties dynamicProperties;
    private final Counter deprecatedCounter;

    /**
     * Constructor of an {@code OnsetChecker}
     *
     * @param recordCache the {@link RecordCache}
     * @param dynamicProperties the {@link GlobalDynamicProperties}
     * @param metrics metrics related to workflows
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public OnsetChecker(
            @NonNull final RecordCache recordCache,
            @NonNull final GlobalDynamicProperties dynamicProperties,
            @NonNull final Metrics metrics) {
        this.recordCache = requireNonNull(recordCache);
        this.dynamicProperties = requireNonNull(dynamicProperties);
        this.deprecatedCounter = metrics.getOrCreate(new Counter.Config("app", COUNTER_DEPRECATED_TXNS_NAME)
                .withDescription(COUNTER_RECEIVED_DEPRECATED_DESC));
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

        final var hasSignedTxnBytes = tx.signedTransactionBytes().getLength() > 0;
        final var hasDeprecatedSigMap = tx.sigMap() != null;
        final var hasDeprecatedBodyBytes = tx.bodyBytes().getLength() > 0;
        final var hasDeprecatedBody = tx.body() != null;
        final var hasDeprecatedSigs = tx.sigs() != null;

        if (hasSignedTxnBytes) {
            if (hasDeprecatedBodyBytes || hasDeprecatedSigMap) {
                throw new PreCheckException(INVALID_TRANSACTION);
            }
        } else if (!hasDeprecatedBodyBytes) {
            throw new PreCheckException(INVALID_TRANSACTION_BODY);
        }

        if (hasDeprecatedBody
                || hasDeprecatedSigs
                || hasDeprecatedSigMap
                || hasDeprecatedBodyBytes) {
            deprecatedCounter.increment();
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

        if (txBody.transactionID() == null) {
            throw new PreCheckException(INVALID_TRANSACTION_ID);
        }

        var txnId = txBody.transactionID();
        if (!isPlausibleAccount(txnId.accountID())) {
            throw new PreCheckException(PAYER_ACCOUNT_NOT_FOUND);
        }

        checkMemo(txBody.memo());

        if (recordCache.isReceiptPresent(txnId)) {
            return DUPLICATE_TRANSACTION;
        }

        if (!isPlausibleTxnFee(txBody.transactionFee())) {
            return INSUFFICIENT_TX_FEE;
        }

        return checkTimeBox(txnId.transactionValidStart(), txBody.transactionValidDuration());
    }

    private static boolean isPlausibleTxnFee(final long transactionFee) {
        return transactionFee >= 0;
    }

    private static boolean isPlausibleAccount(final AccountID accountID) {
        final var optAccountNum = accountID.accountNum();
        final var hasAlias = accountID.alias().isPresent() || accountID.evmAddress().isPresent();
        return ((optAccountNum.isPresent() && optAccountNum.get() > 0) || hasAlias)
                && accountID.realmNum() >= 0
                && accountID.shardNum() >= 0;
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

    private ResponseCodeEnum checkTimeBox(final Timestamp start, final Duration duration) {
        final var validForSecs = duration.seconds();
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
                        Math.max(Instant.MIN.getEpochSecond(), timestamp.seconds()),
                        Instant.MAX.getEpochSecond()),
                Math.min(
                        Math.max(Instant.MIN.getNano(), timestamp.nanos()), Instant.MAX.getNano()));
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
