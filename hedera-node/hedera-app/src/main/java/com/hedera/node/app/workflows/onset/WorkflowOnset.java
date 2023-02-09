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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.service.mono.exceptions.UnknownHederaFunctionality;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class does some pre-processing before each workflow. It parses the provided {@link
 * ByteBuffer} and checks it.
 *
 * <p>This is used in every workflow that deals with transactions, i.e. in all workflows except the
 * query workflow. And even in the query workflow, it is used when dealing with the contained {@link
 * com.hederahashgraph.api.proto.java.CryptoTransfer}.
 */
@Singleton
public class WorkflowOnset {

    private final OnsetChecker checker;

    /**
     * Constructor of {@code WorkflowOnset}
     *
     * @param checker the {@link OnsetChecker}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public WorkflowOnset(@NonNull final OnsetChecker checker) {
        this.checker = requireNonNull(checker);
    }

    /**
     * Parse the given {@link ByteBuffer} and check its validity.
     *
     * <p>The checks are very general: syntax checks, size limit checks, and some general semantic
     * checks that apply to all transactions (e.g. does the transaction have a payer, are the
     * timestamps valid).
     *
     * @param ctx the {@link SessionContext}
     * @param buffer the {@code ByteBuffer} with the serialized transaction
     * @return an {@link OnsetResult} with the parsed and checked entities
     * @throws PreCheckException if the data is not valid
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public OnsetResult parseAndCheck(
            @NonNull final SessionContext ctx, @NonNull final ByteBuffer buffer)
            throws PreCheckException {
        requireNonNull(ctx);
        requireNonNull(buffer);

        return doParseAndCheck(ctx, () -> ctx.txParser().parseFrom(buffer));
    }

    /**
     * Parse the given {@link ByteBuffer} and check its validity
     *
     * <p>The checks are very general: syntax checks, size limit checks, and some general semantic
     * checks that apply to all transactions (e.g. does the transaction have a payer, are the
     * timestamps valid).
     *
     * @param ctx the {@link SessionContext}
     * @param buffer the {@code ByteBuffer} with the serialized transaction
     * @return an {@link OnsetResult} with the parsed and checked entities
     * @throws PreCheckException if the data is not valid
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public OnsetResult parseAndCheck(
            @NonNull final SessionContext ctx, @NonNull final byte[] buffer)
            throws PreCheckException {
        requireNonNull(ctx);
        requireNonNull(buffer);

        return doParseAndCheck(ctx, () -> ctx.txParser().parseFrom(buffer));
    }

    /**
     * Check the validity of the provided {@link Transaction}
     *
     * <p>The checks are very general: syntax checks, size limit checks, and some general semantic
     * checks that apply to all transactions (e.g. does the transaction have a payer, are the
     * timestamps valid).
     *
     * @param ctx the {@link SessionContext}
     * @param transaction the {@link Transaction} that needs to be checked
     * @return an {@link OnsetResult} with the parsed and checked entities
     * @throws PreCheckException if the data is not valid
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public OnsetResult doParseAndCheck(
            @NonNull final SessionContext ctx, @NonNull final Transaction transaction)
            throws PreCheckException {
        requireNonNull(ctx);
        requireNonNull(transaction);

        return doParseAndCheck(ctx, () -> transaction);
    }

    @SuppressWarnings("deprecation")
    private OnsetResult doParseAndCheck(
            @NonNull final SessionContext ctx, @NonNull final TransactionSupplier txSupplier)
            throws PreCheckException {

        // 1. Parse the transaction object
        final Transaction tx;
        try {
            tx = txSupplier.supply();
        } catch (InvalidProtocolBufferException e) {
            throw new PreCheckException(INVALID_TRANSACTION);
        }

        checker.checkTransaction(tx);

        // 2. Parse and validate the signed transaction (if available)
        final ByteString bodyBytes;
        final SignatureMap signatureMap;
        if (!tx.getSignedTransactionBytes().isEmpty()) {
            final SignedTransaction signedTransaction =
                    parse(ctx.signedParser(), tx.getSignedTransactionBytes(), INVALID_TRANSACTION);
            checker.checkSignedTransaction(signedTransaction);
            bodyBytes = signedTransaction.getBodyBytes();
            signatureMap = signedTransaction.getSigMap();
        } else {
            bodyBytes = tx.getBodyBytes();
            signatureMap = tx.getSigMap();
        }

        // 3. Parse and validate TransactionBody
        final TransactionBody txBody =
                parse(ctx.txBodyParser(), bodyBytes, INVALID_TRANSACTION_BODY);
        var errorCode = checker.checkTransactionBody(txBody);

        // 4. Get HederaFunctionality
        var functionality = HederaFunctionality.UNRECOGNIZED;
        try {
            functionality = MiscUtils.functionOf(txBody);
        } catch (UnknownHederaFunctionality e) {
            if (errorCode == ResponseCodeEnum.OK) {
                errorCode = INVALID_TRANSACTION_BODY;
            }
        }

        // 4. return TransactionBody
        return new OnsetResult(
                txBody, bodyBytes.toByteArray(), errorCode, signatureMap, functionality);
    }

    @FunctionalInterface
    public interface TransactionSupplier {
        Transaction supply() throws InvalidProtocolBufferException;
    }

    private static <T> T parse(
            @NonNull final Parser<T> parser,
            @NonNull final ByteString buffer,
            @NonNull final ResponseCodeEnum errorCode)
            throws PreCheckException {
        try {
            return parser.parseFrom(buffer);
        } catch (InvalidProtocolBufferException e) {
            throw new PreCheckException(errorCode);
        }
    }
}
