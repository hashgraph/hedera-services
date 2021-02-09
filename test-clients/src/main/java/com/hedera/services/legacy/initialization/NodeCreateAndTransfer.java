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
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

public class NodeCreateAndTransfer {

  private static final Logger log = LogManager.getLogger(NodeCreateAndTransfer.class);

  public static Map<String, KeyPairObj> nodeKeyPair = null;
  private static ManagedChannel channel;


  public static void main(String args[])
      throws Exception {
    log.info("Node Account to Create and Transfer Started");
    int port = 50211;
    channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext()
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

    NodeCreateAndTransfer nodeTest = new NodeCreateAndTransfer();
    long nodeAccountNum = 3;
    if (args.length > 0) {
      try {
        nodeAccountNum = Long.parseLong(args[0]);
      } catch (Exception e) {
        log.error(
            "Ignoring the input provided , taking the default Node Account Number 3 for test");
      }
    }

    nodeTest.createAccountsAndTransfer(stub, nodeAccountNum);
    channel.shutdown();
  }

  public static Path getStartupPath() throws URISyntaxException {
    return Paths
        .get(NodeCreateAndTransfer.class.getClassLoader().getResource("NodeAccounts.txt").toURI());
  }

  public void createAccountsAndTransfer(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      long nodeAccountNum)
      throws Exception {

    // Node Account which will be used to create and transfer
    AccountID nodeAccount = AccountID.newBuilder().setAccountNum(nodeAccountNum).setRealmNum(0)
        .setShardNum(0).build();

    // First Transfer some coins from Genesis to Node Account to make sure Node Account has sufficient balance.

    // Get the Genesis Account details
    AccountKeyListObj payerAccountDetails = getGenesisAccount();
    KeyPairObj genKeyPairObj = payerAccountDetails.getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);

    TestHelper.initializeFeeClient(channel, payerAccountDetails.getAccountId(), genKeyPair, nodeAccount);

    log.info("The Payer Account Id " + payerAccountDetails.getAccountId().getAccountNum());
    Transaction transfer = TestHelper
        .createTransferSigMap(payerAccountDetails.getAccountId(), genKeyPair,
            nodeAccount, payerAccountDetails.getAccountId(),
            genKeyPair, nodeAccount, 100000l);
    log.info("Transferring 100000l coin from Genesis account to Node account....");
    TransactionResponse transferNodeRes = stub.cryptoTransfer(transfer);
    Assert.assertNotNull(transferNodeRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferNodeRes.getNodeTransactionPrecheckCode());
    log.info("Pre Check Response transfer :: " + transferNodeRes.getNodeTransactionPrecheckCode()
        .name());
    TransactionBody transferBody = TransactionBody.parseFrom(transfer.getBodyBytes());
    TransactionReceipt txNodeReceipt = TestHelper
        .getTxReceipt(transferBody.getTransactionID(), stub);
    Assert.assertNotNull(txNodeReceipt);
    log.info("-------Node has at least 100000l balance now------------------------------");
    // Get the Node Account File
    Path startUpAccountPathJson = getStartupPath();
    String path = startUpAccountPathJson.toString();
    //get the Start up Account details - This is the Genesis Account and will be used for Accounts creation and signing the transaction
    nodeKeyPair = getNodeKeysHolder(path);

    log.info("Node is going to create new Account now..");

    // Generate test account Keypair for the new account
    KeyPair accountKeyPair = new KeyPairGenerator().generateKeyPair();

    PrivateKey nodePrivateKey = nodeKeyPair.get("NODE_ACCOUNT").getPrivateKey();
    PublicKey nodePublicKey = nodeKeyPair.get("NODE_ACCOUNT").getPublicKey();
    KeyPair nodeKeyPair = new KeyPair(nodePublicKey, nodePrivateKey);
    // create the transaction (here node account will create and transfer , so pater account will be same as nodeAccount)
    Transaction transaction = TestHelper
        .createAccountWithFee(nodeAccount, nodeAccount, accountKeyPair, 10000l,
            Collections.singletonList(nodePrivateKey));

    // sign the transaction
//		  Transaction signTransaction = TransactionSigner.signTransaction(transaction, Collections.singletonList(nodePrivateKey));

    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Pre Check Response of Create account :: " + response.getNodeTransactionPrecheckCode()
        .name());
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId1 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    // Now Transfer to this Account from Node Account

    // transfer from Node to newly created account by using payer account as Node
    Transaction transferNode = TestHelper.createTransferSigMap(nodeAccount, nodeKeyPair,
        newlyCreateAccountId1, nodeAccount, nodeKeyPair, nodeAccount, 1000l);

    log.info("Transferring 1000 coin from Node account to Newly created account....");
    TransactionResponse transferRes = stub.cryptoTransfer(transferNode);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionBody transferNodeBody = TransactionBody.parseFrom(transferNode.getBodyBytes());

    TransactionReceipt txReceipt = TestHelper
        .getTxReceipt(transferNodeBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    log.info("--------Node Account ID " + nodeAccountNum
        + " Successfully created and transferred amount----------------------------");

  }


  public Map<String, KeyPairObj> getNodeKeysHolder(String path)
      throws IOException, InvalidKeySpecException, DecoderException, ClassNotFoundException, URISyntaxException {

    // get the content from file
    String keyBase64Pub = CommonUtils.readFileContentUTF8(path);
    byte[] nodeKeyPairHolderBytes = CommonUtils.base64decode(keyBase64Pub);
    @SuppressWarnings("unchecked")
    Map<String, KeyPairObj> nodeKeyPairHolder = (Map<String, KeyPairObj>) CommonUtils
        .convertFromBytes(nodeKeyPairHolderBytes);
    return nodeKeyPairHolder;
  }

  public static AccountKeyListObj getGenesisAccount() {
    String genesisPath = "";
    try {
      genesisPath = Paths.get(
          NodeCreateAndTransfer.class.getClassLoader().getResource(TestHelper.getStartUpFile()).toURI())
          .toString();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    String keyBase64Pub = CommonUtils.readFileContentUTF8(genesisPath);
    byte[] accountKeyPairHolderBytes = CommonUtils.base64decode(keyBase64Pub);
    @SuppressWarnings("unchecked")
    Map<String, List<AccountKeyListObj>> accountKeyPairHolder = (Map<String, List<AccountKeyListObj>>) CommonUtils
        .convertFromBytes(accountKeyPairHolderBytes);
    // Get Start Account key Pair
    List<AccountKeyListObj> startAccount = accountKeyPairHolder.get("START_ACCOUNT");
    // Since in this case there is only one Object, get the first Object from list and return.
    return (AccountKeyListObj) startAccount.get(0);

  }


}
