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
import com.hedera.services.ledger.accounts.FCMapBackingAccounts;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.validation.PureValidation;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.forensics.domain.PojoRecord;
import com.hedera.test.forensics.records.RecordParser;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static com.hedera.test.forensics.records.RecordParser.TxnHistory;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Disabled
@RunWith(JUnitPlatform.class)
public class RecordStreamCmp {
	static ObjectMapper om = new ObjectMapper();

	static final String firstIssRoundAccounts = "/Users/tinkerm/Dev/iss/stable/node00-logs/" +
			"data/saved/com.hedera.services.ServicesMain/0/accounts-round38056100.fcm";
	/*
       DIVERGENT handling @ 1598023087.613967005
	 */
	final Instant DIVERGENCE = Instant.ofEpochSecond(1598023087L).plusNanos(613967005);
	final Instant LAST_BEFORE_DIVERGENCE = Instant.ofEpochSecond(1598023081L).plusNanos(150668000);

	final static long SUSPECT = 6237;

	AccountID suspect = IdUtils.asAccount("0.0.6237");
	String BASE_LOC_TPL = "/Users/tinkerm/Dev/iss/stable/records/record0.0.%s";

	@Test
	public void accountsMakingLargishQueryPaymentsToNode0() throws Exception {
		var node = "5";
		var cutoff = 100_000L;
		var allFromStream = allRecordsFrom(node);
		var accountOfInterest = IdUtils.asAccount(String.format("0.0.%d", SUSPECT));

		var largestQueryPayments = allFromStream
				.stream()
				.filter(pair -> isLargishQueryPaymentToNode(pair, 3L, cutoff))
				.collect(toList());

		System.out.println(largestQueryPayments.size() + " large-ish query payments to 0.0.3 in stream.");
		hashMapInfo("Suspect", accountOfInterest);
		hashMapInfo("Funding", IdUtils.asAccount("0.0.98"));
		hashMapInfo("Node", IdUtils.asAccount("0.0.3"));

		var possiblePayers = largestQueryPayments
				.stream()
				.map(pair -> pair.uncheckedAccessor().getPayer())
				.distinct()
				.collect(toList());
		System.out.println("There are " + possiblePayers.size() + " possible payers.");
		for (AccountID payer : possiblePayers) {
			hashMapInfo(EntityIdUtils.readableId(payer), payer);
		}
	}

	private void hashMapInfo(String desc, AccountID id) {
		System.out.println(desc + " hash: " + hashMapHash(id) + " (bin " + hashMapBinOf(id, 16) + ")");
	}

	private int hashMapBinOf(Object o, int n) {
		return (n - 1) & hashMapHash(o);
	}

	private int hashMapHash(Object o) {
		int h;
		return (o == null) ? 0 : (h = o.hashCode()) ^ (h >>> 16);
	}

	private boolean isLargishQueryPaymentToNode(StreamEntry pair, long accountNum, long cutoff) {
		var accessor = pair.uncheckedAccessor();
		if (accessor.getFunction() != CryptoTransfer) {
			return false;
		}
		var txn = accessor.getTxn();
		if (txn.getNodeAccountID().getAccountNum() != accountNum) {
			return false;
		}
		var adjusts = txn.getCryptoTransfer().getTransfers().getAccountAmountsList();
		if (adjusts.size() > 2) {
			return false;
		}
		return bigEnoughPlus(adjusts.get(0), accountNum, cutoff) || bigEnoughPlus(adjusts.get(1), accountNum, cutoff);
	}

	private boolean bigEnoughPlus(AccountAmount adjust, long to, long cutoff) {
		return adjust.getAccountID().getAccountNum() == to && adjust.getAmount() >= cutoff;
	}

	@Test
	public void dumpStreamRecordsBetweenLastTouchingSuspectAndDivergence() throws Exception {
		var node = "3";
		var allFromStream = allRecordsFrom(node);

		var betweenCheckpoints = allFromStream
				.stream()
				.filter(pair -> fallsBetween(pair, LAST_BEFORE_DIVERGENCE, DIVERGENCE))
				.peek(pair -> System.out.println(pair.consensusTime()))
				.collect(toList());

		System.out.println(betweenCheckpoints.size() +
				" transactions in stream AFTER last believed good record and BEFORE divergence");
		writeReadable(betweenCheckpoints, node, "strictlyBetweenLastGoodAndDivergence");
	}

