package com.hedera.node.app.workflows.common;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.SessionContext;
import com.hedera.services.records.RecordCache;
import com.hedera.services.stats.MiscSpeedometers;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.system.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;

import java.nio.ByteBuffer;
import java.util.Objects;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static java.util.Objects.requireNonNull;

/**
 * The {@code SubmissionManager} provides functionality to submit transactions to the platform.
 */
public class SubmissionManager {

    private static final Logger LOG = LogManager.getLogger(SubmissionManager.class);

    private final Platform platform;
    private final RecordCache recordCache;
    private final MiscSpeedometers speedometers;

    /**
     * Constructor of {@code SubmissionManager}
     *
     * @param platform the {@link Platform}
     * @param recordCache the {@link RecordCache}
     * @param speedometers metrics related to submissions
     */
    public SubmissionManager(@Nonnull final Platform platform, @Nonnull final RecordCache recordCache, @Nonnull final MiscSpeedometers speedometers) {
        this.platform = requireNonNull(platform);
        this.recordCache = requireNonNull(recordCache);
        this.speedometers = requireNonNull(speedometers);
    }

    /**
     * Submit a transaction to the {@link Platform}
     *
     * @param ctx the {@link SessionContext}
     * @param txBody the {@link TransactionBody} that should be submitted to the platform
     * @param byteBuffer the {@link ByteBuffer} of the data that should be submitted
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if the transaction could not be submitted
     */
    public void submit(
            @Nonnull final SessionContext ctx,
            @Nonnull final TransactionBody txBody,
            @Nonnull final ByteBuffer byteBuffer) throws PreCheckException {
        requireNonNull(ctx);
        requireNonNull(txBody);
        requireNonNull(byteBuffer);

        final byte[] payload;
        if (txBody.hasUncheckedSubmit()) {
            try {
                payload = ctx.txParser().parseFrom(txBody.getUncheckedSubmit().getTransactionBytes()).toByteArray();
            } catch (InvalidProtocolBufferException e) {
                LOG.warn("Transaction bytes from UncheckedSubmit not a valid gRPC transaction!", e);
                throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
            }
        } else if (byteBuffer.hasArray()) {
            payload = byteBuffer.array();
        } else {
            payload = new byte[byteBuffer.limit()];
            byteBuffer.get(payload);
        }

        final var success = platform.createTransaction(payload);
        if (success) {
            recordCache.addPreConsensus(txBody.getTransactionID());
        } else {
            speedometers.cyclePlatformTxnRejections();
            throw new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED);
        }
    }
}
