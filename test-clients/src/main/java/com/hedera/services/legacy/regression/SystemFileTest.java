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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/***
 * The system files are controlled by special accounts as follows: 
 * A/c 0.0.55 - Update Address Book files (0.0.101/102)
 * A/c 0.0.56 - Update Fee schedule (0.0.111) - This transaction should be FREE
 * A/c 0.0.57 - Update Exchange Rate (0.0.112) - This transaction should be FREE
 * 
 * Tests the updates for system files 101, 102, 111 and 112 with authorized and unauthorized payers.
 *
 * @author Hua Li
 */
public class SystemFileTest {

  private static final Logger log = LogManager.getLogger(SystemFileTest.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static ManagedChannel channel;
  private static FileServiceGrpc.FileServiceBlockingStub fileServiceBlockingStub;
  private Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();

  public SystemFileTest(int port, String host) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    SystemFileTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
    SystemFileTest.fileServiceBlockingStub = FileServiceGrpc.newBlockingStub(channel);
  }

  public SystemFileTest() {
  }

  public static void main(String args[])
      throws Exception {

    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    SystemFileTest systemFileTest = new SystemFileTest(port, host);
    systemFileTest.demo(101l, 55, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
    systemFileTest.demo(102l, 55, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
    systemFileTest.demo(111l, 56, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
    systemFileTest.demo(112l, 57, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
    
    long unauthorizedAccountNum = 100; //1001;
    systemFileTest.demo(101l, unauthorizedAccountNum, ResponseCodeEnum.AUTHORIZATION_FAILED, null);
    systemFileTest.demo(102l, unauthorizedAccountNum, ResponseCodeEnum.AUTHORIZATION_FAILED, null);
    systemFileTest.demo(111l, unauthorizedAccountNum, ResponseCodeEnum.AUTHORIZATION_FAILED, null);
    systemFileTest.demo(112l, unauthorizedAccountNum, ResponseCodeEnum.AUTHORIZATION_FAILED, null);
  }

  public void demo(Long filenum, long payer, ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode)
      throws Exception {
    AccountKeyListObj accountKeyListObj = getGenKeyPairObj();
    KeyPairObj genKeyPairObj = accountKeyListObj.getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    AccountID genesisAccount = accountKeyListObj.getAccountId();
    AccountID payerAccount = RequestBuilder.getAccountIdBuild(payer, 0L, 0L);
    accountKeys.put(genesisAccount, Collections.singletonList(genesisPrivateKey));
    long defaultNodeAccountSeq = Utilities.getDefaultNodeAccount();
    AccountID defaultNodeAccount = RequestBuilder
        .getAccountIdBuild(defaultNodeAccountSeq, 0L, 0L);

    TestHelper.initializeFeeClient(channel, genesisAccount, genKeyPair, defaultNodeAccount);

    // transfering money into payer from genesis
    Transaction transfer1 = TestHelper.createTransferSigMap(genesisAccount, genKeyPair,
        payerAccount, genesisAccount, genKeyPair, defaultNodeAccount, 500000000000000l);

    log.info("Transferring 10000000000 coin from genesis account to system account " + payer + " ...");
    TransactionResponse transferRes = stub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
    TransactionReceipt txReceipt =
        TestHelper.getTxReceipt(transferBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    log.info("-----------------------------------------");

    FileUpdateTransactionBody fileUpdateTransactionBody = FileUpdateTransactionBody.newBuilder()
        .setFileID(
            FileID.newBuilder().setFileNum(filenum))
        .setContents(ByteString.copyFrom("...new data...".getBytes())).build();

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    AccountID nodeAccountID = AccountID.newBuilder().setAccountNum(3l).build();
    long transactionFee = 10000000000l;
    boolean generateRecord = false;

    TransactionID transactionID = TransactionID.newBuilder()
        .setAccountID(AccountID.newBuilder().setAccountNum(payer).build())
        .setTransactionValidStart(timestamp).build();
    TransactionBody transactionBody1 = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(nodeAccountID)
        .setTransactionFee(transactionFee)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(generateRecord)
        .setMemo("FileUpdate")
        .setFileUpdate(fileUpdateTransactionBody)
        .build();

    byte[] bodyBytesArr1 = transactionBody1.toByteArray();
    ByteString bodyBytes1 = ByteString.copyFrom(bodyBytesArr1);
    Transaction tx1 = Transaction.newBuilder().setBodyBytes(bodyBytes1).build();
    Transaction signedTransaction1 = TransactionSigner
        .signTransaction(tx1, Collections.singletonList(genesisPrivateKey));

    log.info("txBody=" + transactionBody1);
    TransactionResponse response = fileServiceBlockingStub.updateFile(signedTransaction1);
    log.info(response.getNodeTransactionPrecheckCode());
    log.info(response.getNodeTransactionPrecheckCode());
    Assert.assertEquals(expectedPrecheckCode, response.getNodeTransactionPrecheckCode());

    if(response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.OK) {
      txReceipt = TestHelper.getTxReceipt(transactionID, stub);
      log.info(txReceipt);
      
      if(txReceipt.getStatus() == ResponseCodeEnum.SUCCESS) {
        Response response1 = TestHelper
            .getFileContent(fileServiceBlockingStub, FileID.newBuilder().setFileNum(filenum).build(),
                genesisAccount, genKeyPair, nodeAccountID);
        System.out.println(response1.getFileGetContents());
        Assert.assertEquals(fileUpdateTransactionBody.getContents(),
            response1.getFileGetContents().getFileContents().getContents());
      }
    }
  }

  private AccountKeyListObj getGenKeyPairObj() throws URISyntaxException, IOException {

    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    return keyFromFile.get("START_ACCOUNT").get(0);
  }


}
