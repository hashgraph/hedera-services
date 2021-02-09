package com.hedera.services.legacy.initialization;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * This Class includes the test cases to Create and Transfer using Genesis Account. It also includes
 * the Test case to Update the Genesis Key
 *
 * @author Anurag
 */

public class GenesisCreateAndTransfer {

  private static final Logger log = LogManager.getLogger(GenesisCreateAndTransfer.class);

  private static ManagedChannel channel;
  public static Map<String, List<AccountKeyListObj>> hederaStartupAccount = null;


  public static void main(String args[])
      throws Exception {
    log.info("Genesis Account to Create and Transfer Started");
    int port = 50211;
    channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext()
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

    GenesisCreateAndTransfer genesisTest = new GenesisCreateAndTransfer();
    genesisTest.createAccountsAndTransfer(stub);
    channel.shutdown();
  }

  public static Path getStartupPath() throws URISyntaxException {
    return Paths.get(
        GenesisCreateAndTransfer.class.getClassLoader().getResource(TestHelper.getStartUpFile()).toURI());
  }

  public void createAccountsAndTransfer(CryptoServiceGrpc.CryptoServiceBlockingStub stub)
      throws Exception {

    // Get the Genesis Account File
    Path startUpAccountPathJson = getStartupPath();
    String path = startUpAccountPathJson.toString();
    //get the Start up Account details - This is the Genesis Account and will be used for Accounts creation and signing the transaction
    hederaStartupAccount = getStartupAccountMap(path);
    AccountKeyListObj payerAccountDetails = getPayerAccount(hederaStartupAccount);

    // Node Account start with Account ID 3.
    AccountID nodeAccount = AccountID.newBuilder().setAccountNum(3).setRealmNum(0).setShardNum(0)
        .build();

    // Generate test account Keypair for the new account
    KeyPair accountKeyPair = new KeyPairGenerator().generateKeyPair();

    KeyPairObj genKeyPairObj = payerAccountDetails.getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);

    TestHelper.initializeFeeClient(channel, payerAccountDetails.getAccountId(), genKeyPair, nodeAccount);

    // create the transaction
    Transaction transaction = TestHelper
        .createAccountWithFee(payerAccountDetails.getAccountId(), nodeAccount, accountKeyPair,
            10000l, Collections.singletonList(genesisPrivateKey));

    // sign the transaction
    //	  Transaction signTransaction = TransactionSigner.signTransaction(transaction, Collections.singletonList(genesisPrivateKey));

    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId1 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    // Now Transfer to this Account from Genesis

    // transfer from Genesis to newly created account by using payer account as Genesis
    Transaction transfer = TestHelper
        .createTransferSigMap(payerAccountDetails.getAccountId(), genKeyPair,
            newlyCreateAccountId1, payerAccountDetails.getAccountId(),
            genKeyPair, nodeAccount, 1000l);

    log.info("Transferring 1000 coin from Genesis account to Newly created account....");
    TransactionResponse transferRes = stub.cryptoTransfer(transfer);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionReceipt txReceipt = TestHelper
        .getTxReceipt(transferBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    log.info("Transfer from Genesis to newly created account completed....");
    log.info("-----------------------------------------");


  }

  public void printNewlyCreatedAccounts(List<Response> accountInfoList) {

    for (Response accountResponse : accountInfoList) {
      log.info("The Account Info for Account with Account Num " + accountResponse.getCryptoGetInfo()
          .getAccountInfo().getAccountID().getAccountNum());
      log.info(accountResponse.getCryptoGetInfo());
    }

  }


  public Map<String, List<AccountKeyListObj>> getStartupAccountMap(String path)
      throws IOException, InvalidKeySpecException, DecoderException, ClassNotFoundException, URISyntaxException {

    // get the content from file
    String keyBase64Pub = CommonUtils.readFileContentUTF8(path);
    byte[] accountKeyPairHolderBytes = CommonUtils.base64decode(keyBase64Pub);
    @SuppressWarnings("unchecked")
    Map<String, List<AccountKeyListObj>> accountKeyPairHolder = (Map<String, List<AccountKeyListObj>>) CommonUtils
        .convertFromBytes(accountKeyPairHolderBytes);
    return accountKeyPairHolder;
  }

  public static AccountKeyListObj getPayerAccount(
      Map<String, List<AccountKeyListObj>> hederaStartupAccount) {

    // Get Start Account key Pair
    List<AccountKeyListObj> startAccount = hederaStartupAccount.get("START_ACCOUNT");
    // Since in this case there is only one Object, get the first Object from list and return.
    return (AccountKeyListObj) startAccount.get(0);

  }


}
