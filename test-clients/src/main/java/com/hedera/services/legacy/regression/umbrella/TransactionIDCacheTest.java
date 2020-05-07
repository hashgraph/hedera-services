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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.core.CommonUtils;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Akshay
 * @Date : 7/27/2018
 */
public class TransactionIDCacheTest {

  private TransactionIDCache cache;
  private int receiptPeriodInSec = 5;
  private int recordPeriodInSec = 10;

  @Before
  public void setUp() {
    cache = TransactionIDCache.getInstance(receiptPeriodInSec, recordPeriodInSec);
  }

  /**
   * Tests for proper expiration of TxID used for retrieving receipts.
   */
  @Test
  public void testExpirationReceipt() throws InterruptedException {
    AccountID accountIdBuild1 = RequestBuilder.getAccountIdBuild(1l, 0l, 0l);
    AccountID accountIdBuild2 = RequestBuilder.getAccountIdBuild(2l, 0l, 0l);
    AccountID accountIdBuild3 = RequestBuilder.getAccountIdBuild(3l, 0l, 0l);

    long incrementInSec = 2;
    TransactionID transactionID1 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 1)),
        accountIdBuild1);
    TransactionID transactionID2 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 2)),
        accountIdBuild2);
    TransactionID transactionID3 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 3)),
        accountIdBuild3);

    cache.addTransactionID(transactionID1);
    cache.addTransactionID(transactionID2);
    cache.addTransactionID(transactionID3);
    System.out.println("cache=" + cache);

    // tx1 should expire
    CommonUtils.nap(receiptPeriodInSec + incrementInSec + 1);
    TransactionID tid = cache.getRandomTransactionID4Receipt();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertEquals(2, cache.sizeOfReceiptQueue());

    // tx2 should expire
    CommonUtils.nap(incrementInSec);
    tid = cache.getRandomTransactionID4Receipt();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertEquals(1, cache.sizeOfReceiptQueue());

    // tx3 should expire
    CommonUtils.nap(incrementInSec);
    tid = cache.getRandomTransactionID4Receipt();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertEquals(0, cache.sizeOfReceiptQueue());

    System.out.println("Test Success!!");
  }

  @Test
  public void beforeAfterTest() {
    long receiptPeriod = 0;
    Instant t1 = Instant.now();
    CommonUtils.nap(2);
    Instant t2 = Instant.now();
    Timestamp ts1 = RequestBuilder.getTimestamp(t1);
    boolean actual = RequestBuilder.convertProtoTimeStamp(ts1).plusSeconds(receiptPeriod)
        .isBefore(t2);
    Assert.assertEquals(true, actual);
  }

  /**
   * Tests for proper expiration of TxID used for retrieving records.
   */
  @Test
  public void testExpirationRecord() throws InterruptedException {
    AccountID accountIdBuild1 = RequestBuilder.getAccountIdBuild(1l, 0l, 0l);
    AccountID accountIdBuild2 = RequestBuilder.getAccountIdBuild(2l, 0l, 0l);
    AccountID accountIdBuild3 = RequestBuilder.getAccountIdBuild(3l, 0l, 0l);

    long incrementInSec = 4;
    TransactionID transactionID1 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 1)),
        accountIdBuild1);
    TransactionID transactionID2 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 2)),
        accountIdBuild2);
    TransactionID transactionID3 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 3)),
        accountIdBuild3);

    cache.addTransactionID(transactionID1);
    cache.addTransactionID(transactionID2);
    cache.addTransactionID(transactionID3);
    System.out.println("cache=" + cache);

    // tx1 should expire
    CommonUtils.nap(recordPeriodInSec + incrementInSec + 1);
    TransactionID tid = cache.getRandomTransactionID4Record();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertEquals(2, cache.sizeOfRecordQueue());

    // tx2 should expire
    CommonUtils.nap(incrementInSec);
    tid = cache.getRandomTransactionID4Record();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertEquals(1, cache.sizeOfRecordQueue());

    // tx3 should expire
    CommonUtils.nap(incrementInSec);
    tid = cache.getRandomTransactionID4Record();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertEquals(0, cache.sizeOfRecordQueue());

    System.out.println("Test Success!!");
  }

  /**
   * Tests for polling method of TxID used for retrieving receipts.
   */
  @Test
  public void testPollReceipt() throws InterruptedException {
    AccountID accountIdBuild1 = RequestBuilder.getAccountIdBuild(1l, 0l, 0l);
    AccountID accountIdBuild2 = RequestBuilder.getAccountIdBuild(2l, 0l, 0l);
    AccountID accountIdBuild3 = RequestBuilder.getAccountIdBuild(3l, 0l, 0l);
  
    long incrementInSec = 2;
    TransactionID transactionID1 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 1)),
        accountIdBuild1);
    TransactionID transactionID2 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 2)),
        accountIdBuild2);
    TransactionID transactionID3 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 3)),
        accountIdBuild3);
  
    cache.addTransactionID(transactionID1);
    cache.addTransactionID(transactionID2);
    cache.addTransactionID(transactionID3);
    System.out.println("cache=" + cache);
  
    // poll should get tx1
    TransactionID tid = cache.pollOldestTransactionID4Receipt();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertEquals(transactionID1, tid);

    // tx2 should expire
    CommonUtils.nap(receiptPeriodInSec + incrementInSec*2 + 1);
  
    // poll should get tx3
    tid = cache.pollOldestTransactionID4Receipt();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertEquals(transactionID3, tid);

    // no more tx
    tid = cache.pollOldestTransactionID4Receipt();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertNull(tid);
    Assert.assertEquals(0, cache.sizeOfReceiptQueue());
  
    System.out.println("Test testPollReceipt Success!!");
  }

  /**
   * Tests for polling method of TxID used for retrieving records.
   */
  @Test
  public void testPollRecord() throws InterruptedException {
    AccountID accountIdBuild1 = RequestBuilder.getAccountIdBuild(1l, 0l, 0l);
    AccountID accountIdBuild2 = RequestBuilder.getAccountIdBuild(2l, 0l, 0l);
    AccountID accountIdBuild3 = RequestBuilder.getAccountIdBuild(3l, 0l, 0l);
  
    long incrementInSec = 2;
    TransactionID transactionID1 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 1)),
        accountIdBuild1);
    TransactionID transactionID2 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 2)),
        accountIdBuild2);
    TransactionID transactionID3 = RequestBuilder.getTransactionID(
        RequestBuilder.getTimestamp(Instant.now().plusSeconds(incrementInSec * 3)),
        accountIdBuild3);
  
    cache.addTransactionID(transactionID1);
    cache.addTransactionID(transactionID2);
    cache.addTransactionID(transactionID3);
    System.out.println("cache=" + cache);
  
    // poll should get tx1
    TransactionID tid = cache.pollOldestTransactionID4Record();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertEquals(transactionID1, tid);
  
    // tx2 should expire
    CommonUtils.nap(recordPeriodInSec + incrementInSec*2 + 1);
  
    // poll should get tx3
    tid = cache.pollOldestTransactionID4Record();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertEquals(transactionID3, tid);
  
    // no more tx
    tid = cache.pollOldestTransactionID4Record();
    System.out.println("tid=" + tid + "; cache=" + cache);
    Assert.assertNull(tid);
    Assert.assertEquals(0, cache.sizeOfRecordQueue());
  
    System.out.println("Test testPollRecord Success!!");
  }
}
