/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.queries.file;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.FileGetContentsResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetFileContentsAnswer implements AnswerService {
    private final OptionValidator validator;

    @Inject
    public GetFileContentsAnswer(final OptionValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return COST_ANSWER == query.getFileGetContents().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return typicallyRequiresNodePayment(
                query.getFileGetContents().getHeader().getResponseType());
    }

    @Override
    public Response responseGiven(
            final Query query, @Nullable final StateView view, final ResponseCodeEnum validity, final long cost) {
        final var op = query.getFileGetContents();
        final var target = op.getFileID();

        final FileGetContentsResponse.Builder response = FileGetContentsResponse.newBuilder();
        final ResponseType type = op.getHeader().getResponseType();
        if (validity != OK) {
            response.setHeader(header(validity, type, cost));
            response.setFileContents(from(target, Optional.empty()));
        } else {
            if (type == COST_ANSWER) {
                response.setHeader(costAnswerHeader(OK, cost));
                response.setFileContents(from(target, Optional.empty()));
            } else {
                /* Include cost here to satisfy legacy regression tests. */
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setFileContents(
                        from(target, Objects.requireNonNull(view).contentsOf(target)));
            }
        }
        return Response.newBuilder().setFileGetContents(response).build();
    }

    private FileGetContentsResponse.FileContents from(final FileID id, final Optional<byte[]> contents) {
        final FileGetContentsResponse.FileContents.Builder wrapper =
                FileGetContentsResponse.FileContents.newBuilder().setFileID(id);
        contents.ifPresent(d -> wrapper.setContents(ByteString.copyFrom(d)));
        return wrapper.build();
    }

    @Override
    public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
        final var id = query.getFileGetContents().getFileID();

        return validator.queryableFileStatus(id, view);
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return FileGetContents;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getFileGetContents().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        final Transaction paymentTxn = query.getFileGetContents().getHeader().getPayment();
        return Optional.ofNullable(SignedTxnAccessor.uncheckedFrom(paymentTxn));
    }
}
