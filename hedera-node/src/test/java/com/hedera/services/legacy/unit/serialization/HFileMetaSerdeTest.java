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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

import com.hedera.services.files.HFileMetaSerde;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.DecoderException;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.files.HFileMeta;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static com.hedera.services.files.HFileMetaSerde.MAX_CONCEIVABLE_MEMO_UTF8_BYTES;
import static com.hedera.services.files.HFileMetaSerde.MEMO_VERSION;
import static com.hedera.services.files.HFileMetaSerde.deserialize;
import static com.hedera.services.files.HFileMetaSerde.streamContentDiscovery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.BDDMockito.*;

public class HFileMetaSerdeTest {
	private long expiry = 1_234_567L;
	private JKey wacl = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asJKeyUnchecked().getKeyList();
	private String memo = "Remember me?";
	private boolean deleted = true;

	private static final String hexedLegacyKnown =
			"00000000000000010000000000ee994300000000000002e101000000000012d6870000000000000" +
					"0020000000000ecb1f000000000000002c00000000400000000000000020000000000ecf2ea0000" +
					"0000000000209d4eb9cb63c543274d3a15c69335d50c31c948574ef9ae146075a197680502c4000" +
					"00000000000020000000000ecd26d00000000000001380000000100000000000000020000000000" +
					"ecb1f0000000000000011c0000000200000000000000020000000000ecb1f000000000000000c80" +
					"000000200000000000000020000000000ecb1f00000000000000074000000020000000000000002" +
					"0000000000ecf2ea00000000000000204020560eb6a77e8f690eb545dfbbbaee4b516ff8454cf47" +
					"8e3935e2c6b2ff33200000000000000020000000000ecf2ea0000000000000020707d1263ca1070" +
					"2286b02ffdf2b4f6c211ab8d8fe85cde0ac14b2af17efc4a7400000000000000020000000000ecf" +
					"2ea0000000000000020e60fa77b38ed257b294f87f4a62416563530a5decea0f2054f8e86a7c463" +
					"819000000000000000020000000000ecf2ea000000000000002001ddac1c439e1e20819c854b267" +
					"ee3af3e790cd96c44ace40a89ff733501e2ec00000000000000020000000000ecf2ea0000000000" +
					"0000201ebff7723e958d7bf444b42787f5aa9962dd001b368c17cca89a6aea0aa3dae0000000000" +
					"00000020000000000ecb1f000000000000000e40000000100000000000000020000000000ecd26d" +
					"00000000000000c80000000200000000000000020000000000ecb1f000000000000000ac0000000" +
					"300000000000000020000000000ecf2ea0000000000000020ca5667c34cd53224770969525e5cfb" +
					"19c0ad42adfb1e22a981e4ebc68df3947e00000000000000020000000000ecf2ea0000000000000" +
					"020dd8858bde754f7f1f933b749e6fff61163e0a992d89936ad2718bc5b822c0e3e000000000000" +
					"00020000000000ecf2ea000000000000002047c8c60779a621d370686f7b8ae2b670e65d67864da" +
					"b067af7c4407ebeadc7b3";

	DomainSerdes serdes;
	HFileMetaSerde.StreamContentDiscovery discovery;
	Function<InputStream, SerializableDataInputStream> serInFactory;
	Function<OutputStream, SerializableDataOutputStream> serOutFactory;

	HFileMeta known;

	@BeforeEach
	void setUp() {
		known = new HFileMeta(deleted, wacl, expiry, memo);
	}

