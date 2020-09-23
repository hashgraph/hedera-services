package com.hedera.services.legacy.file;

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
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.FileServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Regression tests for get file info API post FCFS artifact changes.
 *
 * @author Hua Li
 * Created on 2019-01-28
 */
public class FileGetInfoTest extends FileServiceTest {

  private static final Logger log = LogManager.getLogger(FileGetInfoTest.class);
  private static final long MAX_TX_FEE = TestHelper.getCryptoMaxFee();
  private static String testConfigFilePath = "config/umbrellaTest.properties";
  private String LOG_PREFIX = "\n>>>>>>>>>>>> ";
  private String INCORRECT_WACL_SIG = "INCORRECT_WACL_SIG";
  private String POSITIVE = "POSITIVE";
  private String INVALID_FILE_ID = "INVALID_FILE_ID";
  private String INVALID_SIGNATURE_TYPE_MISMATCHING_KEY = "INVALID_SIGNATURE_TYPE_MISMATCHING_KEY";
  private String INVALID_SIGNATURE_COUNT_MISMATCHING_KEY = "INVALID_SIGNATURE_COUNT_MISMATCHING_KEY";

  public FileGetInfoTest() {
    super(testConfigFilePath);
  }

  public static void main(String[] args) throws Throwable {
    FileGetInfoTest tester = new FileGetInfoTest();
    tester.init();
    tester.createFileThenGetFileInfoTest();
    tester.appendFileThenGetFileInfoTest();
    tester.updateFileThenGetFileInfoTest();
    tester.deleteFileThenGetFileInfoTest();
    tester.invalidFileIDTest();
  }

  /**
   * Initialize the tests.
   */
  public void init() throws Throwable {
    setUp();
    CryptoServiceTest.payerAccounts = accountCreatBatch4Payer(1); // accounts as payers
  }

