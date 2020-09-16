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
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class QueryChargeTest extends ClientBaseThread {

  private static final Logger log = LogManager.getLogger(QueryChargeTest.class);
  public static String fileName = TestHelper.getStartUpFile();

  public QueryChargeTest(String host, int port, long nodeAccountNumber, boolean useSigMap, String [] args, int index) {
    super(host, port, nodeAccountNumber, useSigMap, args, index);
    log.info("host {} nodeAccountNumber {}", host, nodeAccountNumber);
    this.useSigMap = useSigMap;
    this.nodeAccountNumber = nodeAccountNumber;
    this.host = host;
    this.port = port;

    try {
      initAccountsAndChannels();

    } catch (URISyntaxException | IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  void demo() throws Exception {


    try {

      //build a transaction to be able to estimate transactionFee for crypto transfer
      Transaction createTransferTran = createQueryHeaderTransfer(genesisAccount, 10000000L);
      long transactionFee = FeeClient.getCreateTransferFee(createTransferTran, 1);

      KeyPair dummyKeyPair = new KeyPairGenerator().generateKeyPair();
      TransactionID dummyTxID = callCreateAccount(genesisAccount, dummyKeyPair, 10000L);
      long queryFee = getTransactionRecordFee(genesisAccount, dummyTxID)+2;

      // create a payer account, its balance is not enough to pay
      // two query fee
      long payerInitialBalance = (long) (queryFee * 1.5f) + 150000;


      AccountID testPayerAccount1 = createTestAccount(payerInitialBalance);
      log.error("payAccount Before balance {}", Common.getAccountBalance(stub, testPayerAccount1, genesisAccount, genesisKeyPair, nodeAccount));

      Map<AccountID, Long> preBalance = Common.createBalanceMap(stub,
              new ArrayList<>(List.of(testPayerAccount1, genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
              genesisAccount, genesisKeyPair,
              nodeAccount);

      List<TransactionID> txIDList = new ArrayList<>();
      Pair<Transaction, Response> FirstQuery = executeQueryForTxRecord(testPayerAccount1, dummyTxID, queryFee, ResponseType.ANSWER_ONLY, false);
      Pair<Transaction, Response> SecondQuery = executeQueryForTxRecord(testPayerAccount1, dummyTxID, queryFee, ResponseType.ANSWER_ONLY, false);

      TransactionID firstQueryPaymentTxID = TransactionBody.parseFrom(FirstQuery.getLeft().getBodyBytes()).getTransactionID();
      txIDList.add(firstQueryPaymentTxID);
      TransactionID secondQueryPaymentTxID = TransactionBody.parseFrom(SecondQuery.getLeft().getBodyBytes()).getTransactionID();
      txIDList.add(secondQueryPaymentTxID);

      Common.getReceiptByTransactionId(stub, secondQueryPaymentTxID);

      log.error("{}", getTransactionRecordAndQueryTransactionID(genesisAccount, firstQueryPaymentTxID, false));
      log.error("{}", getTransactionRecordAndQueryTransactionID(genesisAccount, secondQueryPaymentTxID, false));

      //second query should have INSUFFICIENT_PAYER_BALANCE error
      log.error("payAccount After balance {}", Common.getAccountBalance(stub, testPayerAccount1, genesisAccount, genesisKeyPair, nodeAccount));

      verifyBalance(txIDList, preBalance, false);


    }finally {
      channel.shutdown();
    }
  }

  AccountID createTestAccount(long balance){
    try {
      KeyPair payerKeyPair = new KeyPairGenerator().generateKeyPair();

      TransactionID txID = callCreateAccount(genesisAccount, payerKeyPair, balance);
      AccountID newAccount = Common.getAccountIDfromReceipt(stub, txID);

      Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
      accountKeys.put(newAccount, Collections.singletonList(payerKeyPair.getPrivate()));
      acc2ComplexKeyMap.put(newAccount,
              Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(Common.keyPairToPubKey(payerKeyPair))).build());
      return newAccount;
    }catch (Exception e){
      return null;
    }
  }
}