	private boolean fallsBetween(StreamEntry pair, Instant exclusiveLeft, Instant exclusiveRight) {
		var when = pair.consensusTime();
		return exclusiveLeft.isBefore(when) && when.isBefore(exclusiveRight);
	}

	@Test
	public void comparePostIssStateRecordsToStreamRecordsBeforeDivergence() throws Exception {
		var node = "5";
		var allFromStream = allRecordsFrom(node);

		var history = new RecordHistory();
		var suspectRecords = allFromStream
				.stream()
				.filter(pair -> pair.consensusTime().isBefore(DIVERGENCE))
				.peek(history::observe)
				.filter(ignore -> history.lastChange().changeType != ChangeType.NONE)
				.collect(toList());
		System.out.println(suspectRecords.size() + " records with suspect involvement prior to divergence");
		writeReadableWithHistoryInfo(
				suspectRecords,
				String.format(BASE_LOC_TPL, node) + "/withSuspectInvolvementPriorDivergence.txt");

		var issRoundAccounts = AccountsReader.from(firstIssRoundAccounts);
		var suspectAccount = issRoundAccounts.get(new MerkleEntityId(0, 0, SUSPECT));
		int nFromState = suspectAccount.payerRecords().size();
		int nFromStream = history.suspectRecords.size();

		System.out.println(
				nFromState + " from post-ISS round state, " +
						nFromStream + " records expected from pre-divergence stream.");

		int minN = Math.min(nFromState, nFromStream);
		for (int i = 0; i < minN; i++) {
			var fromState = suspectAccount.payerRecords().poll();
			var fromStream = history.suspectRecords.poll();
			fromStream.setSubmittingMember(fromState.getSubmittingMember());
			if (!fromState.equals(fromStream)) {
				System.out.println("=== Divergence at # " + i);
				System.out.println(fromState);
				System.out.println("=========== vs stream record ===========");
				System.out.println(fromStream);
				break;
			}
		}
	}

	@Test
	public void miscIssForensics() throws Exception {
		var node = "5";
		var RECORDS_DIR = String.format(BASE_LOC_TPL, node);

		var allRecords = orderedStreamFrom(RECORDS_DIR);
		summarize(allRecords, "node " + node);

		var history = new RecordHistory();
		var suspectRecords = allRecords
				.stream()
				.peek(history::observe)
				.filter(ignore -> history.lastChange().changeType != ChangeType.NONE)
				.collect(toList());
		System.out.println(suspectRecords.size() + " records with suspect involvement");
		writeReadableWithHistoryInfo(
				suspectRecords,
				String.format(BASE_LOC_TPL, node) + "/allWithSuspectInvolvement.txt");
	}

	private boolean involving6237(StreamEntry pair) {
		try {
			return paidBy(pair.record(), suspect) || hasTransfersWith(pair.accessor(), suspect);
		} catch (InvalidProtocolBufferException e) {
			e.printStackTrace();
			throw new IllegalStateException("Record stream has invalid body bytes!");
		}
	}

	private List<StreamEntry> allRecordsFrom(String node) {
		var RECORDS_DIR = String.format(BASE_LOC_TPL, node);

		var allRecords = orderedStreamFrom(RECORDS_DIR);
		summarize(allRecords, "node " + node);
		return allRecords;
	}

