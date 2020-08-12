package com.hedera.services.legacy.services.stats;

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

import com.hedera.services.grpc.controllers.FileController;
import com.hedera.services.grpc.controllers.NetworkController;
import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import com.swirlds.platform.StatsRunningAverage;
import com.swirlds.platform.StatsSpeedometer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.logging.log4j.Logger;

/**
 * HederaNodeStats serves as a placeholder for all statistics in HGCApp
 */
public class HederaNodeStats {
	/* ---- HCS Metric Names ---- */
	public static final String GET_TOPIC_INFO_COUNT = "getTopicInfo";
	public static final String CREATE_TOPIC_COUNT = "createTopic";
	public static final String UPDATE_TOPIC_COUNT = "updateTopic";
	public static final String DELETE_TOPIC_COUNT = "deleteTopic";
	public static final String SUBMIT_MESSAGE_COUNT = "submitMessage";

	public static final String RECEIVED_SUFFIX = "Rcv";
	public static final String SUBMITTED_SUFFIX = "Sub";
	public static final String HANDLED_SUFFIX = "Hdl";
	private static final double DEFAULT_HALF_LIFE = 10.0;
	public static final int UPDATE_PERIOD = 3000;

	private static final String THRESHOLD_RECORDS_IN_STATE = "thresholdRecInState";

	private final Logger log;

	private static final List<String> consensusQueryList = List.of(
			GET_TOPIC_INFO_COUNT
	);

	private static final List<String> consensusTransactionList = List.of(
			CREATE_TOPIC_COUNT,
			UPDATE_TOPIC_COUNT,
			DELETE_TOPIC_COUNT,
			SUBMIT_MESSAGE_COUNT
	);

	private static final List<String> cryptoTransactionsList = Arrays.asList(
			"createAccount",
			"updateAccount",
			"cryptoTransfer",
			"cryptoDelete",
			"addClaim",
			"deleteClaim"
	);
	private static final List<String> networkQueriesList = Arrays.asList(
			NetworkController.GET_VERSION_INFO_METRIC
	);
	private static final List<String> cryptoQueriesList = Arrays.asList(
			"getClaim",
			"getAccountRecords",
			"cryptoGetBalance",
			"getAccountInfo",
			"getTransactionReceipts",
			"getFastTransactionRecord",
			"getTxRecordByTxID",
			"getStakersByAccountID"
	);
	private static final List<String> fileTransactionsList = Arrays.asList(
			FileController.CREATE_FILE_METRIC,
			FileController.UPDATE_FILE_METRIC,
			FileController.DELETE_FILE_METRIC,
			FileController.APPEND_METRIC,
			FileController.FILE_SYSDEL_METRIC,
			FileController.FILE_SYSUNDEL_METRIC
	);
	private static final List<String> fileQueriesList = Arrays.asList(
			FileController.GET_FILE_CONTENT_METRIC,
			FileController.GET_FILE_INFO_METRIC
	);
	private static final List<String> smartContractTransactionsList = Arrays.asList(
			"createContract",
			"updateContract",
			"contractCallMethod",
			"deleteContract",
			"smartContractSystemDelete",
			"smartContractSystemUndelete"
	);
	private static final List<String> smartContractQueriesList = Arrays.asList(
			"getContractInfo",
			"contractCallLocalMethod",
			"ContractGetBytecode",
			"getBySolidityID",
			"getTxRecordByContractID"
	);

	private HashMap<String, AtomicLong> countStats = new HashMap<>();
	private HashMap<String, StatsSpeedometer> speedometerStats = new HashMap<>();
	private HashMap<String, Long> previousCounts = new HashMap<>();

	private StatsSpeedometer sigVerifyAsyncPerSecond;
	private StatsSpeedometer sigVerifySyncPerSecond;

	private StatsRunningAverage avgAcctLookupRetryAttempts;
	private StatsRunningAverage avgAcctRetryWaitMs;
	private StatsSpeedometer acctLookupRetriesPerSecond;

