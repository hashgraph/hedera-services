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

package com.hedera.node.app.workflows;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.annotations.MaxSignedTxnSize;
import com.hedera.node.app.annotations.NodeSelfId;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.UnknownFieldException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks transactions for internal consistency and validity.
 *
 * <p>This is used in every workflow that deals with transactions including the query workflow for paid queries where
 * it checks the attached {@link HederaFunctionality#CRYPTO_TRANSFER} transaction.
 *
 * <p>Transactions have a deprecated set of fields. Deprecation metrics are kept to track their usage.
 *
 * <p>This class is a thread-safe singleton.
 */
@Singleton
public class TransactionChecker {
    private static final Logger logger = LoggerFactory.getLogger(TransactionChecker.class);

    private static final int USER_TRANSACTION_NONCE = 0;

    // Metric config for keeping track of the number of deprecated transactions received
    private static final String COUNTER_DEPRECATED_TXNS_NAME = "DeprTxnsRcv";
    private static final String COUNTER_RECEIVED_DEPRECATED_DESC =
            "number of deprecated txns (bodyBytes, sigMap) received";
    private static final String COUNTER_SUPER_DEPRECATED_TXNS_NAME = "SuperDeprTxnsRcv";
    private static final String COUNTER_RECEIVED_SUPER_DEPRECATED_DESC =
            "number of super-deprecated txns (body, sigs) received";

    /** The maximum number of bytes that can exist in the transaction */
    private final int maxSignedTxnSize;
    /** The {@link GlobalDynamicProperties} used to get properties needed for these checks. */
    private final GlobalDynamicProperties props;
    /** The {@link Counter} used to track the number of deprecated transactions (bodyBytes, sigMap) received. */
    private final Counter deprecatedCounter;
    /** The {@link Counter} used to track the number of super deprecated transactions (body, sigs) received. */
    private final Counter superDeprecatedCounter;
    /** The account ID of the node running this software */
    private final AccountID nodeAccount;

    // TODO We need to incorporate the check for "TRANSACTION_TOO_MANY_LAYERS". "maxProtoMessageDepth" is a property
    //  passed to StructuralPrecheck used for this purpose. We will need to add this to PBJ as an argument to the
    //  parser in strict mode to prevent it from parsing too deep.

    /**
     * Create a new {@link TransactionChecker}
     *
     * @param maxSignedTxnSize the maximum transaction size
     * @param dynamicProperties the {@link GlobalDynamicProperties}
     * @param metrics metrics related to workflows
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws IllegalArgumentException if {@code maxSignedTxnSize} is not positive
     */
    @Inject
    public TransactionChecker(
            @MaxSignedTxnSize final int maxSignedTxnSize,
            @NodeSelfId @NonNull final AccountID nodeAccount,
            @NonNull final GlobalDynamicProperties dynamicProperties,
            @NonNull final Metrics metrics) {
        if (maxSignedTxnSize <= 0) {
            throw new IllegalArgumentException("maxSignedTxnSize must be > 0");
        }

        this.nodeAccount = requireNonNull(nodeAccount);
        this.maxSignedTxnSize = maxSignedTxnSize;
        this.props = requireNonNull(dynamicProperties);
        this.deprecatedCounter = metrics.getOrCreate(new Counter.Config("app", COUNTER_DEPRECATED_TXNS_NAME)
                .withDescription(COUNTER_RECEIVED_DEPRECATED_DESC));
        this.superDeprecatedCounter = metrics.getOrCreate(new Counter.Config("app", COUNTER_SUPER_DEPRECATED_TXNS_NAME)
                .withDescription(COUNTER_RECEIVED_SUPER_DEPRECATED_DESC));
    }

    /**
     * Parse the given {@link Bytes} into a transaction.
     *
     * <p>After verifying that the number of bytes comprising the transaction does not exceed the maximum allowed, the
     * transaction is parsed. A transaction can be checked with {@link #check(Transaction)}.
     *
     * @param buffer the {@code ByteBuffer} with the serialized transaction
     * @return an {@link TransactionInfo} with the parsed and checked entities
     * @throws PreCheckException if the data is not valid
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public Transaction parse(@NonNull final Bytes buffer) throws PreCheckException {
        // Fail fast if there are too many transaction bytes
        if (buffer.length() > maxSignedTxnSize) {
            throw new PreCheckException(TRANSACTION_OVERSIZE);
        }

        return parseStrict(buffer.toReadableSequentialData(), Transaction.PROTOBUF, INVALID_TRANSACTION);
    }

    /**
     * Check the validity of the provided {@link Transaction}
     *
     * <p>The following checks are made:
     * <ul>
     *   <li>Check that the transaction either uses the deprecated fields, or the new field, but not both</li>
     *   <li>Check that the transaction has a transaction body (either deprecated or non-deprecated field)</li>
     *   <li>If using {@code signedTransactionBytes}, verify that the {@link SignedTransaction} can be parsed</li>
     *   <li>Check that the {@link TransactionBody} can be parsed</li>
     *   <li>Check that the {@code transactionID} is specified</li>
     *   <li>Check that the {@code transactionID} has an accountID that is plausible, meaning that it may exist.</li>
     *   <li>Check that the {@code transactionID} does not have the "scheduled" flag set</li>
     *   <li>Check that the {@code transactionID} does not have a nonce set</li>
     *   <li>Check that this transaction is still live (i.e. its timestamp is within the last 3 minutes).</li>
     *   <li>Check that the {@code memo} is not too large</li>
     *   <li>Check that the {@code transaction fee} is non-zero</li>
     * </ul>
     *
     * <p>In all cases involving parsing, parse <strong>strictly</strong>, meaning, if there are any fields in the
     * protobuf that we do not understand, then throw a {@link PreCheckException}. This means that we are *NOT*
     * forward compatible. You cannot send a protobuf encoded object to any of the workflows that is newer than the
     * version of software that is running.
     *
     * <p>As can be seen from the above list, these checks are verifying that the transaction is internally consistent,
     * rather than comparing with state, OTHER THAN deduplication. The account on the transaction may not actually
     * exist, or may not have enough balance, or the transaction may not have paid enough to cover the fees, or many
     * other scenarios. Those will be checked in later stages of the workflow (and in many cases, within the service
     * modules themselves).</p>
     *
     * @param tx the {@link Transaction} that needs to be checked
     * @return an {@link TransactionInfo} with the parsed and checked entities
     * @throws PreCheckException if the data is not valid
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionInfo check(@NonNull final Transaction tx) throws PreCheckException {

        // NOTE: Since we've already parsed the transaction, we assume that the transaction was not too many
        // bytes. This is a safe assumption because the code that receives the transaction bytes and parses
        // the transaction also verifies that the transaction is not too large.

        // 1. Validate that either the transaction is using deprecated fields, or not, but not both
        checkTransactionDeprecation(tx);

        // 2. Get the transaction body and the signature map
        final Bytes bodyBytes;
        final SignatureMap signatureMap;
        if (tx.signedTransactionBytes().length() > 0) {
            // 2a. Parse and validate the signed transaction (if available). Throws PreCheckException if not parsable.
            final var signedTransaction = parseStrict(
                    tx.signedTransactionBytes().toReadableSequentialData(),
                    SignedTransaction.PROTOBUF,
                    INVALID_TRANSACTION);
            bodyBytes = signedTransaction.bodyBytes();
            signatureMap = signedTransaction.sigMap();
        } else {
            // 2b. Use the deprecated fields instead
            bodyBytes = tx.bodyBytes();
            signatureMap = tx.sigMap();
        }

        // 2b. There has to be a signature map. Every transaction has at least one signature for the payer.
        if (signatureMap == null) {
            throw new PreCheckException(INVALID_TRANSACTION_BODY);
        }

        // 3. Parse and validate TransactionBody
        final var txBody =
                parseStrict(bodyBytes.toReadableSequentialData(), TransactionBody.PROTOBUF, INVALID_TRANSACTION_BODY);
        checkTransactionBody(txBody);

        // 4. Return TransactionInfo
        try {
            final var functionality = HapiUtils.functionOf(txBody);
            return new TransactionInfo(tx, txBody, signatureMap, bodyBytes, functionality);
        } catch (UnknownHederaFunctionality e) {
            throw new PreCheckException(INVALID_TRANSACTION_BODY);
        }
    }

    /**
     * Verifies that a transaction is either using deprecated fields, or not, but not both. If it is using
     * deprecated fields, then increment associated metrics.
     *
     * @param tx the {@code Transaction}
     * @throws PreCheckException If the transaction is using both deprecated and non-deprecated fields
     * @throws NullPointerException if {@code tx} is {@code null}
     */
    private void checkTransactionDeprecation(@NonNull final Transaction tx) throws PreCheckException {
        // There are three ways a transaction can be used. Two of these are deprecated. One is not supported:
        //   1. body & sigs. DEPRECATED, NOT SUPPORTED
        //   2. sigMap & bodyBytes. DEPRECATED, SUPPORTED
        //   3. signedTransactionBytes. SUPPORTED
        //
        // While #1 above is NOT SUPPORTED, we also don't throw an error if either or both field is used
        // as long as the transaction ALSO has either #2 or #3 populated. This seems really odd, and ideally
        // we would be able to remove support for #1 entirely. To do this, we need metrics to see if anyone
        // is using #1 in any way.
        if (tx.hasBody() || tx.hasSigs()) {
            superDeprecatedCounter.increment();
        }

        // A transaction can either use signedTransactionBytes, or sigMap and bodyBytes. Using
        // sigMap and bodyBytes is deprecated.
        final var hasSignedTxnBytes = tx.signedTransactionBytes().length() > 0;
        final var hasDeprecatedSigMap = tx.sigMap() != null;
        final var hasDeprecatedBodyBytes = tx.bodyBytes().length() > 0;

        // Increment the counter if either of `bodyBytes` or `sigMap` were used
        if (hasDeprecatedSigMap || hasDeprecatedBodyBytes) {
            deprecatedCounter.increment();
        }

        // The user either has to use `signedTransactionBytes`, or `bodyBytes` and `sigMap`, but not both.
        if (hasSignedTxnBytes) {
            if (hasDeprecatedBodyBytes || hasDeprecatedSigMap) {
                throw new PreCheckException(INVALID_TRANSACTION);
            }
        } else if (!hasDeprecatedBodyBytes) {
            // If they didn't use `signedTransactionBytes` and they didn't use `bodyBytes` then they didn't send a body
            // NOTE: If they sent a `sigMap` without a `bodyBytes`, then the `sigMap` will be ignored, just like
            // `body` and `sigs` are. This isn't really nice but not fatal.
            throw new PreCheckException(INVALID_TRANSACTION_BODY);
        }
    }

    /**
     * Validates a {@link TransactionBody}
     *
     * @param txBody the {@link TransactionBody} to check
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    private void checkTransactionBody(@NonNull final TransactionBody txBody) throws PreCheckException {
        // The transaction MUST have been sent to *this* node
        if (!nodeAccount.equals(txBody.nodeAccountID())) {
            throw new PreCheckException(INVALID_NODE_ACCOUNT);
        }

        checkTransactionID(txBody.transactionID());
        checkMemo(txBody.memo());

        // You cannot have a negative transaction fee!! We're not paying you, buddy.
        if (txBody.transactionFee() < 0) {
            throw new PreCheckException(INSUFFICIENT_TX_FEE);
        }

        checkTimeBox(
                txBody.transactionID().transactionValidStart(),
                txBody.transactionValidDurationOrElse(Duration.DEFAULT));
    }

    /**
     * Validates a {@link TransactionID}
     *
     * @param txnId the {@link TransactionID} to check
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    private void checkTransactionID(@Nullable final TransactionID txnId) throws PreCheckException {
        if (txnId == null) {
            throw new PreCheckException(INVALID_TRANSACTION_ID);
        }

        // Determines whether the given {@link AccountID} can possibly be valid. This method does not refer to state,
        // it simply looks at the {@code accountID} itself to determine whether it might be valid. An ID is valid if
        // the shard and realm match the shard and realm of this node, AND if the account number is positive or if
        // the alias is set.
        final var accountID = txnId.accountID();
        final var isPlausibleAccount = accountID != null
                && accountID.shardNum() == nodeAccount.shardNum()
                && accountID.realmNum() == nodeAccount.realmNum()
                && ((accountID.hasAccountNum() && accountID.accountNumOrElse(0L) > 0) || (accountID.hasAlias()));

        if (!isPlausibleAccount) {
            throw new PreCheckException(PAYER_ACCOUNT_NOT_FOUND);
        }

        if (txnId.scheduled() || txnId.nonce() != USER_TRANSACTION_NONCE) {
            throw new PreCheckException(TRANSACTION_ID_FIELD_NOT_ALLOWED);
        }
    }

    /**
     * Checks whether the memo passes checks.
     *
     * @param memo The memo to check.
     * @throws PreCheckException if the memo is too long, or otherwise fails the check.
     */
    private void checkMemo(@Nullable final String memo) throws PreCheckException {
        if (memo == null) return; // Nothing to do, a null memo is valid.
        // Verify the number of bytes does not exceed the maximum allowed.
        // Note that these bytes are counted in UTF-8.
        final var buffer = memo.getBytes(StandardCharsets.UTF_8);
        if (buffer.length > props.maxMemoUtf8Bytes()) {
            throw new PreCheckException(MEMO_TOO_LONG);
        }
        // FIXME: This check should be removed after mirror node supports 0x00 in memo fields
        for (final byte b : buffer) {
            if (b == 0) {
                throw new PreCheckException(INVALID_ZERO_BYTE_IN_STRING);
            }
        }
    }

    /**
     * Checks whether the transaction duration is valid as per the configuration for valid durations
     * for the network, and whether the current node wall-clock time falls between the transaction
     * start and the transaction end (transaction start + duration).
     *
     * @param start The start time of the transaction, in some notional "real" wall clock time.
     * @param duration The duration of time for which the transaction is valid. Note that the user may
     *                 select a duration that is <strong>shorter</strong> than the network's configuration
     *                 for max duration, but cannot exceed it, as long as it is not shorter than the network's
     *                 configuration for min duration.
     * @throws PreCheckException if the transaction duration is invalid, or if the start time is too old, or in the future.
     */
    private void checkTimeBox(final Timestamp start, final Duration duration) throws PreCheckException {
        // The transaction duration must not be longer than the configured maximum transaction duration
        // or less than the configured minimum transaction duration.
        final var validForSecs = duration.seconds();
        if (validForSecs < props.minTxnDuration() || validForSecs > props.maxTxnDuration()) {
            throw new PreCheckException(INVALID_TRANSACTION_DURATION);
        }

        final var validStart = toInstant(start);
        final var validDuration = toSecondsDuration(validForSecs, validStart);
        final var currentTime = Instant.now(Clock.systemUTC());
        if (validStart.plusSeconds(validDuration).isBefore(currentTime)) {
            throw new PreCheckException(TRANSACTION_EXPIRED);
        }
        if (!validStart.isBefore(currentTime)) {
            throw new PreCheckException(INVALID_TRANSACTION_START);
        }
    }

    /**
     * This method converts a {@link Timestamp} to an {@link Instant} limited between {@link Instant#MIN} and
     * {@link Instant#MAX}
     *
     * @param timestamp the {@code Timestamp} that should be converted
     * @return the resulting {@code Instant}
     */
    private Instant toInstant(final Timestamp timestamp) {
        return Instant.ofEpochSecond(
                clamp(timestamp.seconds(), Instant.MIN.getEpochSecond(), Instant.MAX.getEpochSecond()),
                clamp(timestamp.nanos(), Instant.MIN.getNano(), Instant.MAX.getNano()));
    }

    /**
     * This method calculates the valid duration given in seconds, which is the provided number of seconds minus a
     * buffer defined in {@link GlobalDynamicProperties}. The result is limited to a value that, if added to the
     * {@code validStart}, will not exceed {@link Instant#MAX}.
     *
     * @param validForSecs the duration in seconds
     * @param validStart the {@link Instant} that is used to calculate the maximum
     * @return the valid duration given in seconds
     */
    private long toSecondsDuration(final long validForSecs, final Instant validStart) {
        return Math.min(
                validForSecs - props.minValidityBuffer(), Instant.MAX.getEpochSecond() - validStart.getEpochSecond());
    }

    /** A simple utility method replaced in Java 21 with {@code Math.clamp(long, long long)} */
    private long clamp(final long value, final long min, final long max) {
        return Math.min(Math.max(value, min), max);
    }

    /**
     * A utility method for strictly parsing a protobuf message, throwing {@link PreCheckException} if the message
     * is malformed or contains unknown fields.
     *
     * @param data The protobuf data to parse.
     * @param codec The codec to use for parsing
     * @param parseErrorCode The error code to use if the data is malformed or contains unknown fields.
     * @param <T> The type of the message to parseStrict.
     * @return The parsed message.
     * @throws PreCheckException if the data is malformed or contains unknown fields.
     */
    private <T extends Record> T parseStrict(
            @NonNull ReadableSequentialData data, Codec<T> codec, ResponseCodeEnum parseErrorCode)
            throws PreCheckException {
        try {
            return codec.parseStrict(data);
        } catch (MalformedProtobufException e) {
            // We could not parseStrict the protobuf because it was not valid protobuf
            throw new PreCheckException(parseErrorCode);
        } catch (UnknownFieldException e) {
            // We do not allow newer clients to send transactions to older networks.
            throw new PreCheckException(TRANSACTION_HAS_UNKNOWN_FIELDS);
        } catch (IOException e) {
            // This should technically not be possible. The data buffer supplied
            // is either based on a byte[] or a byte buffer, in both cases all data
            // is available and a generic IO exception shouldn't happen. If it does,
            // it indicates the data could not be parsed, but for a reason other than
            // those causing an MalformedProtobufException or UnknownFieldException.
            logger.warn("Unexpected IO exception while parsing protobuf", e);
            throw new PreCheckException(parseErrorCode);
        }
    }
}
