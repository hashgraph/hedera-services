package com.hedera.services.fees.calculation;

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

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.primitives.StateView;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.Optional;

import static org.mockito.BDDMockito.*;
import static com.hedera.services.fees.calculation.FeeCalcUtils.*;

public class FeeCalcUtilsTest {
	public static String ARTIFACTS_PREFIX_FILE_CONTENT = "f";
	/**
	 * Default value for the prefix for the virtual metadata file
	 */
	public static String ARTIFACTS_PREFIX_FILE_INFO = "k";
	public static String LEDGER_PATH = "/{0}/";
	private final MerkleEntityId key = new MerkleEntityId(0, 0, 1234);

	public static String pathOf(FileID fid) {
		return path(ARTIFACTS_PREFIX_FILE_CONTENT, fid);
	}

	public static String pathOfMeta(FileID fid) {
		return path(ARTIFACTS_PREFIX_FILE_INFO, fid);
	}

	private static String path(String buildMarker, FileID fid) {
		return String.format(
				"%s%s%d",
				buildPath(LEDGER_PATH, "" + fid.getRealmNum()),
				buildMarker,
				fid.getFileNum());
	}

	public static String buildPath(String path, Object... params) {
		try {
			return MessageFormat.format(path, params);
		} catch (final MissingResourceException e) {
		}
		return path;
	}

	@Test
	public void returnsAccountExpiryIfAvail() {
		// setup:
		MerkleAccount account = mock(MerkleAccount.class);
		FCMap<MerkleEntityId, MerkleAccount> accounts = mock(FCMap.class);
		Timestamp expected = Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build();

		given(account.getExpiry()).willReturn(Long.MAX_VALUE);
		given(accounts.get(key)).willReturn(account);

		// when:
		Timestamp actual = lookupAccountExpiry(key, accounts);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void returnsZeroFileExpiryIfUnavail() {
		// setup:
		StateView view = mock(StateView.class);
		FileID fid = IdUtils.asFile("1.2.3");

		given(view.attrOf(fid)).willReturn(Optional.empty());

		// when:
		Timestamp actual = lookupFileExpiry(fid, view);

		// then:
		assertEquals(ZERO_EXPIRY, actual);
	}

	@Test
	public void returnsZeroAccountExpiryIfUnavail() {
		// when:
		Timestamp actual = lookupAccountExpiry(null, null);

		// then:
		assertEquals(ZERO_EXPIRY, actual);
	}

	@Test
	public void returnsFileExpiryIfAvail() throws Exception {
		// setup:
		StateView view = mock(StateView.class);
		FileID fid = IdUtils.asFile("1.2.3");
		// and:
		JKey wacl = JKey.mapKey(Key.newBuilder().setEd25519(ByteString.copyFrom("YUUP".getBytes())).build());
		HFileMeta jInfo = new HFileMeta(false, wacl, Long.MAX_VALUE);

		given(view.attrOf(fid)).willReturn(Optional.of(jInfo));

		// when:
		Timestamp actual = lookupFileExpiry(fid, view);
		// and:
		Timestamp expected = Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build();

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void constructsExpectedPath() {
		// given:
		FileID fid = IdUtils.asFile("1.2.3");
		// and:
		String expected = String.format(
				"%s%s%d",
				buildPath(LEDGER_PATH, "" + fid.getRealmNum()),
				ARTIFACTS_PREFIX_FILE_CONTENT,
				fid.getFileNum());

		// when:
		String actual = pathOf(fid);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void constructsExpectedMetaPath() {
		// given:
		FileID fid = IdUtils.asFile("1.2.3");
		// and:
		String expected = String.format(
				"%s%s%d",
				buildPath(LEDGER_PATH, "" + fid.getRealmNum()),
				ARTIFACTS_PREFIX_FILE_INFO,
				fid.getFileNum());

		// when:
		String actual = pathOfMeta(fid);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void sumsAsExpected() {
		// given:
		FeeComponents aComp = FeeComponents.newBuilder()
				.setMin(2).setMax(1_234_567)
				.setConstant(1).setBpt(2).setVpt(3).setRbh(4).setSbh(5).setGas(6).setTv(7).setBpr(8).setSbpr(9)
				.build();
		FeeComponents bComp = FeeComponents.newBuilder()
				.setMin(1).setMax(1_234_566)
				.setConstant(9).setBpt(8).setVpt(7).setRbh(6).setSbh(5).setGas(4).setTv(3).setBpr(2).setSbpr(1)
				.build();
		FeeData a = FeeData.newBuilder()
				.setNetworkdata(aComp)
				.setNodedata(aComp)
				.setServicedata(aComp)
				.build();
		FeeData b = FeeData.newBuilder()
				.setNetworkdata(bComp)
				.setNodedata(bComp)
				.setServicedata(bComp)
				.build();

		// when:
		var c = sumOfUsages(a, b);
		// and:
		var scopedUsages = new FeeComponents[] {
			c.getNodedata(), c.getNetworkdata(), c.getServicedata()
		};

		// then:
		for (FeeComponents scopedUsage : scopedUsages) {
			assertEquals(1, scopedUsage.getMin());
			assertEquals(1_234_567, scopedUsage.getMax());
			assertEquals(10, scopedUsage.getConstant());
			assertEquals(10, scopedUsage.getBpt());
			assertEquals(10, scopedUsage.getVpt());
			assertEquals(10, scopedUsage.getRbh());
			assertEquals(10, scopedUsage.getSbh());
			assertEquals(10, scopedUsage.getGas());
			assertEquals(10, scopedUsage.getTv());
			assertEquals(10, scopedUsage.getBpr());
			assertEquals(10, scopedUsage.getSbpr());
		}
	}
}
