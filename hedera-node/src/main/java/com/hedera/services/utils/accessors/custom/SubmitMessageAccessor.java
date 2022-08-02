/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.accessors.custom;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import org.jetbrains.annotations.Nullable;

/** Specialized accessor for ConsensusSubmitMessage transaction. */
public class SubmitMessageAccessor extends SignedTxnAccessor {
    private final ConsensusSubmitMessageTransactionBody body;

    public SubmitMessageAccessor(
            final byte[] signedTxnWrapperBytes, @Nullable final Transaction txn)
            throws InvalidProtocolBufferException {
        super(signedTxnWrapperBytes, txn);
        body = getTxn().getConsensusSubmitMessage();
        setSubmitUsageMeta();
    }

    @Override
    public boolean supportsPrecheck() {
        return true;
    }

    @Override
    public ResponseCodeEnum doPrecheck() {
        return OK;
    }

    public ByteString message() {
        return body.getMessage();
    }

    public TopicID topicId() {
        return body.getTopicID();
    }

    public EntityNum topicNum() {
        return EntityNum.fromTopicId(body.getTopicID());
    }

    public boolean hasChunkInfo() {
        return body.hasChunkInfo();
    }

    public ConsensusMessageChunkInfo chunkInfo() {
        return body.getChunkInfo();
    }

    private void setSubmitUsageMeta() {
        getSpanMapAccessor()
                .setSubmitMessageMeta(this, new SubmitMessageMeta(body.getMessage().size()));
    }
}
