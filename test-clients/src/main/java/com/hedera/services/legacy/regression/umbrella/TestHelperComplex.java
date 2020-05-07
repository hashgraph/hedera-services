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

import com.google.common.base.Strings;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hedera.services.legacy.core.TestHelper;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Extending TestHelper to support complex key structure.
 *
 * @author hua Created on 2018-10-31
 */
public class TestHelperComplex extends TestHelper {

  protected static Map<AccountID, Key> acc2ComplexKeyMap = new LinkedHashMap<>();
  protected static Map<ContractID, Key> contract2ComplexKeyMap = new LinkedHashMap<>();
  protected static Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
  public static long TX_DURATION_SEC = 2 * 60; // 2 minutes for tx dedup
  private static final Logger log = LogManager.getLogger(TestHelperComplex.class);

  /**
   * Creates an account with complex keys with max tx fee.
   */
  public static Transaction createAccountComplex(AccountID payerAccount, AccountID nodeAccount,
      Key key, long initialBalance, boolean receiverSigRequired, long accountDuration) {
    long transactionFee = 100000000L;
    Duration duration = RequestBuilder.getDuration(accountDuration);
    return createAccountComplex(payerAccount, nodeAccount, key,
        initialBalance, transactionFee, receiverSigRequired, duration);
  }

  /**
   * Creates an account with complex keys.
   */
  public static Transaction createAccountComplex(AccountID payerAccount, AccountID nodeAccount,
      Key key, long initialBalance, long transactionFee, boolean receiverSigRequired,
      Duration autoRenewPeriod) {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION_SEC);

    boolean generateRecord = true;
    String memo = "Create Account Test";
    long sendRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;
    long receiveRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;

