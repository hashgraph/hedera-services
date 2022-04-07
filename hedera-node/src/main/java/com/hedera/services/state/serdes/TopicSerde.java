package com.hedera.services.state.serdes;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;

import static com.hedera.services.state.merkle.MerkleTopic.RUNNING_HASH_BYTE_ARRAY_SIZE;
import static com.hedera.services.state.serdes.IoUtils.staticReadNullable;
import static com.hedera.services.state.serdes.IoUtils.staticReadNullableSerializable;
import static com.hedera.services.state.serdes.IoUtils.staticReadNullableString;
import static com.hedera.services.state.serdes.IoUtils.staticWriteNullable;
import static com.hedera.services.state.serdes.IoUtils.staticWriteNullableString;

public class TopicSerde {
	public static final int MAX_MEMO_BYTES = 4_096;

	public void deserialize(final SerializableDataInputStream in, final MerkleTopic to) throws IOException {
		to.setMemo(staticReadNullableString(in, MAX_MEMO_BYTES));
		to.setAdminKey(staticReadNullable(in, JKeySerializer::deserialize));
		to.setSubmitKey(staticReadNullable(in, JKeySerializer::deserialize));
		to.setAutoRenewDurationSeconds(in.readLong());
		to.setAutoRenewAccountId(staticReadNullableSerializable(in));
		to.setExpirationTimestamp(staticReadNullable(in, RichInstant::from));
		to.setDeleted(in.readBoolean());
		to.setSequenceNumber(in.readLong());
		to.setRunningHash(in.readBoolean() ? in.readByteArray(RUNNING_HASH_BYTE_ARRAY_SIZE) : null);
	}

	public void serialize(final MerkleTopic topic, final SerializableDataOutputStream out) throws IOException {
		staticWriteNullableString(topic.getNullableMemo(), out);
		staticWriteNullable(topic.getNullableAdminKey(), out, IoUtils::serializeKey);
		staticWriteNullable(topic.getNullableSubmitKey(), out, IoUtils::serializeKey);
		out.writeLong(topic.getAutoRenewDurationSeconds());
		staticWriteNullable(topic.getNullableAutoRenewAccountId(), out, EntityId::serialize);
		staticWriteNullable(topic.getNullableExpirationTimestamp(), out, RichInstant::serialize);
		out.writeBoolean(topic.isDeleted());
		out.writeLong(topic.getSequenceNumber());
		staticWriteNullable(topic.getNullableRunningHash(), out, (hashOut, dout) -> dout.writeByteArray(hashOut));
	}
}
