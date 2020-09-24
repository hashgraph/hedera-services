package com.hedera.services.state.serdes;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleTopic;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;

public class TopicSerde {
	static DomainSerdes serdes = new DomainSerdes();

	public static int MAX_MEMO_BYTES = 4_096;

	public void deserializeV1(SerializableDataInputStream in, MerkleTopic to) throws IOException {
		to.setMemo(null);
		if (in.readBoolean()) {
			to.setMemo(in.readNormalisedString(MAX_MEMO_BYTES));
		}

		to.setAdminKey(in.readBoolean() ? serdes.deserializeKey(in) : null);
		to.setSubmitKey(in.readBoolean() ? serdes.deserializeKey(in) : null);
		to.setAutoRenewDurationSeconds(in.readLong());
		to.setAutoRenewAccountId(in.readBoolean() ? serdes.deserializeId(in) : null);
		to.setExpirationTimestamp(in.readBoolean() ? serdes.deserializeTimestamp(in) : null);
		to.setTopicDeleted(in.readBoolean());
		to.setSequenceNumber(in.readLong());
		to.setRunningHash(in.readBoolean() ? in.readByteArray(MerkleTopic.RUNNING_HASH_BYTE_ARRAY_SIZE) : null);
	}

	public void serialize(MerkleTopic merkleTopic, SerializableDataOutputStream out) throws IOException {
		if (merkleTopic.hasMemo()) {
			out.writeBoolean(true);
			out.writeNormalisedString(merkleTopic.getMemo());
		} else {
			out.writeBoolean(false);
		}

		if (merkleTopic.hasAdminKey()) {
			out.writeBoolean(true);
			serdes.serializeKey(merkleTopic.getAdminKey(), out);
		} else {
			out.writeBoolean(false);
		}

		if (merkleTopic.hasSubmitKey()) {
			out.writeBoolean(true);
			serdes.serializeKey(merkleTopic.getSubmitKey(), out);
		} else {
			out.writeBoolean(false);
		}

		out.writeLong(merkleTopic.getAutoRenewDurationSeconds());

		if (merkleTopic.hasAutoRenewAccountId()) {
			out.writeBoolean(true);
			serdes.serializeId(merkleTopic.getAutoRenewAccountId(), out);
		} else {
			out.writeBoolean(false);
		}

		if (merkleTopic.hasExpirationTimestamp()) {
			out.writeBoolean(true);
			serdes.serializeTimestamp(merkleTopic.getExpirationTimestamp(), out);
		} else {
			out.writeBoolean(false);
		}

		out.writeBoolean(merkleTopic.isTopicDeleted());
		out.writeLong(merkleTopic.getSequenceNumber());

		if (merkleTopic.hasRunningHash()) {
			out.writeBoolean(true);
			out.writeByteArray(merkleTopic.getRunningHash());
		} else {
			out.writeBoolean(false);
		}
	}
}
