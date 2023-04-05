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

package com.hedera.node.app.service.consensus.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_STATE_PROOF;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.consensus.ConsensusGetTopicInfoQuery;
import com.hedera.hapi.node.consensus.ConsensusGetTopicInfoResponse;
import com.hedera.hapi.node.consensus.ConsensusTopicInfo;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONSENSUS_GET_TOPIC_INFO}.
 */
@Singleton
public class ConsensusGetTopicInfoHandler extends PaidQueryHandler {

    private final NetworkInfo networkInfo;

    @Inject
    public ConsensusGetTopicInfoHandler() {
        // TODO: Not sure how to get the network info here.
        this.networkInfo = null;
    }

    public ConsensusGetTopicInfoHandler(@NonNull final NetworkInfo networkInfo) {
        this.networkInfo = requireNonNull(networkInfo);
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.consensusGetTopicInfoOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        final var response = ConsensusGetTopicInfoResponse.newBuilder().header(header);
        return Response.newBuilder().consensusGetTopicInfo(response).build();
    }

    @Override
    public boolean requiresNodePayment(@NonNull ResponseType responseType) {
        return responseType == ANSWER_ONLY || responseType == ANSWER_STATE_PROOF;
    }

    @Override
    public boolean needsAnswerOnlyCost(@NonNull ResponseType responseType) {
        return COST_ANSWER == responseType;
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
    public ResponseCodeEnum validate(@NonNull final Query query, @NonNull final ReadableTopicStore topicStore)
            throws PreCheckException {
        final ConsensusGetTopicInfoQuery op = query.consensusGetTopicInfoOrThrow();
        if (op.hasTopicID()) {
            final var topicMetadata = topicStore.getTopicMetadata(op.topicIDOrElse(TopicID.DEFAULT));
            if (topicMetadata.failed() || topicMetadata.metadata().isDeleted()) {
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
     * @param query        the {@link Query} with the request
     * @param header       the {@link ResponseHeader} that should be used, if the request was successful
     * @return a {@link Response} with the requested values
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public Response findResponse(
            @NonNull final Query query,
            @NonNull final ResponseHeader header,
            @NonNull final ReadableTopicStore topicStore) {
        final var op = query.consensusGetTopicInfoOrThrow();
        final var response = ConsensusGetTopicInfoResponse.newBuilder();
        final var topic = op.topicIDOrElse(TopicID.DEFAULT);
        response.topicID(topic);

        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        response.header(header);
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var optionalInfo = infoForTopic(topic, topicStore);
            optionalInfo.ifPresent(response::topicInfo);
        }

        return Response.newBuilder().consensusGetTopicInfo(response).build();
    }

    /**
     * Provides information about a topic.
     * @param topicID the topic to get information about
     * @param topicStore the topic store
     * @return the information about the topic
     */
    private Optional<ConsensusTopicInfo> infoForTopic(
            @NonNull final TopicID topicID,
            @NonNull final ReadableTopicStore topicStore) {
        final var metaOrFailure = topicStore.getTopicMetadata(topicID);
        if (metaOrFailure.failed()) {
            return Optional.empty();
        } else {
            final var info = ConsensusTopicInfo.newBuilder();
            final var meta = metaOrFailure.metadata();
            meta.memo().ifPresent(info::memo);
            info.runningHash(Bytes.wrap(meta.runningHash()));
            info.sequenceNumber(meta.sequenceNumber());
            info.expirationTime(meta.expirationTimestamp());
            meta.adminKey().ifPresent(key -> info.adminKey(PbjConverter.toPbj(asKeyUnchecked((JKey) key))));
            meta.submitKey().ifPresent(key -> info.submitKey(PbjConverter.toPbj(asKeyUnchecked((JKey) key))));
            info.autoRenewPeriod(Duration.newBuilder().seconds(meta.autoRenewDurationSeconds()));
            meta.autoRenewAccountId()
                    .ifPresent(account ->
                            info.autoRenewAccount(AccountID.newBuilder().accountNum(account)));

            info.ledgerId(networkInfo.ledgerId());
            return Optional.of(info.build());
        }
    }
}
