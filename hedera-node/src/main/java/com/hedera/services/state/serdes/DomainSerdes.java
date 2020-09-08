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

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class DomainSerdes {
	private static final Logger log = LogManager.getLogger(DomainSerdes.class);

	public JKey deserializeKey(DataInputStream in) throws IOException {
		return JKeySerializer.deserialize(in);
	}

	public void serializeKey(JKey key, DataOutputStream out) throws IOException {
		out.write(key.serialize());
	}

	public void writeNullableInstant(RichInstant at, SerializableDataOutputStream out) throws IOException {
		writeNullable(at, out, RichInstant::serialize);
	}

	public RichInstant readNullableInstant(SerializableDataInputStream in) throws IOException {
		return readNullable(in, RichInstant::from);
	}

	public void writeNullableString(String msg, SerializableDataOutputStream out) throws IOException {
		writeNullable(msg, out, (_msg, _out) -> _out.writeNormalisedString(_msg));
	}

	public String readNullableString(SerializableDataInputStream in, int maxLen) throws IOException {
		return readNullable(in, (_in) -> _in.readNormalisedString(maxLen));
	}

	public <T> void writeNullable(
			T data,
			SerializableDataOutputStream out,
			IoWritingConsumer<T> writer
	) throws IOException {
		if (data == null) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			writer.write(data, out);
		}
	}

	public <T> T readNullable(
			SerializableDataInputStream in,
			IoReadingFunction<T> reader
	) throws IOException {
		return in.readBoolean() ? reader.read(in) : null;
	}

	public <T extends SelfSerializable> void writeNullableSerializable(
			T data,
			SerializableDataOutputStream out
	) throws IOException {
		if (data == null) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			out.writeSerializable(data, true);
		}
	}

	public <T extends SelfSerializable> T readNullableSerializable(
			SerializableDataInputStream in
	) throws IOException {
		return in.readBoolean() ? in.readSerializable() : null;
	}

	@SuppressWarnings("unchecked")
	public void serializeId(EntityId id, DataOutputStream _out) throws IOException {
		var out = (SerializableDataOutputStream) _out;
		out.writeSerializable(id, true);
	}

	public RichInstant deserializeLegacyTimestamp(DataInputStream in) throws IOException {
		in.readLong();
		in.readLong();
		return new RichInstant(in.readLong(), in.readInt());
	}

	public RichInstant deserializeTimestamp(DataInputStream in) throws IOException {
		return RichInstant.from((SerializableDataInputStream)in);
	}

	@SuppressWarnings("unchecked")
	public void serializeTimestamp(RichInstant ts, DataOutputStream out) throws IOException {
		ts.serialize((SerializableDataOutputStream)out);
	}

	public EntityId deserializeId(DataInputStream _in) throws IOException {
		var in = (SerializableDataInputStream)_in;
		return in.readSerializable();
	}

	@Deprecated
	@SuppressWarnings("unchecked")
	public void deserializeIntoRecords(DataInputStream din, FCQueue<ExpirableTxnRecord> to) throws IOException {
		SerializableDataInputStream in = (SerializableDataInputStream)din;
		to.copyFrom(in);
		to.copyFromExtra(in);
	}
}
