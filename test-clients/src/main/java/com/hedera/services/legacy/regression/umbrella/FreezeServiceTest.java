package com.hedera.services.legacy.regression.umbrella;

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
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc;
import com.hedera.services.legacy.client.test.ClientBaseThread;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Adds freeze related functions for regression tests.
 *
 * @author Qian Created on 2019-4-17
 */
public class FreezeServiceTest extends TestHelperComplex {

  private static final Logger log = LogManager.getLogger(FreezeServiceTest.class);
  private static ManagedChannel channel = null;

  /**
   * default freeze service stub that connects to the default listening node
   */
  private static FreezeServiceGrpc.FreezeServiceBlockingStub stub = null;

  /**
   * default crypto service stub that connects to the default listening node
   */
  private static CryptoServiceBlockingStub cstub = null;

  private static List<AccountKeyListObj> genesisAccountList;
  private static AccountID genesisAccountID;
  private static String host = null;
  private static int port = 50211;
  private static final Long freezeAccountID = 58l;
  private static AccountID freezeAccount;

  /**
   * The account ID of the default listening node
   */
  private static AccountID nodeID = null;

  /**
   * maintains the live transaction ids, which are removed when they expire
   */
  private static TransactionIDCache cache = null;

  public FreezeServiceTest() {
  }

  public void setUp() throws Throwable {
    freezeAccount = RequestBuilder.getAccountIdBuild(freezeAccountID, 0l, 0l);
    Properties properties = TestHelper.getApplicationProperties();
    if (host == null) {
      this.host = properties.getProperty("host","localhost");
    }
    nodeID = RequestBuilder.getAccountIdBuild(3l, 0l, 0l);
    readGenesisInfo();
    createStubs();
    cache = TransactionIDCache
        .getInstance(TransactionIDCache.txReceiptTTL, TransactionIDCache.txRecordTTL);
    // Transfer hbars from 2 to freezeAccountID,
    // because initially this account doesn't have enough hbars to pay for Freeze Transaction
    Transaction transferTx = getSignedTransferTx(
            genesisAccountID, nodeID, genesisAccountID, freezeAccount, 300000, "Transfer from 2 to " + freezeAccountID);
    TransactionID transferTxID = CommonUtils.extractTransactionBody(transferTx).getTransactionID();
    cstub.cryptoTransfer(transferTx);
    System.out.println("Transferring hbars from 2 to " + freezeAccountID + "...");
    TransactionReceipt receipt = getTxReceipt(transferTxID);
    System.out.println("Transfer status: " + receipt.getStatus());
  }

  private void createStubs() {
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    stub = FreezeServiceGrpc.newBlockingStub(channel);
    cstub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  /**
   * Get the transaction receipt.
   *
   * @param txId ID of the tx
   * @return the transaction receipt
   */
  public TransactionReceipt getTxReceipt(TransactionID txId) throws Throwable {
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(
            RequestBuilder.getTransactionGetReceiptQuery(txId, ResponseType.ANSWER_ONLY))
        .build();
    Response transactionReceipts = fetchReceipts(query, cstub);
    TransactionReceipt rv = transactionReceipts.getTransactionGetReceipt().getReceipt();
    return rv;
  }

  public static void main(String[] args) throws Throwable {
    int freezeDuration = 1; // freeze duration in minutes
    int freezeStartTimeOffset = 1; // freeze start time offset in minutes
    int freezeStartHour = -1;
    int freezeStartMin = -1;

    if (args.length > 0) {
      host = args[0];
    }

    if (args.length > 1) {
      try {
        freezeDuration = Integer.parseInt(args[1]);
      }
      catch (NumberFormatException e) {
        log.info("Invalid data passed for freeze duration, default value (1 minute) will be used!");
      }
    }

    if (args.length > 2) {
      try {
        String[] freezeStartTime = args[2].split(":");
        if (freezeStartTime.length == 2) {
          freezeStartHour = Integer.parseInt(freezeStartTime[0]);
          freezeStartMin = Integer.parseInt(freezeStartTime[1]);
          freezeStartTimeOffset = -1;
        } else {
          freezeStartTimeOffset = Integer.parseInt(args[2]);
          if (freezeStartTimeOffset <= 0) {
            log.info("Freeze start time offset must be positive, default value (1 minute) will be used!");
            freezeStartTimeOffset = 1;
          }
        }
      } catch (NumberFormatException e) {
        log.info("Invalid data passed for freeze start time offset, default value (1 minute) will be used!");
      }
    }

    FreezeServiceTest test = new FreezeServiceTest();
    test.setUp();

    if (freezeStartTimeOffset > 0) {
      long freezeStartTimeMillis = System.currentTimeMillis() + 60000l * freezeStartTimeOffset;
      int[] startHourMin = Utilities.getUTCHourMinFromMillis(freezeStartTimeMillis);
      freezeStartHour = startHourMin[0];
      freezeStartMin = startHourMin[1];
    }

    Transaction transaction = createFreezeTransaction(freezeStartHour, freezeStartMin, freezeDuration);

    Transaction signedTx = getSignedFreezeTx(transaction);
    FreezeTransactionBody freezeBody =
            TransactionBody.parseFrom(signedTx.getBodyBytes()).getFreeze();
    String freezeBodyToPrint = "\n-----------------------------------" +
            "\nfreeze: FreezeTransactionBody = " +
            "\nstartHour: " + freezeBody.getStartHour() +
            "\nstartMin: " + freezeBody.getStartMin() +
            "\nendHour: " + freezeBody.getEndHour() +
            "\nendMin: " + freezeBody.getEndMin() + "\n";
    System.out.println(freezeBodyToPrint);
    TransactionResponse response = stub.freeze(signedTx);

    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
            "Pre Check Response Freeze :: " + response.getNodeTransactionPrecheckCode().name());

    TransactionBody body = TransactionBody.parseFrom(signedTx.getBodyBytes());
    TransactionID transactionID = body.getTransactionID();
    cache.addTransactionID(transactionID);

    // get tx receipt of payer account by txId
    log.info("Get Tx receipt by Tx Id...");
    TransactionReceipt txReceipt = test.getTxReceipt(transactionID);
    assert txReceipt.getStatus() == ResponseCodeEnum.SUCCESS;
  }


