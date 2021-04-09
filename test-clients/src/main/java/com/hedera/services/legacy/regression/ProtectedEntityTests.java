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

import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

import com.hederahashgraph.api.proto.java.NodeAddressBook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc.FreezeServiceBlockingStub;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.FileServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hedera.services.legacy.regression.umbrella.TransactionIDCache;

/**
 * Systematic crypto API integration tests.
 *
 * @author Hua Li Created on 2019-03-20
 */
public class ProtectedEntityTests extends BaseClient {

  private static final Logger log = LogManager.getLogger(ProtectedEntityTests.class);
  private static final int WAIT_IN_SEC = 0;
  protected static String testConfigFilePath = "config/umbrellaTest.properties";
  protected static String header = "\n\n*******>>>>>>>>>>>>>>> SUCCESS: ";
  private FileID addressBook101 = genFileID(101);
  private FileID addressBook102 = genFileID(102);
  private FileID feeSchedule111 = genFileID(111);
  private FileID exchangeRate112 = genFileID(112);
  private String validFeeFilePath = "testSystemFiles/FeeSchedule_cryptoCreate_cheap.txt";
  private ByteString validFeeFile = null;
  private boolean isSmallExchangeRateUpdate = true;
  private NodeAddressBook serverAddressBook = null;
  private NodeAddressBook serverNodeDetails = null;
  private ExchangeRateSet serverExchangeRate = null;
  private FeeSchedule serverFeeSchedule = null;
  
  public ProtectedEntityTests(String testConfigFilePath) throws URISyntaxException, IOException {
    super(testConfigFilePath);
    validFeeFile = getFeeScheduleByteString(validFeeFilePath);
  }

  protected AccountID genAccountID(long accNum) {
    return AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(accNum).build();
  }

  private FileID genFileID(long accNum) {
    return FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(accNum).build();
  }

  /**
   * Initialize the client.
   *
   * @param accounts accounts to be funded.
   * @throws Throwable
   */
  public void init(long[] accounts) throws Throwable {
    readAppConfig();
    getTestConfig();
    readGenesisInfo();
    createStubs();
    cache = TransactionIDCache
        .getInstance(TransactionIDCache.txReceiptTTL, TransactionIDCache.txRecordTTL);
    nodeID2Stub.put(defaultListeningNodeAccountID, stub);
    nodeAccounts = new AccountID[1];
    nodeAccounts[0] = defaultListeningNodeAccountID;
    receiverSigRequired = false;
    getReceipt = true;
    String[] keyType = {"thresholdKey"};
    accountKeyTypes = keyType;
    payerAccounts = accountCreatBatch(1); // create an account
    fundAccounts(accounts);
    serverAddressBook = getAddressBookFromServer();
    serverNodeDetails = getNodeDetailsFromServer();
    serverExchangeRate = getExchangeRateFromServer();
    serverFeeSchedule = getFeeScheduleFromServer();
    FILE_PART_SIZE = 3000; // 3K, which forces fee schedule (8K in size) to be uploaded in 3 parts, i.e. one update and two append API calls.
  }

  /**
   * Updates an account autoRenew.
   *
   * @param accountID account to be updated
   * @return updated account info.
   */
  public AccountInfo updateAccount(AccountID accountID, AccountID payerAccountID,
      AccountID nodeAccountID, ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode)
      throws Throwable {
    return updateAccount(accountID, payerAccountID,
        nodeAccountID, expectedPrecheckCode, 
        expectedPostcheckCode, true, null);
  }
  
