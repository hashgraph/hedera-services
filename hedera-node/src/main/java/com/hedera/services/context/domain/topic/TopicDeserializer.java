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
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import org.apache.commons.codec.binary.StringUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InvalidClassException;

public enum TopicDeserializer implements SerializedObjectProvider {
	TOPIC_DESERIALIZER;

	DomainSerdes serdes = new DomainSerdes();

	@Override
	@SuppressWarnings("unchecked")
	public <T extends FastCopyable> T deserialize(DataInputStream in) throws IOException {
		Topic topic = new Topic();
		FCDataInputStream fcIn = (FCDataInputStream)in;
		deserializeInto(fcIn, topic);
		return (T)topic;
	}

	public void deserializeInto(FCDataInputStream in, Topic to) throws IOException {
		var objectId = in.readShort();
		if (TopicSerializer.OBJECT_ID != objectId) {
			throw new InvalidClassException(
					"TopicDeserializer::deserializeInto() failed. Invalid object magic number " + objectId);
		}
		var version = in.readShort();
		if (1 == version) {
			deserializeVersion1(in, to);
		} else {
			throw new IOException("Invalid Topic serialized version " + version);
		}
	}

	private void deserializeVersion1(FCDataInputStream in, Topic to) throws IOException {
		to.setMemo(null);
		if (in.readBoolean()) {
			var bytes = in.readBytes();
			if (null != bytes) {
				to.setMemo(StringUtils.newStringUtf8(bytes));
			}
		}

		to.setAdminKey(in.readBoolean() ? serdes.deserializeKey(in) : null);
		to.setSubmitKey(in.readBoolean() ? serdes.deserializeKey(in) : null);
		to.setAutoRenewDurationSeconds(in.readLong());
		to.setAutoRenewAccountId(in.readBoolean() ? serdes.deserializeId(in) : null);
		to.setExpirationTimestamp(in.readBoolean() ? serdes.deserializeTimestamp(in) : null);
		to.setDeleted(in.readBoolean());
		to.setSequenceNumber(in.readLong());
		to.setRunningHash(in.readBoolean() ? in.readBytes() : null);
	}
}
