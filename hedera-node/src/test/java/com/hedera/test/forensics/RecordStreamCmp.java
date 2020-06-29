package com.hedera.test.forensics;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.txns.validation.PureValidation;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.forensics.domain.PojoRecord;
import com.hedera.test.forensics.records.RecordParser;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.legacy.core.jproto.ExpirableTxnRecord;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hedera.test.forensics.records.RecordParser.TxnHistory;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;

@RunWith(JUnitPlatform.class)
public class RecordStreamCmp {
	static ObjectMapper om = new ObjectMapper();

	@Test
	public void compare() throws JsonProcessingException, InvalidProtocolBufferException {
		var CONSENSUS_DIR = "/Users/tinkerm/Dev/iss/records/record0.0.3";
		var SPLINTER_DIR = "/Users/tinkerm/Dev/iss/records/record0.0.5";

		var consensusStream = orderedStreamFrom(CONSENSUS_DIR);
		var splinterStream = orderedStreamFrom(SPLINTER_DIR);

		summarize(consensusStream, "consensus");
		summarize(splinterStream, "splinter");

		var sharedEntryPairs = presentInBoth(consensusStream, splinterStream);
		summarizeShared(sharedEntryPairs, "shared");

		long numDiffering = sharedEntryPairs.stream()
				.filter(pair -> !pair.fromConsensus().record().equals(pair.fromSplinter().record()))
				.count();
		System.out.println("Total differences: " + numDiffering);

		var firstDiffering = sharedEntryPairs.stream()
				.filter(pair -> !pair.fromConsensus().record().equals(pair.fromSplinter().record()))
				.findFirst()
				.get();

		System.out.println("\nFirst difference @ " + firstDiffering.consensusTime());
		compare(firstDiffering);
	}

	private void compare(EntryPair pair) throws JsonProcessingException, InvalidProtocolBufferException {
		var acccessor = new SignedTxnAccessor(pair.fromConsensus.history.getSignedTxn());
		System.out.println(acccessor.getSignedTxn4Log());
		System.out.println("------- CONSENSUS record -------");
		System.out.println(pair.fromConsensus.readable());
		System.out.println("------- SPLINTER record -------");
		System.out.println(pair.fromSplinter.readable());

		System.out.println("\nFor your consideration, the length of the memo field in all required charsets:");
		String memo = acccessor.getTxn().getMemo();
		var requiredCharsets = new Charset[] { ISO_8859_1, US_ASCII, UTF_16, UTF_16BE, UTF_16LE, UTF_8 };
		for (Charset charset : requiredCharsets) {
			System.out.println("In " + charset + " :: " + memo.getBytes(charset).length + " bytes");
		}
	}

	private List<EntryPair> presentInBoth(List<StreamEntry> a, List<StreamEntry> b) {
		int i = 0, j = 0, n = a.size(), m = b.size();
		List<EntryPair>	shared = new ArrayList<>();
		while (i < n && j < m) {
			var fromA = a.get(i);
			var fromB = b.get(j);
			if (fromA.consensusTime().equals(fromB.consensusTime())) {
				shared.add(new EntryPair(fromA, fromB));
				i++;
				j++;
			} else if (fromA.consensusTime().isBefore(fromB.consensusTime())) {
				i++;
			} else {
				j++;
			}
		}
		return shared;
	}

	private static class EntryPair {
		private final StreamEntry fromConsensus;
		private final StreamEntry fromSplinter;

		public EntryPair(StreamEntry fromConsensus, StreamEntry fromSplinter) {
			this.fromConsensus = fromConsensus;
			this.fromSplinter = fromSplinter;
		}

		public Instant consensusTime() {
			return fromConsensus.consensusTime();
		}

		public StreamEntry fromConsensus() {
			return fromConsensus;
		}

		public StreamEntry fromSplinter() {
			return fromSplinter;
		}
	}

	private void summarize(List<StreamEntry> stream, String tag) {
		int last = stream.size() - 1;

		System.out.println(String.format(
				"%d %s records available, (%s to %s)",
				stream.size(),
				tag,
				stream.get(0).consensusTime(),
				stream.get(last).consensusTime()));
	}

	private void summarizeShared(List<EntryPair> shared, String tag) {
		int last = shared.size() - 1;

		System.out.println(String.format(
				"%d %s records available, (%s to %s)",
				shared.size(),
				tag,
				shared.get(0).fromConsensus.consensusTime(),
				shared.get(last).fromConsensus.consensusTime()));
	}

	private List<StreamEntry> orderedStreamFrom(String dir) {
		return allRecordFilesFrom(dir)
				.stream()
				.map(RecordParser::parseFrom)
				.flatMap(rf -> rf.getTxnHistories().stream())
				.map(StreamEntry::new)
				.sorted()
				.collect(Collectors.toList());
	}

	private List<File> allRecordFilesFrom(String dir) {
		return uncheckedWalk(dir)
				.filter(path -> path.toString().endsWith(".rcd"))
				.map(Path::toString)
				.map(File::new)
				.collect(Collectors.toList());
	}

	private String basename(Path p) {
		return p.getName(p.getNameCount() - 1).toString();
	}

	private Stream<Path> uncheckedWalk(String dir) {
		try {
			return Files.walk(Path.of(dir));
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	private static class StreamEntry implements Comparable<StreamEntry> {
		private final TxnHistory history;

		public StreamEntry(TxnHistory history) {
			this.history = history;
		}

		public TxnHistory getHistory() {
			return history;
		}

		public PojoRecord pojo() {
			return PojoRecord.from(ExpirableTxnRecord.fromGprc(history.getRecord()));
		}

		public String readable() throws JsonProcessingException {
			return om.writerWithDefaultPrettyPrinter().writeValueAsString(pojo());
		}

		public Instant consensusTime() {
			return PureValidation.asCoercedInstant(history.getRecord().getConsensusTimestamp());
		}

		public TransactionRecord record() {
			return history.getRecord();
		}

		public Transaction signedTxn() {
			return history.getSignedTxn();
		}

		@Override
		public int compareTo(StreamEntry that) {
			return CANONICAL_ORDER.compare(this, that);
		}

		private static final Comparator<StreamEntry> CANONICAL_ORDER = Comparator.comparing(StreamEntry::consensusTime);
	}
}
