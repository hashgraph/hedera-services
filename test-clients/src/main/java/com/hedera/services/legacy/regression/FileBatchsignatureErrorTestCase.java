package com.hedera.services.legacy.regression;

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
import com.hederahashgraph.api.proto.java.*;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Test Client Specific Error code validations
 *
 *
 * @author Tirupathi Mandala Created on 2019-06-12
 */
public class FileBatchsignatureErrorTestCase extends BaseFeeTests {

  private static final Logger log = LogManager.getLogger(FileBatchsignatureErrorTestCase.class);
  private static String testConfigFilePath = "config/umbrellaTest.properties";
  public FileBatchsignatureErrorTestCase(String testConfigFilePath) {
    super(testConfigFilePath);
  }


  public static void main(String[] args) throws Throwable {
    FileBatchsignatureErrorTestCase tester = new FileBatchsignatureErrorTestCase(testConfigFilePath);
    tester.setup(args);
    tester.fileUpdateTest(ResponseCodeEnum.SUCCESS);
    tester.fileUpdateTest(ResponseCodeEnum.INVALID_FILE_ID);
    tester.fileUpdateTest(ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND);
    tester.fileUpdateTest(ResponseCodeEnum.INVALID_SIGNATURE);
    tester.fileUpdateTest(ResponseCodeEnum.KEY_PREFIX_MISMATCH);
  }

  public void fileUpdateTest(ResponseCodeEnum responseCodeEnum) throws Throwable {
    List<Key> payerKeyList = new ArrayList<>();
    payerKeyList.add(acc2ComplexKeyMap.get(payerID));

    byte[] fileContents = new byte[4];
    random.nextBytes(fileContents);
    ByteString fileData = ByteString.copyFrom(fileContents);
    List<Key> waclPubKeyList = fit.genWaclComplex(1, "single");
    long durationSeconds = CustomPropertiesSingleton.getInstance().getFileDurtion();
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
    String memo = getStringMemo(1);
    Transaction fileCreateRequest = fit
        .createFile(payerID, nodeID, fileData, waclPubKeyList, fileExp, memo);
    TransactionResponse response = stub.createFile(fileCreateRequest);
    log.info("FileCreate Response :: " + response);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
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
    AccountID newAccountID = getMultiSigAccount(3, 1, durationSeconds, 10000000000L);
    List<Key> newWaclPubKeyList = fit.genWaclComplex(3, "single");
    memo = getStringMemo(10);
    fileContents = new byte[8];
    random.nextBytes(fileContents);
    fileData = ByteString.copyFrom(fileContents);
    if(responseCodeEnum == ResponseCodeEnum.INVALID_FILE_ID) {
      fid = FileID.newBuilder().setFileNum(99999).setRealmNum(0).setShardNum(0).build();
    } else if(responseCodeEnum == ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND) {
      newAccountID = AccountID.newBuilder().setAccountNum(99999).setShardNum(0).setRealmNum(0).build();
      Key key = genComplexKey("single");
      acc2ComplexKeyMap.put(newAccountID, key);
    } else if(responseCodeEnum == ResponseCodeEnum.INVALID_SIGNATURE) {
      newAccountID = account_3;
      Key key = genComplexKey("single"); //Messup signature for Invalid Signature
      acc2ComplexKeyMap.put(newAccountID, key);
    }

    Transaction fileUpdateRequest = fit.updateFile(fid, newAccountID, nodeID,
        waclPubKeyList, newWaclPubKeyList, fileData, memo, fileExp);

    if(responseCodeEnum == ResponseCodeEnum.KEY_PREFIX_MISMATCH) {
      fileUpdateRequest = removeSigPairFromTransaction(fileUpdateRequest);
    }

    response = stub.updateFile(fileUpdateRequest);
    Thread.sleep(NAP);
    TransactionBody updateBody = TransactionBody.parseFrom(fileUpdateRequest.getBodyBytes());
    if (updateBody.getTransactionID() == null || !updateBody.getTransactionID()
        .hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    TransactionRecord txRecord = getTransactionRecord( updateBody.getTransactionID(),50);


    log.info("response = "+response.getNodeTransactionPrecheckCode().name());
    log.info("txRecord = "+txRecord);
    if(responseCodeEnum == ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND
    || responseCodeEnum == ResponseCodeEnum.INVALID_SIGNATURE
    || responseCodeEnum == ResponseCodeEnum.KEY_PREFIX_MISMATCH) {
      Assert.assertEquals(responseCodeEnum,response.getNodeTransactionPrecheckCode() );
    } else if(responseCodeEnum == txRecord.getReceipt().getStatus()){
      Assert.assertEquals(responseCodeEnum, txRecord.getReceipt().getStatus());
      validateTransactionRecordForErrorCase(txRecord);
    }
  }


  public static Transaction removeSigPairFromTransaction(Transaction transaction) {
    SignatureMap sigMap =transaction.getSigMap();
    List<SignaturePair> sigPairList = sigMap.getSigPairList();

    List<SignaturePair> newSigPairList = new ArrayList<>();
    for(int i=1;i<sigPairList.size();i++) {
      newSigPairList.add(sigPairList.get(i));
    }
    SignatureMap newSigMap = sigMap.toBuilder().addAllSigPair(newSigPairList).build();
    return transaction.toBuilder().setSigMap(newSigMap).build();
  }

  public static void validateTransactionRecordForErrorCase(TransactionRecord txRecord) {
    Assert.assertNotNull(txRecord);
    List<AccountAmount> accountAmounts = txRecord.getTransferList().getAccountAmountsList();
    Assert.assertTrue(!accountAmounts.isEmpty());
    Assert.assertTrue(accountAmounts.get(0).getAmount() != 0);
  }

}
