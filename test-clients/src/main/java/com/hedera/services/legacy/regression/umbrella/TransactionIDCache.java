package com.hedera.services.legacy.regression.umbrella;

/*-
 * ‌
 * Hedera Services Test Clients
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

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.builder.RequestBuilder;

/**
 * A time sensitive data structure for maintaining transaction ID.
 *
 * @author Hua, Akshay
 * @Date : 7/27/2018
 */
public class TransactionIDCache {

	private static final Logger log = LogManager.getLogger(TransactionIDCache.class);
	private static TransactionIDCache txIDCache;
	private PriorityQueue<TransactionID> txIdQueue4Receipt;
	private int receiptPeriod;
	private PriorityQueue<TransactionID> txIdQueue4Record;
	private int recordPeriod;
	private Random rand = new Random();
	private volatile TransactionID lastTxID = null;
	private volatile long lastTxIDSubmitTimeMillis = -1;
	private long POLL_TIMEOUT_MILLIS = 1000;

	public long getLastTxIDSubmitTimeMillis() {
		return lastTxIDSubmitTimeMillis;
	}

	public TransactionID getLastTxID() {
		return lastTxID;
	}

	/**
	 * receipt ttl setting in seconds
	 */
	public static int txReceiptTTL = 180; //180;
	/**
	 * record ttl setting in seconds
	 */
	public static int txRecordTTL = 180; //86400;

	/**
	 * Constructs two cache for transaction ID for getting receipts and records respectively.
	 *
	 * @param receiptPeriod
	 * 		time to live for the transaction ID used for getting receipts, should be
	 * 		less or equal to the TTL for receipts
	 * @param recordPeriod
	 * 		time to live for the transaction ID used for getting records, should be
	 * 		less or equal to the TTL for records
	 */
	private TransactionIDCache(int receiptPeriod, int recordPeriod) {
		Comparator<TransactionID> idComparatorReceipt = (c1, c2) -> {
			Instant t1 = RequestBuilder.convertProtoTimeStamp(c1.getTransactionValidStart())
					.plusSeconds(receiptPeriod);
			Instant t2 = RequestBuilder.convertProtoTimeStamp(c2.getTransactionValidStart())
					.plusSeconds(receiptPeriod);
			return t1.compareTo(t2);
		};
		this.txIdQueue4Receipt = new PriorityQueue<>(idComparatorReceipt);
		this.receiptPeriod = receiptPeriod;

		Comparator<TransactionID> idComparatorRecord = (c1, c2) -> {
			Instant t1 = RequestBuilder.convertProtoTimeStamp(c1.getTransactionValidStart())
					.plusSeconds(recordPeriod);
			Instant t2 = RequestBuilder.convertProtoTimeStamp(c2.getTransactionValidStart())
					.plusSeconds(recordPeriod);
			return t1.compareTo(t2);
		};
		this.txIdQueue4Record = new PriorityQueue<>(idComparatorRecord);
		this.recordPeriod = recordPeriod;
	}

	/**
	 * Gets a singleton instance of this class.
	 */
	public static TransactionIDCache getInstance(int receiptPeriod, int recordPeriod) {
		if (txIDCache == null) {
			txIDCache = new TransactionIDCache(receiptPeriod, recordPeriod);
		}
		return txIDCache;
	}

	/**
	 * Adds a transaction ID to cache.
	 */
	public synchronized void addTransactionID(TransactionID txId) {
		if (log.isDebugEnabled()) {
			log.debug("Inserting Tx Id :: {}", TextFormat.shortDebugString(txId));
		}
		txIdQueue4Receipt.add(txId);
		txIdQueue4Record.add(txId);
		lastTxID = txId;
		lastTxIDSubmitTimeMillis = System.currentTimeMillis();
		try {
			com.hedera.services.legacy.proto.utils.CommonUtils.writeTxId2File(TextFormat.shortDebugString(txId));
		} catch (IOException e) {
			log.warn("Got IOException when writing TransactionID to file: ", e);
		}
		notifyAll();
		log.info("notify all ...");
	}

	public TransactionID getRandomTransactionID4Receipt() {
		evictExpiredTransactionIDsInReceiptQueue();
		if (txIdQueue4Receipt.size() == 0) {
			return null;
		}

		TransactionID[] a = new TransactionID[1];
		a = txIdQueue4Receipt.toArray(a);

		TransactionID txId = a[rand.nextInt(a.length)];
		log.info("Retrieved Tx ID :: {}", txId);
		return txId;
	}

