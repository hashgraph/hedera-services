package com.hedera.services.context.domain.topic;

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

import com.hedera.services.context.domain.serdes.DomainSerdes;
import com.swirlds.common.io.FCDataOutputStream;
import org.apache.commons.codec.binary.StringUtils;

import java.io.IOException;

public enum TopicSerializer {
	TOPIC_SERIALIZER;

	DomainSerdes serdes = new DomainSerdes();

	public static final short CURRENT_VERSION = 1;
	public static final short OBJECT_ID = 6112;

	public void serialize(Topic topic, FCDataOutputStream out) throws IOException {
		out.writeShort(OBJECT_ID);
		out.writeShort(CURRENT_VERSION);

		if (topic.hasMemo()) {
			out.writeBoolean(true);
			out.writeBytes(StringUtils.getBytesUtf8(topic.getMemo()));
		} else {
			out.writeBoolean(false);
		}

		if (topic.hasAdminKey()) {
			out.writeBoolean(true);
			serdes.serializeKey(topic.getAdminKey(), out);
		} else {
			out.writeBoolean(false);
		}

		if (topic.hasSubmitKey()) {
			out.writeBoolean(true);
			serdes.serializeKey(topic.getSubmitKey(), out);
		} else {
			out.writeBoolean(false);
		}

		out.writeLong(topic.getAutoRenewDurationSeconds());

		if (topic.hasAutoRenewAccountId()) {
			out.writeBoolean(true);
			serdes.serializeId(topic.getAutoRenewAccountId(), out);
		} else {
			out.writeBoolean(false);
		}

		if (topic.hasExpirationTimestamp()) {
			out.writeBoolean(true);
			serdes.serializeTimestamp(topic.getExpirationTimestamp(), out);
		} else {
			out.writeBoolean(false);
		}

		out.writeBoolean(topic.isDeleted());
		out.writeLong(topic.getSequenceNumber());

		if (topic.hasRunningHash()) {
			out.writeBoolean(true);
			out.writeBytes(topic.getRunningHash());
		} else {
			out.writeBoolean(false);
		}
	}
}
