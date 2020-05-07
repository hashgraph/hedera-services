package com.hedera.services.legacy.regression;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CustomProperties;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Test to create same transaction and submit to two different nodes, and validate only one node process the transaction
 *
 * @author Tirupathi Mandala Created on 2019-06-12
 */
public class SameTxTwoDifferentNodesTests extends BaseClient {

  private static final Logger log = LogManager.getLogger(SameTxTwoDifferentNodesTests.class);
  private static String testConfigFilePath = "config/umbrellaTest.properties";
  public static int FEE_VARIANCE_PERCENT;
  public static PrivateKey queryPayerPrivateKey;
  public static KeyPair queryPayerKeyPair;
  public static AccountID queryPayerId;
  public static AccountID payerID;
  public static AccountID nodeID;
  public static AccountID nodeID2;
  protected static CryptoServiceGrpc.CryptoServiceBlockingStub cstubNode2 = null;
  protected AccountID[] commonAccounts = null;
  public SameTxTwoDifferentNodesTests(String testConfigFilePath) {
    super(testConfigFilePath);
  }

  public static void main(String[] args) throws Throwable {
    SameTxTwoDifferentNodesTests tester = new SameTxTwoDifferentNodesTests(testConfigFilePath);
    tester.setup(args);
    tester.cryptoCreateAccountFeeTest_2DifferentNodes();
  }


  public void cryptoCreateAccountFeeTest_2DifferentNodes() throws Throwable {
    accountKeyTypes = new String[]{"single"};
    COMPLEX_KEY_SIZE = 1;

    AccountID account_1 = createAccount(nodeID, 1000000000L);
    AccountID account_2 = createAccount(nodeID, 1000000000L);
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();


    Transaction transfer1 = createTransferTx(queryPayerId, nodeID, account_1,
            account_2,
            1000, "Transfer", timestamp);
    log.info("Transferring 1000 coin from 1st account to 2nd account....request=" + transfer1);
    TransactionResponse transferRes = cstub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
            "Pre Check Response transfer Node 1 :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
    Thread.sleep(1000);
    TransactionReceipt txReceipt = TestHelper.getTxReceipt(transferBody.getTransactionID(), cstub);
    Assert.assertNotNull(txReceipt);
    log.info("txReceipt node 1=" + txReceipt);



    Transaction transfer2 = createTransferTx(queryPayerId, nodeID2, account_1,
        account_2,
        1000, "Transfer", timestamp);
    log.info("Transferring 1000 coin from 1st account to 2nd account....request=" + transfer2);

    TransactionResponse transferRes2 = cstubNode2.cryptoTransfer(transfer2);
    Assert.assertNotNull(transferRes2);
    Assert.assertEquals(ResponseCodeEnum.DUPLICATE_TRANSACTION, transferRes2.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer Node2 :: " + transferRes2.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody2 = TransactionBody.parseFrom(transfer2.getBodyBytes());
    Thread.sleep(1000);
    TransactionReceipt txReceipt2 = TestHelper.getTxReceipt(transferBody2.getTransactionID(), cstub);
    Assert.assertNotNull(txReceipt2);
    log.info("txReceipt=" + txReceipt2);
    long transferTransactionFee = getTransactionFee(transfer1);
    long transferTransactionFee2 = getTransactionFee(transfer2);
    long account_1_Balance = getAccountBalance(account_1, queryPayerId, nodeID);
    long account_2_Balance = getAccountBalance(account_2, queryPayerId, nodeID);
    log.info("Crypto Transfer Node 1 :" + transferTransactionFee);
    log.info("Crypto Transfer Node 2 :" + transferTransactionFee2);
    log.info("account_1 balance :" +account_1_Balance );
    log.info("account_2 balance :" + account_2_Balance);

  }

  public void setup(String[] args) throws Throwable {
    init(args);
    CustomProperties testProps = new CustomProperties("config/estimatedFee.properties", false);

    FEE_VARIANCE_PERCENT = testProps.getInt("feeVariancePercentage", 5);

    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);
    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    queryPayerPrivateKey = genesisAccount.get(0).getKeyPairList().get(0).getPrivateKey();
    queryPayerKeyPair = new KeyPair(genesisAccount.get(0).getKeyPairList().get(0).getPublicKey(), genesisPrivateKey);
    queryPayerId = genesisAccount.get(0).getAccountId();

    commonAccounts = null;
    commonAccounts = accountCreatBatch(4, "single");
    payerID = commonAccounts[0];
    nodeID = getDefaultNodeAccount();
    nodeID2 = RequestBuilder.getAccountIdBuild(4l, 0l, 0l);
    cstubNode2 = createCryptoServiceStub("localhost", 50416);
  }

  /**
   * Creates a transfer transaction with timestamp.
   */
  protected static Transaction createTransferTx(AccountID payerAccountID,
                                                AccountID nodeAccountID,
                                                AccountID fromAccountID, AccountID toAccountID, long amount,
                                                String memo, Timestamp timestamp) throws Exception {


    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccountID.getAccountNum(),
            payerAccountID.getRealmNum(), payerAccountID.getShardNum(), nodeAccountID.getAccountNum(),
            nodeAccountID.getRealmNum(), nodeAccountID.getShardNum(), TestHelper.getCryptoMaxFee(), timestamp,
            transactionDuration, true,
            memo, signatures, fromAccountID.getAccountNum(), -amount, toAccountID.getAccountNum(),
            amount);

    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    Key fromKey = acc2ComplexKeyMap.get(fromAccountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(fromKey);
    if (recvSigRequiredAccounts.contains(toAccountID)) {
      Key toKey = acc2ComplexKeyMap.get(toAccountID);
      keys.add(toKey);
    }
    Transaction paymentTxSigned = TransactionSigner
            .signTransactionComplex(transferTx, keys, pubKey2privKeyMap);
    return paymentTxSigned;
  }

  public AccountID createAccount(AccountID nodeID, long initialBalance) throws Exception {
    Key key = genComplexKey("single");
    Transaction createAccountRequest = TestHelperComplex
            .createAccount(payerID, acc2ComplexKeyMap.get(payerID), nodeID, key, initialBalance, TestHelper.getCryptoMaxFee(), false,
                    10, CustomPropertiesSingleton.getInstance().getContractDuration());
    TransactionResponse response = cstub.createAccount(createAccountRequest);

    AccountID accountID = getAccountID(createAccountRequest);
    acc2ComplexKeyMap.put(accountID, key);
    return accountID;
  }
  public static long getTransactionFee(Transaction transaction) throws Exception {
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionReceipt txReceipt1 = null;
    try {
      txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), cstub);
    } catch (Exception e) {
      log.info(e.getMessage());
      return 0;
    }
    AccountID newlyCreateAccountId1 = txReceipt1.getAccountID();
    Query query = TestHelper.getTxRecordByTxId(body.getTransactionID(), queryPayerId,
            queryPayerKeyPair, nodeID, TestHelper.getCryptoMaxFee(),
            ResponseType.ANSWER_ONLY);
    Response transactionRecord = cstub.getTxRecordByTxID(query);
    long transactionFee = transactionRecord.getTransactionGetRecord().getTransactionRecord()
            .getTransactionFee();
    return transactionFee;
  }
}
