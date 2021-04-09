package com.hedera.services.state.forensics;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.swirlds.common.NodeId;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class FcmDumpTest {
	long selfId = 1, round = 1_234_567;
	NodeId self = new NodeId(false, selfId);

	@Mock
	ServicesState state;
	@Mock
	MerkleDataOutputStream out;
	@Mock
	Function<String, MerkleDataOutputStream> merkleOutFn;
	@Mock
	FCMap<MerkleEntityId, MerkleAccount> accounts;
	@Mock
	FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage;
	@Mock
	FCMap<MerkleEntityId, MerkleTopic> topics;
	@Mock
	FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations;
	@Mock
	FCMap<MerkleEntityId, MerkleSchedule> scheduleTxs;

	private LogCaptor logCaptor;

	@LoggingSubject
	private FcmDump subject = new FcmDump();

	@Test
	void dumpsAllFcms() throws IOException {
		// setup:
		FcmDump.merkleOutFn = merkleOutFn;

		given(merkleOutFn.apply(any())).willReturn(out);
		// and:
		given(state.accounts()).willReturn(accounts);
		given(state.storage()).willReturn(storage);
		given(state.topics()).willReturn(topics);
		given(state.tokens()).willReturn(tokens);
		given(state.tokenAssociations()).willReturn(tokenAssociations);
		given(state.scheduleTxs()).willReturn(scheduleTxs);
//		// and:
//		willThrow(IOException.class).given(out).writeMerkleTree(scheduleTxs);

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
		FcmDump.merkleOutFn = merkleOutFn;

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
	public void merkleSupplierWorks() {
		// given:
		var okPath = "src/test/resources/tmp.nothing";

		// when:
		var fout = FcmDump.merkleOutFn.apply(okPath);
		// and:
		assertDoesNotThrow(() -> fout.writeUTF("Here is something"));

		// cleanup:
		(new File(okPath)).delete();
	}

	@Test
	public void merkleSupplierFnDoesntBlowUp() {
		// given:
		var badPath = "/impermissible/path";

		// then:
		assertDoesNotThrow(() -> FcmDump.merkleOutFn.apply(badPath));
	}
}