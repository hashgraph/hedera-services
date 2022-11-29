package com.hedera.node.app.workflows.onset;

import com.google.protobuf.GeneratedMessageV3;
import com.hedera.node.app.workflows.common.PreCheckException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.records.RecordCache;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.binary.StringUtils;
import org.bouncycastle.util.Arrays;

import javax.annotation.Nonnull;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static com.hedera.services.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;
import static java.util.Objects.requireNonNull;

/**
 * This class preprocess transactions by parsing them and checking for syntax errors.
 */
public final class OnsetChecker {

    private final int maxSignedTxnSize;
    private final int maxProtoMessageDepth;
    private final RecordCache recordCache;
    private final AccountID nodeAccountID;
    private final GlobalDynamicProperties dynamicProperties;
    private final HapiOpCounters counters;

    /**
     * Constructor of an {@code OnsetChecker}
     *
     * @param maxSignedTxnSize the maximum transaction size
     * @param maxProtoMessageDepth the maximum message depth
     * @param recordCache the {@link RecordCache}
     * @param nodeAccountID the {@link AccountID} of the <em>node</em>
     * @param dynamicProperties the {@link GlobalDynamicProperties}
     * @param counters metrics related to workflows
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public OnsetChecker(
            final int maxSignedTxnSize,
            final int maxProtoMessageDepth,
            @Nonnull final RecordCache recordCache,
            @Nonnull final AccountID nodeAccountID,
            @Nonnull final GlobalDynamicProperties dynamicProperties,
            @Nonnull final HapiOpCounters counters) {
        this.maxSignedTxnSize = maxSignedTxnSize;
        this.maxProtoMessageDepth = maxProtoMessageDepth;
        this.recordCache = requireNonNull(recordCache);
        this.nodeAccountID = requireNonNull(nodeAccountID);
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
    public void checkTransaction(@Nonnull final Transaction tx) throws PreCheckException {
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

        if (hasTooManyLayers(tx)) {
            throw new PreCheckException(TRANSACTION_TOO_MANY_LAYERS);
        }
    }

    /**
     * Validates a {@link SignedTransaction}
     *
     * @param tx the {@code SignedTransaction} to check
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if {@code tx} is {@code null}
     */
    public void checkSignedTransaction(@Nonnull final SignedTransaction tx) throws PreCheckException {
        requireNonNull(tx);

        if (MiscUtils.hasUnknownFields(tx)) {
            throw new PreCheckException(TRANSACTION_HAS_UNKNOWN_FIELDS);
        }

        if (hasTooManyLayers(tx)) {
            throw new PreCheckException(TRANSACTION_TOO_MANY_LAYERS);
        }
    }

    /**
     * Validates a {@link TransactionBody}
     *
     * @param txBody the {@code TransactionBody} to check
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    public void checkTransactionBody(@Nonnull final TransactionBody txBody) throws PreCheckException {
        requireNonNull(txBody);

        if (MiscUtils.hasUnknownFields(txBody)) {
            throw new PreCheckException(TRANSACTION_HAS_UNKNOWN_FIELDS);
        }

        if (hasTooManyLayers(txBody)) {
            throw new PreCheckException(TRANSACTION_TOO_MANY_LAYERS);
        }

        if (!txBody.hasTransactionID()) {
            throw new PreCheckException(INVALID_TRANSACTION_ID);
        }

        var txnId = txBody.getTransactionID();
        if (txnId.getScheduled() || txnId.getNonce() != USER_TRANSACTION_NONCE) {
            throw new PreCheckException(TRANSACTION_ID_FIELD_NOT_ALLOWED);
        }

        if (recordCache.isReceiptPresent(txnId)) {
            throw new PreCheckException(DUPLICATE_TRANSACTION);
        }

        if (!isPlausibleTxnFee(txBody.getTransactionFee())) {
            throw new PreCheckException(INSUFFICIENT_TX_FEE);
        }

        if (!isPlausibleAccount(txnId.getAccountID())) {
            throw new PreCheckException(PAYER_ACCOUNT_NOT_FOUND);
        }


        if (!isThisNodeAccount(txBody.getNodeAccountID())) {
            throw new PreCheckException(INVALID_NODE_ACCOUNT);
        }

        checkMemo(txBody.getMemo());

        checkTimebox(txBody.getTransactionValidDuration(), txnId.getTransactionValidStart());
    }

    private static int protoDepthOf(final GeneratedMessageV3 msg) {
        int depth = 0;
        for (final var field : msg.getAllFields().values()) {
            if (field instanceof GeneratedMessageV3 message) {
                depth = Math.max(depth, 1 + protoDepthOf(message));
            } else if (field instanceof List<?> list) {
                for (final var item : list) {
                    depth =
                            Math.max(
                                    depth,
                                    item instanceof GeneratedMessageV3 message
                                            ? 1 + protoDepthOf(message)
                                            : 0);
                }
            }
            /* Otherwise the field is a primitive and adds no depth to the message. */
        }
        return depth;
    }

    private boolean hasTooManyLayers(final GeneratedMessageV3 msg) {
        return protoDepthOf(msg) > maxProtoMessageDepth;
    }

    private static boolean isPlausibleTxnFee(final long transactionFee) {
        return transactionFee >= 0;
    }

    private static boolean isPlausibleAccount(final AccountID accountID) {
        return accountID.getAccountNum() > 0 && accountID.getRealmNum() >= 0 && accountID.getShardNum() >= 0;
    }

    private boolean isThisNodeAccount(final AccountID accountID) {
        return Objects.equals(nodeAccountID, accountID);
    }

    private void checkMemo(final String memo) throws PreCheckException {
        final var buffer = StringUtils.getBytesUtf8(memo);
        if (buffer.length > dynamicProperties.maxMemoUtf8Bytes()) {
            throw new PreCheckException(MEMO_TOO_LONG);
        }
        if (Arrays.contains(buffer, (byte) 0)) {
            throw new PreCheckException(INVALID_ZERO_BYTE_IN_STRING);
        }
    }

    private void checkTimebox(final Duration duration, final Timestamp start) throws PreCheckException {
        final var validForSecs = duration.getSeconds();
        if (validForSecs < dynamicProperties.minTxnDuration() || validForSecs > dynamicProperties.maxTxnDuration()) {
            throw new PreCheckException(INVALID_TRANSACTION_DURATION);
        }

        final var validStart = safeguardedInstant(start);
        final var validDuration = safeguardedDuration(validForSecs, validStart);
        final var consensusTime = Instant.now(Clock.systemUTC());
        if (validStart.plusSeconds(validDuration).isBefore(consensusTime)) {
            throw new PreCheckException(TRANSACTION_EXPIRED);
        }
        if (!validStart.isBefore(consensusTime)) {
            throw new PreCheckException(INVALID_TRANSACTION_START);
        }
    }

    private static Instant safeguardedInstant(final Timestamp timestamp) {
        return Instant.ofEpochSecond(
                Math.min(
                        Math.max(Instant.MIN.getEpochSecond(), timestamp.getSeconds()),
                        Instant.MAX.getEpochSecond()),
                Math.min(Math.max(Instant.MIN.getNano(), timestamp.getNanos()), Instant.MAX.getNano()));
    }

    private long safeguardedDuration(final long validForSecs, final Instant validStart) {
        return Math.min(
                validForSecs - dynamicProperties.minValidityBuffer(),
                Instant.MAX.getEpochSecond() - validStart.getEpochSecond()
        );
    }
}
