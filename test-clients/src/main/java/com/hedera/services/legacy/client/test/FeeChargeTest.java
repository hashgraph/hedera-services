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
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.hedera.services.legacy.client.util.Common.createAccountComplex;

public class FeeChargeTest extends ClientBaseThread {

  private static final Logger log = LogManager.getLogger(FeeChargeTest.class);
  public static String fileName = TestHelper.getStartUpFile();

  public FeeChargeTest(String host, int port, long nodeAccountNumber, boolean useSigMap, String [] args, int index) {
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

      //build a transaction to be able to estimate transactionFee
      KeyPair newKeyPair = new KeyPairGenerator().generateKeyPair();
      Common.addKeyMap(newKeyPair, pubKey2privKeyMap);
      byte[] pubKey = ((EdDSAPublicKey) newKeyPair.getPublic()).getAbyte();
      Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
      Key payerKey = acc2ComplexKeyMap.get(genesisAccount);
      Transaction createRequest = Common.createAccountComplex(genesisAccount, payerKey, nodeAccount, key, 1L,
              pubKey2privKeyMap);

      long transactionFee = FeeClient.getCreateAccountFee(createRequest, 1);


      // create a payer account, its balance is not enough to pay
      // two transaction fees
      TransactionID txID;
      long testAccountBalance = 1000000L;
      long payerInitialBalance = (long) (transactionFee * 1.5f) + 2*testAccountBalance;

      KeyPair payerKeyPair = new KeyPairGenerator().generateKeyPair();

      txID = callCreateAccount(genesisAccount, payerKeyPair, payerInitialBalance);
      AccountID payAccount  = Common.getAccountIDfromReceipt(stub, txID);

      Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
      accountKeys.put(payAccount, Collections.singletonList(payerKeyPair.getPrivate()));
      acc2ComplexKeyMap.put(payAccount, Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(Common.keyPairToPubKey(payerKeyPair))).build());

      log.error("payAccount Before balance {}", Common.getAccountBalance(stub, payAccount, genesisAccount, genesisKeyPair, nodeAccount));


      Map<AccountID, Long> preBalance = Common.createBalanceMap(stub,
              new ArrayList<>(List.of(payAccount, genesisAccount, nodeAccount, DEFAULT_FEE_COLLECTION_ACCOUNT)),
              genesisAccount, genesisKeyPair,
              nodeAccount);


      // send two transaction in a row, both should pass preCheck, but only one
      // will be created, the second one does not have enough balance
      KeyPair firstKeyPair = new KeyPairGenerator().generateKeyPair();
      KeyPair secondKeyPair = new KeyPairGenerator().generateKeyPair();

      TransactionID firstTxID = callCreateAccount(payAccount, firstKeyPair, testAccountBalance);
      TransactionID secondTxID = callCreateAccount(payAccount, secondKeyPair, testAccountBalance);


      Common.getReceiptByTransactionId(stub, secondTxID);

//      TransactionRecord temp = getTransactionRecord(genesisAccount, firstTxID, false);
//
//      Assert.assertEquals(temp.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);

      verifyBalance(firstTxID, preBalance, false);


    }finally {
      channel.shutdown();
    }
  }
}
