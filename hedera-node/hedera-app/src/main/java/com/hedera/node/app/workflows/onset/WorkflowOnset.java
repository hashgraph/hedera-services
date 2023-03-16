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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.asBytes;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.annotations.MaxSignedTxnSize;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.UnknownFieldException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class does some pre-processing before each workflow. It parses the provided {@link
 * RandomAccessData} and checks it.
 *
 * <p>This is used in every workflow that deals with transactions, i.e. in all workflows except the
 * query workflow. And even in the query workflow, it is used when dealing with the contained
 * {@link HederaFunctionality#CRYPTO_TRANSFER}.
 */
@Singleton
public class WorkflowOnset {

    private final OnsetChecker checker;
    private final int maxSignedTxnSize;

    /**
     * Constructor of {@code WorkflowOnset}
     *
     * @param maxSignedTxnSize the maximum transaction size
     * @param checker the {@link OnsetChecker}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public WorkflowOnset(@MaxSignedTxnSize final int maxSignedTxnSize, @NonNull final OnsetChecker checker) {
        if (maxSignedTxnSize <= 0) {
            throw new IllegalArgumentException("maxSignedTxnSize must be > 0");
        }
        this.maxSignedTxnSize = maxSignedTxnSize;

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
    public OnsetResult parseAndCheck(@NonNull final SessionContext ctx, @NonNull final RandomAccessData buffer)
            throws PreCheckException {
        requireNonNull(ctx);
        requireNonNull(buffer);

        return doParseAndCheck(ctx, buffer);
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
    public OnsetResult parseAndCheck(@NonNull final SessionContext ctx, @NonNull final byte[] buffer)
            throws PreCheckException {
        requireNonNull(ctx);
        requireNonNull(buffer);

        return doParseAndCheck(ctx, BufferedData.wrap(buffer));
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
    public OnsetResult check(@NonNull final SessionContext ctx, @NonNull final Transaction transaction)
            throws PreCheckException {
        requireNonNull(ctx);
        requireNonNull(transaction);

        final var byteStream = new ByteArrayOutputStream();

        try {
            Transaction.PROTOBUF.write(transaction, new WritableStreamingData(byteStream));
        } catch (IOException ignored) {
            throw new PreCheckException(INVALID_TRANSACTION);
        }

        return doParseAndCheck(ctx, BufferedData.wrap(byteStream.toByteArray()));
    }

    @SuppressWarnings("deprecation")
    private OnsetResult doParseAndCheck(@NonNull final SessionContext ctx, @NonNull final RandomAccessData txData)
            throws PreCheckException {

        // 0. Fail fast if there are too many transaction bytes
        if (txData.remaining() > maxSignedTxnSize) {
            throw new PreCheckException(TRANSACTION_OVERSIZE);
        }

        // 1. Parse and validate transaction object
        final Transaction tx = parse(txData, Transaction.PROTOBUF, INVALID_TRANSACTION);
        checker.checkTransaction(tx);

        // 2. Parse and validate the signed transaction (if available)
        final Bytes bodyBytes;
        final SignatureMap signatureMap;
        if (tx.signedTransactionBytes().length() > 0) {
            final SignedTransaction signedTransaction = parse(
                    BufferedData.wrap(
                            asBytes(tx.signedTransactionBytes())),
                    SignedTransaction.PROTOBUF, INVALID_TRANSACTION);
            bodyBytes = signedTransaction.bodyBytes();
            signatureMap = signedTransaction.sigMap();
        } else {
            bodyBytes = tx.bodyBytes();
            signatureMap = tx.sigMap();
        }

        // 3. Parse and validate TransactionBody (either using the body that was part of
        //    the transaction, if it was a deprecated transaction, or the body of the
        //    signedTransaction, if it was not.
        final TransactionBody txBody =
                parse(bodyBytes.toReadableSequentialData(), TransactionBody.PROTOBUF, INVALID_TRANSACTION_BODY);
        var errorCode = checker.checkTransactionBody(txBody);

        // 4. return TransactionBody
        try {
            final var functionality = HapiUtils.functionOf(txBody);
            return new OnsetResult(tx, txBody, errorCode, signatureMap, functionality);
        } catch (UnknownHederaFunctionality e) {
            throw new PreCheckException(INVALID_TRANSACTION_BODY);
        }
    }

    public static <T extends Record> T parse(
            ReadableSequentialData data,
            Codec<T> codec,
            ResponseCodeEnum parseErrorCode) throws PreCheckException {
        try {
            return codec.parseStrict(data);
        } catch (MalformedProtobufException e) {
            // We could not parse the protobuf because it was not valid protobuf
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
            throw new PreCheckException(parseErrorCode);
        }
    }
}