  /**
   * Fetches the receipts, wait if necessary.
   */
  private static Response fetchReceipts(Query query, CryptoServiceBlockingStub cstub2)
      throws Exception {
    return TestHelperComplex.fetchReceipts(query, cstub2, log, host);
  }

  /**
   * Creates a signed freeze tx.
   */
  private static Transaction getSignedFreezeTx(Transaction unSignedTransferTx) throws Exception {
    TransactionBody txBody = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(unSignedTransferTx);
    AccountID payerAccountID = txBody.getTransactionID().getAccountID();
    List<Key> keys = new ArrayList<>();
    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    keys.add(payerKey);
    Transaction paymentTxSigned = TransactionSigner
        .signTransactionComplexWithSigMap(unSignedTransferTx, keys, pubKey2privKeyMap);
    return paymentTxSigned;
  }

  /**
   * Read genesis info;
   * Add freezeAccount and its key;
   */
  private void readGenesisInfo() throws Exception {
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(CryptoServiceTest.INITIAL_ACCOUNTS_FILE);

    // Get Genesis Account key Pair
    genesisAccountList = keyFromFile.get("START_ACCOUNT");
    genesisAccountID = genesisAccountList.get(0).getAccountId();

    KeyPairObj genesisKeyPair = genesisAccountList.get(0).getKeyPairList().get(0);
    String pubKeyHex = genesisKeyPair.getPublicKeyAbyteStr();
    Key akey ;

    if (KeyExpansion.USE_HEX_ENCODED_KEY) {
      akey = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyHex)).build();
    } else {
      akey = Key.newBuilder().setEd25519(ByteString.copyFrom(ClientBaseThread.hexToBytes(pubKeyHex)))
              .build();
    }

    pubKey2privKeyMap.put(pubKeyHex, genesisKeyPair.getPrivateKey());
    acc2ComplexKeyMap.put(genesisAccountID,
            Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(akey)).build());

    //Add key of freezeAccount
    acc2ComplexKeyMap.put(freezeAccount,
            Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(akey)).build());
  }

  /**
   * Create a freeze transaction
   *
   * @param startHour
   * @param startMin
   * @param duration in minutes
   * @return a freeze transaction
   */
  public static Transaction createFreezeTransaction(final int startHour, final int startMin, final int duration) {
    AccountID payerAccountId = RequestBuilder.getAccountIdBuild(freezeAccountID, 0l, 0l);
    AccountID nodeAccountId =
            RequestBuilder.getAccountIdBuild(3l, 0l, 0l);
    FreezeTransactionBody freezeBody;

    int endMin = startMin + duration;
    int endHour = (startHour + endMin / 60) % 24;
    endMin = endMin % 60;
    freezeBody = FreezeTransactionBody.newBuilder()
            .setStartHour(startHour).setStartMin(startMin)
            .setEndHour(endHour).setEndMin(endMin).build();

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(120);

    long transactionFee = 100000;
    String memo = "Freeze Test";

    TransactionBody.Builder body = RequestBuilder.getTxBodyBuilder(transactionFee,
            timestamp, transactionDuration, true, memo,
            payerAccountId, nodeAccountId);
    body.setFreeze(freezeBody);
    byte[] bodyBytesArr = body.build().toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    return Transaction.newBuilder().setBodyBytes(bodyBytes).build();
  }

  /**
   * Creates a signed transfer tx.
   */
  private static Transaction getSignedTransferTx(AccountID payerAccountID,
          AccountID nodeAccountID,
          AccountID fromAccountID, AccountID toAccountID, long amount, String memo) throws Exception {

    Transaction paymentTx = CryptoServiceTest.getUnSignedTransferTx(payerAccountID, nodeAccountID, fromAccountID, toAccountID, amount, memo);
    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    Key fromKey = acc2ComplexKeyMap.get(fromAccountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(fromKey);
    Transaction paymentTxSigned = TransactionSigner
            .signTransactionComplexWithSigMap(paymentTx, keys, pubKey2privKeyMap);
    return paymentTxSigned;
  }

}