	@Test
	public void deserializesNewVersionAsExpected() throws IOException {
		// setup:
		doStaticMocking();
		// and:
		DataInputStream in = mock(DataInputStream.class);
		SerializableDataInputStream serIn = mock(SerializableDataInputStream.class);

		given(in.readLong()).willReturn(MEMO_VERSION);
		// and:
		given(serInFactory.apply(in)).willReturn(serIn);
		given(serIn.readBoolean()).willReturn(known.isDeleted());
		given(serIn.readLong()).willReturn(known.getExpiry());
		given(serIn.readNormalisedString(MAX_CONCEIVABLE_MEMO_UTF8_BYTES)).willReturn(known.getMemo());
		given(serdes.readNullable(argThat(serIn::equals), any(IoReadingFunction.class))).willReturn(known.getWacl());

		// when:
		var replica = deserialize(in);

		// then:
		assertEquals(known.toString(), replica.toString());

		// cleanup:
		undoStaticMocking();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void serializesNewVersionAsExpected() throws IOException {
		// setup:
		doStaticMocking();
		byte[] pretend = "NOPE".getBytes();
		// and:
		DataOutputStream out = mock(DataOutputStream.class);
		SerializableDataOutputStream serOut = mock(SerializableDataOutputStream.class);
		// and:
		ArgumentCaptor<JKeySerializer.StreamConsumer<DataOutputStream>> captor =
				ArgumentCaptor.forClass(JKeySerializer.StreamConsumer.class);
		InOrder inOrder = inOrder(out, serOut, serOutFactory, serdes);

		given(streamContentDiscovery.discoverFor(captor.capture())).willReturn(pretend);
		given(serOutFactory.apply(out)).willReturn(serOut);

		// when:
		var shouldBePretend = HFileMetaSerde.serialize(known);
		// and:
		var consumer = captor.getValue();
		consumer.accept(out);

		// then:
		assertSame(pretend, shouldBePretend);
		// and:
		inOrder.verify(serOutFactory).apply(out);
		inOrder.verify(serOut).writeLong(HFileMetaSerde.MEMO_VERSION);
		inOrder.verify(serOut).writeBoolean(deleted);
		inOrder.verify(serOut).writeLong(expiry);
		inOrder.verify(serOut).writeNormalisedString(memo);
		inOrder.verify(serdes).writeNullable(
				argThat(wacl::equals),
				argThat(serOut::equals),
				any(IoWritingConsumer.class));

		// cleanup:
		undoStaticMocking();
	}

	@Test
	public void legacySerdeTest() throws Exception {
		// setup:
		var fid = IdUtils.asFile("0.0.1001");

		// given:
		var expInfo = toGrpc(known, fid, 1024);
		byte[] legacyRepr = Hex.decodeHex(hexedLegacyKnown);

		// when:
		var replica = deserialize(new DataInputStream(new ByteArrayInputStream(legacyRepr)));
		// and:
		var replicaInfo = toGrpc(replica, fid, 1024);

		assertEquals(expInfo.getExpirationTime(), replicaInfo.getExpirationTime());
		assertEquals(expInfo.getDeleted(), replicaInfo.getDeleted());
		assertEquals(expInfo.getKeys().getKeysCount(), replicaInfo.getKeys().getKeysCount());
	}

	@SuppressWarnings("unchecked")
	void doStaticMocking() {
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

	void undoStaticMocking() {
		HFileMetaSerde.serdes = new DomainSerdes();
		HFileMetaSerde.serInFactory = SerializableDataInputStream::new;
		HFileMetaSerde.serOutFactory = SerializableDataOutputStream::new;
		HFileMetaSerde.streamContentDiscovery = JKeySerializer::byteStream;
	}

	public static HFileMeta convert(FileInfo fi) throws DecoderException {
		return new HFileMeta(
				fi.getDeleted(),
				JKey.mapKey(Key.newBuilder().setKeyList(fi.getKeys()).build()),
				fi.getExpirationTime().getSeconds());
	}

	public static FileInfo toGrpc(HFileMeta info, FileID fid, long size) throws Exception {
		var expiry = Timestamp.newBuilder().setSeconds(info.getExpiry()).build();

		return FileInfo.newBuilder()
				.setFileID(fid)
				.setSize(size)
				.setExpirationTime(expiry)
				.setDeleted(info.isDeleted())
				.setKeys(JKey.mapJKey(info.getWacl()).getKeyList())
				.build();
	}
}