	private void writeReadableWithHistoryInfo(List<StreamEntry> orderedPairs, String loc) throws IOException {
		var history = new RecordHistory();
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(loc))) {
			for (StreamEntry entry : orderedPairs) {
				history.observe(entry);
				writer.write("---\n");
				if (history.lastChange().changeType == ChangeType.PLUS_PAYER_RECORD) {
					writer.write("Added a payer record to " + SUSPECT + " (# = " + history.n() + " now)\n");
				} else if (history.lastChange().changeType == ChangeType.MINUS_EXPIRED_RECORDS) {
					writer.write("Expired " +
							history.lastChange().numExpiredRecords.getAsInt() +
							" records from " + SUSPECT + " (# = " + history.n() + " now)\n");
				} else {
					writer.write("Added a payer record and expired " +
							history.lastChange().numExpiredRecords.getAsInt() +
							" records from " + SUSPECT + " (# = " + history.n() + " now)\n");
				}
				writer.write("---\n");
				writer.write(entry.readable() + "\n");
				writer.write(entry.accessor().getSignedTxn4Log() + "\n");
			}
			writer.flush();
		}
	}

	private void writeReadable(List<StreamEntry> pairs, String node, String desc) throws IOException {
		var loc = String.format(BASE_LOC_TPL, node) + "/" + desc + ".txt";
		writeReadable(pairs, loc);
	}

	private void writeReadable(List<StreamEntry> orderedPairs, String loc) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(loc))) {
			for (StreamEntry entry : orderedPairs) {
				writer.write(entry.readable() + "\n");
				writer.write(entry.accessor().getSignedTxn4Log() + "\n");
				writer.write("---");
			}
			writer.flush();
		}
	}

	private boolean paidBy(TransactionRecord record, AccountID payer) {
		return record.getTransactionID().getAccountID().equals(payer);
	}

	private boolean hasTransfersWith(SignedTxnAccessor accessor, AccountID suspect) {
		if (accessor.getFunction() != CryptoTransfer) {
			return false;
		}
		return accessor.getTxn().getCryptoTransfer().getTransfers().getAccountAmountsList()
				.stream()
				.anyMatch(aa -> aa.getAccountID().equals(suspect));
	}

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
		var accessor = new SignedTxnAccessor(pair.fromConsensus.history.getSignedTxn());
		System.out.println(accessor.getSignedTxn4Log());
		System.out.println("------- CONSENSUS record -------");
		System.out.println(pair.fromConsensus.readable());
		System.out.println("------- SPLINTER record -------");
		System.out.println(pair.fromSplinter.readable());

		System.out.println("\nFor your consideration, the length of the memo field in all required charsets:");
		String memo = accessor.getTxn().getMemo();
		var requiredCharsets = new Charset[] { ISO_8859_1, US_ASCII, UTF_16, UTF_16BE, UTF_16LE, UTF_8 };
		for (Charset charset : requiredCharsets) {
			System.out.println("In " + charset + " :: " + memo.getBytes(charset).length + " bytes");
		}
	}

	private List<EntryPair> presentInBoth(List<StreamEntry> a, List<StreamEntry> b) {
		int i = 0, j = 0, n = a.size(), m = b.size();
		List<EntryPair> shared = new ArrayList<>();
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
				.collect(toList());
	}

	private List<File> allRecordFilesFrom(String dir) {
		return uncheckedWalk(dir)
				.filter(path -> path.toString().endsWith(".rcd"))
				.map(Path::toString)
				.map(File::new)
				.collect(toList());
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

		public long payerNum() {
			return uncheckedAccessor().getPayer().getAccountNum();
		}

		public TransactionRecord record() {
			return history.getRecord();
		}

		public Transaction signedTxn() {
			return history.getSignedTxn();
		}

		public SignedTxnAccessor accessor() throws InvalidProtocolBufferException {
			return new SignedTxnAccessor(signedTxn());
		}

		public SignedTxnAccessor uncheckedAccessor() {
			return SignedTxnAccessor.uncheckedFrom(signedTxn());
		}

		@Override
		public int compareTo(StreamEntry that) {
			return CANONICAL_ORDER.compare(this, that);
		}

		private static final Comparator<StreamEntry> CANONICAL_ORDER = Comparator.comparing(StreamEntry::consensusTime);
	}

	enum ChangeType {
		NONE, PLUS_PAYER_RECORD, MINUS_EXPIRED_RECORDS, BOTH
	}

	static class RecordHistoryChange {
		ChangeType changeType;
		OptionalInt numExpiredRecords = OptionalInt.empty();

		private static final RecordHistoryChange NONE = new RecordHistoryChange(ChangeType.NONE);
		private static final RecordHistoryChange PAYER_RECORD = new RecordHistoryChange(ChangeType.PLUS_PAYER_RECORD);

		private RecordHistoryChange(ChangeType changeType) {
			this.changeType = changeType;
		}

		public static RecordHistoryChange none() {
			return NONE;
		}

		public static RecordHistoryChange plusPayerRecord() {
			return PAYER_RECORD;
		}

		public static RecordHistoryChange minusExpiringRecords(int n) {
			var history = new RecordHistoryChange(ChangeType.MINUS_EXPIRED_RECORDS);
			history.numExpiredRecords = OptionalInt.of(n);
			return history;
		}

		public static RecordHistoryChange both(int n) {
			var history = new RecordHistoryChange(ChangeType.BOTH);
			history.numExpiredRecords = OptionalInt.of(n);
			return history;
		}
	}

	static class RecordHistory {
		RecordHistoryChange lastChange = RecordHistoryChange.none();
		Deque<ExpirableTxnRecord> suspectRecords = new ArrayDeque<>();

		public int n() {
			return suspectRecords.size();
		}

		public void observe(StreamEntry pair) {
			var at = pair.consensusTime();
			var suspectPayer = pair.payerNum() == SUSPECT;
			if (suspectPayer) {
				var expiresAt = at.getEpochSecond() + 180;
				var expiringRecord = ExpirableTxnRecord.fromGprc(pair.record());
				expiringRecord.setExpiry(expiresAt);
				suspectRecords.offer(expiringRecord);
				lastChange = RecordHistoryChange.plusPayerRecord();
			}

			int numExpired = expireRecords(at.getEpochSecond());
			if (numExpired > 0) {
				if (!suspectPayer) {
					lastChange = RecordHistoryChange.minusExpiringRecords(numExpired);
				} else {
					lastChange = RecordHistoryChange.both(numExpired);
				}
			} else {
				if (!suspectPayer) {
					lastChange = RecordHistoryChange.none();
				}
			}
		}

		public RecordHistoryChange lastChange() {
			return lastChange;
		}

		private int expireRecords(long now) {
			int n = 0;
			while (!suspectRecords.isEmpty() && suspectRecords.peek().getExpiry() <= now) {
				n++;
				suspectRecords.poll();
			}
			return n;
		}
	}

	@Test
	public void seeWhatHappens() throws InterruptedException {
		final FCMap<MerkleEntityId, MerkleAccount> accounts =
				new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
		final FCMapBackingAccounts backingAccounts = new FCMapBackingAccounts(() -> accounts);

		final AccountID txnPayer = suspect;
		final AccountID queryPayerOne = IdUtils.asAccount("0.0.23538");
		final AccountID queryPayerTwo = IdUtils.asAccount("0.0.9473");
		final SplittableRandom r = new SplittableRandom();

		backingAccounts.put(txnPayer, new MerkleAccount());
		backingAccounts.put(queryPayerOne, new MerkleAccount());
		backingAccounts.put(queryPayerTwo, new MerkleAccount());

		AccountID[] candidates = new AccountID[] { queryPayerOne, queryPayerTwo, txnPayer };
		Runnable contractCallLocal = () -> {
			while (true) {
				try {
					var queryPayer = candidates[2];
					backingAccounts.getRef(queryPayer);
				} catch (ConcurrentModificationException cme) {
					System.out.println("CME in query thread");
				}
			}
		};

		Runnable handleTxn = () -> {
			while (true) {
				try {
					var account = backingAccounts.getRef(txnPayer);
					var payerRecords = account.payerRecords();
					if (!payerRecords.isEmpty()) {
						payerRecords.poll();
					}

					var accountAgain = backingAccounts.getRef(txnPayer);
					accountAgain.payerRecords().offer(new ExpirableTxnRecord());

					backingAccounts.flushMutableRefs();
				} catch (ConcurrentModificationException cme) {
					System.out.println("CME in handle thread");
				}
			}
		};

		var handleThread = new Thread(handleTxn);
		handleThread.setName("handleTxnSimulator");
		var queryThread = new Thread(contractCallLocal);
		queryThread.setName("contractCallLocalSimulator");
		handleThread.start();
		queryThread.start();

		handleThread.join();
	}

}
