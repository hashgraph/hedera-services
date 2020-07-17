package com.hedera.services.state.forensics;
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

import com.hedera.services.ServicesMain;
import com.hedera.services.ServicesState;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.swirlds.common.AddressBook;
import com.swirlds.common.InvalidSignedStateListener;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.Event;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.File;
import java.time.Instant;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
class IssListenerTest {
	long selfId = 1, otherId = 2, round = 1_234_567, numConsEvents = 111;
	NodeId self = new NodeId(false, selfId);
	NodeId other = new NodeId(false, otherId);
	// and:
	byte[] hash = "xyz".getBytes();
	String hashHex = Hex.encodeHexString(hash);
	byte[] sig = "zyx".getBytes();
	String sigHex = Hex.encodeHexString(sig);
	// and:
	byte[] topicRootHash = "sdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfg".getBytes();
	String trHashHex = Hex.encodeHexString(topicRootHash);
	byte[] storageRootHash = "fdsafdsafdsafdsafdsafdsafdsafdsafdsafdsafdsafdsa".getBytes();
	String srHashHex = Hex.encodeHexString(storageRootHash);
	byte[] accountsRootHash = "asdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdf".getBytes();
	String acHashHex = Hex.encodeHexString(accountsRootHash);
	Instant consensusTime = Instant.now();

	FCMap topics;
	FCMap accounts;
	FCMap storage;
	Logger mockLog;
	Platform platform;
	AddressBook book;
	IssEventInfo info;
	ServicesState state;

	IssListener subject;

	@BeforeEach
	public void setup() {
		info = mock(IssEventInfo.class);
		book = mock(AddressBook.class);
		mockLog = mock(Logger.class);
		platform = mock(Platform.class);
		// and:
		accounts = mock(FCMap.class);
		storage = mock(FCMap.class);
		topics = mock(FCMap.class);
		given(accounts.getRootHash()).willReturn(new Hash(accountsRootHash));
		given(storage.getRootHash()).willReturn(new Hash(storageRootHash));
		given(topics.getRootHash()).willReturn(new Hash(topicRootHash));
		// and:
		state = mock(ServicesState.class);
		given(state.topics()).willReturn(topics);
		given(state.storage()).willReturn(storage);
		given(state.accounts()).willReturn(accounts);

		IssListener.log = mockLog;

		subject = new IssListener(info);
	}

	@AfterEach
	public void cleanup() {
		IssListener.log = LogManager.getLogger(IssListener.class);
	}

	@Test
	public void logsFallbackInfo() {
		// given:
		willThrow(IllegalStateException.class).given(info).alert(any());

		// when:
		subject.notifyError(
				platform,
				book,
				state,
				new Event[0],
				self,
				other,
				round,
				consensusTime,
				numConsEvents,
				sig,
				hash);

		// then:

		String msg = String.format(
				IssListener.ISS_FALLBACK_ERROR_MSG_PATTERN,
				round,
				String.valueOf(self),
				String.valueOf(other));
		verify(mockLog).warn((String)argThat(msg::equals), any(Exception.class));
	}

	@Test
	public void logsExpectedIssInfo() throws Exception {
		// setup:
		given(info.shouldDumpThisRound()).willReturn(true);
		// and:
		MerkleDataOutputStream accountsMerkleOut = mock(MerkleDataOutputStream.class);
		MerkleDataOutputStream storageMerkleOut = mock(MerkleDataOutputStream.class);
		MerkleDataOutputStream topicsMerkleOut = mock(MerkleDataOutputStream.class);
		// and:
		Function<String, MerkleDataOutputStream> fn =
				(Function<String, MerkleDataOutputStream>) mock(Function.class);
		subject.merkleOutFn = fn;
		// and:
		InOrder inOrder = inOrder(
				accounts, storage, topics,
				accountsMerkleOut, storageMerkleOut, topicsMerkleOut,
				info);

		// and:
		given(fn.apply(
				String.format(IssListener.FC_DUMP_LOC_TPL,
						ServicesMain.class.getName(), self.getId(), "accounts", round)))
				.willReturn(accountsMerkleOut);
		given(fn.apply(
				String.format(IssListener.FC_DUMP_LOC_TPL,
						ServicesMain.class.getName(), self.getId(), "storage", round)))
				.willReturn(storageMerkleOut);
		given(fn.apply(
				String.format(IssListener.FC_DUMP_LOC_TPL,
						ServicesMain.class.getName(), self.getId(), "topics", round)))
				.willReturn(topicsMerkleOut);

		// when:
		subject.notifyError(
				platform,
				book,
				state,
				new Event[0],
				self,
				other,
				round,
				consensusTime,
				numConsEvents,
				sig,
				hash);

		// then:
		String msg = String.format(
				IssListener.ISS_ERROR_MSG_PATTERN,
				round, selfId, otherId, sigHex, hashHex, acHashHex, srHashHex, trHashHex);
		verify(mockLog).error(msg);
		// and
		inOrder.verify(info).alert(consensusTime);
		// and:
		inOrder.verify(accountsMerkleOut).writeMerkleTree(accounts);
		inOrder.verify(accountsMerkleOut).close();
		inOrder.verify(storageMerkleOut).writeMerkleTree(storage);
		inOrder.verify(storageMerkleOut).close();
		inOrder.verify(topicsMerkleOut).writeMerkleTree(topics);
		inOrder.verify(topicsMerkleOut).close();
	}

	@Test
	public void merkleSupplierWorks() {
		// given:
		var okPath = "src/test/resources/tmp.nothing";

		// when:
		var fout = IssListener.merkleOutFn.apply(okPath);
		// and:
		assertDoesNotThrow(() -> fout.writeUTF("Here is something"));

		// cleanup:
		(new File(okPath)).delete();
	}

	@Test
	public void merkleSupplierFnDoesntBlowUp() {
		// given:
		var badPath = "this/path/does/not/exist";

		// when:
		var fout = IssListener.merkleOutFn.apply(badPath);

		// then:
		assertDoesNotThrow(() -> fout.writeUTF("Here is something"));
	}
}