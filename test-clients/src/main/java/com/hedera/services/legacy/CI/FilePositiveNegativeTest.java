package com.hedera.services.legacy.CI;

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
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.FileServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.CustomProperties;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests with negative file service test.
 *
 * @author Hua Li Created on 2018-11-26
 */
public class FilePositiveNegativeTest extends FileServiceTest {

  private static final Logger log = LogManager.getLogger(FilePositiveNegativeTest.class);
  private static final long MAX_TX_FEE = TestHelper.getFileMaxFee();
  private static String testConfigFilePath = "config/umbrellaTest.properties";
  private static String estimatedFeeProperties = "config/estimatedFee.properties";
  private String LOG_PREFIX = "\n>>>>>>>>>>>> ";
  private String INCORRECT_WACL_SIG = "INCORRECT_WACL_SIG";
  private String POSITIVE = "POSITIVE";
  private String INVALID_FILE_ID = "INVALID_FILE_ID";
  private String INVALID_SIGNATURE_TYPE_MISMATCHING_KEY = "INVALID_SIGNATURE_TYPE_MISMATCHING_KEY";
  private String INVALID_SIGNATURE_COUNT_MISMATCHING_KEY =
      "INVALID_SIGNATURE_COUNT_MISMATCHING_KEY";
  private static CustomProperties testProps;
  private static int QUERY_GET_FILE_INFO_FEE;
  private static int QUERY_GET_FILE_CONTENT_FEE;
  private static int QUERY_DEFAULT_FEE = 8;


  public FilePositiveNegativeTest() {
    super(testConfigFilePath);
  }

  public static void main(String[] args) throws Throwable {
    FilePositiveNegativeTest tester = new FilePositiveNegativeTest();
    tester.init();
    tester.createFileTest();
    tester.appendFileTest();
    tester.updateFileTest();
    tester.recordFieldsCheckTest();
    tester.deleteFileTest();
    tester.queryFileTest();
  }

  /**
   * Initialize the tests.
   */
  public void init() throws Throwable {
    testProps = new CustomProperties(estimatedFeeProperties, false);
    setUp();
    QUERY_GET_FILE_INFO_FEE = testProps.getInt("queryGetFileInfoSig_1", QUERY_DEFAULT_FEE);
    QUERY_GET_FILE_CONTENT_FEE = testProps.getInt("queryGetFileContent_Sig_1", QUERY_DEFAULT_FEE);
    CryptoServiceTest.payerAccounts = accountCreatBatch(1);

  }

