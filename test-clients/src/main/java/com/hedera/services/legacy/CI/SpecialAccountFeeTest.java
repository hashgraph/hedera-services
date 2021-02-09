package com.hedera.services.legacy.CI;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * This is for testing special Account 50. It creates a new account, transfers some hbars from
 * this new account to Account 50, checks balance of account 50 and one transaction is performed
 * from account 50 and trhen checks for duplicate transaction being rejected for account 50
 *
 * @author Achal
 */
public class SpecialAccountFeeTest {

  private static final Logger log = LogManager.getLogger(SpecialAccountFeeTest.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static ManagedChannel channel;

  private long NEW_ACCOUNT_BALANCE = 1000000000;

  public SpecialAccountFeeTest(int port, String host) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    SpecialAccountFeeTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public static void main(String args[])
      throws Exception {

    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    SpecialAccountFeeTest specialAccountFeeTest = new SpecialAccountFeeTest(port, host);
    specialAccountFeeTest.demo();
  }

  public void demo()
      throws Exception {

 //
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID defaultNodeAccount = RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

    TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, defaultNodeAccount);

    // create 1st account by payer as genesis
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    String privkey = Common.bytes2Hex(firstPair.getPrivate().getEncoded());
    String publicKey = Common.bytes2Hex(firstPair.getPublic().getEncoded());
    String genPrivateKey = Common.bytes2Hex(genesisPrivateKey.getEncoded());
    log.info("private key :: " + privkey);
    log.info("public key :: " + publicKey);
    log.info("gen private key :: " + genPrivateKey);
    Transaction transaction = TestHelper.createAccountWithSigMap(payerAccount, defaultNodeAccount,
        firstPair, 10000000000000000l, genKeyPair);
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionReceipt txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), stub);
    AccountID newlyCreateAccountId1 = txReceipt1.getAccountID();
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    Assert.assertTrue(txReceipt1.getExchangeRate().getCurrentRate().getHbarEquiv() > 0);
    Assert.assertTrue(txReceipt1.getExchangeRate().getCurrentRate().getCentEquiv() > 0);
    Assert.assertTrue(
        txReceipt1.getExchangeRate().getCurrentRate().getExpirationTime().getSeconds() > 0);
    Assert.assertTrue(txReceipt1.getExchangeRate().getNextRate().getHbarEquiv() > 0);
    Assert.assertTrue(txReceipt1.getExchangeRate().getNextRate().getCentEquiv() > 0);
    Assert.assertTrue(
        txReceipt1.getExchangeRate().getNextRate().getExpirationTime().getSeconds() > 0);
    log.info("--------------------------------------");

    // transfer to account 50, special account

    // transfer between 1st to 2nd account by using payer account as 3rd account
    Transaction transfer1 = TestHelper.createTransferSigMap(newlyCreateAccountId1, firstPair,
        AccountID.newBuilder().setAccountNum(50l).build(), newlyCreateAccountId1,
        firstPair, defaultNodeAccount, 10000000000000l);

    log.info("Transferring 1000 coin from 1st account to account 50....");
    TransactionResponse transferRes = stub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
    TransactionReceipt txReceipt = TestHelper.getTxReceipt(transferBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    Assert.assertEquals(txReceipt.getStatus(), ResponseCodeEnum.SUCCESS);
    log.info("-----------------------------------------");

    // check balance of account 50

    Response accountInfoResponse = TestHelper
        .getCryptoGetAccountInfo(stub, AccountID.newBuilder().setAccountNum(50l).build(),
            newlyCreateAccountId1,
            firstPair, defaultNodeAccount);

    log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());

    // create a transfer with account 50 as payer and from account

    transfer1 = TestHelper
        .createTransferSigMap(AccountID.newBuilder().setAccountNum(50l).build(), genKeyPair,
            newlyCreateAccountId1, AccountID.newBuilder().setAccountNum(50l).build(),
            genKeyPair, defaultNodeAccount, 1000L);

    log.info("Transferring 1000 coin from 1st account to account 50....");
    transferRes = stub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
    txReceipt = TestHelper.getTxReceipt(transferBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    Assert.assertEquals(txReceipt.getStatus(), ResponseCodeEnum.SUCCESS);
    log.info("-----------------------------------------");

    accountInfoResponse = TestHelper
        .getCryptoGetAccountInfo(stub, AccountID.newBuilder().setAccountNum(50l).build(),
            newlyCreateAccountId1,
            firstPair, defaultNodeAccount);
    log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());

    // send duplicate transaction for account 50

    for (int i = 0; i < 10; i++) {
      transferRes = stub.cryptoTransfer(transfer1);
      Assert.assertNotNull(transferRes);
      Assert.assertEquals(ResponseCodeEnum.DUPLICATE_TRANSACTION,
          transferRes.getNodeTransactionPrecheckCode());

      // check that no fee is charged after the pre check failure
      accountInfoResponse = TestHelper
          .getCryptoGetAccountInfo(stub, AccountID.newBuilder().setAccountNum(50l).build(),
              newlyCreateAccountId1,
              firstPair, defaultNodeAccount);
      log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());
    }

    Thread.sleep(180000);

    // create 2nd account by payer as genesis
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, secondPair, 1000000l,
            genKeyPair);
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Pre Check Response of Create second account :: " + response
        .getNodeTransactionPrecheckCode().name());
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId2 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId2);
    Assert
        .assertTrue(newlyCreateAccountId2.getAccountNum() > newlyCreateAccountId1.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");

    log.info("--------------------------------------");

    transferRes = stub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    // Issue #1693 moved this to the precheck code from the receipt
    Assert.assertEquals(ResponseCodeEnum.TRANSACTION_EXPIRED,
        transferRes.getNodeTransactionPrecheckCode());
    log.info("-----------------------------------------");


  }

}
