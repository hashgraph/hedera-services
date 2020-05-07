package com.hedera.services.context.primitives;

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

import static com.hedera.test.utils.IdUtils.asFile;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class StateViewTest {
	long expiry = 2_000_000L;
	byte[] data = "SOMETHING".getBytes();
	JFileInfo metadata;
	JFileInfo immutableMetadata;
	FileID target = asFile("0.0.123");

	FileGetInfoResponse.FileInfo expected;
	FileGetInfoResponse.FileInfo expectedImmutable;

	Map<FileID, byte[]> contents = mock(Map.class);
	Map<FileID, JFileInfo> attrs = mock(Map.class);

	StateView subject;

	@BeforeEach
	private void setup() throws Throwable {
		metadata = new JFileInfo(
				false,
				TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey(),
				expiry);
		immutableMetadata = new JFileInfo(
				false,
				StateView.EMPTY_WACL,
				expiry);

		expectedImmutable = FileGetInfoResponse.FileInfo.newBuilder()
				.setDeleted(false)
				.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
				.setFileID(target)
				.setSize(data.length)
				.build();
		expected = expectedImmutable.toBuilder()
				.setKeys(TxnHandlingScenario.MISC_FILE_WACL_KT.asKey().getKeyList())
				.build();

		contents = mock(Map.class);
		attrs = mock(Map.class);

		subject = new StateView(StateView.EMPTY_TOPICS, StateView.EMPTY_ACCOUNTS);
		subject.fileAttrs = attrs;
		subject.fileContents = contents;
	}

	@Test
	public void getsAttrs() {
		given(attrs.get(target)).willReturn(metadata);

		// when
		var stuff = subject.attrOf(target);

		// then:
		assertEquals(metadata.toString(), stuff.get().toString());
	}

	@Test
	public void getsContents() {
		given(contents.get(target)).willReturn(data);

		// when
		var stuff = subject.contentsOf(target);

		// then:
		assertTrue(Arrays.equals(data, stuff.get()));
	}

	@Test
	public void assemblesFileInfo() {
		given(attrs.get(target)).willReturn(metadata);
		given(contents.get(target)).willReturn(data);

		// when:
		var info = subject.infoFor(target);

		// then:
		assertTrue(info.isPresent());
		assertEquals(expected, info.get());
	}

	@Test
	public void assemblesFileInfoForImmutable() {
		given(attrs.get(target)).willReturn(immutableMetadata);
		given(contents.get(target)).willReturn(data);

		// when:
		var info = subject.infoFor(target);

		// then:
		assertTrue(info.isPresent());
		assertEquals(expectedImmutable, info.get());
	}

	@Test
	public void assemblesFileInfoForDeleted() {
		// setup:
		expected = expected.toBuilder()
				.setDeleted(true)
				.setSize(0)
				.build();
		metadata.setDeleted(true);

		given(attrs.get(target)).willReturn(metadata);

		// when:
		var info = subject.infoFor(target);

		// then:
		assertTrue(info.isPresent());
		assertEquals(expected, info.get());
	}

	@Test
	public void returnsEmptyForMissing() {
		// when:
		var info = subject.infoFor(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void returnsEmptyForMissingContent() {
		// when:
		var info = subject.contentsOf(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void returnsEmptyForMissingAttr() {
		// when:
		var info = subject.attrOf(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void returnsEmptyForTrouble() {
		given(attrs.get(any())).willThrow(IllegalArgumentException.class);

		// when:
		var info = subject.infoFor(target);

		// then:
		assertTrue(info.isEmpty());
	}
}
