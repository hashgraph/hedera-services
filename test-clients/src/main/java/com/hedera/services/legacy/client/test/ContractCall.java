package com.hedera.services.legacy.client.test;

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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.client.util.Ed25519KeyStore;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ContractCall extends ClientBaseThread {

	enum FETCH_MODE {
		FETCH_RECEIPT,
		FETCH_RECORD_ANSWER,
		FETCH_RECORD_COST_ANSWER;
	}

	private final static Logger log = LogManager.getLogger(ContractCall.class);


	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	private static final String SIMPLE_STORAGE_BIN = "simpleStorage.bin";
	private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}]," +
			"\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";


	private int ITERATIONS = 1;
	private int TPS_TARGET = 10;
	private int RECORD_EXPIRE_SECOND = 60; //time elapsed before contract call transaction record expires

	private ContractID contractId;


	private FETCH_MODE fetchMode = FETCH_MODE.FETCH_RECEIPT;
	private LinkedBlockingQueue<TransactionID> txIdQueue = new LinkedBlockingQueue<>();

	// Save confirmed receipt in this queue, later check if its record expired as as expected
	private LinkedBlockingQueue<TransactionID> recordQueue = new LinkedBlockingQueue<>();
	private boolean checkRunning = true;
	private String pemFileName;
	private long pemAccountNumber = 0;

	private Thread checkThread;
	private Thread checkExpiredRecordThread;

	public ContractCall(String host, int port, long nodeAccountNumber, boolean useSigMap, String[] args, int index) {
		super(host, port, nodeAccountNumber, useSigMap, args, index);
		this.useSigMap = useSigMap;
		this.nodeAccountNumber = nodeAccountNumber;
		this.host = host;
		this.port = port;

		if ((args.length) > 0) {
			ITERATIONS = Integer.parseInt(args[0]);
			log.info("Got Number of Iterations as " + ITERATIONS);
		}

		if ((args.length) > 1) {
			TPS_TARGET = Integer.parseInt(args[1]);
			log.info("Got TPS target as " + TPS_TARGET);
		}

		if ((args.length) > 2) {
			fetchMode = FETCH_MODE.valueOf(args[2]);
			log.info("Got fetchMode as " + fetchMode);
		}

		if ((args.length) > 3) {
			RECORD_EXPIRE_SECOND = Integer.parseInt(args[3]);
			log.info("Got RECORD_EXPIRE_SECOND as " + RECORD_EXPIRE_SECOND);
		}

		if ((args.length) > 4) {
			pemFileName = args[4];
			pemAccountNumber = Integer.parseInt(args[5]);
		}


		Properties properties = TestHelper.getApplicationProperties();

		long contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));

		try {
			initAccountsAndChannels();

			//start an extra thread to check transaction result, make receipt are ready
			//then add transactionID in recordQueue
			checkThread = new Thread("New Thread") {
				public void run() {
					long items = 0L;
					long startTimeMS = 0;
					while (checkRunning) {
						try {
							TransactionID item = txIdQueue.poll(100, TimeUnit.MILLISECONDS);
							if (item != null) {
								if (startTimeMS == 0) {
									startTimeMS = System.currentTimeMillis();
								}
								//either get receipt or RECEIPT_NOT_FOUND (receipt expire already)
								Common.getReceiptByTransactionId(stub, item);
								recordQueue.add(item);

								if (isBackupTxIDRecord) {
									TransactionRecord checkRecord = getTransactionRecord(genesisAccount, item, false);
									confirmedTxRecord.add(checkRecord);
									log.info(" checkRecord {}", checkRecord);

								}

								items++;
								long endTimeMS = System.currentTimeMillis();
								float handledTPS = items / ((float) (endTimeMS - startTimeMS) / 1000.0f);

								if ((items % 20) == 0) {
									log.info("{} txIdQueue size {}  handledTPS {}", getName(), txIdQueue.size(),
											handledTPS);
								}

							} else {
								sleep(10);
							}
						} catch (io.grpc.StatusRuntimeException e) {
							if (!tryReconnect(e)) {
								break;
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					log.info("{} Check thread end", getName());
				}
			};
			checkThread.setName("checkThread" + index);

			// Another thread to check if expired record will be expired after expected time
			checkExpiredRecordThread = new Thread("New Thread") {
				public void run() {
					long counter = 0;
					long startTimeMS = 0;

					while (checkRunning) {
						try {
							TransactionID item = recordQueue.poll(500, TimeUnit.MILLISECONDS);
							if (item != null) {
								if (startTimeMS == 0) {
									startTimeMS = System.currentTimeMillis();
								}

								TransactionRecord record = null;
								if (fetchMode == FETCH_MODE.FETCH_RECEIPT) {
								} else if (fetchMode == FETCH_MODE.FETCH_RECORD_ANSWER) {
									record = getTransactionRecordANSWER(genesisAccount, item);
								} else if (fetchMode == FETCH_MODE.FETCH_RECORD_COST_ANSWER) {
									record = getTransactionRecord(genesisAccount, item, true);
								} else {
									log.error("Unknown fetch mode {}", fetchMode);
									return;
								}

								if (record != null) {
									log.info(" record {}", record);

									Timestamp consensusTimeStamp = record.getConsensusTimestamp();
									Instant payerExpiryTime = RequestBuilder.convertProtoTimeStamp(
											consensusTimeStamp).plusSeconds(
											RECORD_EXPIRE_SECOND);

									Instant nowTime = Instant.now();
									java.time.Duration duration = java.time.Duration.between(nowTime, payerExpiryTime);
									log.info("Expected Expired time {}", payerExpiryTime);
									log.info("Current               {}", nowTime);
									log.info("duration              {} {} {}", duration, duration.getSeconds(),
											duration.getNano() / 1000000.0f);

									if (payerExpiryTime.isAfter(nowTime)) {
										log.info("Record will expire later ");
										float waitMSSeconds = (float) (duration.getSeconds() * 1000 +
												Math.ceil(duration.getNano() / 1000000.0f));
										log.info("Wait to expire millisecond {}", waitMSSeconds);

										Thread.sleep((long) waitMSSeconds);

									} else {
										log.info("Record not expired as expected ");
									}

									// keep trying to see when it will be expired
									while (getTransactionRecord(genesisAccount, item, false) != null) {
										;
									}
									log.info("txRecord finally expired {} after {}", item,
											java.time.Duration.between(Instant.now(), payerExpiryTime));
								}

								counter++;
								long endTimeMS = System.currentTimeMillis();
								float checkTPS = counter / ((float) (endTimeMS - startTimeMS) / 1000.0f);
								if ((counter % 100) == 0) {
									log.info("{} checkTPS {}", getName(), checkTPS);
								}


							} else {
								sleep(50);
							}
						} catch (io.grpc.StatusRuntimeException e) {
							if (!tryReconnect(e)) {
								break;
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					log.info("{} Confirm thread end", getName());
				}
			};
			checkExpiredRecordThread.setName("checkExpiredRecord" + index);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	void demo() throws Exception {
		long accumulatedTransferCount = 0;
		long startTime = System.currentTimeMillis();
		AccountID pemAccount = null;

		if (!isCheckTransferList) {
			//no need to run check thread if we are checking balance using txRecord and transferList
			checkThread.start();
			checkExpiredRecordThread.start();
		} else {
			log.info("isCheckTransferList is enabled, disable checkThreadRunning");
		}

		try {
			if (pemFileName != null) {
				final Ed25519KeyStore restoreKeyStore = new Ed25519KeyStore("password".toCharArray(),
						pemFileName);
				KeyPair restoredKeyPair = restoreKeyStore.get(0);
				Common.addKeyMap(restoredKeyPair, pubKey2privKeyMap);
				pemAccount = RequestBuilder.getAccountIdBuild(pemAccountNumber, 0L, 0L);
				accountKeys.put(pemAccount, Collections.singletonList(restoredKeyPair.getPrivate()));
			}

			// create some random key pairs, as fake key who grant access to the file
			List<KeyPair> accessKeys = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				KeyPair pair = new KeyPairGenerator().generateKeyPair();
				accessKeys.add(pair);
				Common.addKeyMap(pair, pubKey2privKeyMap);
			}

			Map<AccountID, Long> preBalance = null;

			long transactionFee = TestHelper.getCryptoMaxFee();
			byte[] bytes = CommonUtils.readBinaryFileAsResource(SIMPLE_STORAGE_BIN);
			Properties properties = TestHelper.getApplicationProperties();
			long fileDuration = Long.parseLong(properties.getProperty("FILE_DURATION"));
			if (isCheckTransferList) {
				preBalance = Common.createBalanceMap(stub,
						new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
						genesisAccount, genesisKeyPair,
						nodeAccount);
			}

			Pair<List<Transaction>, FileID> result = grpcStub.uploadFile(genesisAccount, genesisPrivateKey, accessKeys,
					fileDuration, transactionFee, pubKey2privKeyMap, bytes, nodeAccountNumber);
			FileID contractFileId = result.getRight();
			if (isCheckTransferList) { //FileID is ready so transaction record should be ready already
				//iterate all transaction records generated in grpcStub.uploadFile
				List<TransactionID> txIDList = new ArrayList<>();
				TransactionID txID = null;
				for (Transaction item : result.getLeft()) {
					txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
					txIDList.add(txID);
				}
				Common.getReceiptByTransactionId(stub,
						txID); //make sure all pending transactions, even the ones embedded in query have been handled
				// already
				preBalance = verifyBalance(txIDList, preBalance, true);
			} else {
				for (Transaction item : result.getLeft()) {
					txIdQueue.add(TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID());
					if (isBackupTxIDRecord) {
						this.submittedTxID.add(TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID());
					}
				}
			}
			if (isCheckTransferList) {
				preBalance = Common.createBalanceMap(stub,
						new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
						genesisAccount, genesisKeyPair,
						nodeAccount);
			}
			long contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
			Key adminKey = Common.keyPairToPubKey(genesisKeyPair);
			TransactionID txID = createContractOnly(genesisAccount, contractFileId, null, contractDuration, adminKey);
			ContractID contractId = Common.getContractIDfromReceipt(stub, genesisAccount, txID);

			if (isCheckTransferList) {
				//contractId is ready so transaction record should be ready already
				preBalance = verifyBalance(txID, preBalance, true);
			} else {
				txIdQueue.add(txID);
				if (isBackupTxIDRecord) {
					submittedTxID.add(txID);
				}
			}

			for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
				if (isCheckTransferList) {  // save snapshot of balance before start calling contract
					preBalance = Common.createBalanceMap(stub,
							new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
							genesisAccount, genesisKeyPair,
							nodeAccount);
				}

				if ((iteration % 100) == 0) {
					log.info("Contract call number " + iteration);
				}
				int currValueToSet = ThreadLocalRandom.current().nextInt(1, 1000000 + 1);
				log.info("int currValueToSet {}", currValueToSet);
				try {
					TransactionID txId = setValueToContract(
							pemAccount != null ? pemAccount : genesisAccount,
							contractId, currValueToSet, SC_SET_ABI);

					if (isCheckTransferList) {
						Common.getReceiptByTransactionId(stub, txId);
						verifyBalance(txId, preBalance, true);
					} else {
						if (txId != null) {
							txIdQueue.add(txId);
							if (isBackupTxIDRecord) {
								submittedTxID.add(txId);
							}
						}
					}
				} catch (StatusRuntimeException e) {
					if (!tryReconnect(e)) {
						return;
					}
				}
				accumulatedTransferCount++;
				float currentTPS = Common.tpsControl(startTime, accumulatedTransferCount, TPS_TARGET);

				if ((accumulatedTransferCount % 100) == 0) {
					log.info("{} currentTPS {}", getName(), currentTPS);
				}
			}

			if (isCheckTransferList) {

				preBalance = Common.createBalanceMap(stub,
						new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
						genesisAccount, genesisKeyPair,
						nodeAccount);

				//test balance after get contract info
				Pair<List<Transaction>, ContractGetInfoResponse.ContractInfo> infoResult = getContractInfo(contractId,
						genesisAccount);
				List<TransactionID> txIDList = new ArrayList<>();
				for (Transaction item : infoResult.getLeft()) {
					txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
					txIDList.add(txID);
				}
				String solidityID = infoResult.getRight().getContractAccountID();

				//test balance after contract update
				Timestamp c1NewExpirationDate = Timestamp.newBuilder()
						.setSeconds(infoResult.getRight().getExpirationTime().getSeconds() + 24 * 60 * 60 * 30).build();
				txID = updateContract(genesisAccount, genesisKeyPair, contractId, null, c1NewExpirationDate, adminKey);
				txIDList.add(txID);


				// test balance after contract get record
				Pair<List<Transaction>, List<TransactionRecord>> recordResult = getContractRecords(contractId,
						genesisAccount);
				for (Transaction item : recordResult.getLeft()) {
					txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
					txIDList.add(txID);
				}


				// test balance after contract get by solidityID
				Pair<List<Transaction>, GetBySolidityIDResponse> contractResult = getBySolidityID(solidityID,
						genesisAccount);
				for (Transaction item : contractResult.getLeft()) {
					txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
					txIDList.add(txID);
				}

				// test balance after get contract byteCode
				Pair<List<Transaction>, ByteString> byteCodeResult = getContractByteCode(contractId,
						genesisAccount);
				for (Transaction item : byteCodeResult.getLeft()) {
					txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
					txIDList.add(txID);
				}

				Common.getReceiptByTransactionId(stub, txID);
				verifyBalance(txIDList, preBalance, true);

				// test balance after contract delete

				preBalance = Common.createBalanceMap(stub,
						new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
						genesisAccount, genesisKeyPair,
						nodeAccount);

				txID = deleteContract(genesisAccount, genesisKeyPair, contractId, genesisAccount);

				Common.getReceiptByTransactionId(stub, txID);
				verifyBalance(txID, preBalance, true);
			}
		} finally {
			if (isBackupTxIDRecord) {
				while (txIdQueue.size() > 0) {
					; //wait query thread to finish
				}
				sleep(1000);         //wait check thread done query
				log.info("{} query queue empty", getName());
			}
			checkRunning = false;
		}
	}

}
