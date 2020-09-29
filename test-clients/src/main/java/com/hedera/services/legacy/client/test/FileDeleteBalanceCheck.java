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
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public class FileDeleteBalanceCheck extends ClientBaseThread {

  private final static Logger log = LogManager.getLogger(ContractCall.class);

  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private static final String LARGE_BINARY_BIN = "RandomLargeBinary.bin";
  private static final String LARGE_BINARY_BIN2 = "RandomLargeBinary2.bin";

  public FileDeleteBalanceCheck(String host, int port, long nodeAccountNumber, String [] args, int index)
  {
    super(host, port, nodeAccountNumber, args, index);
    this.nodeAccountNumber = nodeAccountNumber;
    this.host = host;
    this.port = port;

    isCheckTransferList = true;
    try {
      initAccountsAndChannels();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  void demo() throws Exception {

    if(isCheckTransferList) {
      log.info("isCheckTransferList is enabled, disable checkThreadRunning");
    }

    try {
      // create some random key pairs, as fake key who grant access to the file
      List<KeyPair> accessKeys = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        KeyPair pair = new KeyPairGenerator().generateKeyPair();
        accessKeys.add(pair);
        Common.addKeyMap(pair, pubKey2privKeyMap);
      }

      Map<AccountID, Long> preBalance = null;

      long transactionFee = TestHelper.getFileMaxFee();
      byte[] bytes = CommonUtils.readBinaryFileAsResource(LARGE_BINARY_BIN);
      Properties properties = TestHelper.getApplicationProperties();
      long fileDuration = Long.parseLong(properties.getProperty("FILE_DURATION"));
      if (isCheckTransferList){
        preBalance = Common.createBalanceMap(stub,
                new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)), genesisAccount, genesisKeyPair,
                nodeAccount);
      }

      Pair<List<Transaction>, FileID> result = grpcStub.uploadFile(genesisAccount, genesisPrivateKey, accessKeys,
              fileDuration, transactionFee, pubKey2privKeyMap , bytes, nodeAccountNumber);
      FileID randomFileID = result.getRight();
      if (isCheckTransferList){ //FileID is ready so transaction record should be ready already
        //iterate all transaction records generated in grpcStub.uploadFile
        List<TransactionID> txIDList = new ArrayList<>();
        TransactionID txID = null;
        for (Transaction item : result.getLeft()) {
          txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
          txIDList.add(txID);
        }
        Common.getReceiptByTransactionId(stub, txID); //make sure all pending transactions, even the ones embedded in query have been handled already
        preBalance = verifyBalance(txIDList, preBalance, true);
      }

      if (isCheckTransferList){
        preBalance = Common.createBalanceMap(stub,
                new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)), genesisAccount, genesisKeyPair,
                nodeAccount);
      }

      //update file
      bytes = CommonUtils.readBinaryFileAsResource(LARGE_BINARY_BIN2);
      result = grpcStub.updateFile(randomFileID, genesisAccount, genesisPrivateKey, accessKeys,
              fileDuration, transactionFee, pubKey2privKeyMap , bytes, nodeAccountNumber);
      if (isCheckTransferList){ //FileID is ready so transaction record should be ready already
        //iterate all transaction records generated in grpcStub.uploadFile
        List<TransactionID> txIDList = new ArrayList<>();
        TransactionID txID = null;
        for (Transaction item : result.getLeft()) {
          txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
          txIDList.add(txID);
        }
        Common.getReceiptByTransactionId(stub, txID); //make sure all pending transactions, even the ones embedded in query have been handled already
        preBalance = verifyBalance(txIDList, preBalance, true);
      }


      // get file info
      if (isCheckTransferList){
        preBalance = Common.createBalanceMap(stub,
                new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)), genesisAccount, genesisKeyPair,
                nodeAccount);
      }
      Pair<List<Transaction>, FileGetInfoResponse.FileInfo> getFileResult =
              grpcStub.getFileInfo(
              genesisAccount, genesisPrivateKey, nodeAccountNumber, randomFileID);

      if (isCheckTransferList){
        List<TransactionID> txIDList = new ArrayList<>();
        TransactionID txID = null;
        for (Transaction item : getFileResult.getLeft()) {
          txID = TransactionBody.parseFrom(item.getBodyBytes()).getTransactionID();
          txIDList.add(txID);
        }
        Common.getReceiptByTransactionId(stub, txID); //make sure all pending transactions, even the ones embedded in query have been handled already
        preBalance = verifyBalance(txIDList, preBalance, true);
      }

      //random choose one access key as key for deleting
      Random rand = new Random();
      KeyPair randomPair = accessKeys.get(rand.nextInt(accessKeys.size()));
      List<KeyPair> deleteKeys = Collections.singletonList(randomPair);
      if (isCheckTransferList){
        preBalance = Common.createBalanceMap(stub,
                new ArrayList<>(List.of(genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)), genesisAccount, genesisKeyPair,
                nodeAccount);
      }

      // delete file
      Key payerKey = Common.keyPairToPubKey(genesisKeyPair);
      TransactionID deleteTranID = grpcStub.deleteFile(genesisAccount, payerKey,
              deleteKeys,
              nodeAccount, randomFileID, pubKey2privKeyMap);
      Common.getReceiptByTransactionId(stub, deleteTranID);

      if (isCheckTransferList){
        verifyBalance(deleteTranID, preBalance, false);
      }

    }finally {
      channel.shutdown();
    }
  }

}
