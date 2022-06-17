package com.hedera.services.state.forensics;

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

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.system.NodeId;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class FcmDumpTest {
	private static final long selfId = 1; 
	private static final long round = 1_234_567;
	private static final NodeId self = new NodeId(false, selfId);
	private static final String OK_PATH = "src/test/resources/tmp.nothing";

	@Mock
	private ServicesState state;
	@Mock
	private MerkleDataOutputStream out;
	@Mock
	private Function<String, MerkleDataOutputStream> merkleOutFn;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private VirtualMap<VirtualBlobKey, VirtualBlobValue> storage;
	@Mock
	private MerkleMap<EntityNum, MerkleTopic> topics;
	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations;
	@Mock
	private MerkleScheduledTransactions scheduleTxs;
	@Mock
	private FcmDump.DirectoryCreation directoryCreation;

	@LoggingTarget
	private LogCaptor logCaptor;

	@LoggingSubject
	private FcmDump subject = new FcmDump();

	@Test
	void dumpsAllFcms() throws IOException {
		subject.setMerkleOutFn(merkleOutFn);

		given(merkleOutFn.apply(any())).willReturn(out);
		// and:
		given(state.accounts()).willReturn(accounts);
		given(state.storage()).willReturn(storage);
		given(state.topics()).willReturn(topics);
		given(state.tokens()).willReturn(tokens);
		given(state.tokenAssociations()).willReturn(tokenAssociations);
		given(state.scheduleTxs()).willReturn(scheduleTxs);

		// when:
		subject.dumpFrom(state, self, round);

		// then:
		verify(out).writeMerkleTree(accounts);
		verify(out).writeMerkleTree(storage);
		verify(out).writeMerkleTree(topics);
		verify(out).writeMerkleTree(tokens);
		verify(out).writeMerkleTree(tokenAssociations);
		verify(out).writeMerkleTree(scheduleTxs);
		// and:
		verify(out, times(6)).close();
	}

	@Test
	void recoversToKeepTryingDumps() throws IOException {
		// setup:
		subject.setMerkleOutFn(merkleOutFn);

		given(merkleOutFn.apply(any())).willReturn(out);
		// and:
		given(state.accounts()).willReturn(accounts);
		given(state.storage()).willReturn(storage);
		given(state.topics()).willReturn(topics);
		given(state.tokens()).willReturn(tokens);
		given(state.tokenAssociations()).willReturn(tokenAssociations);
		given(state.scheduleTxs()).willReturn(scheduleTxs);
		// and:
		willThrow(IOException.class).given(out).writeMerkleTree(any());

		// when:
		subject.dumpFrom(state, self, round);

		// then:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(FcmDump.DUMP_IO_WARNING, "accounts"),
				String.format(FcmDump.DUMP_IO_WARNING, "storage"),
				String.format(FcmDump.DUMP_IO_WARNING, "topics"),
				String.format(FcmDump.DUMP_IO_WARNING, "tokens"),
				String.format(FcmDump.DUMP_IO_WARNING, "tokenAssociations"),
				String.format(FcmDump.DUMP_IO_WARNING, "scheduleTxs")));
	}

	@Test
	void merkleSupplierWorksWithOkPath() {
		// when:
		final var fout = subject.getMerkleOutFn().apply(OK_PATH);
		// and:
		assertDoesNotThrow(() -> fout.writeUTF("Here is something"));

		(new File(OK_PATH)).delete();
	}

	@Test
	void propagatesIoEUnchecked() throws IOException {
		given(directoryCreation.createDirectories(Paths.get(OK_PATH).getParent())).willThrow(IOException.class);

		subject.setDirectoryCreation(directoryCreation);

		final var actualMerkleOutFn = subject.getMerkleOutFn();
		assertThrows(UncheckedIOException.class, () -> actualMerkleOutFn.apply(OK_PATH));
	}
}
