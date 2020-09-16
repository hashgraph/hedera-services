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
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.builder.RequestBuilder;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ContractDeleteTransferListCheck extends ClientBaseThread {
  private final static Logger log = LogManager.getLogger(ContractDeleteTransferListCheck.class);
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private static final String CONTRACT_BINARY_FILE = "DeleteTest.bin";

  private long accountDuration;

  private FileID contractFileId;

  public ContractDeleteTransferListCheck(String host, int port, long nodeAccountNumber, boolean useSigMap, String [] args, int index)
  {
    super(host, port, nodeAccountNumber, useSigMap, args, index);
    this.useSigMap = useSigMap;
    this.nodeAccountNumber = nodeAccountNumber;
    this.host = host;
    this.port = port;

    Properties properties = TestHelper.getApplicationProperties();

    accountDuration = Long.parseLong(properties.getProperty("ACCOUNT_DURATION"));

    isCheckTransferList = true;

    try {
      initAccountsAndChannels();

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

      long transactionFee = TestHelper.getFileMaxFee();
      byte[] bytes = CommonUtils.readBinaryFileAsResource(CONTRACT_BINARY_FILE);
      Properties properties = TestHelper.getApplicationProperties();
      long fileDuration = Long.parseLong(properties.getProperty("FILE_DURATION"));
      Pair<List<Transaction>, FileID> result = grpcStub.uploadFile(genesisAccount, genesisPrivateKey, accessKeys,
              fileDuration, transactionFee, pubKey2privKeyMap , bytes, nodeAccountNumber);
      contractFileId = result.getRight();

      try {
        // genesisAccount pays for contract creation
        Key adminKey = Common.keyPairToPubKey(genesisKeyPair);
        TransactionID txID = createContractOnly(genesisAccount, contractFileId, null, accountDuration, adminKey, 3000000L);
        ContractID contractId = Common.getContractIDfromReceipt(stub, genesisAccount, txID);

        long currentBalance = Common.getAccountBalance(stub,
                RequestBuilder.getAccountIdBuild(contractId.getContractNum(), 0L, 0L),
                genesisAccount, genesisKeyPair, nodeAccount);

        if(isCheckTransferList) {
          Map<AccountID, Long> preBalance = Common.createBalanceMap(stub,
                  new ArrayList<>(List.of(genesisAccount, nodeAccount,
                          RequestBuilder.getAccountIdBuild(contractId.getContractNum(), 0L, 0L),
                          DEFAULT_FEE_COLLECTION_ACCOUNT)), genesisAccount,
                  genesisKeyPair,
                  nodeAccount);

          txID = deleteContract(genesisAccount, genesisKeyPair, contractId, genesisAccount);

          Common.getReceiptByTransactionId(stub, txID);
          verifyBalance(txID, preBalance, true);

        }
      } catch (io.grpc.StatusRuntimeException e) {
        if (!tryReconnect(e)) {
			return;
		}
      }

    }finally {
      channel.shutdown();
    }
  }

}