	/** size of the queue from which we take records and write to RecordStream file */
	private int recordStreamQueueSize = 0;

	private void initializeOneCountStat(String request, String requestSuffix, String descriptionSuffix,
			Platform platform) {
		countStats.put(request + requestSuffix, new AtomicLong(0));
		previousCounts.put(request + requestSuffix, 0l);
		platform.addAppStatEntry(new StatEntry(//
				"app",//
				request + requestSuffix,//
				"number of " + request + " " + descriptionSuffix,//
				"%d",//
				null,//
				null,//
				null,//
				() -> getCountStat(request, requestSuffix))
		);
	}

	private void initializeOneSpeedometerStat(String request, String requestSuffix, String descriptionSuffix,
			Platform platform) {
		final StatsSpeedometer stat = new StatsSpeedometer(DEFAULT_HALF_LIFE);
		speedometerStats.put(request + requestSuffix, stat);
		platform.addAppStatEntry(new StatEntry(//
				"app",//
				request + requestSuffix + "/sec",//
				"number of " + request + " " + descriptionSuffix + " per second",//
				"%,13.2f",//
				stat,//
				(h) -> {
					stat.reset(h);
					return stat;
				},//
				stat::reset,//
				() -> getSpeedometerStat(request, requestSuffix))
		);
	}

	private void initializeInternalStats(List<String> requestsList, Platform platform) {
		for (String request : requestsList) {
			initializeOneCountStat(request, RECEIVED_SUFFIX, "received", platform);
			initializeOneCountStat(request, SUBMITTED_SUFFIX, "submitted", platform);
			initializeOneSpeedometerStat(request, RECEIVED_SUFFIX, "received", platform);
			initializeOneSpeedometerStat(request, SUBMITTED_SUFFIX, "submitted", platform);
		}
	}

	private void initializeInternalStatsForTransactions(List<String> transactionsList, Platform platform) {
		initializeInternalStats(transactionsList, platform);
		for (String transaction : transactionsList) {
			initializeOneCountStat(transaction, HANDLED_SUFFIX, "handled", platform);
			initializeOneSpeedometerStat(transaction, HANDLED_SUFFIX, "handled", platform);
		}
	}

