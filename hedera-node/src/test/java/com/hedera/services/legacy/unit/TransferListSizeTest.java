package com.hedera.services.legacy.unit;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.legacy.unit.handler.CryptoHandlerTestHelper;
import com.hedera.services.legacy.util.ComplexKeyManager;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hedera.services.legacy.TestHelper;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.legacy.proto.utils.CommonUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

/**
 * Unit test for tranfer list limit.
 * 
 * @author Hua Li
 * Created on 2019-06-05
 */
@RunWith(JUnitPlatform.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@DisplayName("TransferListSizeTest Suite")
public class TransferListSizeTest {
  private final Logger log = LogManager.getLogger(TransferListSizeTest.class);
  private CryptoHandlerTestHelper cryptoHandler;
  private int TRANSFER_ACCOUNTS_LIST_SIZE_LIMIT = 10;
  private String LOG_PREFIX = ">>>>> ";
  protected SequenceNumber sequenceNum = new SequenceNumber(
      ApplicationConstants.HEDERA_START_SEQUENCE);
  private final long nodeAccount = 3L;
  private final long payerAccount = sequenceNum.getAndIncrement();
  private final long feeCollAccount = sequenceNum.getAndIncrement();
  AccountID payerAccountId;
  protected AccountID nodeAccountId;
  AccountID feeCollAccountId;
  protected FCMap<MerkleEntityId, MerkleAccount> fcMap = null;
  public long TX_DURATION_SEC = 2 * 60; // 2 minutes for tx dedup
  protected SignatureList signatures = SignatureList.newBuilder()
      .getDefaultInstanceForType();
  protected Duration transactionDuration = Duration.newBuilder().setSeconds(TX_DURATION_SEC)
      .build();
  protected long MAX_FEE = 100L;

  @Test
  @DisplayName("Transfer List Size < Limit: Success")
  public void testTransferListSizeLessThanLimit() throws Throwable {
    cryptoTransferTestsWithVariableAccountAmounts(TRANSFER_ACCOUNTS_LIST_SIZE_LIMIT - 1);
  }
  
  @Test
  @DisplayName("Transfer List Size == Limit: Success")
  public void testTransferListSizeEqualLimit() throws Throwable {
    cryptoTransferTestsWithVariableAccountAmounts(TRANSFER_ACCOUNTS_LIST_SIZE_LIMIT);
  }

  @Test
  @DisplayName("Transfer List Size > Limit: Failure")
  @Disabled
  public void testTransferListSizeGreaterThanLimit() throws Throwable {
    cryptoTransferTestsWithVariableAccountAmounts(TRANSFER_ACCOUNTS_LIST_SIZE_LIMIT + 1);
  }

  @BeforeAll
  public void setUp() throws Throwable {
    payerAccountId = RequestBuilder.getAccountIdBuild(payerAccount, 0l, 0l);
    nodeAccountId = RequestBuilder.getAccountIdBuild(nodeAccount, 0l, 0l);
    feeCollAccountId = RequestBuilder.getAccountIdBuild(feeCollAccount, 0l, 0l);

    fcMap = new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
    createAccount(payerAccountId, 1_000_000_000L);
    createAccount(nodeAccountId, 10_000L);
    createAccount(feeCollAccountId, 10_000L);
    cryptoHandler = new CryptoHandlerTestHelper(fcMap);
  }

  /**
   * Tests crypto transfer API with more than 10 account amounts.
   * 
   * @param accountAmountListCount the size of the transfer list, including both from and to
   *        accounts.
   */
  public void cryptoTransferTestsWithVariableAccountAmounts(int accountAmountListCount)
      throws Throwable {
    // create accounts
    int toAccountCount = accountAmountListCount - 1;
    AccountID[] payerAccounts = accountCreatBatch(toAccountCount + 2);
    AccountID payerID = payerAccounts[0];
    AccountID nodeID = nodeAccountId;
  
    AccountID fromID = payerAccounts[1];
    Assert.assertNotNull(fromID);
  
    AccountID[] toIDs = new AccountID[toAccountCount];
  
    System.arraycopy(payerAccounts, 2, toIDs, 0, toAccountCount);
    Arrays.stream(toIDs).forEach(a -> Assert.assertNotNull(a));
  
    // positive scenario transfer with one from and one to accounts, verify the balance change
    long amount = 100L;
    TransactionReceipt receipt = null;
    long fromBalPre = getBalance(fromID);
    long toAccBal[] = new long[toIDs.length];
    for (int i = 0; i < toAccBal.length; i++) {
      toAccBal[i] = getBalance(toIDs[i]);
    }
    // Transfer list First account is From Account and remaining to Account List
    AccountID[] transferList = new AccountID[toIDs.length + 1];
    long[] amountsList = new long[toIDs.length + 1];
    transferList[0] = fromID;
    amountsList[0] = -amount * toAccountCount;
    for (int i = 0; i < toIDs.length; i++) {
      transferList[i + 1] = toIDs[i];
      amountsList[i + 1] = amount;
    }
    receipt = transferWrapper(payerID, nodeID, transferList, amountsList);
    if (accountAmountListCount <= TRANSFER_ACCOUNTS_LIST_SIZE_LIMIT) {
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
  
      long toAccBalPost[] = new long[toIDs.length];
      for (int i = 0; i < toAccBalPost.length; i++) {
        toAccBalPost[i] = getBalance(toIDs[i]);
      }
  
      long fromBalPost = getBalance(fromID);
      Assert.assertEquals(fromBalPost, fromBalPre + amountsList[0]);
      for (int i = 0; i < toAccBal.length; i++) {
        Assert.assertEquals(toAccBalPost[i], toAccBal[i] + amountsList[i + 1]);
      }
  
      log.info(LOG_PREFIX + "cryptoTransferTestsWithVariableAccountAmounts: PASSED! :)");
    } else {
      Assert.assertEquals(ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED.name(),
          receipt.getStatus().name());
      log.info("receipt status = " + receipt.getStatus().name());
      log.info(LOG_PREFIX + "cryptoTransferTestsWithVariableAccountAmounts: PASSED! :)");
    }
  }

  /**
   * Create a given number of accounts.
   * 
   * @param size number of accounts to create
   * @return created accounts
   * @throws Exception 
   */
  private AccountID[] accountCreatBatch(int size) throws Exception {
    AccountID[] accounts = new AccountID[size];
    for(int i=0; i<size; i++) {
      AccountID newAccount = nextAccountId();
      createAccount(newAccount, 1_000_000_000L);
      accounts[i] = newAccount;
    }
    return accounts;
  }

  /**
   * A wrapper for transfer with a list of accouts and amounts.
   *
   * @param payerID
   * @param nodeID
   * @param accs
   * @param amts
   * @throws Throwable
   */
  private TransactionReceipt transferWrapper(AccountID payerID, AccountID nodeID, AccountID[] accs,
      long[] amts) throws Throwable {
    Transaction transferTxModSigned = createSignedTransferTx(payerID, nodeID, accs, amts);
    TransactionReceipt receipt = transfer(transferTxModSigned);
    return receipt;
  }

  public AccountID nextAccountId() {
    long accountNum = sequenceNum.getAndIncrement();
    return AccountID.newBuilder().setRealmNum(0).setAccountNum(accountNum).build();
  }

  /**
   * Gets the balance of account from FCMap.
   * 
   * @param aid account id to get balance
   * @return retrieved balance
   */
  public long getBalance(AccountID aid) {
    MerkleEntityId mk = new MerkleEntityId();
    mk.setNum(aid.getAccountNum());
    mk.setRealm(aid.getRealmNum());
    mk.setShard(aid.getShardNum());
    MerkleAccount mv = new MerkleAccount();
    mv = fcMap.get(mk);
//    assertNotNull(mv);
    return mv.getBalance();
  }

  /**
   * Makes a transfer.
   */
  public TransactionReceipt transfer(Transaction transaction) throws Throwable {
    log.info("\n-----------------------------------\ntransfer: request = " +
            com.hedera.services.legacy.proto.utils.CommonUtils.toReadableString(transaction));
    TransactionBody txBody =
        com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(transaction);
    Instant consensusTime = new Date().toInstant();
    TransactionRecord record = cryptoHandler.cryptoTransfer(txBody, consensusTime);

    log.info("Transfer record :: " + record);
    Assert.assertNotNull(record);
    TransactionReceipt receipt = record.getReceipt(); 
    return receipt;
  }

  /**
   * Creating a transaction for a transfer with a list of accouts and amounts.
   *
   * @param payerID
   * @param nodeID
   * @param accs
   * @param amts
   * @throws Throwable
   */
  private Transaction createSignedTransferTx(AccountID payerID, AccountID nodeID, AccountID[] accs,
      long[] amts) throws Throwable {
    Assert.assertEquals(accs.length, amts.length);
    List<AccountAmount> accountAmountsMod = new ArrayList<>();
    for (int i = 0; i < accs.length; i++) {
      AccountAmount aa1 =
          AccountAmount.newBuilder().setAccountID(accs[i]).setAmount(amts[i]).build();
      accountAmountsMod.add(aa1);
    }
  
    Transaction transferTx =
        getUnSignedTransferTx(payerID, nodeID, accs[0], accs[1], 100L, "Transfer");
    com.hederahashgraph.api.proto.java.TransactionBody.Builder txBodyBuilder =
        CommonUtils.extractTransactionBody(transferTx).toBuilder();
    Builder transferListBuilder =
        com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody.newBuilder();
  
    TransferList transferListMod =
        TransferList.newBuilder().addAllAccountAmounts(accountAmountsMod).build();
    transferListBuilder.setTransfers(transferListMod);
    txBodyBuilder.setCryptoTransfer(transferListBuilder);
    Transaction transferTxModUnsigned =
        transferTx.toBuilder().setBodyBytes(txBodyBuilder.build().toByteString()).build();
    Transaction transferTxModSigned = getSignedTransferTx(transferTxModUnsigned);
    return transferTxModSigned;
  }

  protected void createAccount(AccountID payerAccount, long balance) throws Exception {
    MerkleEntityId mk = new MerkleEntityId();
    mk.setNum(payerAccount.getAccountNum());
    mk.setRealm(0);
    MerkleAccount mv = new MerkleAccount();
    mv.setBalance(balance);
    Key accountKey = ComplexKeyManager
        .genComplexKey(ComplexKeyManager.SUPPORTE_KEY_TYPES.single.name());
    JKey jkey = JKey.convertKey(accountKey, 1);
    mv.setKey(jkey);
    fcMap.put(mk, mv);
    ComplexKeyManager.setAccountKey(payerAccount, accountKey);
  }

  /**
   * Creates a signed transfer tx.
   */
  protected Transaction getSignedTransferTx(Transaction unSignedTransferTx) throws Exception {
    TransactionBody txBody =
            com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(unSignedTransferTx);
    com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody.Builder bodyBuilder =
            txBody.getCryptoTransfer().toBuilder();
    AccountID payerAccountID = txBody.getTransactionID().getAccountID();
    List<Key> keys = new ArrayList<Key>();
    Key payerKey = ComplexKeyManager.getAccountKey(payerAccountID);
    keys.add(payerKey);
    TransferList transferList = bodyBuilder.getTransfers();
    List<AccountAmount> accountAmounts = transferList.getAccountAmountsList();
    for(AccountAmount aa : accountAmounts) {
      AccountID accountID = aa.getAccountID();
      long amount = aa.getAmount();
      if(amount <= 0) { // from account
        Key fromKey = ComplexKeyManager.getAccountKey(accountID);
        keys.add(fromKey);
      } 
    }
    
    Transaction paymentTxSigned = TransactionSigner
        .signTransactionComplex(unSignedTransferTx, keys, ComplexKeyManager.getPubKey2privKeyMap());
    return paymentTxSigned;
  }

  /**
   * Creates a transfer tx with signatures.
   */
  protected Transaction getUnSignedTransferTx(AccountID payerAccountID,
      AccountID nodeAccountID,
      AccountID fromAccountID, AccountID toAccountID, long amount, String memo) throws Exception {
  
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccountID.getAccountNum(),
        payerAccountID.getRealmNum(), payerAccountID.getShardNum(), nodeAccountID.getAccountNum(),
        nodeAccountID.getRealmNum(), nodeAccountID.getShardNum(), MAX_FEE, timestamp,
        transactionDuration, true,
        memo, signatures, fromAccountID.getAccountNum(), -amount, toAccountID.getAccountNum(),
        amount);
    return transferTx;
  }
  
  @AfterAll
  public void tearDown() throws Exception {
    try {

      cryptoHandler = null;

    } catch (Throwable tx) {
      //do nothing now.
    }
  }
}
