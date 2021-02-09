package com.hedera.services.legacy.regression;

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

import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.stub.StreamObserver;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Calendar;
import java.util.Collections;
import java.util.Properties;
import java.util.TimeZone;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

public class Utilities {

  private static final Logger log = LogManager.getLogger(Utilities.class);
  protected static String DEFAULT_NODE_ACCOUNT_ID_STR = "0.0.3";

  /*
   * Send create account request and get receipt to get the real account number
   */
  public static AccountID createSingleAccountAndReturnID(AccountID payerAccount, Long accountNum,
          Long realmNum, Long shardNum, long initialBalance, PrivateKey genesisPrivateKey,
          CryptoServiceGrpc.CryptoServiceBlockingStub stub, KeyPair firstPair) throws Exception {
    AccountID newAccount = RequestBuilder.getAccountIdBuild(accountNum, realmNum, shardNum);

    Transaction transaction;
    while (true) {
      transaction = TestHelper
              .createAccountWithFee(payerAccount, newAccount, firstPair, initialBalance,
                      Collections.singletonList(genesisPrivateKey));
      TransactionResponse response = stub.createAccount(transaction);
      if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.OK) {
        log.info("Create OK");
        break;
      } else if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY ||
              response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED) {
        // Try again
        log.info("Busy try again");
        Thread.sleep(50);
      } else {
        Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
      }
    }

    AccountID newlyCreateAccountId1 = null;
    try {
      TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
      Query query = Query.newBuilder().setTransactionGetReceipt(
              RequestBuilder.getTransactionGetReceiptQuery(body.getTransactionID(), ResponseType.ANSWER_ONLY))
              .build();

      TransactionReceipt receipt;
      Response transactionReceipts = TestHelper.fetchReceipts(query, stub, null, null);
      receipt = transactionReceipts.getTransactionGetReceipt().getReceipt();


      newlyCreateAccountId1 = receipt.getAccountID();

      if ( newlyCreateAccountId1.getAccountNum() == 0 ) {
        log.error("Account ID is 0, transactionReceipts = " + transactionReceipts);
      }

    } catch (InvalidNodeTransactionPrecheckCode e) {
      Assert.fail("Invalid Node Transaction Precheck Code: " + e);
    }
    Assert.assertNotNull(newlyCreateAccountId1);

    if ( newlyCreateAccountId1.getAccountNum() == 0 ) {
      log.error("Account ID is 0");

      return null;
    } else {
      log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");

      return newlyCreateAccountId1;
    }
  }

  /*
   * Send a create account request and return the transaction to be used later to
   * retrieve receipt
   */
  public static Transaction createSingleAccountReturnTran(AccountID payerAccount, Long accountNum,
          Long realmNum,
          Long shardNum, PrivateKey genesisPrivateKey, CryptoServiceGrpc.CryptoServiceBlockingStub stub,
          CryptoServiceGrpc.CryptoServiceStub nonblockingStub, KeyPair firstPair,
          boolean nonnBlocking) throws Exception {
    AccountID newAccount = RequestBuilder.getAccountIdBuild(accountNum, realmNum, shardNum);

    // create 1st account by payer as genesis
    Transaction transaction = TestHelper
            .createAccountWithFee(payerAccount, newAccount, firstPair, 10000000l,
                    Collections.singletonList(genesisPrivateKey));
    StreamObserver<TransactionResponse> responseObserver = new StreamObserver<TransactionResponse>() {

      @Override
      public void onNext(TransactionResponse response) {
        if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.OK) {
        } else if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY) {
        } else {
          Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
        }
      }

      @Override
      public void onError(Throwable t) {
        log.info("TransactionResponse Failed: {0}", t);
      }

      @Override
      public void onCompleted() {

      }

    };

    if (nonnBlocking) {
      nonblockingStub.createAccount(transaction, responseObserver);

    } else {
      while (true) {
        TransactionResponse response = stub.createAccount(transaction);

        Assert.assertNotNull(response);
        if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.OK) {
          break;
        } else if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY) {
          // Try again
        } else {
          Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
        }
      }
    }

    return transaction;
  }

  public static TransactionRecord getTransactionRecord(TransactionID tranID, AccountID payerAccount,
          KeyPair payerKey, AccountID nodeAccount,
          CryptoServiceBlockingStub stub) throws Exception {

    long queryFee = TestHelper.getCryptoMaxFee();

    TransactionRecord transactionRecordResponse = null;

    while (true) {
      Query query = TestHelper.getTxRecordByTxId(tranID, payerAccount, payerKey, nodeAccount, queryFee,
              ResponseType.ANSWER_ONLY);
      Response transactionRecord = stub.getTxRecordByTxID(query);
      Assert.assertNotNull(transactionRecord);
      Assert.assertNotNull(transactionRecord.getTransactionGetRecord());

      ResponseCodeEnum precheck = transactionRecord.getTransactionGetRecord().getHeader()
              .getNodeTransactionPrecheckCode();

      if (ResponseCodeEnum.OK == precheck) {

        transactionRecordResponse = transactionRecord.getTransactionGetRecord()
                .getTransactionRecord();
        Assert.assertEquals(tranID, transactionRecordResponse.getTransactionID());

        return transactionRecordResponse;
      } else if (ResponseCodeEnum.BUSY == precheck || ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED == precheck) {
        log.info("busy try again");
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      } else if (ResponseCodeEnum.RECORD_NOT_FOUND == precheck) {
        log.info("not found try again");
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      } else {
        // unhandled error coce
        Assert.assertEquals(ResponseCodeEnum.OK, precheck);
        return null;
      }
    }

  }

  public static long getAccountBalance(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
          AccountID accountID,
          AccountID payerAccount, KeyPair payerKeyPair, AccountID nodeAccount, long accountInfoFee) throws Exception {

    long acccuntBalance = -1;
    Transaction transferTransaction = TestHelper
            .createTransferSigMap(payerAccount, payerKeyPair, nodeAccount, payerAccount,
                    payerKeyPair, nodeAccount, accountInfoFee);
    Query cryptoGetInfoQuery = RequestBuilder.getCryptoGetInfoQuery(accountID, transferTransaction,
            ResponseType.ANSWER_ONLY);

    while (true) {
      Response accountInfoResponse = stub.getAccountInfo(cryptoGetInfoQuery);

      ResponseCodeEnum precheck = accountInfoResponse.getCryptoGetInfo().getHeader()
              .getNodeTransactionPrecheckCode();

      if (precheck == ResponseCodeEnum.OK) {
        if (accountInfoResponse.getCryptoGetInfo().hasAccountInfo()) {
          acccuntBalance = accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance();
        } else {
          acccuntBalance = -2;
        }
        break;
      } else if (precheck == ResponseCodeEnum.BUSY) {
        // Try again
      } else {
        Assert.assertEquals(ResponseCodeEnum.OK, precheck);
      }

    }

    return acccuntBalance;
  }

  /**
   * get configured Node Account from application.properties
   *
   * @return nodeAccountId
   */
  public static long getDefaultNodeAccount() {
    Properties properties = TestHelper.getApplicationProperties();
    String nodeAccIDStr = properties
            .getProperty("defaultListeningNodeAccount", DEFAULT_NODE_ACCOUNT_ID_STR);
    long nodeAccount = 3;
    try {
      nodeAccount = Long.parseLong(nodeAccIDStr.substring(nodeAccIDStr.lastIndexOf('.') + 1));
    } catch (NumberFormatException e) {
      log.error("incorrect format of defaultListeningNodeAccount, using default nodeAccountId=3 ",
              e);
    }
    return nodeAccount;
  }

  /**
   * get UTC Hour and Minutes from utcMillis
   * @return
   */
  public static int[] getUTCHourMinFromMillis(final long utcMillis) {
    int[] hourMin = new int[2];
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    cal.setTimeInMillis(utcMillis);
    hourMin[0] = cal.get(Calendar.HOUR_OF_DAY);
    hourMin[1] = cal.get(Calendar.MINUTE);
    return hourMin;
  }

}