	/**
	 * Retrieves and the removes the oldest Tx ID from the queue for getting receipts.
	 *
	 * @return the oldest Tx ID, null if none exists
	 */
	public synchronized TransactionID pollOldestTransactionID4Receipt() {
		evictExpiredTransactionIDsInReceiptQueue();
		TransactionID txId = txIdQueue4Receipt.poll();
		log.info("Retrieved and removed Tx ID :: {}", txId);
		if (txId == null) {
			try {
				log.info("wait for txID ...");
				wait(POLL_TIMEOUT_MILLIS);
				txId = txIdQueue4Receipt.poll();
				log.info("awake, txID = {}", txId);
			} catch (InterruptedException e) {
				log.error("Got exception waiting to poll oldest txID for receipt: ", e);
			}
		}
		return txId;
	}

	public TransactionID getRandomTransactionID4Record() {
		evictExpiredTransactionIDsInRecordQueue();
		if (txIdQueue4Record.size() == 0) {
			return null;
		}

		TransactionID[] a = new TransactionID[1];
		a = txIdQueue4Record.toArray(a);

		TransactionID txId = a[rand.nextInt(a.length)];
		log.info("Retrieved Tx ID :: {}", txId);
		return txId;
	}

	public int sizeOfReceiptQueue() {
		return txIdQueue4Receipt.size();
	}

	public int sizeOfRecordQueue() {
		return txIdQueue4Record.size();
	}

	public void evictExpiredTransactionIDsInReceiptQueue() {
		Instant timestamp = Instant.now();
		while (true) {
			TransactionID transactionID = txIdQueue4Receipt.peek();
			if (transactionID != null && isExpiredReceipt(timestamp, transactionID)) {
				log.debug("evictExpiredTransactionIDsInReceiptQueue: Removing txID {}", transactionID);
				txIdQueue4Receipt.remove(transactionID);
			} else {
				log.info("No more expired transactionID in receipt queue..");
				break;
			}
		}
	}

	public void evictExpiredTransactionIDsInRecordQueue() {
		Instant timestamp = Instant.now();
		while (true) {
			TransactionID transactionID = txIdQueue4Record.peek();
			if (transactionID != null && isExpiredRecord(timestamp, transactionID)) {
				log.debug("evictExpiredTransactionIDsInRecordQueue: Removing txID " + transactionID);
				txIdQueue4Record.remove(transactionID);
			} else {
				log.info("No more expired transactionID in record queue..");
				break;
			}
		}
	}

	private boolean isExpiredReceipt(Instant consensusTimestamp, TransactionID transactionID) {
		return RequestBuilder.convertProtoTimeStamp(transactionID.getTransactionValidStart())
				.plusSeconds(receiptPeriod)
				.isBefore(consensusTimestamp);
	}

	private boolean isExpiredRecord(Instant consensusTimestamp, TransactionID transactionID) {
		return RequestBuilder.convertProtoTimeStamp(transactionID.getTransactionValidStart())
				.plusSeconds(recordPeriod)
				.isBefore(consensusTimestamp);
	}

	@Override
	public String toString() {
		TransactionID[] a = new TransactionID[1];
		a = txIdQueue4Receipt.toArray(a);
		String rv = "txIdQueue4Receipt: size=" + txIdQueue4Receipt.size() + "\n";
		for (TransactionID item : a) {
			rv += item + "\n";
		}

		a = txIdQueue4Record.toArray(a);
		rv += "\ntxIdQueue4Record: size=" + txIdQueue4Record.size() + "\n";
		for (TransactionID item : a) {
			rv += item + "\n";
		}

		return rv;
	}

	/**
	 * Retrieves and the removes the oldest Tx ID from the queue for getting records.
	 *
	 * @return the oldest Tx ID, null if none exists
	 */
	public synchronized TransactionID pollOldestTransactionID4Record() {
		evictExpiredTransactionIDsInRecordQueue();
		TransactionID txId = txIdQueue4Record.poll();
		log.info("Retrieved and removed Tx ID :: " + txId);
		if (txId == null) {
			try {
				log.info("wait for txID ...");
				wait(POLL_TIMEOUT_MILLIS);
				txId = txIdQueue4Record.poll();
				log.info("awake, txID = {}", txId);
			} catch (InterruptedException e) {
				log.error("Got exception waiting to poll oldest txID for record: ", e);
			}
		}
		return txId;
	}
}
