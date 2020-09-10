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
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ContractBigArray extends ClientBaseThread {
  private final static Logger log = LogManager.getLogger(ContractBigArray.class);

  private static final String BA_SETSIZEINKB_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_howManyKB\",\"type\":\"uint256\"}],\"name\":\"setSizeInKB\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String BA_CHANGEARRAY_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"changeArray\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private static final String BIG_ARRAY_BIN = "BigArray.bin";

  private int sizeInKb;
  private int numberOfIterations;
  private float TPS_TARGET = 10;

  private AccountID crAccount;
  private ContractID zeroContractId;
  private LinkedBlockingQueue<TransactionID> txnIdList = new LinkedBlockingQueue<>();
  private boolean checkRunning = true;
  private boolean checkRecord = false;

  public ContractBigArray(String host, int port, long nodeAccountNumber, String[] args, int index)
  {
    super(host, port, nodeAccountNumber, args, index);
    this.nodeAccountNumber = nodeAccountNumber;
    this.host = host;
    this.port = port;

    numberOfIterations = Integer.parseInt(args[0]);
    log.info("Got number of iterations as " + numberOfIterations);

    sizeInKb = Integer.parseInt(args[1]);
    log.info("Got size in KB as " + sizeInKb);

    if ((args.length) > 2) {
      TPS_TARGET = Float.parseFloat(args[2]);
      log.info("Got TPS target as " + TPS_TARGET);
    }

    if ((args.length) > 3) {
      checkRecord = Boolean.parseBoolean(args[3]);
      log.info("Got checkRecord as " + checkRecord);
    }

    try {

      initAccountsAndChannels();

      KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();

      crAccount = Utilities
              .createSingleAccountAndReturnID(genesisAccount, nodeAccountNumber, 0l, 0l,
                      TestHelper.getContractMaxFee() * 100L,
                      genesisPrivateKey, stub, crAccountKeyPair);
      accountKeys.put(crAccount, Collections.singletonList(crAccountKeyPair.getPrivate()));

      Common.addKeyMap(crAccountKeyPair, pubKey2privKeyMap);

      Assert.assertNotNull(crAccount);
      Assert.assertNotEquals(0, crAccount.getAccountNum());
      log.info("Account created successfully: " + crAccount);

      // Upload contract file
      FileID zeroContractFileId = LargeFileUploadIT
              .uploadFile(crAccount, BIG_ARRAY_BIN, new ArrayList<>(
                      List.of(crAccountKeyPair.getPrivate())), host, nodeAccount);
      Assert.assertNotNull(zeroContractFileId);
      Assert.assertNotEquals(0, zeroContractFileId.getFileNum());
      log.info("Contract file uploaded successfully");

      // Create contract
      //zeroContractId = createContract(crAccount, zeroContractFileId, null);
      Properties properties = TestHelper.getApplicationProperties();
      long contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
      TransactionID txID = createContractOnly(crAccount, zeroContractFileId, null, contractDuration, null);
      TransactionGetReceiptResponse contractCreateReceipt = Common.getReceiptByTransactionId(stub,
              txID);
      if (contractCreateReceipt != null) {
        zeroContractId = contractCreateReceipt.getReceipt().getContractID();
      }

      Assert.assertNotNull(zeroContractId);
      Assert.assertNotEquals(0, zeroContractId.getContractNum());
      log.info("Contract created successfully: " + zeroContractId);

      //start an extra thread to check account transaction result
      Thread checkThread = new Thread("New Thread") {
        public void run() {
          long queryCount = 0;

          while (checkRunning) {
            try {
              TransactionID item = txnIdList.poll(500, TimeUnit.MILLISECONDS);
              if (item != null) {
                readReceiptAndRecord(crAccount, item);
                queryCount++;
                if ((queryCount % 100) == 0) {
                  log.info("{} remained queue size {} ", getName(), txnIdList.size());
                }
              } else {//if empty wait a while
                Thread.sleep(50);
              }
            } catch (io.grpc.StatusRuntimeException e) {
              if(!tryReconnect(e)) break;
            } catch (Exception e){
              log.error("{} Exception ", getName(), e);
            }
          }
          txnIdList.clear();
          log.info("{} finished", getName());
        }
      };
      checkThread.setName("checkThread" + index);
      if(checkRecord) {
        checkThread.start();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  void demo() throws Exception {

    try {
      // Initialize storage size
      TransactionID txnId = setValueToContract(crAccount, zeroContractId, sizeInKb, BA_SETSIZEINKB_ABI);
      txnIdList.add(txnId);
      log.info(getName() + "Contract storage size set");

      // Loop of store calls
      long accumulatedTransferCount = 0;
      long startTime = System.currentTimeMillis();
      for (int i = 0; i < numberOfIterations; i++) {

        try {
          txnId = setValueToContract(crAccount, zeroContractId, ThreadLocalRandom.current().nextInt(1000),
                  BA_CHANGEARRAY_ABI);
          txnIdList.add(txnId);
        } catch (io.grpc.StatusRuntimeException e) {
          if(!tryReconnect(e)) return;
        }

        accumulatedTransferCount++;
        float currentTPS = Common.tpsControl(startTime, accumulatedTransferCount, TPS_TARGET);

        if ((accumulatedTransferCount % 100) == 0) {
          log.info("{} currentTPS {}", getName(), currentTPS);
        }
      }
    }finally {
      while(txnIdList.size()>0); //wait query thread to finish
      sleep(1000);         //wait check thread done query
      log.info("{} query queue empty", getName());
      channel.shutdown();
      checkRunning = false;
    }
  }

  private void readReceiptAndRecord(AccountID payerAccount, TransactionID txId)
      throws Exception {
    TransactionGetReceiptResponse contractCallReceipt = Common.getReceiptByTransactionId(stub, txId);
    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      //Thread.sleep(6000);
      TransactionRecord trRecord = getTransactionRecord(payerAccount, txId, true);
      if (trRecord != null && trRecord.hasContractCallResult()) {
        ContractFunctionResult callResults = trRecord.getContractCallResult();
        String errMsg = callResults.getErrorMessage();
        if (!StringUtils.isEmpty(errMsg)) {
           log.info("@@@ Contract Call resulted in error: " + errMsg);
        }
      }
    }
  }
}
