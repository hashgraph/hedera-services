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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class GetFileInfoAnswer implements AnswerService {
    private static final Logger log = LogManager.getLogger(GetFileInfoAnswer.class);

    private final OptionValidator validator;

    @Inject
    public GetFileInfoAnswer(final OptionValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return COST_ANSWER == query.getFileGetInfo().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return typicallyRequiresNodePayment(query.getFileGetInfo().getHeader().getResponseType());
    }

    @Override
    public Response responseGiven(
            final Query query,
            @Nullable final StateView view,
            final ResponseCodeEnum validity,
            final long cost) {
        final var op = query.getFileGetInfo();
        final FileGetInfoResponse.Builder response = FileGetInfoResponse.newBuilder();

        final ResponseType type = op.getHeader().getResponseType();
        if (validity != OK) {
            log.debug(
                    "FileGetInfo not successful for: validity {}, query {} ",
                    validity,
                    query.getFileGetInfo());
            response.setHeader(header(validity, type, cost));
        } else {
            if (type == COST_ANSWER) {
                response.setHeader(costAnswerHeader(OK, cost));
            } else {
                final var info = Objects.requireNonNull(view).infoForFile(op.getFileID());
                /* Include cost here to satisfy legacy regression tests. */
                if (info.isPresent()) {
                    response.setHeader(answerOnlyHeader(OK, cost));
                    response.setFileInfo(info.get());
                } else {
                    response.setHeader(answerOnlyHeader(FAIL_INVALID));
                }
            }
        }
        return Response.newBuilder().setFileGetInfo(response).build();
    }

    @Override
    public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
        final var id = query.getFileGetInfo().getFileID();

        return validator.queryableFileStatus(id, view);
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return FileGetInfo;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        final Transaction paymentTxn = query.getFileGetInfo().getHeader().getPayment();
        return Optional.ofNullable(SignedTxnAccessor.uncheckedFrom(paymentTxn));
    }
}