  /**
   * Update an account with auto renew and new keys.
   * 
   * @param accountID account to be updated
   * @param payerAccountID
   * @param nodeAccountID
   * @param expectedPrecheckCode
   * @param expectedPostcheckCode
   * @param newKey new keys for the account
   * @return account info if the update is success
   * @throws Throwable
   */
  public AccountInfo updateAccount(AccountID accountID, AccountID payerAccountID,
      AccountID nodeAccountID, ResponseCodeEnum expectedPrecheckCode, 
      ResponseCodeEnum expectedPostcheckCode, boolean requireExistingKey, Key newKey)
      throws Throwable {
    CustomPropertiesSingleton properties = CustomPropertiesSingleton.getInstance();
    Duration autoRenew = RequestBuilder.getDuration(properties.getAccountDuration() + 30);
  
    Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
    Key accKey = acc2ComplexKeyMap.get(accountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    if(requireExistingKey) {
		keys.add(accKey);
	}
    Transaction updateTx = updateAccount(accountID, payerAccountID,
        nodeAccountID, autoRenew);
    
    if (newKey != null) {
      com.hederahashgraph.api.proto.java.TransactionBody.Builder builder = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(updateTx).toBuilder();
      CryptoUpdateTransactionBody newUpdateBody = builder
          .getCryptoUpdateAccountBuilder().setKey(newKey).build();
      TransactionBody newTxBody = builder.setCryptoUpdateAccount(newUpdateBody).build();
      log.info("\n-----------------------------------\nupdateAccount with new keys: tx body = " + newTxBody);
      updateTx = Transaction.newBuilder().setBodyBytes(newTxBody.toByteString()).build();
      keys.add(newKey);
    }

    Transaction signUpdate = TransactionSigner
        .signTransactionComplexWithSigMap(updateTx, keys, pubKey2privKeyMap);
  
    log.info("\n-----------------------------------\nupdateAccount: request = " + signUpdate);
    TransactionResponse response = cstub.updateAccount(signUpdate);
    Assert.assertNotNull(response);
    Assert.assertEquals(expectedPrecheckCode, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signUpdate);
    TransactionID transactionID = body.getTransactionID();
    cache.addTransactionID(transactionID);
  
    AccountInfo accInfo = null;
    if(ResponseCodeEnum.OK == expectedPrecheckCode) {
      TransactionReceipt fastRecord = getTxFastRecord(transactionID);
      Assert.assertNotNull(fastRecord);
      Assert.assertEquals(expectedPostcheckCode, fastRecord.getStatus());
  
      if(fastRecord.getStatus() == ResponseCodeEnum.SUCCESS) {
        if (newKey != null) {
          acc2ComplexKeyMap.put(accountID, newKey); // remember the new key
        }
        accInfo = getAccountInfo(accountID, payerAccountID, nodeAccountID);
        Assert.assertNotNull(accInfo);
        log.info(accInfo);
        Assert.assertEquals(properties.getAccountDuration() + 30,
            accInfo.getAutoRenewPeriod().getSeconds());
          log.info("updating successful" + "\n");
      }
    }
    
    return accInfo;
  }
  
  public static void main(String[] args) throws Throwable {
    ProtectedEntityTests tester = new ProtectedEntityTests(testConfigFilePath);
    long[] sysAccountToFund = {3, 49, 50, 51, 55, 56, 57, 58, 59, 60, 80, 81, 100, 45, 46};
    tester.init(sysAccountToFund);
    tester.runTests();
  }

  protected void runTests() throws Throwable {
    updateAddressFileTests();
    updateFeeScheduleTests();
    updateExchangeRateFileTests();
    deleteSystemFileTests();
    deleteSystemAccountTests();
    systemDeleteUndeleteTests();
    dynamicRestartTests();
  }

  /**
   * Tests deletion of protected system files, i.e. files below 1000 cannot be deleted
   * @throws Throwable
   */
  public void deleteSystemFileTests() throws Throwable {
    // create an account
    receiverSigRequired = false;
    payerAccounts = accountCreatBatch(1);

    AccountID nodeID = defaultListeningNodeAccountID;

    // create a regular file and delete it
    byte[] fileContents = genRandomBytes(3000);
    ByteString fileData = ByteString.copyFrom(fileContents);
    AccountID payerID = genesisAccountID;
    List<Key> waclPubKeyList = genWaclComplex(FileServiceTest.NUM_WACL_KEYS);
    FileID fid = createFile(payerID, nodeID, fileData, waclPubKeyList, false, true);
    Assert.assertEquals(true, (fid != null) && (fid.getFileNum() > 0));
    fid2waclMap.put(fid, waclPubKeyList);
    // now delete the newly created file
    deleteFile(fid, payerID, nodeID, waclPubKeyList, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
    log.info(header + "create a regular file and delete it");
    
    long[] payers = {2, 50, 55, 56, 57, 100, payerAccounts[0].getAccountNum()};
    long[] files = {101, 102, 111, 112, 113, 500, 1000};
    
    // files below 1000 cannot be deleted 
    for (int i = 0; i < payers.length; i++) {
      AccountID payer = genAccountID(payers[i]);
      for (int j = 0; j < files.length; j++) {
        fid = genFileID(files[j]);
        List<Key> wacl = null;
        if(fid2waclMap.contains(fid)) {
			wacl = fid2waclMap.get(fid);
		} else {
			wacl = acc2ComplexKeyMap.get(genesisAccountID).getKeyList().getKeysList(); // note: same key is used for genesis account and system file wacl
		}
        deleteFile(fid, payer, nodeID, wacl, ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE, null);
      }
    }
    log.info(header + "files below 1000 cannot be deleted");
    log.info(header + "deleteFileTests finished");
  }

  /**
   * Generates number of bytes.
   * 
   * @param numBytes
   * @return bytes generated
   */
  private byte[] genRandomBytes(int numBytes) {
    byte[] fileContents = new byte[numBytes];
    (new Random()).nextBytes(fileContents);
    return fileContents;
  }

  /**
   * Tests system delete and undelete of files and contracts according the following requirements:
   * A/c 0.0.59 - System Deletes (HAPI Transaction)
   * A/c 0.0.60 - System UnDeletes (HAPI Transaction)
   * System Deleted and Undelete - For accounts 59 and 60, no wacl needed; accounts 2 and 50 are authorized, but need wacl to sign
   */
  public void systemDeleteUndeleteTests() throws Throwable {
    AccountID nodeID = defaultListeningNodeAccountID;

    long[] systemDeleteAccounts = {2, 50, 59};
    long[] systemUndeleteAccounts = {2, 50, 60};
    long[] unauthAccounts = {80, 81, 100, payerAccounts[0].getAccountNum()};
    
    // system deletes and undelete a file
    for (int i = 0; i < systemDeleteAccounts.length; i++) {
      // create a file first
      byte[] fileContents = genRandomBytes(3000);
      ByteString fileData = ByteString.copyFrom(fileContents);
      List<Key> waclPubKeyList = genWaclComplex(FileServiceTest.NUM_WACL_KEYS);
      FileID fid = createFile(genesisAccountID , nodeID, fileData, waclPubKeyList, false, true);
      Assert.assertEquals(true, (fid != null) && (fid.getFileNum() > 0));
      fid2waclMap.put(fid, waclPubKeyList);

      AccountID authDeletePayerID = genAccountID(systemDeleteAccounts[i]);
      AccountID undeletePayerID = genAccountID(systemUndeleteAccounts[i]);
      
      // unauthorized system delete will fail
      for (int i1 = 0; i1 < unauthAccounts.length; i1++) {
        AccountID unauthPayerID = genAccountID(unauthAccounts[i1]);
        ResponseCodeEnum code = ResponseCodeEnum.NOT_SUPPORTED; // result of api permissions check
        if(unauthPayerID.getAccountNum() < 59) {
			code = ResponseCodeEnum.AUTHORIZATION_FAILED;
		}
        systemDelete(fid, unauthPayerID, nodeID, waclPubKeyList, code, null);
      }
      
      // authorized system delete will succeed
      if(authDeletePayerID.getAccountNum() == 59) // no wacl needed
	  {
		  systemDelete(fid, authDeletePayerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
	  } else // need wacl
	  {
		  systemDelete(fid, authDeletePayerID, nodeID, waclPubKeyList, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
	  }
      
      // unauthorized system undelete will fail
      for (int i1 = 0; i1 < unauthAccounts.length; i1++) {
        AccountID unauthPayerID = genAccountID(unauthAccounts[i1]);
        ResponseCodeEnum code = ResponseCodeEnum.NOT_SUPPORTED;
        if(unauthPayerID.getAccountNum() < 60) {
			code = ResponseCodeEnum.AUTHORIZATION_FAILED;
		}
        systemUndelete(fid, unauthPayerID, nodeID, waclPubKeyList, code , null);
      }
      
      // authorized system undelete will succeed
      if(undeletePayerID.getAccountNum() == 60) // no wacl needed
	  {
		  systemUndelete(fid, undeletePayerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
	  } else // need wacl
	  {
		  systemUndelete(fid, undeletePayerID, nodeID, waclPubKeyList, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
	  }
    }
    log.info(header + "system deletes and undelete a file");
    
    // system deletes and undelete a contract
    for (int i = 0; i < systemDeleteAccounts.length; i++) {
      // create a contract first
      String fileName = getRandomSmartContractFile();
      byte[] bytes = CommonUtils.readBinaryFileAsResource(fileName, getClass());
      List<Key> waclKeys = genWaclComplex(FileServiceTest.NUM_WACL_KEYS);
      String savePath = "saved" + FileSystems.getDefault().getSeparator() + fileName;
      FileID contractFid = uploadFile(savePath, bytes, genesisAccountID, nodeID, waclKeys);
      CryptoServiceTest.getReceipt = true;
      ContractID contractID = createContract(contractFid, fileName, genesisAccountID, nodeID, true);

      AccountID authDeletePayerID = genAccountID(systemDeleteAccounts[i]);
      AccountID undeletePayerID = genAccountID(systemUndeleteAccounts[i]);
      
      // unauthorized system delete will fail
      for (int i1 = 0; i1 < unauthAccounts.length; i1++) {
        AccountID unauthPayerID = genAccountID(unauthAccounts[i1]);
        ResponseCodeEnum code = ResponseCodeEnum.NOT_SUPPORTED;
        if(unauthPayerID.getAccountNum() <= 59) {
			code = ResponseCodeEnum.AUTHORIZATION_FAILED;
		}
        systemDelete(contractID, unauthPayerID , nodeID, code, null);
      }
      
      // authorized system delete will succeed
//      systemDelete(contractID, authDeletePayerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
      
      // unauthorized system undelete will fail
      for (int i1 = 0; i1 < unauthAccounts.length; i1++) {
        AccountID unauthPayerID = genAccountID(unauthAccounts[i1]);
        ResponseCodeEnum code = ResponseCodeEnum.NOT_SUPPORTED;
        if(unauthPayerID.getAccountNum() <= 60) {
			code = ResponseCodeEnum.AUTHORIZATION_FAILED;
		}
        systemUndelete(contractID, unauthPayerID, nodeID, code, null);
      }
      
      // authorized system undelete will succeed
//      systemUndelete(contractID, undeletePayerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
    }
    log.info(header + "system deletes and undelete a contract");
  }
  
  protected TransactionResponse updateFileWithFee(FileID fid, AccountID payerID, AccountID nodeID,
      List<Key> oldWaclKeyList, List<Key> newWaclKeyList, ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode) throws Throwable {
    return updateFileWithValidContent(fid, payerID, nodeID, oldWaclKeyList, newWaclKeyList, expectedPrecheckCode, expectedPostcheckCode, false);
  }
  
  protected TransactionResponse updateFileWithNoFee(FileID fid, AccountID payerID, AccountID nodeID,
      List<Key> oldWaclKeyList, List<Key> newWaclKeyList, ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode) throws Throwable {
    return updateFileWithValidContent(fid, payerID, nodeID, oldWaclKeyList, newWaclKeyList, expectedPrecheckCode, expectedPostcheckCode, true);
  }

  /**
   * Updates a file with valid data content. 
   */
  protected TransactionResponse updateFileWithValidContent(FileID fid, AccountID payerID, AccountID nodeID,
      List<Key> oldWaclKeyList, List<Key> newWaclKeyList, ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode, boolean isFree) throws Throwable {
    return updateFile(fid, payerID, nodeID,
         oldWaclKeyList, newWaclKeyList, expectedPrecheckCode, expectedPostcheckCode, isFree, true);
  }
  
  /**
   * Updates a file with invalid data content. 
   */
  protected TransactionResponse updateFileWithInvalidContent(FileID fid, AccountID payerID, AccountID nodeID,
      List<Key> oldWaclKeyList, List<Key> newWaclKeyList, ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode, boolean isFree) throws Throwable {
    return updateFile(fid, payerID, nodeID,
         oldWaclKeyList, newWaclKeyList, expectedPrecheckCode, expectedPostcheckCode, isFree, false);
  }  
  /**
   * Updates a file.
   *
   * @param fid the ID of the file to be updated
   * @param payerID the fee payer ID
   * @param nodeID the node ID
   * @param oldWaclKeyList existing wacl keys for file, null allowed 
   * @param newWaclKeyList the new wacl keys to replace existing ones, null allowed
   * @param expectedPrecheckCode expected precheck code
   * @param expectedPostcheckCode expected postcheck code
   * @param isFree whether the transaction is free
   * @param isValidFileContent whether the fee file data for updating is valid
   * @return the transaction response
   * @throws Throwable
   */
  protected TransactionResponse updateFile(FileID fid, AccountID payerID, AccountID nodeID,
      List<Key> oldWaclKeyList, List<Key> newWaclKeyList, ResponseCodeEnum expectedPrecheckCode, 
      ResponseCodeEnum expectedPostcheckCode, boolean isFree, boolean isValidFileContent) throws Throwable {
    ByteString fileData = null;
    if (isValidFileContent) {
      if(fid.equals(feeSchedule111)) {
      fileData = validFeeFile;
      } else if (fid.equals(exchangeRate112)) {
        fileData = genExchangeFile();
      } else if (fid.equals(addressBook101)) {
        fileData = modAddressBookFile(serverAddressBook, false);
      } else if (fid.equals(addressBook102)) {
        fileData = modNodeDetailsFile(serverNodeDetails, false);
      }
    } else {
      byte[] fileDataBytes = genFileContent(3, "bin");
      fileData = ByteString.copyFrom(fileDataBytes);
    }
   
    return updateFile(fid, payerID, nodeID, oldWaclKeyList, newWaclKeyList, expectedPrecheckCode, 
        expectedPostcheckCode, isFree, fileData);
  }

  /**
   * Deletes a file.
   *
   * @param fid the ID of the file to be updated
   * @param payerID the fee payer ID
   * @param nodeID the node ID
   * @param waclKeyList the file creation WACL public key list
   */
  protected void deleteFile(FileID fid, AccountID payerID,
      AccountID nodeID, List<Key> waclKeyList, ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode) throws Throwable {
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    Transaction FileDeleteRequest = RequestBuilder.getFileDeleteBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp,
        transactionDuration, true, "FileDelete", fid);
  
    Key payerKey = acc2ComplexKeyMap.get(payerID);
    Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeyList)).build();
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(waclKey);
    Transaction txSigned =
        TransactionSigner.signTransactionComplexWithSigMap(FileDeleteRequest, keys, pubKey2privKeyMap);
  
    log.info("\n-----------------------------------");
    log.info("FileDelete: request = " + txSigned);
  
    FileServiceBlockingStub stub = getStub(nodeID);
    TransactionResponse response = stub.deleteFile(txSigned);
    log.info("FileDelete Response :: " + response.getNodeTransactionPrecheckCodeValue());
    
    Assert.assertNotNull(response);
    Assert.assertEquals(expectedPrecheckCode, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response file delete :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(txSigned);
    TransactionID transactionID = body.getTransactionID();
  
    if(ResponseCodeEnum.OK == expectedPrecheckCode) {
      TransactionReceipt fastRecord = getTxFastRecord(transactionID);
      Assert.assertNotNull(fastRecord);
      Assert.assertEquals(expectedPostcheckCode, fastRecord.getStatus());
    }
  }

  /**
   * Tests deletion of protected system accounts, i.e. those below 1000 cannot be deleted
   * @throws Throwable
   */
  public void deleteSystemAccountTests() throws Throwable {
    // create an account
    receiverSigRequired = false;
    payerAccounts = accountCreatBatch(2);
  
    AccountID nodeID = defaultListeningNodeAccountID;
  
    AccountID aid = payerAccounts[1];
    AccountID payerID = genesisAccountID;
    Key accountKey = acc2ComplexKeyMap.get(genesisAccountID);
    // delete a regular account
    deleteAccount(aid, payerID, nodeID, accountKey, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
    log.info(header + "create a regular file and delete it");
    
    long[] payers = {2, 50, 55, 56, 57, 100, payerAccounts[0].getAccountNum()};
    long[] accounts = {2, 50, 55, 56, 57, 100};
    
    // accounts below 1000 cannot be deleted 
    for (int i = 0; i < payers.length; i++) {
      AccountID payer = genAccountID(payers[i]);
      for (int j = 0; j < accounts.length; j++) {
        aid = genAccountID(accounts[j]);
        accountKey = acc2ComplexKeyMap.get(aid); // note: same key is used for genesis account and system account wacl
        deleteAccount(aid, payer, nodeID, accountKey, ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE, null);
      }
    }
    log.info(header + "accounts below 1000 cannot be deleted");
    log.info(header + "deleteAccountTests finished");
  }

  /**
   * Deletes an account.
   * 
   * @param accountID
   * @param payerAccount
   * @param nodeID
   * @param accountKey
   * @param expectedPrecheckCode
   * @param expectedPostcheckCode
   * @throws Throwable
   */
  private void deleteAccount(AccountID accountID, AccountID payerAccount, AccountID nodeID,
      Key accountKey, ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode) throws Throwable {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    TransactionID transactionID = TransactionID.newBuilder().setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(accountID).setTransferAccountID(payerAccount)
        .build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(nodeID)
        .setTransactionFee(TestHelper.getCryptoMaxFee())
        .setTransactionValidDuration(transactionValidDuration)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    
    Key payerKey = acc2ComplexKeyMap.get(payerAccount);
    Key accKey = acc2ComplexKeyMap.get(accountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(accKey);
    Transaction signTx = TransactionSigner
        .signTransactionComplexWithSigMap(tx, keys, pubKey2privKeyMap);

    TransactionResponse response = cstub.cryptoDelete(signTx);
    log.info("cryptoDelete Response :: " + response.getNodeTransactionPrecheckCodeValue());
    
    Assert.assertNotNull(response);
    Assert.assertEquals(expectedPrecheckCode, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account delete :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signTx);
  
    if(ResponseCodeEnum.OK == expectedPrecheckCode) {
      TransactionReceipt fastRecord = getTxFastRecord(transactionID);
      Assert.assertNotNull(fastRecord);
      Assert.assertEquals(expectedPostcheckCode, fastRecord.getStatus());
    }
  }

  /**
   * Deletes a file or contract using system delete API with only the payer sign the transaction.
   * 
   * @param entityID file or contract ID to be deleted
   * @param payerAccount
   * @param nodeID
   * @param expectedPrecheckCode
   * @param expectedPostcheckCode
   * @throws Throwable
   */
  private void systemDelete(Object entityID, AccountID payerAccount, AccountID nodeID,
      ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode) throws Throwable {
    systemDelete(entityID, payerAccount, nodeID, null, expectedPrecheckCode, expectedPostcheckCode);    
  }
  
  /**
   * Deletes a file or contract using system delete API.
   * 
   * @param entityID file or contract ID to be deleted
   * @param payerAccount
   * @param nodeID
   * @param fileWacl the wacl of the file to be deleted, it should sign if not null
   * @param expectedPrecheckCode
   * @param expectedPostcheckCode
   * @throws Throwable
   */
  private void systemDelete(Object entityID, AccountID payerAccount, AccountID nodeID, List<Key> fileWacl, 
      ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode) throws Throwable {
    Builder systemDeleteTransactionBodyBuilder = SystemDeleteTransactionBody
        .newBuilder()
        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(10000000l));
    
    if(entityID instanceof FileID) {
		systemDeleteTransactionBodyBuilder.setFileID((FileID) entityID);
	} else {
		systemDeleteTransactionBodyBuilder.setContractID((ContractID) entityID);
	}
      
    SystemDeleteTransactionBody systemDeleteTransactionBody = systemDeleteTransactionBodyBuilder.build();

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    long transactionFee = TestHelper.getCryptoMaxFee();
    String memo = "system delete";

    TransactionID transactionID = TransactionID.newBuilder()
        .setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(nodeID)
        .setTransactionFee(transactionFee)
        .setTransactionValidDuration(transactionValidDuration)
        .setMemo(memo)
        .setSystemDelete(systemDeleteTransactionBody)
        .build();

    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);

    Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    
    Key payerKey = acc2ComplexKeyMap.get(payerAccount);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    
    if(systemDeleteTransactionBody.hasFileID() && fileWacl != null) {
      Key existingWaclKey = Key.newBuilder()
          .setKeyList(KeyList.newBuilder().addAllKeys(fileWacl)).build();
      keys.add(existingWaclKey);
    }
    
    Transaction signTx = TransactionSigner
        .signTransactionComplexWithSigMap(tx, keys, pubKey2privKeyMap);
  
    TransactionResponse response = null;
    if(entityID instanceof FileID) {
		response = stub.systemDelete(signTx);
	} else {
		response = scstub.systemDelete(signTx);
	}
      
    log.info("systemDelete Response :: " + response.getNodeTransactionPrecheckCodeValue() 
      + ", transactionBody=" + transactionBody);
    
    Assert.assertNotNull(response);
    Assert.assertEquals(expectedPrecheckCode, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account delete :: " + response.getNodeTransactionPrecheckCode().name());
  
    if(ResponseCodeEnum.OK == expectedPrecheckCode) {
      TransactionReceipt fastRecord = getTxFastRecord(transactionID);
      Assert.assertNotNull(fastRecord);
    }
  }

  /**
   * Undeletes a file or contract using system delete API with only the payer sign the transaction.
   * 
   * @param entityID file or contract ID to be deleted
   * @param payerAccount
   * @param nodeID
   * @param expectedPrecheckCode
   * @param expectedPostcheckCode
   * @throws Throwable
   */
  private void systemUndelete(Object entityID, AccountID payerAccount, AccountID nodeID,
      ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode) throws Throwable {
    systemUndelete(entityID, payerAccount, nodeID, null, expectedPrecheckCode, expectedPostcheckCode); 
  }
  
  /**
   * Undeletes a file or contract using system delete API.
   * 
   * @param entityID file or contract ID to be deleted
   * @param payerAccount
   * @param nodeID
   * @param fileWacl the wacl of the file to be deleted, it should sign if not null
   * @param expectedPrecheckCode
   * @param expectedPostcheckCode
   * @throws Throwable
   */
  private void systemUndelete(Object entityID, AccountID payerAccount, AccountID nodeID, List<Key> fileWacl,
      ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode) throws Throwable {
    com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody.Builder systemUndeleteTransactionBodyBuilder = SystemUndeleteTransactionBody
        .newBuilder();
    
    if(entityID instanceof FileID) {
		systemUndeleteTransactionBodyBuilder.setFileID((FileID) entityID);
	} else {
		systemUndeleteTransactionBodyBuilder.setContractID((ContractID) entityID);
	}
      
    SystemUndeleteTransactionBody systemUndeleteTransactionBody = systemUndeleteTransactionBodyBuilder.build();
  
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    long transactionFee = TestHelper.getFileMaxFee();
    String memo = "system delete";
  
    TransactionID transactionID = TransactionID.newBuilder()
        .setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(nodeID)
        .setTransactionFee(transactionFee)
        .setTransactionValidDuration(transactionValidDuration)
        .setMemo(memo)
        .setSystemUndelete(systemUndeleteTransactionBody)
        .build();
  
    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
  
    Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    
    Key payerKey = acc2ComplexKeyMap.get(payerAccount);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    
    if(systemUndeleteTransactionBodyBuilder.hasFileID() && fileWacl != null) {
      Key existingWaclKey = Key.newBuilder()
          .setKeyList(KeyList.newBuilder().addAllKeys(fileWacl)).build();
      keys.add(existingWaclKey);
    }

    Transaction signTx = TransactionSigner
        .signTransactionComplexWithSigMap(tx, keys, pubKey2privKeyMap);
  
    TransactionResponse response = null;
    if(entityID instanceof FileID) {
		response = stub.systemDelete(signTx);
	} else {
		response = scstub.systemDelete(signTx);
	}
      
    log.info("systemUndelete Response :: " + response.getNodeTransactionPrecheckCodeValue());
    
    Assert.assertNotNull(response);
    Assert.assertEquals(expectedPrecheckCode, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account delete :: " + response.getNodeTransactionPrecheckCode().name());
  
    if(ResponseCodeEnum.OK == expectedPrecheckCode) {
      TransactionReceipt fastRecord = getTxFastRecord(transactionID);
      Assert.assertNotNull(fastRecord);
    }
  }

  /**
   * Dynamic restart with freeze API.
   * 
   * @param payerAccount
   * @param nodeID
   * @param expectedPrecheckCode
   * @param expectedPostcheckCode
   * @throws Throwable
   */
  private void dynamicRestart(AccountID payerAccount, AccountID nodeID,
      ResponseCodeEnum expectedPrecheckCode, ResponseCodeEnum expectedPostcheckCode) throws Throwable {
    // set restart to be 4 hours from now and with duration of 1 minute
    Calendar rightNow = Calendar.getInstance();
    int startHour = (rightNow.get(Calendar.HOUR_OF_DAY) + 4) % 24;
    com.hederahashgraph.api.proto.java.FreezeTransactionBody.Builder systemDeleteTransactionBodyBuilder = FreezeTransactionBody
        .newBuilder()
        .setStartHour(startHour)
        .setStartMin(0)
        .setEndHour(startHour)
        .setEndMin(1);
      
    FreezeTransactionBody freezeBody = systemDeleteTransactionBodyBuilder.build();
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    long transactionFee = TestHelper.getFileMaxFee();
    String memo = "dynamic restart";
  
    TransactionID transactionID = TransactionID.newBuilder()
        .setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(nodeID)
        .setTransactionFee(transactionFee)
        .setTransactionValidDuration(transactionValidDuration)
        .setMemo(memo)
        .setFreeze(freezeBody)
        .build();
  
    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
  
    Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    
    Key payerKey = acc2ComplexKeyMap.get(payerAccount);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    Transaction signTx = TransactionSigner
        .signTransactionComplexWithSigMap(tx, keys, pubKey2privKeyMap);
  
    FreezeServiceBlockingStub frstub = FreezeServiceGrpc.newBlockingStub(channel);
    TransactionResponse response = frstub.freeze(signTx);
    log.info("dynamicRestart Response :: " + response.getNodeTransactionPrecheckCodeValue()
        + ", transactionBody=" + transactionBody
        + ", tx=" + signTx);
    
    Assert.assertNotNull(response);
    Assert.assertEquals(expectedPrecheckCode, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response dynamicRestart :: " + response.getNodeTransactionPrecheckCode().name());
  
    if(ResponseCodeEnum.OK == expectedPrecheckCode) {
      TransactionReceipt fastRecord = getTxFastRecord(transactionID);
      Assert.assertNotNull(fastRecord);
      Assert.assertEquals(expectedPostcheckCode, fastRecord.getStatus());
    }
  }

  /**
   * Tests permissions for dynamic restart, only account 58 is allowed to execute this API.
   * 
   * @throws Throwable
   */
  public void dynamicRestartTests() throws Throwable {
    // create an account
    receiverSigRequired = false;
    payerAccounts = accountCreatBatch(1);
    AccountID nodeID = defaultListeningNodeAccountID;
  
    long[] authorizedAccounts = {2, 50, 58};
    long[] unauthAccounts = {49, 51, 55, 57, 59, 60, 80, 81, 100, payerAccounts[0].getAccountNum()};
    
    // unauthorized accounts will fail
    for (int i1 = 0; i1 < unauthAccounts.length; i1++) {
      AccountID unauthorizedPayerID = genAccountID(unauthAccounts[i1]);
      ResponseCodeEnum code = ResponseCodeEnum.NOT_SUPPORTED;
      if(unauthorizedPayerID.getAccountNum() <= 58) {
		  code = ResponseCodeEnum.AUTHORIZATION_FAILED;
	  }
      dynamicRestart(unauthorizedPayerID, nodeID, code, null);
    }
    log.info(header + "dynamic restart with unauthorized accounts fails");
    
    // authorized accounts will succeed
    for (int i = 0; i < authorizedAccounts.length; i++) {
      AccountID authorizedPayerID = genAccountID(authorizedAccounts[i]);
      dynamicRestart(authorizedPayerID, nodeID, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
    }
    log.info(header + "dynamic restart with authorized accounts succeeds");
    
    log.info(header + "dynamicRestartTests finished");
  }

  /**
   * Updates a file.
   *
   * @param fid the ID of the file to be updated
   * @param payerID the fee payer ID
   * @param nodeID the node ID
   * @param oldWaclKeyList existing wacl keys for file, null allowed 
   * @param newWaclKeyList the new wacl keys to replace existing ones, null allowed
   * @param expectedPrecheckCode expected precheck code
   * @param expectedPostcheckCode expected postcheck code
   * @param isFree whether the transaction is free
   * @param fileData file data to be updated with
   * @return the transaction response
   * @throws Throwable
   */
  protected TransactionResponse updateFile(FileID fid, AccountID payerID, AccountID nodeID,
      List<Key> oldWaclKeyList, List<Key> newWaclKeyList, ResponseCodeEnum expectedPrecheckCode, 
      ResponseCodeEnum expectedPostcheckCode, boolean isFree, ByteString fileData) throws Throwable {
    Timestamp fileExp = null;
    KeyList wacl = null;
    if(newWaclKeyList != null) {
		wacl = KeyList.newBuilder().addAllKeys(newWaclKeyList).build();
	}
    
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    long fee = 0l;
    if(!isFree) {
		fee = TestHelper.getFileMaxFee();
	}
    Transaction FileUpdateRequest = getFileUpdateBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), fee , timestamp, fileExp, transactionDuration, true,
        "FileUpdate",
        fileData , fid, wacl);
  
    Key payerKey = acc2ComplexKeyMap.get(payerID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
  
    if(oldWaclKeyList != null) {
      Key existingWaclKey = Key.newBuilder()
          .setKeyList(KeyList.newBuilder().addAllKeys(oldWaclKeyList)).build();
      keys.add(existingWaclKey);
    }
  
    if(wacl != null) {
      Key newWaclKey = Key.newBuilder().setKeyList(wacl).build();
      keys.add(newWaclKey);
    }
    
    Transaction txSigned = TransactionSigner
        .signTransactionComplexWithSigMap(FileUpdateRequest, keys, pubKey2privKeyMap);
  
    log.info("\n-----------------------------------");
    log.info(
        "FileUpdate: input data = " + fileData + "\nexpirationTime = " + fileExp + "\nWACL keys = "
            + newWaclKeyList);
    log.info("FileUpdate: request = " + txSigned);
  
    checkTxSize(txSigned);
  
    TransactionResponse response = stub.updateFile(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(expectedPrecheckCode, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response file update :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(txSigned);
    TransactionID transactionID = body.getTransactionID();
  
    FileInfo fileInfo = null;
    if(ResponseCodeEnum.OK == expectedPrecheckCode) {
      TransactionReceipt fastRecord = getTxFastRecord(transactionID);
      Assert.assertNotNull(fastRecord);
  
      checkFeeCharge(transactionID, payerID, nodeID, isFree);
      
      if (fastRecord.getStatus() == ResponseCodeEnum.SUCCESS) {
        fileInfo = getFileInfo(fid, payerID, nodeID);
        Assert.assertNotNull(fileInfo);
        log.info(fileInfo);
        if (wacl != null) {
          Assert.assertEquals(wacl, fileInfo.getKeys());
        }
  
        Assert.assertEquals(fileData.size(), fileInfo.getSize());
        log.info("updating successful" + "\n");
      } 
    }
    
    return response;
  }

  /**
   * Checks the fee charged in the record.
   * 
   * @param transactionID transaction whose record is to be checked. 
   * @param payerID
   * @param nodeID
   * @param isFree the transaction should be free
   * @throws Exception
   */
  private void checkFeeCharge(TransactionID transactionID, AccountID payerID, AccountID nodeID,
      boolean isFree) throws Exception {
    TransactionRecord record = getTransactionRecord(transactionID, payerID, nodeID);
    log.info("record=" + record);
    
    if(isFree) {
      // check that no fee is actually charged
      Assert.assertEquals(true, record.getTransferList().getAccountAmountsCount() == 0);
      Assert.assertEquals(0, record.getTransactionFee());
    } else {
      Assert.assertEquals(true, record.getTransferList().getAccountAmountsCount() > 0);
      Assert.assertEquals(true, record.getTransactionFee() > 0);
    }
  }

  /**
   * Updates a large file with file update and append APIs.
   * 
   * @param largeFileBytes
   * @param fid
   * @param payerID
   * @param nodeID
   * @param waclKeyList
   * @param expectedPrecheckCodeUpdate
   * @param expectedPostcheckCodeUpdate
   * @param expectedPrecheckCodeAppend
   * @param expectedPostcheckCodeAppend
   * @param isFree
   * @throws Throwable
   */
  public void updateLargeFile(byte[] largeFileBytes, FileID fid, AccountID payerID,
      AccountID nodeID, List<Key> waclKeyList, ResponseCodeEnum expectedPrecheckCodeUpdate,
      ResponseCodeEnum expectedPostcheckCodeUpdate, ResponseCodeEnum expectedPrecheckCodeAppend,
      ResponseCodeEnum expectedPostcheckCodeAppend, boolean isFree) throws Throwable {
    updateLargeFile(largeFileBytes, fid, payerID,
        nodeID, waclKeyList, expectedPrecheckCodeUpdate, expectedPostcheckCodeUpdate, expectedPrecheckCodeAppend,
        expectedPostcheckCodeAppend, isFree, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
  }
  
  public void updateLargeFile(byte[] largeFileBytes, FileID fid, AccountID payerID,
      AccountID nodeID, List<Key> waclKeyList, ResponseCodeEnum expectedPrecheckCodeUpdate,
      ResponseCodeEnum expectedPostcheckCodeUpdate, ResponseCodeEnum expectedPrecheckCodeAppend,
      ResponseCodeEnum expectedPostcheckCodeAppend, boolean isFree, ResponseCodeEnum expectedPrecheckCodeIntermediateAppend,
      ResponseCodeEnum expectedPostcheckCodeIntermediateAppend) throws Throwable {
  
    int numParts = largeFileBytes.length / FILE_PART_SIZE;
    int remainder = largeFileBytes.length % FILE_PART_SIZE;
    log.info(
        "@@@ file size=" + largeFileBytes.length + "; FILE_PART_SIZE=" + FILE_PART_SIZE + "; numParts="
            + numParts + "; remainder=" + remainder);
  
    byte[] firstPartBytes = null;
    if (largeFileBytes.length <= FILE_PART_SIZE) {
      firstPartBytes = largeFileBytes;
      remainder = 0;
    } else {
      firstPartBytes = CommonUtils.copyBytes(0, FILE_PART_SIZE, largeFileBytes);
    }
  
    //update file with first part
    ByteString fileData = ByteString.copyFrom(firstPartBytes);
    updateFile(fid, payerID, nodeID, waclKeyList, null, expectedPrecheckCodeUpdate, 
        expectedPostcheckCodeUpdate, isFree, fileData);
    log.info("@@@ updated file with first part.");
    if(!(ResponseCodeEnum.OK == expectedPrecheckCodeUpdate && ResponseCodeEnum.SUCCESS == expectedPostcheckCodeUpdate)) {
      return;
    }
    
    //append the rest of parts except the last
    int midParts = 0;
    int lastPartLen = 0;
    if(remainder == 0) {
      midParts = numParts - 1;
      lastPartLen = FILE_PART_SIZE;
    } else {
      midParts = numParts;
      lastPartLen = remainder;
    }
    
    int i = 1;
    for (; i < midParts; i++) {
      byte[] partBytes = CommonUtils.copyBytes(i * FILE_PART_SIZE, FILE_PART_SIZE, largeFileBytes);
      fileData = ByteString.copyFrom(partBytes);
      appendFile(payerID, nodeID, fid, fileData, waclKeyList, expectedPrecheckCodeIntermediateAppend, expectedPostcheckCodeIntermediateAppend, isFree);
      log.info("@@@ append file count = " + i);
      if(!(ResponseCodeEnum.OK == expectedPrecheckCodeIntermediateAppend && ResponseCodeEnum.SUCCESS == expectedPostcheckCodeIntermediateAppend)) {
        return;
      }
    }
  
    // append last part
    if(numParts >= 2 || remainder > 0) {
      byte[] partBytes = CommonUtils.copyBytes(midParts * FILE_PART_SIZE, lastPartLen, largeFileBytes);
      fileData = ByteString.copyFrom(partBytes);
      appendFile(payerID, nodeID, fid, fileData, waclKeyList, expectedPrecheckCodeAppend, expectedPostcheckCodeAppend, isFree);
      log.info("@@@ append file last part: total count = " + i);
    }
 
    if(ResponseCodeEnum.OK == expectedPrecheckCodeAppend && ResponseCodeEnum.SUCCESS == expectedPostcheckCodeAppend) {
      // get file content and compare with source
      byte[] content = null;
      for (i = 0; i < 10; i++) {
        content = getFileContent(fid, payerID, nodeID).toByteArray();
        if (Arrays.equals(largeFileBytes, content)) {
          break;
        }
      }
      Assert.assertArrayEquals(largeFileBytes, content);
    }
  }

  /**
   * Appends a file with data.
   */
  public void appendFile(AccountID payerID, AccountID nodeID, FileID fid, ByteString fileData,
      List<Key> waclKeys, ResponseCodeEnum expectedPrecheckCode, 
      ResponseCodeEnum expectedPostcheckCode, boolean isFree) throws Throwable {
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    long fee = TestHelper.getFileMaxFee()*100;
    Transaction fileAppendRequest = RequestBuilder.getFileAppendBuilder(payerID.getAccountNum(),
        payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
        nodeID.getShardNum(), fee, timestamp, transactionDuration, true,
        "FileAppend", fileData,
        fid);
  
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(fileAppendRequest);
    TransactionID txId = body.getTransactionID();
  
    List<Key> keys = new ArrayList<Key>();
    Key payerKey = acc2ComplexKeyMap.get(payerID);
    keys.add(payerKey);
    if(waclKeys != null) {
      Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeys)).build();
      keys.add(waclKey);
    }
    Transaction txSigned = TransactionSigner
        .signTransactionComplexWithSigMap(fileAppendRequest, keys, pubKey2privKeyMap);
  
    log.info("\n-----------------------------------");
    log.info("FileAppend: request = " + txSigned);
    checkTxSize(txSigned);
  
    TransactionResponse response = stub.appendContent(txSigned);
    log.info("FileAppend Response :: " + response);
    Assert.assertNotNull(response);
    Assert.assertEquals(expectedPrecheckCode.getNumber(), response.getNodeTransactionPrecheckCodeValue());
  
    if(ResponseCodeEnum.OK == expectedPrecheckCode) {
      TransactionReceipt receipt = getTxReceipt(txId);
      Assert.assertEquals(expectedPostcheckCode, receipt.getStatus());
      checkFeeCharge(txId, payerID, nodeID, isFree);
    }
  }

  /**
   * Tests fee schedule file updates as follows:
    A/c 0.0.56 - Update Fee schedule (0.0.111) - This transaction should be FREE
    A/c 0.0.57 - Update Exchange Rate (0.0.112) - This transaction should be FREE
   */
  public void updateFeeScheduleTests() throws Throwable {
    AccountID nodeID = defaultListeningNodeAccountID;
    
    long[] feeScheduleAccounts = {2, 50, 56};
    long[] feeScheduleAccountsUnauth = {3, 49, 51, 55, 57, 80, 81, 100, payerAccounts[0].getAccountNum()};
    
    // update feeSchedule with authorized accounts
    byte[] feeBytes = validFeeFile.toByteArray();
    List<Key> feeFileWacl = (acc2ComplexKeyMap.get(genesisAccountID)).getKeyList().getKeysList(); // fee schedul file share keys with genesis
    for (int i = 0; i < feeScheduleAccounts.length; i++) {
      AccountID payer = genAccountID(feeScheduleAccounts[i]);
      if(!acc2ComplexKeyMap.containsKey(payer)) {
		  acc2ComplexKeyMap.put(payer, acc2ComplexKeyMap.get(genesisAccountID));
	  }
      
      if(payer.getAccountNum() == 50 || payer.getAccountNum() == 56) {//no wacl needed, also free
        // success with no wacl signing and no fee
        updateLargeFile(feeBytes, feeSchedule111, payer, nodeID, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 
            ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true);

        // if the uploaded content is invalid the append will still have SUCCESS
        byte[] invalid8KFile = genRandomBytes(8000); 
        updateLargeFile(invalid8KFile, feeSchedule111, payer, nodeID, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 
            ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true);
      } else if(payer.getAccountNum() == 2) {
        // success with wacl signing and fee
        updateLargeFile(feeBytes, feeSchedule111, payer, nodeID, feeFileWacl, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 
            ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false);

        // success due to payer and exchange file share the same key, with sig map, wacl is implicitly signed as payer is signing
        updateLargeFile(feeBytes, feeSchedule111, payer, nodeID, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false);

        // fails with no fee
        updateLargeFile(feeBytes, feeSchedule111, payer, nodeID, null, ResponseCodeEnum.INSUFFICIENT_TX_FEE, null, ResponseCodeEnum.INSUFFICIENT_TX_FEE, null, true);
      }
    }
    log.info(header + "update feeSchedule with authorized accounts");
    
    // update feeSchedule with un-authorized accounts
    for (int i = 0; i < feeScheduleAccountsUnauth.length; i++) {
      AccountID payer = genAccountID(feeScheduleAccountsUnauth[i]);
      if(!acc2ComplexKeyMap.containsKey(payer)) {
		  acc2ComplexKeyMap.put(payer, acc2ComplexKeyMap.get(genesisAccountID));
	  }
      updateLargeFile(feeBytes, feeSchedule111, payer, nodeID, feeFileWacl, ResponseCodeEnum.AUTHORIZATION_FAILED, null, ResponseCodeEnum.AUTHORIZATION_FAILED, null, false);
    }
    log.info(header + "update feeSchedule with un-authorized accounts");
      
    // use genesis account to restore feeSchedule to the state before the tests
    AccountID payer = genAccountID(2);
    updateLargeFile(serverFeeSchedule.toByteArray(), feeSchedule111, payer, nodeID, feeFileWacl, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 
        ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false);
    log.info(header + "use genesis account to restore feeSchedule to the state before the tests");

    log.info(header + "updateFeeScheduleTests finished");
  }

  /**
   * Transfer funds to existing accounts.
   *
   * @param accounts accounts to be funded.
   */
  public AccountID[] fundAccounts(long[] accounts) throws Throwable {
    AccountID[] rv = new AccountID[accounts.length];
    long realm = defaultListeningNodeAccountID.getRealmNum();
    long shard = defaultListeningNodeAccountID.getShardNum();
  
    for (int i = 0; i < accounts.length; i++) {
      long accNum = accounts[i];
      AccountID sysAccountID = RequestBuilder.getAccountIdBuild(accNum, realm, shard);
      Key key = acc2ComplexKeyMap
          .get(genesisAccountID); // note system accounts use same keypair as genesis
  
      //transfer funds to the account from genesis
      TransactionReceipt receipt = transfer(genesisAccountID, defaultListeningNodeAccountID,
          genesisAccountID, sysAccountID, DEFAULT_INITIAL_ACCOUNT_BALANCE, false, true);
      Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
  
      acc2ComplexKeyMap.put(sysAccountID, key);
      rv[i] = sysAccountID;
    }
  
    return rv;
  }

  /**
   * Tests address file updates as follows:
    A/c 0.0.55 - Update Address Book files (0.0.101/102)
    2 x Address book - Accounts 55 and 50: no signing by existing file wacl
   */
  public void updateAddressFileTests() throws Throwable {
    AccountID nodeID = defaultListeningNodeAccountID;
    
    long[] addressAccounts = {2, 50, 55};
    long[] addressAccountsUnauth = {3, 49, 51, 56, 57, 80, 81, 100, payerAccounts[0].getAccountNum()};
    List<Key> addressFileWacl = (acc2ComplexKeyMap.get(genesisAccountID)).getKeyList().getKeysList(); // address files share keys with genesis
    
    // update address book with authorized accounts
    for (int i = 0; i < addressAccounts.length; i++) {
      AccountID payer = genAccountID(addressAccounts[i]);
      if(payer.getAccountNum() == 50) {//free and no wacl needed
        // success with no fee and no wacl
        updateFileWithNoFee(addressBook101, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        updateFileWithNoFee(addressBook102, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        
        // update address book as a multipart file
        byte[] addressBytes = genRandomBytes(FILE_PART_SIZE * 3 + 1000);
        updateLargeFile(addressBytes, addressBook101, payer, nodeID, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 
            ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true);
        updateLargeFile(addressBytes, addressBook102, payer, nodeID, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 
            ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, true);
      } else if(payer.getAccountNum() == 55) {//not free, though no wacl needed
        // success with fee but no wacl
        updateFileWithFee(addressBook101, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        updateFileWithFee(addressBook102, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        
        // fails without fee
        updateFileWithNoFee(addressBook101, payer, nodeID, addressFileWacl, null, ResponseCodeEnum.INSUFFICIENT_TX_FEE, null);
        updateFileWithNoFee(addressBook102, payer, nodeID, addressFileWacl, null, ResponseCodeEnum.INSUFFICIENT_TX_FEE, null);

        // update address book as a multipart file
        byte[] addressBytes = genRandomBytes(FILE_PART_SIZE * 3 + 1000);
        updateLargeFile(addressBytes, addressBook101, payer, nodeID, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 
            ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false);
        updateLargeFile(addressBytes, addressBook102, payer, nodeID, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 
            ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false);
      } else if(payer.getAccountNum() == 2) {//not free, and require wacl
        // succeeds with wacl and fee
        updateFileWithFee(addressBook101, payer, nodeID, addressFileWacl, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        updateFileWithFee(addressBook102, payer, nodeID, addressFileWacl, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);

        // success due to payer and address files share the same key, with sig map, wacl is implicitly signed as payer is signing
        updateFileWithFee(addressBook101, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        updateFileWithFee(addressBook102, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        
        // fails without fee
        updateFileWithNoFee(addressBook101, payer, nodeID, addressFileWacl, null, ResponseCodeEnum.INSUFFICIENT_TX_FEE, null);
        updateFileWithNoFee(addressBook102, payer, nodeID, addressFileWacl, null, ResponseCodeEnum.INSUFFICIENT_TX_FEE, null);

        // update address book as a multipart file
        byte[] addressBytes = genRandomBytes(FILE_PART_SIZE * 3 + 1000);
        updateLargeFile(addressBytes, addressBook101, payer, nodeID, addressFileWacl, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 
            ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false);
        updateLargeFile(addressBytes, addressBook102, payer, nodeID, addressFileWacl, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 
            ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false);
      }
    }
    log.info(header + "update address book with authorized accounts");
    
    // update address book with un-authorized accounts
    for (int i = 0; i < addressAccountsUnauth.length; i++) {
      AccountID payer = genAccountID(addressAccountsUnauth[i]);
      if(!acc2ComplexKeyMap.containsKey(payer)) {
		  acc2ComplexKeyMap.put(payer, acc2ComplexKeyMap.get(genesisAccountID));
	  }
      updateFileWithFee(addressBook101, payer, nodeID, null, null, ResponseCodeEnum.AUTHORIZATION_FAILED, null);
      updateFileWithFee(addressBook102, payer, nodeID, null, null, ResponseCodeEnum.AUTHORIZATION_FAILED, null);
    }
    log.info(header + "update address book with un-authorized accounts");
      
    // use genesis account to restore address book to the state before the tests
    AccountID payer = genAccountID(2);
    updateFile(addressBook101, payer, nodeID, addressFileWacl, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false, serverAddressBook.toByteString());
    updateFile(addressBook102, payer, nodeID, addressFileWacl, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false, serverNodeDetails.toByteString());
    log.info(header + "use genesis account to restore address book to the state before the tests");
    
    log.info(header + "updateAddressFileTests finished");
  }

  /**
   * Tests Exchange Rate file updates as follows:
    A/c 0.0.57 - Update Exchange Rate (0.0.112) - This transaction should be FREE
    Exchange Rate - Account 57 and 50: no file wacl required
   */
  public void updateExchangeRateFileTests() throws Throwable {
    AccountID nodeID = defaultListeningNodeAccountID;
    
    long[] exchangeRateAccounts = {57, 2, 50};
    long[] exchangeRateAccountsUnauth = {3, 49, 51, 55, 56, 80, 81, 100, payerAccounts[0].getAccountNum()};
    List<Key> exchangeRateFileWacl = (acc2ComplexKeyMap.get(genesisAccountID)).getKeyList().getKeysList(); // exchange file share keys with genesis
    
    // update exchangeRate with authorized accounts
    for (int i = 0; i < exchangeRateAccounts.length; i++) {
      AccountID payer = genAccountID(exchangeRateAccounts[i]);
      if(!acc2ComplexKeyMap.containsKey(payer)) {
		  acc2ComplexKeyMap.put(payer, acc2ComplexKeyMap.get(genesisAccountID));
	  }
      
      if(payer.getAccountNum() == 50) {//no wacl needed, also free
        // success with no wacl signing and no fee
        isSmallExchangeRateUpdate = true; // small change is OK
        updateFileWithNoFee(exchangeRate112, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        isSmallExchangeRateUpdate = false; // large change is also OK
        updateFileWithNoFee(exchangeRate112, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        isSmallExchangeRateUpdate = true; // reset flag
        
        // fails if the uploaded content is invalid
        updateFileWithInvalidContent(exchangeRate112, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE, true);
      } else if(payer.getAccountNum() == 57) {//no wacl needed, also free
        // success with no wacl signing and no fee
        isSmallExchangeRateUpdate = true; // small change is OK. Note this works if this is the first time to change the exchange rate in the same day 
        updateFileWithNoFee(exchangeRate112, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);

        isSmallExchangeRateUpdate = false; // large change is NOT OK
        updateFileWithNoFee(exchangeRate112, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED);
        isSmallExchangeRateUpdate = true; // reset flag

        // fails if the uploaded content is invalid
        updateFileWithInvalidContent(exchangeRate112, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE, true);
      } else if(payer.getAccountNum() == 2) {
        // success with wacl signing and fee
        isSmallExchangeRateUpdate = true; // small change is OK
        updateFileWithFee(exchangeRate112, payer, nodeID, exchangeRateFileWacl, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        isSmallExchangeRateUpdate = false; // large change is also OK
        updateFileWithFee(exchangeRate112, payer, nodeID, exchangeRateFileWacl, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);
        isSmallExchangeRateUpdate = true; // reset flag

        // success due to payer and exchange file share the same key, with sig map, wacl is implicitly signed as payer is signing
        updateFileWithFee(exchangeRate112, payer, nodeID, null, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS);

        // fails with no fee
        updateFileWithNoFee(exchangeRate112, payer, nodeID, exchangeRateFileWacl, null, ResponseCodeEnum.INSUFFICIENT_TX_FEE, null);
      }
    }
    log.info(header + "update exchangeRate with authorized accounts");
    
    // update exchangeRate with un-authorized accounts
    for (int i = 0; i < exchangeRateAccountsUnauth.length; i++) {
      AccountID payer = genAccountID(exchangeRateAccountsUnauth[i]);
      if(!acc2ComplexKeyMap.containsKey(payer)) {
		  acc2ComplexKeyMap.put(payer, acc2ComplexKeyMap.get(genesisAccountID));
	  }
      updateFileWithFee(exchangeRate112, payer, nodeID, exchangeRateFileWacl, null, ResponseCodeEnum.AUTHORIZATION_FAILED, null);
    }
    log.info(header + "update exchangeRate with un-authorized accounts");
    
    // use genesis account to restore exchangeRate to the state before the tests
    AccountID payer = genAccountID(2);
    updateFile(exchangeRate112, payer, nodeID, exchangeRateFileWacl, null, ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, false, serverExchangeRate.toByteString());
    log.info(header + "use genesis account to restore exchangeRate to the state before the tests");

    log.info(header + "updateExchangeRateFileTests finished");
  }

  /**
   * Gets the fee schedule file from a file path.
   * 
   * @param feeFileBytesPath path to the file
   * @return byte string of the fee file read.
   * @throws URISyntaxException
   * @throws IOException
   */
  public static ByteString getFeeScheduleByteString(String feeFileBytesPath) throws URISyntaxException, IOException {
    Path pathFeeSch =
        Paths.get(TestHelper.class.getClassLoader().getResource(feeFileBytesPath).toURI());
    File feeSchFile = new File(pathFeeSch.toString());
    InputStream fis = new FileInputStream(feeSchFile);
    byte[] fileBytes = new byte[(int) feeSchFile.length()];
    fis.read(fileBytes);
    CurrentAndNextFeeSchedule feeSch = CurrentAndNextFeeSchedule.parseFrom(fileBytes);
  
    return ByteString.copyFrom(feeSch.toByteArray());
  }

  public static Transaction getFileUpdateBuilder(Long payerAccountNum, Long payerRealmNum,
      Long payerShardNum,
      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
      long transactionFee, Timestamp timestamp, Timestamp fileExpTime,
      Duration transactionDuration, boolean generateRecord, String memo,
      ByteString data, FileID fid, KeyList keys) {
    FileUpdateTransactionBody.Builder builder = FileUpdateTransactionBody.newBuilder()
        .setContents(data)
        .setFileID(fid);
    
    if(fileExpTime != null) {
		builder.setExpirationTime(fileExpTime);
	}

    if(keys != null) {
		builder.setKeys(keys);
	}

    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
        nodeAccountNum,
        nodeRealmNum, nodeShardNum, transactionFee, timestamp, transactionDuration, generateRecord,
        memo);
    body.setFileUpdate(builder);
    byte[] bodyBytesArr = body.build().toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    return Transaction.newBuilder().setBodyBytes(bodyBytes).build();
  }

  private static TransactionBody.Builder getTransactionBody(Long payerAccountNum,
      Long payerRealmNum, Long payerShardNum, Long nodeAccountNum, Long nodeRealmNum,
      Long nodeShardNum, long transactionFee, Timestamp timestamp, Duration transactionDuration,
      boolean generateRecord, String memo) {
    AccountID payerAccountID = RequestBuilder.getAccountIdBuild(payerAccountNum, payerRealmNum, payerShardNum);
    AccountID nodeAccountID = RequestBuilder.getAccountIdBuild(nodeAccountNum, nodeRealmNum, nodeShardNum);
    return getTxBodyBuilder(transactionFee, timestamp, transactionDuration, generateRecord, memo,
        payerAccountID, nodeAccountID);
  }

  public static TransactionBody.Builder getTxBodyBuilder(long transactionFee, Timestamp timestamp,
      Duration transactionDuration, boolean generateRecord, String memo, AccountID payerAccountID,
      AccountID nodeAccountID) {
    TransactionID transactionID = RequestBuilder.getTransactionID(timestamp, payerAccountID);
    return TransactionBody.newBuilder().setTransactionID(transactionID)
        .setNodeAccountID(nodeAccountID)
        .setTransactionFee(transactionFee).setTransactionValidDuration(transactionDuration)
        .setGenerateRecord(generateRecord).setMemo(memo);
  }

  /**
   * Gets the address book file from a file path.
   * 
   * @param addressBookFileBytesPath path to the file
   * @return byte string of the address book file read.
   * @throws URISyntaxException
   * @throws IOException
   */
  public static ByteString getAddresBookByteString(String addressBookFileBytesPath) throws URISyntaxException, IOException {
    Path pathFeeSch =
        Paths.get(TestHelper.class.getClassLoader().getResource(addressBookFileBytesPath).toURI());
    File feeSchFile = new File(pathFeeSch.toString());
    InputStream fis = new FileInputStream(feeSchFile);
    byte[] fileBytes = new byte[(int) feeSchFile.length()];
    fis.read(fileBytes);

    return ByteString.copyFrom(fileBytes);
  }

  /**
   * Generates valid but random exchange rate file.
   * @return the byte string of the generated exchange rate file.
   */
  public static ByteString getRandomExchangeFile() {
    int currentCent = (new Random()).nextInt(100);
    int nextCent = currentCent + 5;
    return getExchangeRate(1, currentCent, 1, nextCent);
  }
  
  /**
   * Generates a valid exchange rate file with small or large changes to the current server exchange rate.
   * @return the byte string of the generated exchange rate file.
   * @throws Throwable 
   */
  public ByteString genExchangeFile() throws Throwable {
    ExchangeRateSet serverRates = getExchangeRateFromServer();
    
    log.info("server exchange=" + serverRates);
    long c0 = serverRates.getCurrentRate().getCentEquiv();
    long c1 = serverRates.getNextRate().getCentEquiv();
    
    int B = 1; // 1 percent bound
    int b = 0;
    if(isSmallExchangeRateUpdate) {
		b = B;
	} else {
		b = 100;
	}

    long c0n = c0 + c0 * b / 100;
    long c1n = c1 + c1 * b / 100;
    boolean c0Change = isSmallChange(B, c0, 1, c0n, 1);
    boolean c1Change = isSmallChange(B, c1, 1, c1n, 1);
    log.info("c0=" + c0 + ", c0Change=" + c0Change + "; c1=" + c1 + ", c1Change=" + c1Change);
    Assert.assertEquals(isSmallExchangeRateUpdate, c0Change);
    Assert.assertEquals(isSmallExchangeRateUpdate, c1Change);
    
    com.hederahashgraph.api.proto.java.ExchangeRate curr = serverRates.getCurrentRate().toBuilder().setCentEquiv((int) c0n).build();
    com.hederahashgraph.api.proto.java.ExchangeRate next = serverRates.getNextRate().toBuilder().setCentEquiv((int) c1n).build();
    
    ExchangeRateSet newExchange = serverRates.toBuilder().setCurrentRate(curr).setNextRate(next).build();
    log.info("isSmallExchangeRateUpdate=" + isSmallExchangeRateUpdate + ", generated exchange=" + newExchange);
    return newExchange .toByteString();
  }
  
  /**
   * Gets the current exchange rate from server.
   * 
   * @return the current exchange rate
   * @throws Throwable
   */
  public ExchangeRateSet getExchangeRateFromServer() throws Throwable {
    ByteString content = getFileContent(exchangeRate112, genesisAccountID, defaultListeningNodeAccountID);
    ExchangeRateSet rateSet = ExchangeRateSet.parseFrom(content);
    return rateSet;
  }
  
  /**
   * Gets the current fee schedule from server.
   * 
   * @return the current fee schedule
   * @throws Throwable
   */
  public FeeSchedule getFeeScheduleFromServer() throws Throwable {
    ByteString content = getFileContent(feeSchedule111, genesisAccountID, defaultListeningNodeAccountID);
    FeeSchedule rv = FeeSchedule.parseFrom(content);
    return rv;
  }
  
  /**
   * Generates a valid exchange rate file with small changes to the current server exchange rate.
   * @throws Throwable
   */
  public void smallChangeOnExchangeTest() throws Throwable {
    ExchangeRateSet serverRates = getExchangeRateFromServer();
  
    log.info("server exchange=" + serverRates);
    long c0 = serverRates.getCurrentRate().getCentEquiv();
    long c1 = serverRates.getNextRate().getCentEquiv();
    
    int B = 1; // 1 percent bound

    long c0n = 200;
    long c1n = 202;
    boolean c0Change = isSmallChange(B, c0, 1, c0n, 1);
    boolean c1Change = isSmallChange(B, c1, 1, c1n, 1);
    log.info("c0=" + c0 + ", c0Change=" + c0Change + "; c1=" + c1 + ", c1Change=" + c1Change);
    Assert.assertEquals(c0n <= (c0 + c0*B/100), c0Change);
    Assert.assertEquals(c1n <= (c1 + c1*B/100), c1Change);
  }

  public boolean isSmallChange(long bound, long oldC, long oldH, long newC, long newH) {
    BigInteger k100 = BigInteger.valueOf(100);
    BigInteger b100 = BigInteger.valueOf(bound).add(k100);
    BigInteger oC = BigInteger.valueOf(oldC);
    BigInteger oH = BigInteger.valueOf(oldH);
    BigInteger nC = BigInteger.valueOf(newC);
    BigInteger nH = BigInteger.valueOf(newH);
  
    return
        bound > 0 && oldC > 0 && oldH > 0 && newC > 0 && newH > 0
            && oC.multiply(nH).multiply(b100).subtract(
            nC.multiply(oH).multiply(k100)
        ).signum() >= 0
            && oH.multiply(nC).multiply(b100).subtract(
            nH.multiply(oC).multiply(k100)
        ).signum() >= 0;
  }

  /**
   * Gets the current address book from server.
   * 
   * @return the current address book
   * @throws Throwable
   */
  public NodeAddressBook getAddressBookFromServer() throws Throwable {
    ByteString content = getFileContent(addressBook101, genesisAccountID, defaultListeningNodeAccountID);
    NodeAddressBook rv = NodeAddressBook.parseFrom(content);
    return rv;
  }

  /**
   * Gets the current node details from server.
   * 
   * @return the current node details
   * @throws Throwable
   */
  public NodeAddressBook getNodeDetailsFromServer() throws Throwable {
    ByteString content = getFileContent(addressBook102, genesisAccountID, defaultListeningNodeAccountID);
    NodeAddressBook rv = NodeAddressBook.parseFrom(content);
    return rv;
  }

  /**
   * Generates a modified version of the node details file by removing one of the elements of the source node details file.
   * 
   * @param sourceNodeDetails the source node details file
   * @param isRandom if true, remove a random element from the source node details file, otherwise remove the last element
   * 
   * @return modified node details file
   * @throws Throwable
   */
  public ByteString modNodeDetailsFile(NodeAddressBook sourceNodeDetails, boolean isRandom) throws Throwable {
    return modAddressBookFile(addressBook102, sourceNodeDetails, isRandom);
  }
  
  /**
   * Generates a modified version of the address book file by removing one of the elements of the source address book file.
   * 
   * @param sourceAddressBook the source address book file
   * @param isRandom if true, remove a random element from the source address book file, otherwise remove the last element
   * 
   * @return modified address book file
   * @throws Throwable
   */
  public ByteString modAddressBookFile(NodeAddressBook sourceAddressBook, boolean isRandom) throws Throwable {
    return modAddressBookFile(addressBook101, sourceAddressBook, isRandom);
  }
  
  /**
   * Generates a modified version of the addressbook or node details file.
   *  
   * @param fid file ID of the addressbook or node details file
   * @param sourceAddressBook the source of the addressbook or node details file
   * @param isRandom if true, remove a random element from the source address book file, otherwise remove the last element
   * @return modified version of the addressbook or node details file
   * @throws Throwable
   */
  public ByteString modAddressBookFile(FileID fid, NodeAddressBook sourceAddressBook, boolean isRandom) throws Throwable {
    List<NodeAddress> addressList = new ArrayList<>(sourceAddressBook.getNodeAddressList());
    int index = addressList.size() - 1;
    if(isRandom) {
		index = (new Random()).nextInt(addressList.size());
	}
    
    addressList.remove(index);
    NodeAddressBook mod = NodeAddressBook.newBuilder().addAllNodeAddress(addressList).build();
    
    return mod.toByteString();
  }
  
  /**
   * Generates valid exchange rate file.
   * @return the byte string of the generated exchange rate file.
   */
  public static ByteString getExchangeRate(int currentHbarEquivalent, int currentCentEquivalent, 
      int nextHbarEquivalent, int nextCentEquivalent) {
    long expiryTime = 4688462211l;
    ExchangeRateSet exchange = RequestBuilder.getExchangeRateSetBuilder(currentHbarEquivalent, 
        currentCentEquivalent, expiryTime, nextHbarEquivalent, nextCentEquivalent, expiryTime);
    log.info("exchange file = " + exchange);
    return ByteString.copyFrom(exchange.toByteArray());
  }

}