	public HederaNodeStats(Platform platform, long id, Logger log) {
		this.log = log;
		initializeInternalStatsForTransactions(cryptoTransactionsList, platform);
		initializeInternalStats(cryptoQueriesList, platform);
		initializeInternalStatsForTransactions(fileTransactionsList, platform);
		initializeInternalStats(fileQueriesList, platform);
		initializeInternalStatsForTransactions(smartContractTransactionsList, platform);
		initializeInternalStats(smartContractQueriesList, platform);
		initializeOneCountStat(THRESHOLD_RECORDS_IN_STATE, "", "Number of threshold records held in state", platform);

		initializeInternalStats(consensusQueryList, platform);
		initializeInternalStatsForTransactions(consensusTransactionList, platform);

		initializeInternalStats(networkQueriesList, platform);

		platform.addAppStatEntry(new StatEntry(//
				"app",//
				"sigVerifyAsync/sec",//
				"number of transactions received per second that were verified asynchronously via expandSignatures",
				"%,13.6f",//
				sigVerifyAsyncPerSecond,//
				(h) -> {
					sigVerifyAsyncPerSecond = new StatsSpeedometer(h);
					return sigVerifyAsyncPerSecond;
				},//
				null,//
				() -> sigVerifyAsyncPerSecond.getCyclesPerSecond())
		);

		platform.addAppStatEntry(new StatEntry(//
				"app",//
				"sigVerifySync/sec",//
				"number of transactions received per second that must be verified synchronously in handleTransaction",
				"%,13.6f",//
				sigVerifySyncPerSecond,//
				(h) -> {
					sigVerifySyncPerSecond = new StatsSpeedometer(h);
					return sigVerifySyncPerSecond;
				},//
				null,//
				() -> sigVerifySyncPerSecond.getCyclesPerSecond())
		);

		platform.addAppStatEntry(new StatEntry(//
				"app",//
				"acctLookupRetries/sec",//
				"number of times per second that an account lookup must be retried",
				"%,13.6f",//
				acctLookupRetriesPerSecond,//
				(h) -> {
					acctLookupRetriesPerSecond = new StatsSpeedometer(h);
					return acctLookupRetriesPerSecond;
				},//
				null,//
				() -> acctLookupRetriesPerSecond.getCyclesPerSecond())
		);

		platform.addAppStatEntry(new StatEntry(//
				"app",//
				"avgAcctLookupRetryAttempts",//
				"average number of retry attempts made to lookup the account number",
				"%,13.6f",//
				avgAcctLookupRetryAttempts,//
				(h) -> {
					avgAcctLookupRetryAttempts = new StatsRunningAverage(h);
					return avgAcctLookupRetryAttempts;
				},//
				null,//
				() -> avgAcctLookupRetryAttempts.getWeightedMean())
		);

		platform.addAppStatEntry(new StatEntry(//
				"app",//
				"avgAcctRetryWaitMs",//
				"average time is millis spent waiting to lookup the account number",
				"%,13.6f",//
				avgAcctRetryWaitMs,//
				(h) -> {
					avgAcctRetryWaitMs = new StatsRunningAverage(h);
					return avgAcctRetryWaitMs;
				},//
				null,//
				() -> avgAcctRetryWaitMs.getWeightedMean())
		);

		platform.addAppStatEntry(new StatEntry(//
				"app",//
				"recordStreamQueueSize",//
				"size of the queue from which we take records and write to RecordStream file",
				"%d",//
				null,//
				null,//
				null,//
				() -> recordStreamQueueSize)
		);

		platform.appStatInit();

		Thread updateStatsThread = new Thread() {
			public void run() {
				updateStats();
			}
		};
		updateStatsThread.setName("updateStatsThread_node" + String.valueOf(id));
		updateStatsThread.start();
	}

	private void updateStats() {
		while (true) {
			try {
				Thread.sleep(UPDATE_PERIOD);

//				log.info("Updating stats...");

				for (Map.Entry<String, StatsSpeedometer> entry : speedometerStats.entrySet()) {
					String statName = entry.getKey();
					StatsSpeedometer stat = entry.getValue();
					long currentCount = getCountStat(statName, "");
					stat.update(currentCount - previousCounts.get(statName));
					previousCounts.put(statName, currentCount);
				}
			} catch (InterruptedException e) {
				if (log.isDebugEnabled())
					log.debug("Interruption error when trying to sleep in HGCAppStats... ignore and continue");
			}
		}
	}

	/**
	 * This method is called internally to update stats
	 * Only RECEIVED_SUFFIX, SUBMITTED_SUFFIX and HANDLED_SUFFIX are supported
	 *
	 * @param requestType
	 * @param suffix
	 */
	private void updateCountStat(String requestType, String suffix) {
		updateCountStat(requestType, suffix, (l) -> l.incrementAndGet());
	}

	private void updateCountStat(String requestType, String suffix, Consumer<AtomicLong> update) {
		AtomicLong stat = countStats.get(requestType + suffix);
		if (null == stat) {
			log.debug("Stat for {} is not supported", requestType);
		} else {
			update.accept(stat);
		}
	}

	public void networkQueryReceived(String type) {
		updateCountStat(type, RECEIVED_SUFFIX);
	}

	public void networkQueryAnswered(String type) {
		updateCountStat(type, SUBMITTED_SUFFIX);
	}

	public void hcsQueryReceived(String type) {
		updateCountStat(type, RECEIVED_SUFFIX);
	}

	public void hcsQueryAnswered(String type) {
		updateCountStat(type, SUBMITTED_SUFFIX);
	}

