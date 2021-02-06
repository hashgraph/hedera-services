package com.hedera.services.legacy.unit.serialization;

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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.time.Instant;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;
import org.junit.Test;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.legacy.core.jproto.HFileMeta;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;

import static com.hedera.services.legacy.core.jproto.HFileMetaSerde.deserialize;
import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class HFileMetaSerdeTest {
	@Test
	public void legacySerdeTest() throws Exception {
		// setup:
		var fid = IdUtils.asFile("0.0.1001");
		var wacl = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey().getKeyList();
		var origInfo = FileInfo.newBuilder()
				.setFileID(fid)
				.setSize(1024)
				.setExpirationTime(asTimestamp(Instant.ofEpochSecond(System.currentTimeMillis())))
				.setDeleted(true)
				.setKeys(wacl)
				.build();

		// given:
		byte[] origBytes = convert(origInfo).serialize();

		// when:
		var inter = deserialize(new DataInputStream(new ByteArrayInputStream(origBytes)));
		// and:
		var replicaBytes = inter.serialize();
		var replicaInfo = toGrpc(inter, fid, 1024);

		assertEquals(origInfo, replicaInfo);
		assertArrayEquals(origBytes, replicaBytes);
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
