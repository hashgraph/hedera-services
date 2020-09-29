package com.hedera.services.legacy.CI;

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
import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.regression.BaseFeeTests;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Test Client for File Fee test cases
 * - File Create Fee Test
 * - File Update Fee Test
 * - File Delete Fee Test
 * - File Append Fee Test
 * @author Tirupathi Mandala Created on 2019-06-12
 */
public class FileFeeTests extends BaseFeeTests {

  private static final Logger log = LogManager.getLogger(FileFeeTests.class);
  private static String testConfigFilePath = "config/umbrellaTest.properties";
  public FileFeeTests(String testConfigFilePath) {
    super(testConfigFilePath);
  }


  public static void main(String[] args) throws Throwable {
    FileFeeTests tester = new FileFeeTests(testConfigFilePath);
    tester.setup(args);
    tester.fileCreateFeeTest();
    tester.fileCreateFeeTest_multiSig();
    tester.fileUpdateFeeTest();
    tester.fileUpdateFeeTestMultiSig();
    tester.fileDeleteFeeTest();
    tester.fileMultiDeleteFeeTest();
    tester.fileAppendFeeTest();
    tester.fileAppendMultiSigFeeTest();
    log.info("------------ Test Results --------------");
    testResults.stream().forEach(a->log.info(a));
  }

  public void fileCreateFeeTest() throws Throwable {
    long durationSeconds = 90 * 24 * 60 * 60; //30 Day
    AccountID newAccountID = getMultiSigAccount(1, 1, durationSeconds);
    long payerAccountBalance_before = getAccountBalance(newAccountID, queryPayerId, nodeID);

    List<Key> payerKeyList = new ArrayList<>();
    payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(newAccountID));

    byte[] fileContents = new byte[4];
    random.nextBytes(fileContents);
    ByteString fileData = ByteString.copyFrom(fileContents);
    List<Key> waclPubKeyList = fit.genWaclComplex(1, "single");
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
    String memo = TestHelperComplex.getStringMemo(1);
    Transaction fileCreateRequest = fit
        .createFile(newAccountID, nodeID, fileData, waclPubKeyList, fileExp, memo);
    TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
    log.info("FileCreate Response :: " + response);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
    Thread.sleep(NAP);
    TransactionBody body = TransactionBody.parseFrom(fileCreateRequest.getBodyBytes());
    if (body.getTransactionID() == null || !body.getTransactionID().hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    long transactionFee = getTransactionFee(fileCreateRequest);

    long payerAccountBalance_after = getAccountBalance(newAccountID, queryPayerId, nodeID);
    log.info("waclPubKeyList : " + waclPubKeyList.size());
    log.info("payerKeyList: " + payerKeyList.size());
    String result = "File Create(size:4Bytes, Keys:1, memoSize: 1) transactionFee=" + transactionFee;
    testResults.add(result);
    log.info(result);

    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = FILE_CREATE_SIZE_1_KEY_1_DUR_30 + feeVariance;
    long minTransactionFee = FILE_CREATE_SIZE_1_KEY_1_DUR_30 - feeVariance;
    Assert.assertTrue(maxTransactionFee > transactionFee);
    Assert.assertTrue(minTransactionFee < transactionFee);

    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }

  public void fileCreateFeeTest_multiSig() throws Throwable {
    long durationSeconds = 180 * 24 * 60 * 60; //6 Months (180 Days)
    AccountID newAccountID = getMultiSigAccount(10, 10, durationSeconds);
    long payerAccountBalance_before = getAccountBalance(newAccountID, queryPayerId, nodeID);

    List<Key> payerKeyList = new ArrayList<>();
    payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(newAccountID));

    byte[] fileContents = new byte[1000];
    random.nextBytes(fileContents);
    ByteString fileData = ByteString.copyFrom(fileContents);
    List<Key> waclPubKeyList = fit.genWaclComplex(10, "single");
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
    String memo = TestHelperComplex.getStringMemo(10);
    Transaction fileCreateRequest = fit
        .createFile(newAccountID, nodeID, fileData, waclPubKeyList, fileExp, memo);
    TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
    log.info("FileCreate Response :: " + response);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
    Thread.sleep(NAP);
    TransactionBody body = TransactionBody.parseFrom(fileCreateRequest.getBodyBytes());
    if (body.getTransactionID() == null || !body.getTransactionID().hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    long transactionFee = getTransactionFee(fileCreateRequest);

    long payerAccountBalance_after = getAccountBalance(newAccountID, queryPayerId, nodeID);
    String result = "File Create(size:1000k, Keys:10, memoSize: 10) transactionFee=" + transactionFee;
    testResults.add(result);
    log.info(result);

    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = FILE_CREATE_SIZE_10_KEY_10_DUR_90 + feeVariance;
    long minTransactionFee = FILE_CREATE_SIZE_10_KEY_10_DUR_90 - feeVariance;
    Assert.assertTrue(maxTransactionFee > transactionFee);
    Assert.assertTrue(minTransactionFee < transactionFee);
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }

