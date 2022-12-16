/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.serdes;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKeySerializer;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

public class TopicSerde {
    public static final int MAX_MEMO_BYTES = 4_096;

    public void deserialize(final SerializableDataInputStream in, final MerkleTopic to)
            throws IOException {
        to.setMemo(IoUtils.readNullableString(in, MAX_MEMO_BYTES));
        to.setAdminKey(IoUtils.readNullable(in, JKeySerializer::deserialize));
        to.setSubmitKey(IoUtils.readNullable(in, JKeySerializer::deserialize));
        to.setAutoRenewDurationSeconds(in.readLong());
        to.setAutoRenewAccountId(IoUtils.readNullableSerializable(in));
        to.setExpirationTimestamp(IoUtils.readNullable(in, RichInstant::from));
        to.setDeleted(in.readBoolean());
        to.setSequenceNumber(in.readLong());
        to.setRunningHash(
                in.readBoolean()
                        ? in.readByteArray(MerkleTopic.RUNNING_HASH_BYTE_ARRAY_SIZE)
                        : null);
    }

    public void serialize(final MerkleTopic topic, final SerializableDataOutputStream out)
            throws IOException {
        IoUtils.writeNullableString(topic.getNullableMemo(), out);
        IoUtils.writeNullable(topic.getNullableAdminKey(), out, IoUtils::serializeKey);
        IoUtils.writeNullable(topic.getNullableSubmitKey(), out, IoUtils::serializeKey);
        out.writeLong(topic.getAutoRenewDurationSeconds());
        IoUtils.writeNullableSerializable(topic.getNullableAutoRenewAccountId(), out);
        IoUtils.writeNullable(topic.getNullableExpirationTimestamp(), out, RichInstant::serialize);
        out.writeBoolean(topic.isDeleted());
        out.writeLong(topic.getSequenceNumber());
        IoUtils.writeNullable(
                topic.getNullableRunningHash(),
                out,
                (hashOut, dout) -> dout.writeByteArray(hashOut));
    }
}