  /**
   * Runs file creation and then get file info.
   */
  public void createFileThenGetFileInfoTest() {
    try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();
      log.info(LOG_PREFIX + "Create file: creating a file with 1024 bytes ...");
      int fileSizeK = 1;
      String fileType = FileServiceTest.fileTypes[0];
      byte[] fileContents = genFileContent(fileSizeK, fileType);
      ByteString fileData = ByteString.copyFrom(fileContents);
      List<Key> waclPubKeyList = genWaclComplex(FileServiceTest.NUM_WACL_KEYS);

      // creates a file
      TransactionReceipt receipt = createFile(POSITIVE, payerID, nodeID, fileData, waclPubKeyList);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
      FileID fid = receipt.getFileID();
      Assert.assertNotNull(fid);
      Assert.assertTrue(fid.getFileNum() > 0);
      
      // check file info
      FileInfo fi = getFileInfo(fid, payerID, nodeID);
      Assert.assertEquals(false, fi.getDeleted());
      log.info(LOG_PREFIX + "Create file: get file info test passed! fi = " + fi);
      fid2waclMap.put(fid, waclPubKeyList);
    } catch (Throwable e) {
      log.error("fileCreate error!", e);
    }
  }
 
  /**
   * Test case where the fileID for getting file info is invalid.
   */
  public void invalidFileIDTest() {
    try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();
      log.info(LOG_PREFIX + "Create file: creating a file with 1024 bytes ...");
      FileID fid = FileID.newBuilder().setFileNum(1).setRealmNum(1).setShardNum(0).build(); // non-existent
      getFileInfo(fid, payerID, nodeID);
    } catch (Throwable e) {
      log.info(LOG_PREFIX + "Invalid FileID Test: passed! Caught expected exception = " + e);
    }
  }
  

  /**
   * Runs file appending and then check file size.
   */
  public void appendFileThenGetFileInfoTest() {
    try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();
      log.info(LOG_PREFIX + "Append file: appending the file with 1024 bytes ...");
      int fileSizeK = 1;
      String fileType = FileServiceTest.fileTypes[0];
      byte[] fileContents = genFileContent(fileSizeK, fileType);
      ByteString fileData = ByteString.copyFrom(fileContents);
      FileID fileID = fid2waclMap.keys().nextElement();
      List<Key> waclPubKeyList = fid2waclMap.get(fileID);

      // append file
      TransactionReceipt receipt = appendFile(POSITIVE, payerID, nodeID, fileID, fileData,
          waclPubKeyList);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

      // check file info
      FileInfo fi = getFileInfo(fileID, payerID, nodeID);
      Assert.assertEquals(false, fi.getDeleted());
      log.info(LOG_PREFIX + "Append file: get file info test passed! fi = " + fi);
    } catch (Throwable e) {
      log.error("fileAppend error!", e);
    }

  }

  /**
   * Runs file updating and then check file size.
   */
  public void updateFileThenGetFileInfoTest() {
    try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();
      log.info(LOG_PREFIX + "Update file: resetting the file to 1024 bytes ...");
      int fileSizeK = 1;
      String fileType = FileServiceTest.fileTypes[0];
      byte[] fileContents = genFileContent(fileSizeK, fileType);
      ByteString fileData = ByteString.copyFrom(fileContents);
      FileID fileID = fid2waclMap.keys().nextElement();
      List<Key> waclPubKeyList = fid2waclMap.get(fileID);
      List<Key> newWaclPubKeyList = genWaclComplex(FileServiceTest.NUM_WACL_KEYS);

      // update file
      TransactionReceipt receipt = updateFile(POSITIVE, fileID, payerID, nodeID, fileData,
          waclPubKeyList, newWaclPubKeyList);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
      
      // check file info
      FileInfo fi = getFileInfo(fileID, payerID, nodeID);
      Assert.assertEquals(false, fi.getDeleted());
      fid2waclMap.put(fileID, newWaclPubKeyList);
      log.info(LOG_PREFIX + "Update file: get file info test passed! fi = " + fi);

    } catch (Throwable e) {
      log.error("fileUpdate error!", e);
    }

  }

  /**
   * Runs file deleting and then get file info.
   */
  public void deleteFileThenGetFileInfoTest() {
    try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();
      log.info(LOG_PREFIX + "Delete file: deleting the file contents ...");
      FileID fileID = fid2waclMap.keys().nextElement();
      List<Key> waclPubKeyList = fid2waclMap.get(fileID);

      // delete a file
      TransactionReceipt receipt = deleteFile(POSITIVE, fileID, payerID, nodeID, waclPubKeyList);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

      // get file info
      try {
        FileInfo fi = getFileInfo(fileID, payerID, nodeID);
        log.error(LOG_PREFIX + ":) Delete file: get file info test failed! fileInfo=" + fi);
      } catch(Exception ex) {
        String msg = ex.getMessage();
        Assert.assertTrue(msg.contains("FILE_DELETED"));
        log.info(LOG_PREFIX + "Delete file: get file info test passed!");
      }
    } catch (Throwable e) {
      log.error("fileDelete error!", e);
    }
  }

  /**
   * Appends a file with data.
   *
   * @param scenario test scenario string
   * @return TransactionReceipt object
   */
  public TransactionReceipt appendFile(String scenario, AccountID payerID, AccountID nodeID,
      FileID fid, ByteString fileData, List<Key> waclKeys) throws Throwable {
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
  
    if (scenario.equals(INVALID_FILE_ID)) {
      fid = FileID.newBuilder().setFileNum(0).setRealmNum(0).setShardNum(0)
          .build(); // non-existent file ID
    }
  
    Transaction fileAppendRequest = RequestBuilder.getFileAppendBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), MAX_TX_FEE, timestamp, transactionDuration, true, "FileAppend",
        fileData, fid);
    TransactionBody body = TransactionBody.parseFrom(fileAppendRequest.getBodyBytes());
    TransactionID txId = body.getTransactionID();
  
    Key payerKey = acc2ComplexKeyMap.get(payerID);
    Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeys)).build();
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    if (scenario.equals(INCORRECT_WACL_SIG)) {
      keys.add(payerKey); // use payer sig as wacl sig
    } else {
      keys.add(waclKey);
    }
    Transaction txSigned = TransactionSigner
        .signTransactionComplexWithSigMap(fileAppendRequest, keys, pubKey2privKeyMap);
  
    log.info("\n-----------------------------------");
    log.info("FileAppend: request = " + txSigned);
  
    FileServiceBlockingStub stub = getStub(nodeID);
    TransactionResponse response = stub.appendContent(txSigned);
    log.info("FileAppend Response :: " + response);
    Assert.assertNotNull(response);
  
    Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
    cache.addTransactionID(txId);
  
    TransactionReceipt receipt = getTxReceipt(txId);
    return receipt;
  }

  /**
   * Creates a file on the ledger.
   *
   * @param scenario test scenario string
   * @return TransactionReceipt object
   */
  public TransactionReceipt createFile(String scenario, AccountID payerID, AccountID nodeID,
      ByteString fileData,
      List<Key> waclKeyList) throws Throwable {
    log.debug("@@@ upload file: file size in byte = " + fileData.size());
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(DAY_SEC);
  
    Transaction FileCreateRequest = RequestBuilder.getFileCreateBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), MAX_TX_FEE, timestamp, transactionDuration, true, "FileCreate",
        fileData, fileExp, waclKeyList);
    TransactionBody body = TransactionBody.parseFrom(FileCreateRequest.getBodyBytes());
    TransactionID txId = body.getTransactionID();
  
    Key payerKey = acc2ComplexKeyMap.get(payerID);
    if (scenario.equals(INVALID_SIGNATURE_TYPE_MISMATCHING_KEY)) {
      if (payerKey.hasKeyList()) {
        // change payer key type from key list to threshold key
        payerKey = Key.newBuilder().setThresholdKey(
            ThresholdKey.newBuilder().setKeys(payerKey.getKeyList())
                .setThreshold(payerKey.getKeyList().getKeysCount())).build();
      } else if (payerKey.hasThresholdKey()) {
        // change payer key type from threshold key to a KeyList
        payerKey = Key.newBuilder().setKeyList(
            KeyList.newBuilder().addAllKeys(payerKey.getThresholdKey().getKeys().getKeysList()))
            .build();
      } else {
        // change payer key from base key to a keylist
        payerKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(payerKey)).build();
      }
    } else if (scenario.equals(INVALID_SIGNATURE_COUNT_MISMATCHING_KEY)) {
      if (payerKey.hasKeyList() || payerKey.hasThresholdKey()) {
        List<Key> existingKeys = null;
        if (payerKey.hasKeyList()) {
          existingKeys = payerKey.getKeyList().getKeysList();
          // add one more key to the key list
          KeyList newKeyList = KeyList.newBuilder().addAllKeys(existingKeys)
              .addKeys(existingKeys.get(0)).build();
          payerKey = Key.newBuilder().setKeyList(newKeyList).build();
        } else if (payerKey.hasThresholdKey()) {
          existingKeys = payerKey.getThresholdKey().getKeys().getKeysList();
          // add one more key to the key list
          KeyList newKeyList = KeyList.newBuilder().addAllKeys(existingKeys)
              .addKeys(existingKeys.get(0)).build();
          payerKey = Key.newBuilder().setThresholdKey(ThresholdKey.newBuilder().setKeys(newKeyList)
              .setThreshold(payerKey.getThresholdKey().getThreshold())).build();
        }
      }
    }
  
    Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeyList)).build();
    List<Key> keys = new ArrayList<Key>();
  
    keys.add(payerKey);
    if (scenario.equals(INCORRECT_WACL_SIG)) {
      keys.add(payerKey); // use payer sig as wacl sig
    } else {
      keys.add(waclKey);
    }
    Transaction filesigned = TransactionSigner
        .signTransactionComplexWithSigMap(FileCreateRequest, keys, pubKey2privKeyMap);
  
    log.debug("\n-----------------------------------");
    log.debug("FileCreate: request = " + filesigned);
  
    FileServiceBlockingStub stub = getStub(nodeID);
    TransactionResponse response = stub.createFile(filesigned);
    log.debug("FileCreate Response :: " + response);
    Assert.assertNotNull(response);
  
    TransactionReceipt receipt = null;
    if (scenario.equals(INVALID_SIGNATURE_TYPE_MISMATCHING_KEY)) {
      log.info(LOG_PREFIX + "Create file: Negative test with payer sig TYPE mismatch: response="
          + response);
      Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE_TYPE_MISMATCHING_KEY_VALUE,
          response.getNodeTransactionPrecheckCodeValue());
    } else if (scenario.equals(INVALID_SIGNATURE_COUNT_MISMATCHING_KEY)) {
      if (payerKey.hasKeyList() || payerKey.hasThresholdKey()) {
        log.info(LOG_PREFIX + "Create file: Negative test with payer sig COUNT mismatch: response="
            + response);
        Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY_VALUE,
            response.getNodeTransactionPrecheckCodeValue());
      }
    } else {
      Assert
          .assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
      cache.addTransactionID(txId);
  
      receipt = getTxReceipt(txId);
    }
  
    return receipt;
  }

  /**
   * Deletes a file.
   *
   * @param scenario test scenario string
   * @param fid the ID of the file to be updated
   * @param payerID the fee payer ID
   * @param nodeID the node ID
   * @param waclKeyList the file creation WACL public key list
   * @return transaction receipt
   */
  protected TransactionReceipt deleteFile(String scenario, FileID fid, AccountID payerID,
      AccountID nodeID, List<Key> waclKeyList) throws Throwable {
    if (scenario.equals(INVALID_FILE_ID)) {
      fid = FileID.newBuilder().setFileNum(0).setRealmNum(0).setShardNum(0)
          .build(); // non-existent file ID
    }
  
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    Transaction FileDeleteRequest = RequestBuilder.getFileDeleteBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), MAX_TX_FEE, timestamp, transactionDuration, true, "FileDelete", fid);
  
    Key payerKey = acc2ComplexKeyMap.get(payerID);
    Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeyList)).build();
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    if (scenario.equals(INCORRECT_WACL_SIG)) {
      keys.add(payerKey); // use payer sig as wacl sig
    } else {
      keys.add(waclKey);
    }
    Transaction txSigned = TransactionSigner
        .signTransactionComplexWithSigMap(FileDeleteRequest, keys, pubKey2privKeyMap);
  
    log.info("\n-----------------------------------");
    log.info("FileDelete: request = " + txSigned);
  
    FileServiceBlockingStub stub = getStub(nodeID);
    TransactionResponse response = stub.deleteFile(txSigned);
    log.info("FileDelete Response :: " + response.getNodeTransactionPrecheckCodeValue());
    Assert.assertNotNull(response);
    TransactionBody body = TransactionBody.parseFrom(FileDeleteRequest.getBodyBytes());
    TransactionID txId = body.getTransactionID();
    cache.addTransactionID(txId);
    TransactionReceipt receipt = getTxReceipt(txId);
    return receipt;
  }

  /**
   * Updates a file.
   *
   * @param scenario test scenario string
   * @param fid the ID of the file to be updated
   * @param payerID the fee payer ID
   * @param nodeID the node ID
   * @param newWaclKeyList the new wacl keys to replace existing ones
   * @return TransactionReceipt object
   */
  protected TransactionReceipt updateFile(String scenario, FileID fid, AccountID payerID,
      AccountID nodeID, ByteString fileData, List<Key> oldWaclKeyList, List<Key> newWaclKeyList)
      throws Throwable {
    if (scenario.equals(INVALID_FILE_ID)) {
      fid = FileID.newBuilder().setFileNum(0).setRealmNum(0).setShardNum(0)
          .build(); // non-existent file ID
    }
  
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(DAY_SEC * 10);
    KeyList wacl = KeyList.newBuilder().addAllKeys(newWaclKeyList).build();
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    Transaction FileUpdateRequest = RequestBuilder.getFileUpdateBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), MAX_TX_FEE, timestamp, fileExp, transactionDuration, true,
        "FileUpdate", fileData, fid, wacl);
  
    Key payerKey = acc2ComplexKeyMap.get(payerID);
    Key existingWaclKey = Key.newBuilder()
        .setKeyList(KeyList.newBuilder().addAllKeys(oldWaclKeyList)).build();
    Key newWaclKey = Key.newBuilder().setKeyList(wacl).build();
  
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(existingWaclKey);
    if (scenario.equals(INCORRECT_WACL_SIG)) {
      keys.add(payerKey); // use payer sig as new wacl sig
    } else {
      keys.add(newWaclKey);
    }
    Transaction txSigned = TransactionSigner
        .signTransactionComplexWithSigMap(FileUpdateRequest, keys, pubKey2privKeyMap);
  
    log.info("\n-----------------------------------");
    log.info(
        "FileUpdate: input data = " + fileData + "\nexpirationTime = " + fileExp + "\nWACL keys = "
            + newWaclKeyList);
    log.info("FileUpdate: request = " + txSigned);
  
    FileServiceBlockingStub stub = getStub(nodeID);
    TransactionResponse response = stub.updateFile(txSigned);
    log.info("FileUpdate with data, exp, and wacl respectively, Response :: "
        + response.getNodeTransactionPrecheckCodeValue());
    Assert.assertNotNull(response);
    TransactionBody body = TransactionBody.parseFrom(FileUpdateRequest.getBodyBytes());
    TransactionID txId = body.getTransactionID();
    cache.addTransactionID(txId);
  
    TransactionReceipt receipt = getTxReceipt(txId);
    return receipt;
  }
}