  public void fileUpdateFeeTest() throws Throwable {
    List<Key> payerKeyList = new ArrayList<>();
    payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));

    byte[] fileContents = new byte[4];
    random.nextBytes(fileContents);
    ByteString fileData = ByteString.copyFrom(fileContents);
    List<Key> waclPubKeyList = fit.genWaclComplex(1, "single");
    long durationSeconds = 30 * 24 * 60 * 60; //1 Day
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
    String memo = TestHelperComplex.getStringMemo(1);
    Transaction fileCreateRequest = fit
        .createFile(payerID, nodeID, fileData, waclPubKeyList, fileExp, memo);
    TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
    log.info("FileCreate Response :: " + response);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
    Thread.sleep(NAP);

    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(fileCreateRequest);
    TransactionID txId = body.getTransactionID();
    // get the file ID
    TransactionReceipt receipt = getTxReceipt(txId);
    if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
      throw new Exception(
          "Create file failed! The receipt retrieved receipt=" + receipt);
    }
    FileID fid = receipt.getFileID();
    log.info("GetTxReceipt: file ID = " + fid);
    AccountID newAccountID = getMultiSigAccount(1, 1, durationSeconds);
    long payerAccountBalance_before = getAccountBalance(newAccountID, queryPayerId, nodeID);
    List<Key> newWaclPubKeyList = fit.genWaclComplex(1, "single");
    memo = TestHelperComplex.getStringMemo(10);
    fileContents = new byte[8];
    random.nextBytes(fileContents);
    fileData = ByteString.copyFrom(fileContents);
    Transaction fileUpdateRequest = fit.updateFile(fid, newAccountID, nodeID,
        waclPubKeyList, newWaclPubKeyList, fileData, memo, fileExp);
    response = CryptoServiceTest.stub.updateFile(fileUpdateRequest);
    Thread.sleep(NAP);
    TransactionBody updateBody = TransactionBody.parseFrom(fileUpdateRequest.getBodyBytes());
    if (updateBody.getTransactionID() == null || !updateBody.getTransactionID()
        .hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    long updateTransactionFee = getTransactionFee(fileUpdateRequest);

    long payerAccountBalance_after = getAccountBalance(newAccountID, queryPayerId, nodeID);
    log.info(" Payer KeyList Count on List : " + payerKeyList.size());
    log.info(" Payer KeyList Count on Key: " + TestHelperComplex.acc2ComplexKeyMap.get(newAccountID).getKeyList()
        .getKeysCount());
    log.info("Signature Pair Count: " + fileUpdateRequest.getSigMap().getSigPairCount());
    String result = "File Update(size:4, Keys:1, memoSize: 1) transactionFee=" + updateTransactionFee;
    testResults.add(result);
    log.info(result);

    long feeVariance = (updateTransactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = FILE_UPDATE_SIZE_1_KEY_1_DUR_30 + feeVariance;
    long minTransactionFee = FILE_UPDATE_SIZE_1_KEY_1_DUR_30 - feeVariance;
    Assert.assertTrue(maxTransactionFee > updateTransactionFee);
    Assert.assertTrue(minTransactionFee < updateTransactionFee);

    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }

  public void fileUpdateFeeTestMultiSig() throws Throwable {
    List<Key> payerKeyList = new ArrayList<>();
    payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));

    byte[] fileContents = new byte[4];
    random.nextBytes(fileContents);
    ByteString fileData = ByteString.copyFrom(fileContents);
    List<Key> waclPubKeyList = fit.genWaclComplex(1, "single");
    long durationSeconds = 90 * 24 * 60 * 60; //1 Day
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
    String memo = TestHelperComplex.getStringMemo(1);
    Transaction fileCreateRequest = fit
        .createFile(payerID, nodeID, fileData, waclPubKeyList, fileExp, memo);
    TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
    log.info("FileCreate Response :: " + response);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
    Thread.sleep(NAP);

    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(fileCreateRequest);
    TransactionID txId = body.getTransactionID();
    // get the file ID
    TransactionReceipt receipt = getTxReceipt(txId);
    if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
      throw new Exception(
          "Create file failed! The receipt retrieved receipt=" + receipt);
    }
    FileID fid = receipt.getFileID();
    log.info("GetTxReceipt: file ID = " + fid);
    AccountID newAccountID = getMultiSigAccount(10, 10, durationSeconds);
    long payerAccountBalance_before = getAccountBalance(newAccountID, queryPayerId, nodeID);
    List<Key> newWaclPubKeyList = fit.genWaclComplex(10);
    memo = TestHelperComplex.getStringMemo(10);
    fileContents = new byte[1000];
    random.nextBytes(fileContents);
    fileData = ByteString.copyFrom(fileContents);
    Transaction fileUpdateRequest = fit.updateFile(fid, newAccountID, nodeID,
        waclPubKeyList, newWaclPubKeyList, fileData, memo, fileExp);

    response = CryptoServiceTest.stub.updateFile(fileUpdateRequest);
    Thread.sleep(NAP);
    TransactionBody updateBody = TransactionBody.parseFrom(fileUpdateRequest.getBodyBytes());
    if (updateBody.getTransactionID() == null || !updateBody.getTransactionID()
        .hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    long updateTransactionFee = getTransactionFee(fileUpdateRequest);

    long payerAccountBalance_after = getAccountBalance(newAccountID, queryPayerId, nodeID);
    log.info(" Payer KeyList Count on List : " + payerKeyList.size());
    log.info(" Payer KeyList Count on Key: " + TestHelperComplex.acc2ComplexKeyMap.get(newAccountID).getKeyList()
        .getKeysCount());
    log.info("Signature Pair Count: " + fileUpdateRequest.getSigMap().getSigPairCount());
    log.info("File Update transactionFee=" + updateTransactionFee);
    String result = "File Update(size:1000, Keys:10, memoSize: 10) transactionFee=" + updateTransactionFee;
    testResults.add(result);
    log.info(result);
    long feeVariance = (updateTransactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = FILE_UPDATE_SIZE_10_KEY_10_DUR_90 + feeVariance;
    long minTransactionFee = FILE_UPDATE_SIZE_10_KEY_10_DUR_90 - feeVariance;
    Assert.assertTrue(maxTransactionFee > updateTransactionFee);
    Assert.assertTrue(minTransactionFee < updateTransactionFee);
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }
  
  public void fileDeleteFeeTest() throws Throwable {
    List<Key> payerKeyList = new ArrayList<>();
    payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    // Craete Content for File
    byte[] fileContents = new byte[4];
    random.nextBytes(fileContents);
    ByteString fileData = ByteString.copyFrom(fileContents);
    
    // Prepare WACL Keys for file, same will be used to delete
    
    List<Key> waclPubKeyList = new ArrayList<Key>();
    List<PrivateKey> waclPrivKeyList = new ArrayList<PrivateKey>();
    
    genWacl(1, waclPubKeyList,  waclPrivKeyList);
    
        
    byte[] pubKeyBytes = waclPubKeyList.get(0).getEd25519().toByteArray();
    TestHelperComplex.pubKey2privKeyMap.put(Common.bytes2Hex(pubKeyBytes), waclPrivKeyList.get(0));
    
    // duration to keep file
    long durationSeconds = 30 * 24 * 60 * 60; //1 Day
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
    
    // memo for file
    String memo = TestHelperComplex.getStringMemo(1);
    Transaction fileCreateRequest = fit.createFile(payerID, nodeID, fileData, waclPubKeyList, fileExp, memo);
    TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
    log.info("FileCreate Response :: " + response);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
    Thread.sleep(NAP);

    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(fileCreateRequest);
    TransactionID txId = body.getTransactionID();
    // get the file ID
    TransactionReceipt receipt = getTxReceipt(txId);
    if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
      throw new Exception(
          "Create file failed! The receipt retrieved receipt=" + receipt);
    }
    FileID fid = receipt.getFileID();
    log.info("GetTxReceipt: file ID = " + fid);
    AccountID newAccountID = getMultiSigAccount(1, 1, durationSeconds);
    long payerAccountBalance_before = getAccountBalance(payerID, queryPayerId, nodeID);
        
    Transaction FileDeleteRequest = RequestBuilder.getFileDeleteBuilder(payerID.getAccountNum(), 0l, 0l,
            nodeID.getAccountNum(), 0l, 0l, TestHelper.getFileMaxFee(),
            timestamp, CryptoServiceTest.transactionDuration, true, "FileDelete", fid);
    
    
    
    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclPubKeyList)).build();
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(waclKey);
    Transaction txSigned = TransactionSigner.signTransactionComplexWithSigMap(FileDeleteRequest, keys, TestHelperComplex.pubKey2privKeyMap);

    log.info("\n-----------------------------------");
    log.info("FileDelete: request = " + txSigned);
    response = CryptoServiceTest.stub.deleteFile(txSigned);
    log.info("FileDelete Response :: " + response.getNodeTransactionPrecheckCode().name());
    
    
    Thread.sleep(NAP);
    TransactionBody deleteBody = TransactionBody.parseFrom(FileDeleteRequest.getBodyBytes());
    if (deleteBody.getTransactionID() == null || !deleteBody.getTransactionID()
        .hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    long deleteTransactionFee = getTransactionFee(FileDeleteRequest);

    long payerAccountBalance_after = getAccountBalance(payerID, queryPayerId, nodeID);
    log.info(" Payer KeyList Count on List : " + payerKeyList.size());
    log.info(" Payer KeyList Count on Key: " + TestHelperComplex.acc2ComplexKeyMap.get(newAccountID).getKeyList()
        .getKeysCount());
    log.info("Signature Pair Count: " + FileDeleteRequest.getSigMap().getSigPairCount());
    String result = "File Delete transactionFee=" + deleteTransactionFee;
    testResults.add(result);
    log.info(result);

    long feeVariance = (deleteTransactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = FILE_DELETE_KEY_1 + feeVariance;
    long minTransactionFee = FILE_DELETE_KEY_1 - feeVariance;
    Assert.assertTrue(maxTransactionFee > deleteTransactionFee);
    Assert.assertTrue(minTransactionFee < deleteTransactionFee);

    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }
  
  
  public void fileMultiDeleteFeeTest() throws Throwable {
    List<Key> payerKeyList = new ArrayList<>();
    payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    // Craete Content for File
    byte[] fileContents = new byte[4];
    random.nextBytes(fileContents);
    ByteString fileData = ByteString.copyFrom(fileContents);
    
    // Prepare WACL Keys for file, same will be used to delete
    
    List<Key> waclPubKeyList = new ArrayList<Key>();
    List<PrivateKey> waclPrivKeyList = new ArrayList<PrivateKey>();
    
    genWacl(10, waclPubKeyList,  waclPrivKeyList);
    
    for(int i =0; i<waclPubKeyList.size() ; i++) {
      byte[] pubKeyBytes = waclPubKeyList.get(i).getEd25519().toByteArray();
      TestHelperComplex.pubKey2privKeyMap.put(Common.bytes2Hex(pubKeyBytes), waclPrivKeyList.get(i));
     }
    
        
       
    // duration to keep file
    long durationSeconds = 30 * 24 * 60 * 60; //1 Day
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
    
    // memo for file
    String memo = TestHelperComplex.getStringMemo(1);
    Transaction fileCreateRequest = fit.createFile(payerID, nodeID, fileData, waclPubKeyList, fileExp, memo);
    TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
    log.info("FileCreate Response :: " + response);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
    Thread.sleep(NAP);

    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(fileCreateRequest);
    TransactionID txId = body.getTransactionID();
    // get the file ID
    TransactionReceipt receipt = getTxReceipt(txId);
    if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
      throw new Exception(
          "Create file failed! The receipt retrieved receipt=" + receipt);
    }
    FileID fid = receipt.getFileID();
    log.info("GetTxReceipt: file ID = " + fid);
    AccountID newAccountID = getMultiSigAccount(1, 1, durationSeconds);
    long payerAccountBalance_before = getAccountBalance(payerID, queryPayerId, nodeID);
        
    Transaction FileDeleteRequest = RequestBuilder.getFileDeleteBuilder(payerID.getAccountNum(), 0l,
            0l, nodeID.getAccountNum(), 0l, 0l, TestHelper.getFileMaxFee(),
            timestamp, CryptoServiceTest.transactionDuration, true, "FileDelete", fid);
    
    
    
    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclPubKeyList)).build();
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(waclKey);
    Transaction txSigned = TransactionSigner.signTransactionComplexWithSigMap(FileDeleteRequest, keys, TestHelperComplex.pubKey2privKeyMap);

    log.info("\n-----------------------------------");
    log.info("FileDelete: request = " + txSigned);
    response = CryptoServiceTest.stub.deleteFile(txSigned);
    log.info("FileDelete Response :: " + response.getNodeTransactionPrecheckCode().name());
    
    
    Thread.sleep(NAP);
    TransactionBody deleteBody = TransactionBody.parseFrom(FileDeleteRequest.getBodyBytes());
    if (deleteBody.getTransactionID() == null || !deleteBody.getTransactionID()
        .hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    long deleteTransactionFee = getTransactionFee(FileDeleteRequest);

    long payerAccountBalance_after = getAccountBalance(payerID, queryPayerId, nodeID);
    log.info(" Payer KeyList Count on List : " + payerKeyList.size());
    log.info(" Payer KeyList Count on Key: " + TestHelperComplex.acc2ComplexKeyMap.get(newAccountID).getKeyList()
        .getKeysCount());
    log.info("Signature Pair Count: " + FileDeleteRequest.getSigMap().getSigPairCount());
    String result = "File Delete transactionFee=" + deleteTransactionFee;
    testResults.add(result);
    log.info(result);

    long feeVariance = (deleteTransactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = FILE_DELETE_KEY_10 + feeVariance;
    long minTransactionFee = FILE_DELETE_KEY_10 - feeVariance;
    Assert.assertTrue(maxTransactionFee > deleteTransactionFee);
    Assert.assertTrue(minTransactionFee < deleteTransactionFee);

    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }
  
  public void fileAppendFeeTest() throws Throwable {
    List<Key> payerKeyList = new ArrayList<>();
    payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    // Craete Content for File
    byte[] fileContents = new byte[4];
    random.nextBytes(fileContents);
    ByteString fileData = ByteString.copyFrom(fileContents);
    
    // Prepare WACL Keys for file, same will be used to append
    
    List<Key> waclPubKeyList = new ArrayList<Key>();
    List<PrivateKey> waclPrivKeyList = new ArrayList<PrivateKey>();
    
    genWacl(1, waclPubKeyList,  waclPrivKeyList);
    
        
    byte[] pubKeyBytes = waclPubKeyList.get(0).getEd25519().toByteArray();
    TestHelperComplex.pubKey2privKeyMap.put(Common.bytes2Hex(pubKeyBytes), waclPrivKeyList.get(0));
    
    // duration to keep file
    long durationSeconds = 30 * 24 * 60 * 60; //1 Day
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
    
    // memo for file
    String memo = TestHelperComplex.getStringMemo(1);
    Transaction fileCreateRequest = fit.createFile(payerID, nodeID, fileData, waclPubKeyList, fileExp, memo);
    TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
    log.info("FileCreate Response :: " + response);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
    Thread.sleep(NAP);

    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(fileCreateRequest);
    TransactionID txId = body.getTransactionID();
    // get the file ID
    TransactionReceipt receipt = getTxReceipt(txId);
    if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
      throw new Exception(
          "Create file failed! The receipt retrieved receipt=" + receipt);
    }
    FileID fid = receipt.getFileID();
    log.info("GetTxReceipt: file ID = " + fid);
    AccountID newAccountID = getMultiSigAccount(1, 1, durationSeconds);
    long payerAccountBalance_before = getAccountBalance(payerID, queryPayerId, nodeID);
        
    Transaction fileAppendRequest = RequestBuilder.getFileAppendBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, CryptoServiceTest.transactionDuration, true,
        "FileAppend", fileData,fid);
    
    
    
    
    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclPubKeyList)).build();
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(waclKey);
    Transaction txSigned = TransactionSigner.signTransactionComplexWithSigMap(fileAppendRequest, keys, TestHelperComplex.pubKey2privKeyMap);
   
    log.info("\n-----------------------------------");
    log.info("FileAppend: request = " + txSigned);
    response = CryptoServiceTest.stub.appendContent(txSigned);
    log.info("FileAppend Response :: " + response.getNodeTransactionPrecheckCode().name());
    
    
    Thread.sleep(NAP);
    TransactionBody appendBody = TransactionBody.parseFrom(fileAppendRequest.getBodyBytes());
    if (appendBody.getTransactionID() == null || !appendBody.getTransactionID()
        .hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    long appendTransactionFee = getTransactionFee(fileAppendRequest);
    Thread.sleep(NAP);
    long payerAccountBalance_after = getAccountBalance(payerID, queryPayerId, nodeID);
    log.info(" Payer KeyList Count on List : " + payerKeyList.size());
    log.info(" Payer KeyList Count on Key: " + TestHelperComplex.acc2ComplexKeyMap.get(newAccountID).getKeyList()
        .getKeysCount());
    log.info("Signature Pair Count: " + fileAppendRequest.getSigMap().getSigPairCount());
    String result = "File Append transactionFee=" + appendTransactionFee;
    testResults.add(result);
    log.info(result);

    long feeVariance = (appendTransactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = FILE_APPEND_SIZE_4_KEY_1_DUR_30 + feeVariance;
    long minTransactionFee = FILE_APPEND_SIZE_4_KEY_1_DUR_30 - feeVariance;
    Assert.assertTrue(maxTransactionFee > appendTransactionFee);
    Assert.assertTrue(minTransactionFee < appendTransactionFee);

    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }
  
  public void fileAppendMultiSigFeeTest() throws Throwable {
    List<Key> payerKeyList = new ArrayList<>();
    payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    // Craete Content for File
    byte[] fileContents = new byte[4];
    random.nextBytes(fileContents);
    ByteString fileData = ByteString.copyFrom(fileContents);
    
    // Prepare WACL Keys for file, same will be used to append
    
    List<Key> waclPubKeyList = new ArrayList<Key>();
    List<PrivateKey> waclPrivKeyList = new ArrayList<PrivateKey>();
    
    genWacl(5, waclPubKeyList,  waclPrivKeyList);
    
     for(int i =0; i<waclPubKeyList.size() ; i++) {
      byte[] pubKeyBytes = waclPubKeyList.get(i).getEd25519().toByteArray();
      TestHelperComplex.pubKey2privKeyMap.put(Common.bytes2Hex(pubKeyBytes), waclPrivKeyList.get(i));
     }
    
    // duration to keep file
    long durationSeconds = 30 * 24 * 60 * 60; //1 Day
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
    
    // memo for file
    String memo = TestHelperComplex.getStringMemo(1);
    Transaction fileCreateRequest = fit.createFile(payerID, nodeID, fileData, waclPubKeyList, fileExp, memo);
    TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
    log.info("FileCreate Response :: " + response);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
    Thread.sleep(NAP);

    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(fileCreateRequest);
    TransactionID txId = body.getTransactionID();
    // get the file ID
    TransactionReceipt receipt = getTxReceipt(txId);
    if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
      throw new Exception(
          "Create file failed! The receipt retrieved receipt=" + receipt);
    }
    FileID fid = receipt.getFileID();
    log.info("GetTxReceipt: file ID = " + fid);
    AccountID newAccountID = getMultiSigAccount(1, 1, durationSeconds);
    long payerAccountBalance_before = getAccountBalance(payerID, queryPayerId, nodeID);
        
    Transaction fileAppendRequest = RequestBuilder.getFileAppendBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, CryptoServiceTest.transactionDuration, true,
        "FileAppend", fileData,fid);
     
    
    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclPubKeyList)).build();
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(waclKey);
    Transaction txSigned = TransactionSigner.signTransactionComplexWithSigMap(fileAppendRequest, keys, TestHelperComplex.pubKey2privKeyMap);
   
    log.info("\n-----------------------------------");
    log.info("FileAppend: request = " + txSigned);
    response = CryptoServiceTest.stub.appendContent(txSigned);
    log.info("FileAppend Response :: " + response.getNodeTransactionPrecheckCode().name());
    
    
    Thread.sleep(NAP);
    TransactionBody appendBody = TransactionBody.parseFrom(fileAppendRequest.getBodyBytes());
    if (appendBody.getTransactionID() == null || !appendBody.getTransactionID()
        .hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    long appendTransactionFee = getTransactionFee(fileAppendRequest);
    Thread.sleep(NAP);
    long payerAccountBalance_after = getAccountBalance(payerID, queryPayerId, nodeID);
    log.info(" Payer KeyList Count on List : " + payerKeyList.size());
    log.info(" Payer KeyList Count on Key: " + TestHelperComplex.acc2ComplexKeyMap.get(newAccountID).getKeyList()
        .getKeysCount());
    log.info("Signature Pair Count: " + fileAppendRequest.getSigMap().getSigPairCount());
    String result = "File Append transactionFee=" + appendTransactionFee;
    testResults.add(result);
    log.info(result);

    long feeVariance = (appendTransactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = FILE_APPEND_SIZE_4_KEY_10_DUR_30 + feeVariance;
    long minTransactionFee = FILE_APPEND_SIZE_4_KEY_10_DUR_30 - feeVariance;
    Assert.assertTrue(maxTransactionFee > appendTransactionFee);
    Assert.assertTrue(minTransactionFee < appendTransactionFee);

    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }
  
  
  
 

}
