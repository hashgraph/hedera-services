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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

public class ContractCallLocalBalanceCheck extends ClientBaseThread {

  public static final long GAS_REQUIRED_GET = 25_000L;
  public static final long GAS_INSUFFICIENT_GET = 50;
  public static final long GAS_REQUIRED_SET = 250_000L;
  private static long DAY_SEC = 24 * 60 * 60; // secs in a day

  private final static Logger log = LogManager.getLogger(ContractCallLocalBalanceCheck.class);


  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private static final String SIMPLE_STORAGE_BIN = "simpleStorage.bin";
  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

  private static long localCallGas;

  private int ITERATIONS = 1;
  private int TPS_TARGET = 10;

  private FileID contractFileId;

  public ContractCallLocalBalanceCheck(String host, int port, long nodeAccountNumber, boolean useSigMap, String [] args, int index)
  {
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


    isCheckTransferList = true;

    try {
      initAccountsAndChannels();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  void demo() throws Exception {


    // extra payer account
    KeyPair payerKeyPair = new KeyPairGenerator().generateKeyPair();
    TransactionID createTxID = callCreateAccount(genesisAccount, payerKeyPair, 500000L);
    if (isBackupTxIDRecord) submittedTxID.add(createTxID); // used by parent thread for checking event files & record files
    AccountID payerAccount = Common.getAccountIDfromReceipt(stub, createTxID);
    Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
    accountKeys.put(payerAccount, Collections.singletonList(payerKeyPair.getPrivate()));
    acc2ComplexKeyMap.put(payerAccount, Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(Common.keyPairToPubKey(payerKeyPair))).build());

    // create some random key pairs, as fake key who grant access to the file
    List<KeyPair> accessKeys = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      KeyPair pair = new KeyPairGenerator().generateKeyPair();
      accessKeys.add(pair);
      Common.addKeyMap(pair, pubKey2privKeyMap);
    }

    long transactionFee = TestHelper.getFileMaxFee();
    byte[] bytes = CommonUtils.readBinaryFileAsResource(SIMPLE_STORAGE_BIN);;
    Properties properties = TestHelper.getApplicationProperties();
    long fileDuration = Long.parseLong(properties.getProperty("FILE_DURATION"));
    Pair<List<Transaction>, FileID> result = grpcStub.uploadFile(genesisAccount, genesisPrivateKey, accessKeys,
            fileDuration, transactionFee, pubKey2privKeyMap , bytes, nodeAccountNumber);
    contractFileId = result.getRight();

    localCallGas = Long.parseLong(properties.getProperty("LOCAL_CALL_GAS"));
    long contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
    TransactionID txID = createContractOnly(genesisAccount, contractFileId, null, contractDuration, null);
    ContractID contractId = Common.getContractIDfromReceipt(stub, genesisAccount, txID);

    long accumulatedTransferCount = 0;
    long startTime = System.currentTimeMillis();
    try {
      int currValueToSet = ThreadLocalRandom.current().nextInt(1, 1000000 + 1);
      log.info("Random set value is {}", currValueToSet);
      setValueToContract(genesisAccount, contractId, currValueToSet, SC_SET_ABI);

      ContractCallLocalResponse response;
      byte[] getValueEncodedFunction = Common.encodeGetValue(SC_GET_ABI);

      sleep(3000); //wait set function reach consensus
      for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
        if ((iteration % 100) == 0) {
          log.info("{} Contract call number {}", getName(), iteration);
        }
        try{
          Map<AccountID, Long> preBalance = null;
          if(isCheckTransferList) {
            preBalance = Common.createBalanceMap(stub,
                    new ArrayList<>(List.of(payerAccount, genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
                    genesisAccount, genesisKeyPair,
                    nodeAccount);
          }

          log.error("Before balance {}", Common.getAccountBalance(stub, payerAccount, genesisAccount, genesisKeyPair, nodeAccount));

          Pair<List<Transaction>, ContractCallLocalResponse> callResult = callContractLocal2(payerAccount, contractId, getValueEncodedFunction,
                  GAS_REQUIRED_GET, localCallGas);

          if(isCheckTransferList){
            List<TransactionID> txIDList = new ArrayList<>();
            for (Transaction item : callResult.getLeft()) {
              txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
              txIDList.add(txID);
            }
            TransactionGetReceiptResponse receipt = Common.getReceiptByTransactionId(stub,
                    txID);
            if( receipt.getReceipt().getStatus() == ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE ){

              log.error("After balance {}", Common.getAccountBalance(stub, payerAccount, genesisAccount, genesisKeyPair, nodeAccount));
              log.error("Transaction record {}", getTransactionRecord(genesisAccount, txID, false));
            }

            verifyBalance(txIDList, preBalance, true);
          }

          response = callResult.getRight();
          if (response != null){
            bytes = response.getFunctionResult().getContractCallResult().toByteArray();
            long readValue = new BigInteger(bytes).longValue();
            //log.info("readValue " + readValue);
            Assert.assertEquals(readValue, currValueToSet);
            long gasUsed = response.getFunctionResult().getGasUsed();
            Assert.assertTrue(gasUsed > 0L);
            Assert.assertTrue(gasUsed <= GAS_REQUIRED_GET);

          }
        } catch (StatusRuntimeException e) {
          if(!tryReconnect(e)) return;
        }

        accumulatedTransferCount++;
        float currentTPS = Common.tpsControl(startTime, accumulatedTransferCount, TPS_TARGET);

        if ((accumulatedTransferCount % 100) == 0) {
          log.info("{} currentTPS {}", getName(), currentTPS);
        }
      }
    }finally {
      log.info("Test ending");
    }
  }

}
