package com.hedera.test.forensics;

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

import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.forensics.records.RecordParser;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Disabled
class RecordStreamCmp {
	static final String consRecordLoc = "/Users/tinkerm/Dev/wellllll/paired/node0";
	static final String issRecordsLoc = "/Users/tinkerm/Dev/wellllll/paired/node3";

	@Test
	void compareConsAndIssRecords() throws ConstructableRegistryException {
		// setup:
		registerConstructables();

		final var fromCons = orderedRecordFilesFrom(consRecordLoc);
		final var fromIss = orderedRecordFilesFrom(issRecordsLoc);
		final var n = fromIss.size();
		System.out.println(" -> "
				+ fromCons.size() + " record files from node0, "
				+ fromIss.size() + " record files from node3 (using " + n + ")");

//		compareNames(fromCons, fromIss, n);
		final var consRecs = allRsos(fromCons);
		final var issRecs = allRsos(fromIss);
		final var m = Math.min(consRecs.size(), issRecs.size());
//		compareTimestamps(consRecs, issRecs, m);
		System.out.println(" -> "
				+ consRecs.size() + " records from node0, "
				+ issRecs.size() + " records from node3 " +
				"(using " + m + " records up to consensus time "
				+ consRecs.get(m - 1).getTimestamp() + ")");

		for (int i = 0; i < m; i++) {
			final var consRec = consRecs.get(i);
			final var issRec = issRecs.get(i);

			final var consGrpc = consRec.getTransactionRecord();
			final var issGrpc = issRec.getTransactionRecord();
			if (!consGrpc.equals(issGrpc)) {
				System.out.println(
						"!!! gRPC record @ index=" + i + " (cons time " + consRec.getTimestamp() + ") differs !!!");

				final var consTxn = accessorFor(consRec);
				System.out.println(consTxn.getTxn());
				System.out.println("-------------");

				System.out.println(consGrpc);
				System.out.println(" ---- vs ----");
				System.out.println(issGrpc);

				return;
			}
		}
	}

	private SignedTxnAccessor accessorFor(RecordStreamObject rso) {
		return SignedTxnAccessor.uncheckedFrom(rso.getTransaction());
	}

	private List<RecordStreamObject> allRsos(List<File> records) {
		final List<RecordStreamObject> ans = new ArrayList<>();
		for (var record : records) {
			ans.addAll(RecordParser.parseV5From(record));
		}
		return ans;
	}

	private void compareTimestamps(List<RecordStreamObject> consRecs, List<RecordStreamObject> issRecs, int m) {
		System.out.println("\n<<Timestamp comparisons>>");
		for (int i = 0; i < m; i++) {
			final var consRec = consRecs.get(i);
			final var issRec = issRecs.get(i);
			if (!consRec.getTimestamp().equals(issRec.getTimestamp())) {
				System.out.println("!!! Record #" + (i + 1) + " differs ("
						+ " cons @ " + consRec.getTimestamp()
						+ " iss @ " + issRec.getTimestamp());
			}
		}
	}

	private void compareNames(List<File> cons, List<File> iss, int n) {
		for (int i = 0; i < n; i++) {
			final var simpleCons = simpleName(cons.get(i));
			final var simpleIss = simpleName(iss.get(i));
			System.out.println("["
					+ (simpleCons.equals(simpleIss) ? "X" : "-")
					+ "] Cons: " + simpleCons + " - vs -  Iss: " + simpleIss);
		}
	}

	private String simpleName(File f) {
		final var p = f.getAbsolutePath();
		final var l = p.lastIndexOf("/");
		return p.substring(l + 1);
	}

	private void registerConstructables() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(Hash.class, Hash::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(RecordStreamObject.class, RecordStreamObject::new));
	}

	private List<File> orderedRecordFilesFrom(String dir) {
		return uncheckedWalk(dir)
				.filter(path -> path.toString().endsWith(".rcd"))
				.map(Path::toString)
				.map(File::new)
				.sorted(Comparator.comparing(f -> consTimeOf(f.getPath())))
				.collect(toList());
	}

	private Instant consTimeOf(String rcdFile) {
		final var s = rcdFile.lastIndexOf("/");
		final var n = rcdFile.length();
		return Instant.parse(rcdFile.substring(s + 1, n - 4).replace("_", ":"));
	}

	private Stream<Path> uncheckedWalk(String dir) {
		try {
			return Files.walk(Path.of(dir));
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}
}
