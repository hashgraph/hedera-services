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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.AtomicCounter;
import com.hedera.services.legacy.regression.umbrella.SmartContractServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hedera.services.legacy.regression.umbrella.TransactionIDCache;
import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

/**
 * A client base class that supports different signature formats.
 * 
 * @author Hua Li Created on 2019-03-07
 */
public class BaseClient extends SmartContractServiceTest {
  protected static final Logger log = LogManager.getLogger(BaseClient.class);
  protected static String LOG_PREFIX = "\n>>>>>>>>>>>> ";
  protected static long nodeAccount;
  protected static List<ManagedChannel> channelList = new ArrayList<>();
  public BaseClient(String testConfigFilePath) {
    super(testConfigFilePath);
  }

  /**
   * Initialize the client.
   * 
   * @param args command line arguments, if supplied, the host should be the first argument
   * @throws Throwable 
   */
  protected void init(String[] args) throws Throwable {
    readAppConfig();
    getTestConfig();
    readGenesisInfo();

    if ((args.length) > 0) {
      host = args[0];
    }

    if ((args.length) > 1) {
      try {
        nodeAccount = Long.parseLong(args[1]);
      } catch (Exception ex) {
        log.debug("Invalid data passed for node id");
        nodeAccount = Utilities.getDefaultNodeAccount();
      }
    } else {
      nodeAccount = defaultListeningNodeAccountID.getAccountNum();
    }
    log.debug("Connecting host = " + host + "; port = " + port);

    createStubs();
    cache = TransactionIDCache.getInstance(TransactionIDCache.txReceiptTTL,
        TransactionIDCache.txRecordTTL);
    nodeID2Stub.put(defaultListeningNodeAccountID, stub);
    getNodeAccountsFromLedger();

    TestHelper.initializeFeeClient(channel, genesisAccountID, genKeyPair,
        AccountID.newBuilder().setAccountNum(nodeAccount).build());
  }

  public void demo() throws Exception {}

  public CryptoServiceGrpc.CryptoServiceBlockingStub createCryptoServiceStub(String host, int port)  throws Exception {
    return CryptoServiceGrpc.newBlockingStub(createChannel(host, port));
  }
  public SmartContractServiceGrpc.SmartContractServiceBlockingStub createSmartContractStub(String host, int port)  throws Exception {
    ManagedChannel channel= createChannel(host, port);
    channelList.add(channel);
    return SmartContractServiceGrpc.newBlockingStub(channel);
  }

  public ManagedChannel createChannel(String host, int port) throws Exception {
    return NettyChannelBuilder.forAddress(host, port)
            .negotiationType(NegotiationType.PLAINTEXT)
            .directExecutor()
            .enableRetry()
            .build();
  }

