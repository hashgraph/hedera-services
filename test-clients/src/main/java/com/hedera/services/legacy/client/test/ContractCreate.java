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

import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ContractCreate extends ClientBaseThread {
  private final static Logger log = LogManager.getLogger(ContractCreate.class);
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private static final String SIMPLE_STORAGE_BIN = "simpleStorage.bin";

  private int ITERATIONS = 1;
  private int TPS_TARGET = 10;

  private long accountDuration;
  private LinkedBlockingQueue<TransactionID> txIdQueue = new LinkedBlockingQueue<>();

  private FileID contractFileId;
  private boolean checkRunning = true;

  public ContractCreate(String host, int port, long nodeAccountNumber, String [] args, int index)
  {
    super(host, port, nodeAccountNumber, args, index);
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

    Properties properties = TestHelper.getApplicationProperties();

    accountDuration = Long.parseLong(properties.getProperty("ACCOUNT_DURATION"));

    try {
      initAccountsAndChannels();

      //start an extra thread to check account transaction result
      Thread checkThread = new Thread("New Thread") {
        public void run() {
          long items = 0L;
          while (checkRunning) {
            try {
              TransactionID item = txIdQueue.poll(500, TimeUnit.MILLISECONDS);
              if (item != null) {
                ContractID createdContractId = Common.getContractIDfromReceipt(stub, genesisAccount, item);

                if (isBackupTxIDRecord) {
                  TransactionRecord record = getTransactionRecord(genesisAccount, item, false);
                  confirmedTxRecord.add(record);
                }

                if (createdContractId != null) {
                  items++;
                  if ((items % 100) == 0) {
                    log.info("receipt fetched for iteration " + ", created contract " + createdContractId);

                    log.info("{} txIdQueue size {} ", getName(), txIdQueue.size());

                  }
                } else {
                  log.warn("receipt not found for " + item);
                }
              }else{
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
          txIdQueue.clear();
          log.info("{} Check thread end", getName());
        }
      };
      checkThread.setName("checkThread" + index);
      checkThread.start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  void demo() throws Exception {

    try {

      // create some random key pairs, as fake key who grant access to the file
      List<KeyPair> accessKeys = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        KeyPair pair = new KeyPairGenerator().generateKeyPair();
        accessKeys.add(pair);
        Common.addKeyMap(pair, pubKey2privKeyMap);
      }

      long transactionFee = TestHelper.getCryptoMaxFee();
      byte[] bytes = CommonUtils.readBinaryFileAsResource(SIMPLE_STORAGE_BIN);
      Properties properties = TestHelper.getApplicationProperties();
      long fileDuration = Long.parseLong(properties.getProperty("FILE_DURATION"));
      Pair<List<Transaction>, FileID> result = grpcStub.uploadFile(genesisAccount, genesisPrivateKey, accessKeys,
              fileDuration, transactionFee, pubKey2privKeyMap , bytes, nodeAccountNumber);
      contractFileId = result.getRight();
      for(Transaction item : result.getLeft()){
        txIdQueue.add(TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID());
        if(isBackupTxIDRecord) {
			this.submittedTxID.add(TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID());
		}
      }

      long accumulatedTransferCount = 0;
      long startTime = System.currentTimeMillis();
      for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
        if ((iteration % 100) == 0) {
          log.info("Create number " + iteration);
        }
        try {
          // genesisAccount pays for contract creation
          TransactionID txId = createContractOnly(genesisAccount, contractFileId, null, accountDuration, null);
          if (txId != null) {
            txIdQueue.add(txId);
          }
          if (isBackupTxIDRecord) {
			  submittedTxID.add(txId);
		  }
        } catch (io.grpc.StatusRuntimeException e) {
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
    }finally {
      while(txIdQueue.size()>0) {
		  ; //wait query thread to finish
	  }
      sleep(1000);         //wait check thread done query
      log.info("{} query queue empty", getName());
      channel.shutdown();
      checkRunning = false;
    }
  }

}