  /**
   * Runs file creation negative tests.
   * @throws Throwable 
   */
  public void createFileTest() throws Throwable {
     try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();

      int fileSizeK = 1;
      String fileType = FileServiceTest.fileTypes[0];
      byte[] fileContents = genFileContent(fileSizeK, fileType);
      ByteString fileData = ByteString.copyFrom(fileContents);
      List<Key> waclPubKeyList = genWaclComplex(FileServiceTest.NUM_WACL_KEYS);

      // positive test
      TransactionReceipt receipt = createFile(POSITIVE, payerID, nodeID, fileData, waclPubKeyList);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
      FileID fid = receipt.getFileID();
      Assert.assertNotNull(fid);
      Assert.assertTrue(fid.getFileNum() > 0);
      FileInfo fi = getFileInfo(fid, payerID, nodeID);
      Assert.assertEquals(false, fi.getDeleted());
      log.info(LOG_PREFIX + "Create file: Positive test passed! file ID = " + fid);
      fid2waclMap.put(fid, waclPubKeyList);

      // negative test 1: incorrect signature for wacl keys
      receipt = createFile(INCORRECT_WACL_SIG, payerID, nodeID, fileData, waclPubKeyList);
      String status = receipt.getStatus().name();
      boolean actual = ResponseCodeEnum.INVALID_SIGNATURE.name().equals(status)
          || ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY.name().equals(status);
      Assert.assertEquals(true, actual);
      fid = receipt.getFileID();
      Assert.assertFalse(fid.getFileNum() > 0);
      log.info(LOG_PREFIX
          + "Create file: Negative test with incorrect signature for wacl keys passed! tx status = "
          + status);

      // payer sig type mismatch
      createFile(INVALID_SIGNATURE_TYPE_MISMATCHING_KEY, payerID, nodeID, fileData, waclPubKeyList);
      log.info(LOG_PREFIX + "Create file: Negative test with payer sig type mismatch passed!");

      // payer sig count mismatch
      createFile(INVALID_SIGNATURE_COUNT_MISMATCHING_KEY, payerID, nodeID, fileData,
          waclPubKeyList);
      log.info(LOG_PREFIX + "Create file: Negative test with payer sig count mismatch passed!");
    } catch (Throwable e) {
      log.error("fileCreate error!", e);
      throw e;
    }
  }

  /**
   * Creates a file on the ledger.
   *
   * @param scenario test scenario string
   * @return TransactionReceipt object
   */
  public TransactionReceipt createFile(String scenario, AccountID payerID, AccountID nodeID,
      ByteString fileData, List<Key> waclKeyList) throws Throwable {
    log.debug("@@@ upload file: file size in byte = " + fileData.size());
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
  Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(fileDuration);

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
        payerKey = Key.newBuilder().setThresholdKey(ThresholdKey.newBuilder()
            .setKeys(payerKey.getKeyList()).setThreshold(payerKey.getKeyList().getKeysCount()))
            .build();
      } else if (payerKey.hasThresholdKey()) {
        // change payer key type from threshold key to a KeyList
        payerKey = Key.newBuilder()
            .setKeyList(
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
          // remove one key from the key list
          List<Key> oneKeyLess = new ArrayList<>();
          oneKeyLess.addAll(existingKeys);
          oneKeyLess.remove(0);
          KeyList newKeyList = KeyList.newBuilder().addAllKeys(oneKeyLess).build();
          payerKey = Key.newBuilder().setKeyList(newKeyList).build();
        } else if (payerKey.hasThresholdKey()) {
          existingKeys = payerKey.getThresholdKey().getKeys().getKeysList();
          // remove one key from the key list
          List<Key> oneKeyLess = new ArrayList<>();
          oneKeyLess.addAll(existingKeys);
          int threshold = payerKey.getThresholdKey().getThreshold();
          for (int i = 0; i < (existingKeys.size() - threshold + 1); i++) {
			  oneKeyLess.remove(0);
		  }
          KeyList newKeyList = KeyList.newBuilder().addAllKeys(oneKeyLess).build();
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
    Transaction filesigned =
        TransactionSigner.signTransactionComplexWithSigMap(FileCreateRequest, keys, pubKey2privKeyMap);

    log.debug("\n-----------------------------------");
    log.debug("FileCreate: request = " + filesigned);

    FileServiceBlockingStub stub = getStub(nodeID);
    TransactionResponse response = stub.createFile(filesigned);
    log.debug("FileCreate Response :: " + response);
    Assert.assertNotNull(response);

    TransactionReceipt receipt = null;
    if (scenario.equals(INVALID_SIGNATURE_TYPE_MISMATCHING_KEY)) {
      // When create file using SignatureMap, this condition does not cause a problem
      Assert.assertEquals(ResponseCodeEnum.OK_VALUE,
          response.getNodeTransactionPrecheckCodeValue());
    } else if (scenario.equals(INVALID_SIGNATURE_COUNT_MISMATCHING_KEY)) {
      if (payerKey.hasKeyList() || payerKey.hasThresholdKey()) {
        log.info(LOG_PREFIX
            + "Create file using SignatureMap: Negative test with payer sig COUNT mismatch: response="
            + response);
        Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE_VALUE,
            response.getNodeTransactionPrecheckCodeValue());
      }
    } else {
      Assert.assertEquals(ResponseCodeEnum.OK,
          response.getNodeTransactionPrecheckCode());
      cache.addTransactionID(txId);

      receipt = getTxReceipt(txId);
    }

    return receipt;
  }

  /**
   * Appends a file with data.
   *
   * @param scenario test scenario string
   * @return TransactionReceipt object
   */
  public TransactionReceipt appendFile(String scenario, AccountID payerID, AccountID nodeID,
      FileID fid, ByteString fileData, List<Key> waclKeys) throws Throwable {
    return appendFile(scenario, payerID, nodeID, fid, fileData, waclKeys, null);
  }

  public TransactionReceipt appendFile(String scenario, AccountID payerID, AccountID nodeID,
      FileID fid, ByteString fileData, List<Key> waclKeys, Transaction[] txHolder)
      throws Throwable {

    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();

    if (scenario.equals(INVALID_FILE_ID)) {
      fid = FileID.newBuilder().setFileNum(0).setRealmNum(0).setShardNum(0).build(); // non-existent
                                                                                     // file ID
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
    Transaction txSigned =
        TransactionSigner.signTransactionComplexWithSigMap(fileAppendRequest, keys, pubKey2privKeyMap);

    if (txHolder != null) {
		txHolder[0] = txSigned;
	}

    log.info("\n-----------------------------------");
    log.info("  -- FileID: " + fid.getFileNum());
    log.info("  -- Payer: " + payerID.getAccountNum());
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
   * Checks if the record fields of a failed transaction are instantiated.
   * @throws Throwable 
   */
  public void recordFieldsCheckTest() throws Throwable {
    try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();
      int fileSizeK = 1;
      String fileType = FileServiceTest.fileTypes[0];
      byte[] fileContents = genFileContent(fileSizeK, fileType);
      ByteString fileData = ByteString.copyFrom(fileContents);
      FileID fileID = fid2waclMap.keys().nextElement();
      List<Key> waclPubKeyList = fid2waclMap.get(fileID);

      // positive test
      Transaction[] txHolder = new Transaction[1];
      TransactionReceipt receipt =
          appendFile(POSITIVE, payerID, nodeID, fileID, fileData, waclPubKeyList, txHolder);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
      FileInfo fi = getFileInfo(fileID, payerID, nodeID);
      Assert.assertEquals(false, fi.getDeleted());

      // get record and check its fields
      checkRecord(txHolder[0], payerID, nodeID);

      log.info(LOG_PREFIX + "Append file: Positive test passed!");

      // negative test 1: incorrect signature for wacl keys
      receipt = appendFile(INCORRECT_WACL_SIG, payerID, nodeID, fileID, fileData, waclPubKeyList,
          txHolder);
      String status = receipt.getStatus().name();
      boolean actual = ResponseCodeEnum.INVALID_SIGNATURE.name().equals(status)
          || ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY.name().equals(status);
      Assert.assertEquals(true, actual);

      // get record and check its fields
      checkRecord(txHolder[0], payerID, nodeID);

      log.info(LOG_PREFIX
          + "Append file: Negative test with incorrect signature for wacl keys passed! tx status = "
          + status);

      // negative test 2: invalid file ID, post-consensus check
      receipt =
          appendFile(INVALID_FILE_ID, payerID, nodeID, fileID, fileData, waclPubKeyList, txHolder);
      status = receipt.getStatus().name();
      Assert.assertEquals(ResponseCodeEnum.INVALID_FILE_ID.name(), status);

      // get record and check its fields
      checkRecord(txHolder[0], payerID, nodeID);

      log.info(LOG_PREFIX + "Append file: Negative test with invalid file ID passed!");
    } catch (Throwable e) {
      log.error("recordFieldsCheckTest error!", e);
      throw e;
    }

  }

  /**
   * Checks if record fields are instantiated.
   * 
   * @param transaction
   * @param payerID
   * @param nodeID
   * @throws Exception
   */
  private void checkRecord(Transaction transaction, AccountID payerID, AccountID nodeID)
      throws Exception {
    TransactionBody body =
        com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(transaction);
    TransactionRecord record = getTransactionRecord(body.getTransactionID(), payerID, nodeID);
    CommonUtils.checkRecord(record, body);
  }

  /**
   * Runs file updating negative tests.
   * @throws Throwable 
   */
  public void updateFileTest() throws Throwable {
    try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();
      int fileSizeK = 1;
      String fileType = FileServiceTest.fileTypes[0];
      byte[] fileContents = genFileContent(fileSizeK, fileType);
      ByteString fileData = ByteString.copyFrom(fileContents);
      FileID fileID = fid2waclMap.keys().nextElement();
      List<Key> waclPubKeyList = fid2waclMap.get(fileID);
      List<Key> newWaclPubKeyList = genWaclComplex(FileServiceTest.NUM_WACL_KEYS);

      // positive test
      TransactionReceipt receipt = updateFile(POSITIVE, fileID, payerID, nodeID, fileData,
          waclPubKeyList, newWaclPubKeyList);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
      FileInfo fi = getFileInfo(fileID, payerID, nodeID);
      // Assert.assertEquals(1024, fi.getSize());
      Assert.assertEquals(false, fi.getDeleted());
      fid2waclMap.put(fileID, newWaclPubKeyList);
      log.info(LOG_PREFIX + "Update file: Positive test passed!");

      // negative test 1: incorrect signature for wacl keys
      receipt = updateFile(INCORRECT_WACL_SIG, fileID, payerID, nodeID, fileData, waclPubKeyList,
          newWaclPubKeyList);
      String status = receipt.getStatus().name();
      boolean actual = ResponseCodeEnum.INVALID_SIGNATURE.name().equals(status)
          || ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY.name().equals(status);
      Assert.assertEquals(true, actual);
      log.info(LOG_PREFIX
          + "Update file: Negative test with incorrect signature for wacl keys passed! tx status = "
          + status);

      // negative test 2: invalid file ID, post-consensus check
      receipt = updateFile(INVALID_FILE_ID, fileID, payerID, nodeID, fileData, waclPubKeyList,
          newWaclPubKeyList);
      status = receipt.getStatus().name();
      Assert.assertEquals(ResponseCodeEnum.INVALID_FILE_ID.name(), status);
      log.info(LOG_PREFIX + "Update file: Negative test with invalid file ID passed!");
    } catch (Throwable e) {
      log.error("fileUpdate error!", e);
      throw e;
    }

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
      fid = FileID.newBuilder().setFileNum(0).setRealmNum(0).setShardNum(0).build(); // non-existent
                                                                                     // file ID
    }

  Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(fileDuration + 30);
    KeyList wacl = KeyList.newBuilder().addAllKeys(newWaclKeyList).build();
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    Transaction FileUpdateRequest = RequestBuilder.getFileUpdateBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), MAX_TX_FEE, timestamp, fileExp, transactionDuration, true,
        "FileUpdate", fileData, fid, wacl);

    Key payerKey = acc2ComplexKeyMap.get(payerID);
    Key existingWaclKey =
        Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(oldWaclKeyList)).build();
    Key newWaclKey = Key.newBuilder().setKeyList(wacl).build();

    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(existingWaclKey);
    if (scenario.equals(INCORRECT_WACL_SIG)) {
      keys.add(payerKey); // use payer sig as new wacl sig
    } else {
      keys.add(newWaclKey);
    }
    Transaction txSigned =
        TransactionSigner.signTransactionComplexWithSigMap(FileUpdateRequest, keys, pubKey2privKeyMap);

    log.info("\n-----------------------------------");
    log.info("FileUpdate: input data = " + fileData + "\nexpirationTime = " + fileExp
        + "\nWACL keys = " + newWaclKeyList);
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

  /**
   * Runs file deleting negative tests.
   * @throws Throwable 
   */
  public void deleteFileTest() throws Throwable {
    try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();
      FileID fileID = fid2waclMap.keys().nextElement();
      List<Key> waclPubKeyList = fid2waclMap.get(fileID);

      // positive test
      TransactionReceipt receipt = deleteFile(POSITIVE, fileID, payerID, nodeID, waclPubKeyList);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
      getFileInfoDeletedFile(fileID, payerID, nodeID);
      // Assert.assertEquals(0, fi.getSize());
      log.info(LOG_PREFIX + "Delete file: Positive test passed!");

      // negative test 1: incorrect signature for wacl keys
      receipt = deleteFile(INCORRECT_WACL_SIG, fileID, payerID, nodeID, waclPubKeyList);
      String status = receipt.getStatus().name();
      boolean actual = ResponseCodeEnum.INVALID_SIGNATURE.name().equals(status)
          || ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY.name().equals(status);
      Assert.assertEquals(true, actual);
      log.info(LOG_PREFIX
          + "Delete file: Negative test with incorrect signature for wacl keys passed! tx status = "
          + status);

      // negative test 2: invalid file ID, post-consensus check
      receipt = deleteFile(INVALID_FILE_ID, fileID, payerID, nodeID, waclPubKeyList);
      status = receipt.getStatus().name();
      Assert.assertEquals(ResponseCodeEnum.INVALID_FILE_ID.name(), status);
      log.info(LOG_PREFIX + "Delete file: Negative test with invalid file ID passed!");
    } catch (Throwable e) {
      log.error("fileDelete error!", e);
      throw e;
    }

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
      fid = FileID.newBuilder().setFileNum(0).setRealmNum(0).setShardNum(0).build(); // non-existent
                                                                                     // file ID
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
    Transaction txSigned =
        TransactionSigner.signTransactionComplexWithSigMap(FileDeleteRequest, keys, pubKey2privKeyMap);

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

  public void getFileInfoDeletedFile(FileID fid, AccountID payerID, AccountID nodeID)
      throws Exception {
    Response fileInfoResp = getFileInfoReturnResponse(fid, payerID, nodeID);
    Assert.assertEquals(ResponseCodeEnum.OK,
        fileInfoResp .getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());
    Assert.assertEquals(true,
            fileInfoResp.getFileGetInfo().getFileInfo().getDeleted());
  }

  public Response getFileInfoReturnResponse(FileID fid, AccountID payerID, AccountID nodeID)
        throws Exception {
    Transaction paymentTxSigned = getQueryPaymentSigned(payerID, nodeID, "fileGetInfoQuery");
    Query fileGetInfoQuery =
        RequestBuilder.getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.ANSWER_ONLY);
    log.info("\n-----------------------------------");
    log.info("fileGetInfoQuery: query = " + fileGetInfoQuery);

    Response fileInfoResp = stub.getFileInfo(fileGetInfoQuery);
    
    return fileInfoResp;
  }

  /**
   * Runs file appending negative tests.
   * @throws Throwable 
   */
  public void appendFileTest() throws Throwable {
    try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();
      int fileSizeK = 1;
      String fileType = FileServiceTest.fileTypes[0];
      byte[] fileContents = genFileContent(fileSizeK, fileType);
      ByteString fileData = ByteString.copyFrom(fileContents);
      FileID fileID = fid2waclMap.keys().nextElement();
      List<Key> waclPubKeyList = fid2waclMap.get(fileID);

      // positive test
      TransactionReceipt receipt =
          appendFile(POSITIVE, payerID, nodeID, fileID, fileData, waclPubKeyList);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
      FileInfo fi = getFileInfo(fileID, payerID, nodeID);
      Assert.assertEquals(false, fi.getDeleted());
      log.info(LOG_PREFIX + "Append file: Positive test passed!");

      // negative test 1: incorrect signature for wacl keys
      receipt = appendFile(INCORRECT_WACL_SIG, payerID, nodeID, fileID, fileData, waclPubKeyList);
      String status = receipt.getStatus().name();
      boolean actual = ResponseCodeEnum.INVALID_SIGNATURE.name().equals(status)
          || ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY.name().equals(status);
      Assert.assertEquals(true, actual);
      log.info(LOG_PREFIX
          + "Append file: Negative test with incorrect signature for wacl keys passed! tx status = "
          + status);

      // negative test 2: invalid file ID, post-consensus check
      receipt = appendFile(INVALID_FILE_ID, payerID, nodeID, fileID, fileData, waclPubKeyList);
      status = receipt.getStatus().name();
      Assert.assertEquals(ResponseCodeEnum.INVALID_FILE_ID.name(), status);
      log.info(LOG_PREFIX + "Append file: Negative test with invalid file ID passed!");
    } catch (Throwable e) {
      log.error("fileAppend error!", e);
      throw e;
    }

  }

  public TransactionRecord getTransactionRecord(TransactionID transactionId, AccountID payerAccount,
      AccountID nodeAccount) throws Exception {
    Transaction paymentTx =
        getQueryPaymentSigned(payerAccount, nodeAccount, "getTransactionRecord");
    Query getRecordQuery = RequestBuilder.getTransactionGetRecordQuery(transactionId, paymentTx,
        ResponseType.ANSWER_ONLY);
    Response recordResp = cstub.getTxRecordByTxID(getRecordQuery);
    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
    log.info("tx record = " + txRecord);
    return txRecord;
  }

  /**
   * Tests for get file info and content queries.
   */
  public void queryFileTest() {
    try {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();

      int fileSizeK = 1;
      String fileType = FileServiceTest.fileTypes[0];
      byte[] fileContents = genFileContent(fileSizeK, fileType);
      ByteString fileData = ByteString.copyFrom(fileContents);
      List<Key> waclPubKeyList = genWaclComplex(FileServiceTest.NUM_WACL_KEYS);

      // create file
      TransactionReceipt receipt = createFile(POSITIVE, payerID, nodeID, fileData, waclPubKeyList);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
      FileID fid = receipt.getFileID();
      Assert.assertNotNull(fid);
      Assert.assertTrue(fid.getFileNum() > 0);
      fid2waclMap.put(fid, waclPubKeyList);
      log.info(LOG_PREFIX + "queryFileTest: created file ID = " + fid);
      
      // getInfo correctly
      Response res = getFileInfoReturnResponse(fid, payerID, nodeID);
      log.info("getInfo correctly: response=" + res);
      Assert.assertEquals(ResponseCodeEnum.OK, res.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());
      Assert.assertEquals(QUERY_GET_FILE_INFO_FEE, res.getFileGetInfo().getHeader().getCost());

      // getInfo with payerID as node ID
      res = getFileInfoReturnResponse(fid, payerID, payerID);
      log.info("getInfo with payerID as node ID: response=" + res);
      Assert.assertEquals(ResponseCodeEnum.INVALID_NODE_ACCOUNT, res.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());
      Assert.assertEquals(QUERY_GET_FILE_INFO_FEE, res.getFileGetInfo().getHeader().getCost());
      
      // getContent correctly
      res = getFileContentReturnResponse(fid, payerID, nodeID);
      log.info("getContent correctly: response=" + res);
      Assert.assertEquals(ResponseCodeEnum.OK, res.getFileGetContents().getHeader().getNodeTransactionPrecheckCode());
      Assert.assertEquals(QUERY_GET_FILE_CONTENT_FEE, res.getFileGetContents().getHeader().getCost());

      // getContent with payerID as node ID
      res = getFileContentReturnResponse(fid, payerID, payerID);
      log.info("getContent with payerID as node ID: response=" + res);
      Assert.assertEquals(ResponseCodeEnum.INVALID_NODE_ACCOUNT, res.getFileGetContents().getHeader().getNodeTransactionPrecheckCode());
      Assert.assertEquals(QUERY_GET_FILE_CONTENT_FEE, res.getFileGetContents().getHeader().getCost());
      
      log.info(LOG_PREFIX + "queryFileTest: passed!");
    } catch (Throwable e) {
      log.error("queryFileTest error!", e);
    }
  }

  /**
   * Gets file contents and return the query response.
   * 
   * @param fid
   * @param payerID
   * @param nodeID
   * @return the query response
   * @throws Exception
   */
  public Response getFileContentReturnResponse(FileID fid, AccountID payerID, AccountID nodeID)
        throws Exception {
    Transaction paymentTxSigned = getQueryPaymentSigned(payerID, nodeID, "fileGetInfoQuery");
    Query fileGetContentsQuery =
        RequestBuilder.getFileGetContentBuilder(paymentTxSigned, fid, ResponseType.ANSWER_ONLY);
    log.info("\n-----------------------------------");
    log.info("fileGetContentsQuery: query = " + fileGetContentsQuery);
  
    Response fileContentResp = stub.getFileContent(fileGetContentsQuery);
    
    return fileContentResp;
  }
}
