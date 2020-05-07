package com.hedera.services.context.domain.serdes;

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

import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import com.swirlds.common.list.ListDigestException;
import com.swirlds.fcmap.fclist.FCLinkedList;
import com.swirlds.fcqueue.FCQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DomainSerdes {
	private static final Logger log = LogManager.getLogger(DomainSerdes.class);

	public JKey deserializeKey(DataInputStream in) throws IOException {
		return JKeySerializer.deserialize(in);
	}

	public void serializeKey(JKey key, DataOutputStream out) throws IOException {
		out.write(key.serialize());
	}

	@SuppressWarnings("unchecked")
	public void serializeId(JAccountID id, DataOutputStream out) throws IOException {
		FCDataOutputStream fcOut = (FCDataOutputStream)out;
		id.copyTo(fcOut);
		id.copyToExtra(fcOut);
	}

	public JTimestamp deserializeTimestamp(DataInputStream in) throws IOException {
		return JTimestamp.deserialize(in);
	}

	@SuppressWarnings("unchecked")
	public void serializeTimestamp(JTimestamp ts, DataOutputStream out) throws IOException {
		FCDataOutputStream fcOut = (FCDataOutputStream)out;
		ts.copyTo(fcOut);
		ts.copyToExtra(fcOut);
	}

	public JAccountID deserializeId(DataInputStream in) throws IOException {
		return JAccountID.deserialize(in);
	}

	@SuppressWarnings("unchecked")
	public void serializeRecords(FCQueue<JTransactionRecord> records, DataOutputStream out) throws IOException {
		FCDataOutputStream fcOut = (FCDataOutputStream)out;
		records.copyTo(fcOut);
		records.copyToExtra(fcOut);
	}

	@SuppressWarnings("unchecked")
	public void deserializeIntoRecords(DataInputStream in, FCQueue<JTransactionRecord> to) throws IOException {
		FCDataInputStream fcIn = (FCDataInputStream)in;
		to.copyFrom(fcIn);
		to.copyFromExtra(fcIn);
	}

	@SuppressWarnings("unchecked")
	public void serializeLegacyRecords(
			FCLinkedList<JTransactionRecord> records,
			DataOutputStream out
	) throws IOException {
		FCDataOutputStream fcOut = (FCDataOutputStream)out;
		records.copyTo(fcOut);
		records.copyToExtra(fcOut);
	}

	@SuppressWarnings("unchecked")
	public void deserializeIntoLegacyRecords(
			DataInputStream in,
			FCLinkedList<JTransactionRecord> to
	) throws IOException {
		FCDataInputStream fcIn = (FCDataInputStream)in;
		to.copyFrom(fcIn);
		try {
			to.copyFromExtra(fcIn);
		} catch (ListDigestException e) {
			log.warn("During data migration, an exception occurred copying from the legacy FCLL. This is expected during migration off FCLL and the list should have been read properly before the exception was thrown.", e);
		}
	}
}
