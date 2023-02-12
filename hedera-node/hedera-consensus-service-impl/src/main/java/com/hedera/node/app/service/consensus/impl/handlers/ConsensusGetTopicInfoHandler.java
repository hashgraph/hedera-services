/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.consensus.impl.handlers;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hederahashgraph.api.proto.java.*;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.*;
import static java.util.Objects.requireNonNull;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#ConsensusGetTopicInfo}.
 */
@Singleton
public class ConsensusGetTopicInfoHandler extends PaidQueryHandler {
    private final NetworkInfo networkInfo;
    @Inject
    public ConsensusGetTopicInfoHandler(final NetworkInfo networkInfo) {
        this.networkInfo = networkInfo;
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.getConsensusGetTopicInfo().getHeader();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        final var response = ConsensusGetTopicInfoResponse.newBuilder().setHeader(header);
        return Response.newBuilder().setConsensusGetTopicInfo(response).build();
    }

    /**
     * This method is called during the query workflow. It validates the query, but does not
     * determine the response yet.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param query the {@link Query} that should be validated
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if validation fails
     */
    public ResponseCodeEnum validate(@NonNull final Query query, final ReadableTopicStore topicStore) throws PreCheckException {
        final ConsensusGetTopicInfoQuery op = query.getConsensusGetTopicInfo();
        if (op.hasTopicID()) {
            final var topicMetadata = topicStore.getTopicMetadata(op.getTopicID());
            if(topicMetadata.failed() || topicMetadata.metadata().isDeleted()) {
                return INVALID_TOPIC_ID;
            }
        }
        return OK;
    }

    /**
     * This method is called during the query workflow. It determines the requested value(s) and
     * returns the appropriate response.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param query the {@link Query} with the request
     * @param header the {@link ResponseHeader} that should be used, if the request was successful
     * @return a {@link Response} with the requested values
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public Response findResponse(@NonNull final Query query,
                                 @NonNull final ResponseHeader header,
                                 @NonNull final ReadableTopicStore topicStore) {
        final ConsensusGetTopicInfoQuery op = query.getConsensusGetTopicInfo();
        final ConsensusGetTopicInfoResponse.Builder response =
                ConsensusGetTopicInfoResponse.newBuilder();
        response.setTopicID(op.getTopicID());

        final var responseType = op.getHeader().getResponseType();
        response.setHeader(header);
        if (header.getNodeTransactionPrecheckCode() == OK) {
            if (responseType != COST_ANSWER) {
                final var optionalInfo = infoForTopic(op.getTopicID(), topicStore);
                if (optionalInfo.isPresent()) {
                    response.setTopicInfo(optionalInfo.get());
                }
            }
        }

        return Response.newBuilder().setConsensusGetTopicInfo(response).build();
    }

    @Override
    public boolean requiresNodePayment(@NonNull ResponseType responseType) {
        return responseType == ANSWER_ONLY || responseType == ANSWER_STATE_PROOF;
    }

    @Override
    public boolean needsAnswerOnlyCost(@NonNull ResponseType responseType) {
        return COST_ANSWER == responseType;
    }

    private Optional<ConsensusTopicInfo> infoForTopic(@NonNull final TopicID topicID,
                                                      @NonNull final ReadableTopicStore topicStore){
        final var metaOrFailure = topicStore.getTopicMetadata(topicID);
        if(metaOrFailure.failed()) {
            return Optional.empty();
        } else {
                final var info = ConsensusTopicInfo.newBuilder();
                final var meta = metaOrFailure.metadata();
                meta.memo().ifPresent(memo -> info.setMemo(memo));
                info.setRunningHash(ByteString.copyFrom(meta.runningHash()));
                info.setSequenceNumber(meta.sequenceNumber());
                info.setExpirationTime(meta.expirationTimestamp());
                meta.adminKey().ifPresent(key -> info.setAdminKey(asKeyUnchecked((JKey) key)));
                meta.submitKey().ifPresent(key -> info.setSubmitKey(asKeyUnchecked((JKey) key)));
                info.setAutoRenewPeriod(Duration.newBuilder().setSeconds(meta.autoRenewDurationSeconds()));
                meta.autoRenewAccountId().ifPresent(account -> info.setAutoRenewAccount(AccountID.newBuilder().setAccountNum(account)));

                info.setLedgerId(networkInfo.ledgerId());
                return Optional.of(info.build());
            }
        }
}
