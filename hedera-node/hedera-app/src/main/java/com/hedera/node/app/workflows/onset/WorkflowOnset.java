package com.hedera.node.app.workflows.onset;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.workflows.common.PreCheckException;
import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

/**
 * This class does some pre-processing before each workflow.
 * It parses the provided {@link ByteBuffer} and checks it.
 */
public class WorkflowOnset {

    private final NodeInfo nodeInfo;
    private final CurrentPlatformStatus currentPlatformStatus;
    private final OnsetChecker checker;

    /**
     * Constructor of {@code WorkflowOnset}
     *
     * @param nodeInfo the {@link NodeInfo} of the current node
     * @param currentPlatformStatus the {@link CurrentPlatformStatus}
     * @param checker the {@link OnsetChecker}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public WorkflowOnset(
            @Nonnull final NodeInfo nodeInfo,
            @Nonnull final CurrentPlatformStatus currentPlatformStatus,
            @Nonnull final OnsetChecker checker) {
        this.nodeInfo = requireNonNull(nodeInfo);
        this.currentPlatformStatus = requireNonNull(currentPlatformStatus);
        this.checker = requireNonNull(checker);
    }

    /**
     * Parse the given {@link ByteBuffer} and check its validity
     *
     * @param ctx the {@link SessionContext}
     * @param buffer the {@code ByteBuffer} with the serialized transaction
     * @return an {@link OnsetResult} with the parsed and checked entities
     * @throws PreCheckException if the data is not valid
     */
    public OnsetResult parseAndCheck(
            @Nonnull final SessionContext ctx,
            @Nonnull final ByteBuffer buffer) throws PreCheckException {

        if (nodeInfo.isSelfZeroStake()) {
            throw new PreCheckException(INVALID_NODE_ACCOUNT);
        }
        if (currentPlatformStatus.get() != ACTIVE) {
            throw new PreCheckException(PLATFORM_NOT_ACTIVE);
        }

        // 1. Parse the transaction object from the txBytes (protobuf)
        final var tx = parse(ctx.txParser(), buffer);
        checker.checkTransaction(tx);

        // 2. Parse and validate the signed transaction (if available)
        final ByteString bodyBytes;
        final SignatureMap signatureMap;
        if (!tx.getSignedTransactionBytes().isEmpty()) {
            final SignedTransaction signedTransaction = parse(ctx.signedParser(), tx.getSignedTransactionBytes(), INVALID_TRANSACTION);
            checker.checkSignedTransaction(signedTransaction);
            bodyBytes = signedTransaction.getBodyBytes();
            signatureMap = signedTransaction.getSigMap();
        } else {
            //noinspection deprecation
            bodyBytes = tx.getBodyBytes();
            //noinspection deprecation
            signatureMap = tx.getSigMap();
        }

        // 3. Parse and validate TransactionBody
        final TransactionBody txBody = parse(ctx.txBodyParser(), bodyBytes, INVALID_TRANSACTION_BODY);
        checker.checkTransactionBody(txBody);

        // 4. Get HederaFunctionality
        final HederaFunctionality functionality;
        try {
            functionality = MiscUtils.functionOf(txBody);
        } catch (UnknownHederaFunctionality e) {
            throw new PreCheckException(INVALID_TRANSACTION_BODY);
        }

        // 4. return TransactionBody
        return new OnsetResult(txBody, signatureMap, functionality);
    }

    private static Transaction parse(
            @Nonnull final Parser<Transaction> parser,
            @Nonnull final ByteBuffer buffer) throws PreCheckException {
        try {
            return parser.parseFrom(buffer);
        } catch (InvalidProtocolBufferException e) {
            throw new PreCheckException(INVALID_TRANSACTION);
        }
    }
    private static <T> T parse(
            @Nonnull final Parser<T> parser,
            @Nonnull final ByteString buffer,
            @Nonnull final ResponseCodeEnum errorCode) throws PreCheckException {
        try {
            return parser.parseFrom(buffer);
        } catch (InvalidProtocolBufferException e) {
            throw new PreCheckException(errorCode);
        }
    }
}