	public void hcsTxnReceived(String type) {
		updateCountStat(type, RECEIVED_SUFFIX);
	}

	public void hcsTxnSubmitted(String type) {
		updateCountStat(type, SUBMITTED_SUFFIX);
	}

	public void cryptoTransactionReceived(String transactionType) {
		updateCountStat(transactionType, RECEIVED_SUFFIX);
		// Can also update stats for Crypto and/or transactions
	}

	public void cryptoTransactionSubmitted(String transactionType) {
		updateCountStat(transactionType, SUBMITTED_SUFFIX);
		// Can also update stats for Crypto and/or transactions
	}

	public void cryptoQueryReceived(String queryType) {
		updateCountStat(queryType, RECEIVED_SUFFIX);
		// Can also update stats for Crypto and/or queries
	}

	public void cryptoQuerySubmitted(String queryType) {
		updateCountStat(queryType, SUBMITTED_SUFFIX);
		// Can also update stats for Crypto and/or queries
	}

	public void fileTransactionReceived(String transactionType) {
		updateCountStat(transactionType, RECEIVED_SUFFIX);
		// Can also update stats for File and/or transactions
	}

	public void fileTransactionSubmitted(String transactionType) {
		updateCountStat(transactionType, SUBMITTED_SUFFIX);
		// Can also update stats for File and/or transactions
	}

	public void fileQueryReceived(String queryType) {
		updateCountStat(queryType, RECEIVED_SUFFIX);
		// Can also update stats for File and/or queries
	}

	public void fileQuerySubmitted(String queryType) {
		updateCountStat(queryType, SUBMITTED_SUFFIX);
		// Can also update stats for File and/or queries
	}

	public void smartContractTransactionReceived(String transactionType) {
		updateCountStat(transactionType, RECEIVED_SUFFIX);
		// Can also update stats for SmartContract and/or transactions
	}

	public void smartContractTransactionSubmitted(String transactionType) {
		updateCountStat(transactionType, SUBMITTED_SUFFIX);
		// Can also update stats for SmartContract and/or transactions
	}

	public void smartContractQueryReceived(String queryType) {
		updateCountStat(queryType, RECEIVED_SUFFIX);
		// Can also update stats for SmartContract and/or queries
	}

	public void smartContractQuerySubmitted(String queryType) {
		updateCountStat(queryType, SUBMITTED_SUFFIX);
		// Can also update stats for SmartContract and/or queries
	}

	public void transactionHandled(String transactionType) {
		updateCountStat(transactionType, HANDLED_SUFFIX);
	}

	public void signatureVerified(final boolean async) {
		if (async) {
			sigVerifyAsyncPerSecond.update(1);
		} else {
			sigVerifySyncPerSecond.update(1);
		}
	}

	public void lookupRetries(final int attempts, final double waitTime) {
		acctLookupRetriesPerSecond.update(1);
		avgAcctLookupRetryAttempts.recordValue(attempts);
		avgAcctRetryWaitMs.recordValue(waitTime);
	}

	public void updateRecordStreamQueueSize(int size) {
		recordStreamQueueSize = size;
	}

	/**
	 * Only RECEIVED_SUFFIX, SUBMITTED_SUFFIX and HANDLED_SUFFIX are supported
	 *
	 * @param requestType
	 * @param suffix
	 * @throws IllegalArgumentException
	 * 		on unsupported stat
	 */
	public long getCountStat(String requestType, String suffix) {
		AtomicLong stat = countStats.get(requestType + suffix);
		if (null == stat) {
			throw new IllegalArgumentException("Count stat for " + requestType + suffix + " is not supported");
		} else {
			return stat.get();
		}
	}

	public double getSpeedometerStat(String requestType, String suffix) {
		StatsSpeedometer stat = speedometerStats.get(requestType + suffix);
		if (null == stat) {
			throw new IllegalArgumentException("Speedometer stat for " + requestType + suffix + " is not supported");
		} else {
			return stat.getCyclesPerSecond();
		}
	}
}