    Transaction createAccountRequest = RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(),
            payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transactionFee, timestamp,
            transactionDuration,
            generateRecord, memo, key, initialBalance, sendRecordThreshold, receiveRecordThreshold,
            receiverSigRequired, autoRenewPeriod,
            SignatureList.newBuilder().getDefaultInstanceForType());
    List<Key> keys = new ArrayList<Key>();
    Key payerKey = acc2ComplexKeyMap.get(payerAccount);
    keys.add(payerKey);
    if (receiverSigRequired) {
      keys.add(key);
    }

    Transaction transaction = null;
    try {
      transaction = TransactionSigner
          .signTransactionComplex(createAccountRequest, keys, pubKey2privKeyMap);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return transaction;
  }

  /**
   * create Account Request with parameters
   */
  public static Transaction createAccount(AccountID payerAccount, Key payerKey,
      AccountID nodeAccount, Key key, long initialBalance, long transactionFee,
      boolean receiverSigRequired, int memoSize, long duration) throws Exception {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION_SEC);
    boolean generateRecord = true;
    String memo = getStringMemo(memoSize);
    long sendRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;
    long receiveRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;
    Duration autoRenewPeriod = RequestBuilder.getDuration(duration);

    Transaction createAccountRequest = RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(),
            payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transactionFee, timestamp,
            transactionDuration,
            generateRecord, memo, key, initialBalance, sendRecordThreshold, receiveRecordThreshold,
            receiverSigRequired, autoRenewPeriod,
            SignatureList.newBuilder().getDefaultInstanceForType());
    List<Key> keys = new ArrayList<>();
    keys.add(payerKey);
    if (receiverSigRequired) {
      keys.add(key);
    }
    Transaction txFirstSigned = TransactionSigner.signTransactionComplex(createAccountRequest, keys,
        pubKey2privKeyMap);
    TransactionBody transferBody = TransactionBody.parseFrom(txFirstSigned.getBodyBytes());
    if (transferBody.getTransactionID() == null || !transferBody.hasTransactionID()) {
      return createAccount(payerAccount, payerKey, nodeAccount,
              key, initialBalance, transactionFee,
              receiverSigRequired, memoSize, duration);
    }
    return txFirstSigned;
  }
  
  
  public static Transaction createAccountWithThreshold(AccountID payerAccount, Key payerKey,
	      AccountID nodeAccount, Key key, long initialBalance, long transactionFee,
	      boolean receiverSigRequired, int memoSize, long duration,long sendRecordThreshold, long receiveRecordThreshold) throws Exception {
	    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
	    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION_SEC);
	    boolean generateRecord = true;
	    String memo = getStringMemo(memoSize);	   
	    Duration autoRenewPeriod = RequestBuilder.getDuration(duration);

	    Transaction createAccountRequest = RequestBuilder
	        .getCreateAccountBuilder(payerAccount.getAccountNum(),
	            payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
	            nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transactionFee, timestamp,
	            transactionDuration,
	            generateRecord, memo, key, initialBalance, sendRecordThreshold, receiveRecordThreshold,
	            receiverSigRequired, autoRenewPeriod,
	            SignatureList.newBuilder().getDefaultInstanceForType());
	    List<Key> keys = new ArrayList<>();
	    keys.add(payerKey);
	    if (receiverSigRequired) {
	      keys.add(key);
	    }
	    Transaction txFirstSigned = TransactionSigner.signTransactionComplex(createAccountRequest, keys,
	        pubKey2privKeyMap);
	    TransactionBody transferBody = TransactionBody.parseFrom(txFirstSigned.getBodyBytes());
	    if (transferBody.getTransactionID() == null || !transferBody.hasTransactionID()) {
	      return createAccount(payerAccount, payerKey, nodeAccount,
	              key, initialBalance, transactionFee,
	              receiverSigRequired, memoSize, duration);
	    }
	    return txFirstSigned;
	  }

  public static String getStringMemo(int size) {
    if (size == 0) {
      return "";
    } else {
      return Strings.padEnd("a", size, 'a');
    }
  }

  public static Transaction updateAccount(AccountID accountID, AccountID payerAccount,
      AccountID nodeAccount, CryptoUpdateTransactionBody cryptoUpdate) {

    Timestamp startTime = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

    long nodeAccountNum = nodeAccount.getAccountNum();
    long payerAccountNum = payerAccount.getAccountNum();
    return RequestBuilder
        .getAccountUpdateRequest(accountID, payerAccountNum, 0l, 0l, nodeAccountNum, 0l, 0l,
            TestHelper.getCryptoMaxFee(),
            startTime, transactionDuration, true, "Update Account", cryptoUpdate,
            SignatureList.newBuilder().getDefaultInstanceForType());

  }


  /**
   * Gets records by account ID.
   */
  public static Query getTxRecordByAccountIdComplex(AccountID accountID, AccountID payerAccount,
      AccountID nodeAccount, long getTxRecordFee, ResponseType responsetype) throws Exception {
    Transaction transferTransaction = CryptoServiceTest
        .getSignedTransferTx(payerAccount, nodeAccount, payerAccount,
            nodeAccount, getTxRecordFee, "getTxRecordByAccountId");
    return RequestBuilder.getAccountRecordsQuery(accountID, transferTransaction, responsetype);
  }

  public static Transaction updateAccount(AccountID accountID, AccountID payerAccount,
      AccountID nodeAccount, Duration autoRenew) {

    Timestamp startTime = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

    long nodeAccountNum = nodeAccount.getAccountNum();
    long payerAccountNum = payerAccount.getAccountNum();
    return RequestBuilder
        .getAccountUpdateRequest(accountID, payerAccountNum, 0l, 0l, nodeAccountNum, 0l, 0l,
            TestHelper.getCryptoMaxFee(),
            startTime, transactionDuration, true, "Update Account", 100l, 100l, autoRenew,
            SignatureList.newBuilder().getDefaultInstanceForType());

  }

  public static Transaction updateAccount(AccountID accountID, AccountID payerAccount,
      AccountID nodeAccount, Duration autoRenew, String memo) {

    Timestamp startTime = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

    long nodeAccountNum = nodeAccount.getAccountNum();
    long payerAccountNum = payerAccount.getAccountNum();
    return RequestBuilder
        .getAccountUpdateRequest(accountID, payerAccountNum, 0l, 0l, nodeAccountNum, 0l, 0l,
            TestHelper.getCryptoMaxFee(),
            startTime, transactionDuration, true, memo, 100l, 100l, autoRenew,
            SignatureList.newBuilder().getDefaultInstanceForType());

  }
}