  /**
   * Creates an account using appropriate signature format.
   * 
   * @param payerAccount
   * @param nodeAccount
   * @param initBal
   * @param retrieveTxReceipt whether or not to get receipt
   * @param goodResponseCounter
   * @param badResponseCounter
   * @param goodReceiptCounter
   * @param badReceiptCounter
   * @return account ID created or null if failed, also null if retrieveTxReceipt is set to false
   * @throws Exception
   */
  public AccountID createAccount(AccountID payerAccount, AccountID nodeAccount, long initBal,
      boolean retrieveTxReceipt, AtomicCounter goodResponseCounter,
      AtomicCounter badResponseCounter, AtomicCounter goodReceiptCounter,
      AtomicCounter badReceiptCounter) throws Exception {
    String accountKeyType = getRandomAccountKeyType();
    Key key = genComplexKey(accountKeyType);

    Duration duration = RequestBuilder.getDuration(super.accountDuration);
    Transaction createAccountRequest = TestHelperComplex.createAccountComplex(payerAccount,
        nodeAccount, key, initBal, 10000l, receiverSigRequired, duration);
    long requiredFee = FeeClient.getCreateAccountFee(createAccountRequest, 1);
    createAccountRequest = TestHelperComplex.createAccountComplex(payerAccount,
        nodeAccount, key, initBal, requiredFee, receiverSigRequired, duration);

    log.debug("\n-----------------------------------");
    log.debug("createAccount: request = " + createAccountRequest);
    TransactionResponse response = cstub.createAccount(createAccountRequest);
    log.debug("createAccount Response :: " + response.getNodeTransactionPrecheckCodeValue());
    Assert.assertNotNull(response);

    if (ResponseCodeEnum.OK == response.getNodeTransactionPrecheckCode()) {
      if (goodResponseCounter != null) {
        goodResponseCounter.increment();
      }
      log.debug("PreCheck response for creating account :: "
          + response.getNodeTransactionPrecheckCode().name());
    } else {
      if (badResponseCounter != null) {
        badResponseCounter.increment();
      }
      log.warn("Got a bad PreCheck response " + response.getNodeTransactionPrecheckCode());
    }

    // get transaction receipt
    AccountID accountID = null;
    if (retrieveTxReceipt) {
      log.debug("preparing to getTransactionReceipts....");
      TransactionID transactionID = CommonUtils.extractTransactionBody(createAccountRequest).getTransactionID();

      Query query = Query.newBuilder()
          .setTransactionGetReceipt(
              RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
          .build();

      Response transactionReceipts = fetchReceipts(query, cstub);
      if (!ResponseCodeEnum.SUCCESS.name()
          .equals(transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name())) {
        if (badReceiptCounter != null) {
          badReceiptCounter.increment();
        }
      } else {
        if (goodReceiptCounter != null) {
          goodReceiptCounter.increment();
        }
      }

      accountID = transactionReceipts.getTransactionGetReceipt().getReceipt().getAccountID();
      acc2ComplexKeyMap.put(accountID, key);
      if (receiverSigRequired) {
        recvSigRequiredAccounts.add(accountID);
      }
      log.debug("Account created: account num :: " + accountID.getAccountNum());
    }

    return accountID;
  }

  /**
   * Creates an account using appropriate signature format, receipt is retrieved to get the account
   * ID.
   * 
   * @param payerAccount
   * @param nodeAccount
   * @param initBal
   * @return account ID created or null if failed
   * @throws Exception
   */
  public AccountID createAccount(AccountID payerAccount, AccountID nodeAccount, long initBal)
      throws Exception {
    return createAccount(payerAccount, nodeAccount, initBal, true, null, null, null, null);
  }

  /**
   * Makes a transfer.
   * 
   * @param payerAccountID
   * @param nodeAccountID
   * @param fromAccountID
   * @param toAccountID
   * @param amount
   * @param retrieveTxReceipt whether or not to get receipt
   * @param goodResponseCounter
   * @param badResponseCounter
   * @param goodReceiptCounter
   * @param badReceiptCounter
   * @return retrieved receipt or null if retrieveTxReceipt is set to false
   * @throws Throwable
   */
  public TransactionReceipt transfer(AccountID payerAccountID, AccountID nodeAccountID,
      AccountID fromAccountID, AccountID toAccountID, long amount, boolean retrieveTxReceipt,
      AtomicCounter goodResponseCounter, AtomicCounter badResponseCounter,
      AtomicCounter goodReceiptCounter, AtomicCounter badReceiptCounter) throws Throwable {
    Transaction transferTxSigned = getSignedTransferTx(payerAccountID, nodeAccountID, fromAccountID,
        toAccountID, amount, "Transfer");

    log.debug("\n-----------------------------------\ntransfer: request = " + transferTxSigned);
    TransactionResponse response = cstub.cryptoTransfer(transferTxSigned);
    log.debug("Transfer Response :: " + response.getNodeTransactionPrecheckCodeValue());
    Assert.assertNotNull(response);

    if (ResponseCodeEnum.OK == response.getNodeTransactionPrecheckCode()) {
      if (goodResponseCounter != null) {
        goodResponseCounter.increment();
      }
      log.debug("PreCheck response for creating account :: "
          + response.getNodeTransactionPrecheckCode().name());
    } else {
      if (badResponseCounter != null) {
        badResponseCounter.increment();
      }
      log.warn("Got a bad PreCheck response " + response.getNodeTransactionPrecheckCode());
    }

    TransactionBody body = TransactionBody.parseFrom(transferTxSigned.getBodyBytes());
    TransactionID txId = body.getTransactionID();

    TransactionReceipt receipt = null;
    if (retrieveTxReceipt) {
      receipt = getTxReceipt(txId);

      if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
        if (badReceiptCounter != null) {
          badReceiptCounter.increment();
        }
      } else {
        if (goodReceiptCounter != null) {
          goodReceiptCounter.increment();
        }
      }
    }
    
    return receipt;
  }

  public AccountID createAccountWithSingelKey(AccountID payerID, AccountID nodeID, Timestamp timestamp) throws Exception{
    Key key = genSingleKey();

    Transaction createAccountRequest = createAccount(payerID, acc2ComplexKeyMap.get(payerID), nodeID, key,
            (DEFAULT_INITIAL_ACCOUNT_BALANCE / SMALL_ACCOUNT_BALANCE_FACTOR), TestHelper.getCryptoMaxFee(), false,
                    10, DAY_SEC, timestamp);
    TransactionResponse response = cstub.createAccount(createAccountRequest);
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
            .extractTransactionBody(createAccountRequest);
    TransactionID transactionID = body.getTransactionID();
    if (transactionID == null || !body.hasTransactionID()) {
      return createAccountWithSingelKey(payerID, nodeID, timestamp);
    }
    AccountID accountID = getAccountID(createAccountRequest);
    acc2ComplexKeyMap.put(accountID, key);
    return accountID;
  }

  /**
   * create Account Request with parameters
   */
  public static Transaction createAccount(AccountID payerAccount, Key payerKey,
                                          AccountID nodeAccount, Key key, long initialBalance, long transactionFee,
                                          boolean receiverSigRequired, int memoSize, long duration, Timestamp timestamp) throws Exception {
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
                    receiverSigRequired, autoRenewPeriod);
    List<Key> keys = new ArrayList<>();
    keys.add(payerKey);
    if (receiverSigRequired) {
      keys.add(key);
    }
    Transaction txFirstSigned = TransactionSigner.signTransactionComplexWithSigMap(createAccountRequest, keys,
            pubKey2privKeyMap);
    TransactionBody transferBody = TransactionBody.parseFrom(txFirstSigned.getBodyBytes());
    if (transferBody.getTransactionID() == null || !transferBody.hasTransactionID()) {
      return createAccount(payerAccount, payerKey, nodeAccount,
              key, initialBalance, transactionFee,
              receiverSigRequired, memoSize, duration);
    }
    return txFirstSigned;
  }
  public static Key genSingleKey() {
    KeyPair pair = new KeyPairGenerator().generateKeyPair();
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    String pubKeyHex = HexUtils.bytes2Hex(pubKey);
    Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
    pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
    return key;
  }


  /**
   * Get Account from Transaction
   *
   * @return accountId
   */
  public static AccountID getAccountID(Transaction createAccountRequest) throws Exception {
    TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
    TransactionReceipt txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), cstub);
    return txReceipt1.getAccountID();
  }

}
