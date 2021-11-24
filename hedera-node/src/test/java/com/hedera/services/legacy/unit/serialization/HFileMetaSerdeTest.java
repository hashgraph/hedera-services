package com.hedera.services.legacy.unit.serialization;

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

import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HFileMetaSerde;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

import static com.hedera.services.files.HFileMetaSerde.MAX_CONCEIVABLE_MEMO_UTF8_BYTES;
import static com.hedera.services.files.HFileMetaSerde.MEMO_VERSION;
import static com.hedera.services.files.HFileMetaSerde.PRE_MEMO_VERSION;
import static com.hedera.services.files.HFileMetaSerde.deserialize;
import static com.hedera.services.files.HFileMetaSerde.streamContentDiscovery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

class HFileMetaSerdeTest {
	private static final long expiry = 1_234_567L;
	private static final JKey wacl = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asJKeyUnchecked().getKeyList();
	private static final String memo = "Remember me?";
	private static final boolean deleted = true;

	private DomainSerdes serdes;
	private HFileMetaSerde.StreamContentDiscovery discovery;
	private Function<InputStream, SerializableDataInputStream> serInFactory;
	private Function<OutputStream, SerializableDataOutputStream> serOutFactory;

	private HFileMeta known;

	@BeforeEach
	void setUp() {
		known = new HFileMeta(deleted, wacl, expiry, memo);
	}

	@Test
	void throwsOnNoLongerSupportedPreMemoVersion() throws IOException {
		final var in = mock(DataInputStream.class);
		given(in.readLong()).willReturn(PRE_MEMO_VERSION);
		assertThrows(IllegalArgumentException.class, () -> deserialize(in));
	}

	@Test
	void deserializesNewVersionAsExpected() throws IOException {
		doStaticMocking();
		final var in = mock(DataInputStream.class);
		final var serIn = mock(SerializableDataInputStream.class);
		given(in.readLong()).willReturn(MEMO_VERSION);
		given(serInFactory.apply(in)).willReturn(serIn);
		given(serIn.readBoolean()).willReturn(known.isDeleted());
		given(serIn.readLong()).willReturn(known.getExpiry());
		given(serIn.readNormalisedString(MAX_CONCEIVABLE_MEMO_UTF8_BYTES)).willReturn(known.getMemo());
		given(serdes.readNullable(argThat(serIn::equals), any(IoReadingFunction.class))).willReturn(known.getWacl());

		final var replica = deserialize(in);

		assertEquals(known.toString(), replica.toString());

		undoStaticMocking();
	}

	@Test
	@SuppressWarnings("unchecked")
	void serializesNewVersionAsExpected() throws IOException {
		doStaticMocking();
		final var pretend = "NOPE".getBytes();
		final var out = mock(DataOutputStream.class);
		final var serOut = mock(SerializableDataOutputStream.class);
		final var captor =
				ArgumentCaptor.forClass(JKeySerializer.StreamConsumer.class);
		final var inOrder = inOrder(out, serOut, serOutFactory, serdes);
		given(streamContentDiscovery.discoverFor(captor.capture())).willReturn(pretend);
		given(serOutFactory.apply(out)).willReturn(serOut);

		final var shouldBePretend = HFileMetaSerde.serialize(known);
		final var consumer = captor.getValue();
		consumer.accept(out);

		assertSame(pretend, shouldBePretend);
		inOrder.verify(serOutFactory).apply(out);
		inOrder.verify(serOut).writeLong(HFileMetaSerde.MEMO_VERSION);
		inOrder.verify(serOut).writeBoolean(deleted);
		inOrder.verify(serOut).writeLong(expiry);
		inOrder.verify(serOut).writeNormalisedString(memo);
		inOrder.verify(serdes).writeNullable(
				argThat(wacl::equals),
				argThat(serOut::equals),
				any(IoWritingConsumer.class));

		undoStaticMocking();
	}

	@SuppressWarnings("unchecked")
	private void doStaticMocking() {
		serdes = mock(DomainSerdes.class);
		discovery = mock(HFileMetaSerde.StreamContentDiscovery.class);
		serInFactory = mock(Function.class);
		serOutFactory = mock(Function.class);

		serdes = mock(DomainSerdes.class);
		HFileMetaSerde.serdes = serdes;
		HFileMetaSerde.serInFactory = serInFactory;
		HFileMetaSerde.serOutFactory = serOutFactory;
		HFileMetaSerde.streamContentDiscovery = discovery;
	}

	private void undoStaticMocking() {
		HFileMetaSerde.serdes = new DomainSerdes();
		HFileMetaSerde.serInFactory = SerializableDataInputStream::new;
		HFileMetaSerde.serOutFactory = SerializableDataOutputStream::new;
		HFileMetaSerde.streamContentDiscovery = JKeySerializer::byteStream;
	}

	private static final FileInfo toGrpc(final HFileMeta info, final FileID fid, final long size) throws Exception {
		final var expiry = Timestamp.newBuilder().setSeconds(info.getExpiry()).build();

		return FileInfo.newBuilder()
				.setFileID(fid)
				.setSize(size)
				.setExpirationTime(expiry)
				.setDeleted(info.isDeleted())
				.setKeys(JKey.mapJKey(info.getWacl()).getKeyList())
				.build();
	}
}
