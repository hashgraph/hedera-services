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
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.FeeClient;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileGetContentsResponse.FileContents;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.builder.RequestBuilder;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;

/**
 * Integration tests for upload a large file.
 *
 * @author hua
 */
public class LargeFileUploadIT extends FileServiceIT {

  protected static int FILE_PART_SIZE = 4096; //4K bytes

  public static void main(String[] args) throws Exception {
//		uploadLargeFileTestWithPayer();
    uploadLargeFileTest();
//		checkFileContentTest(5, "spring-core-4.2.0.RELEASE-1mb-jar");			
  }

  public static void uploadLargeFileTestWithPayer() throws Exception {
    LargeFileUploadIT tester = new LargeFileUploadIT();
    tester.init();

//		String filePath = "spring-core-4.2.0.RELEASE-1mb-jar"; 
//		String filePath = "overview-frame.html"; 
    String filePath = "contract/bytecodes/octoken.bin";
    localPath = UPLOAD_PATH + filePath;

    uploadFile(genesisAccountID, localPath, tester.getGenesisPrivateKeyList());
  }

  /**
   * Upload a file. If the file is larger than default size (4K), it will be segmented into 4K parts
   * and uploaded one at a time.
   *
   * @param payerAccountKeyPair the payer is a single keypair
   */
  public static FileID uploadFile(AccountID payerAccount, String filePath,
      KeyPair payerAccountKeyPair) throws Exception {
    List<PrivateKey> payerAccountPrivateKeys = new ArrayList<>();
    payerAccountPrivateKeys.add(payerAccountKeyPair.getPrivate());
    return uploadFile(payerAccount, filePath, payerAccountPrivateKeys);
  }

  /**
   * Upload a file with string substitution. If the file is larger than default size (4K), it will
   * be segmented into 4K parts and uploaded one at a time.
   *
   * @param payerAccountKeyPair the payer is a single keypair
   */
  public static FileID uploadFileWithStringSubstitution(AccountID payerAccount, String filePath,
      KeyPair payerAccountKeyPair, String regex, String replacement) throws Exception {
    List<PrivateKey> payerAccountPrivateKeys = new ArrayList<>();
    payerAccountPrivateKeys.add(payerAccountKeyPair.getPrivate());
    return uploadFileWithStringSubstitution(payerAccount, filePath, payerAccountPrivateKeys, regex, replacement,
            null, null);
  }

  /**
   * Upload a file. If the file is larger than default size (4K), it will be segmented into 4K parts
   * and uploaded one at a time.
   */
  public static FileID uploadFile(AccountID payerAccount, String filePath,
    List<PrivateKey> payerAccountPrivateKeys) throws Exception {
    LargeFileUploadIT tester = new LargeFileUploadIT();
    tester.init();

    payerSeq = payerAccount.getAccountNum();
    recvSeq = defaultListeningNodeAccountID.getAccountNum();
    acc2keyMap.put(payerAccount.getAccountNum(), payerAccountPrivateKeys);

    //segment file into parts and append them in order
    byte[] bytes = CommonUtils.readBinaryFileAsResource(filePath);
    System.out.println("@@@ upload file at: " + filePath + "; size=" + bytes.length);

    tester.uploadFile(filePath, bytes);
    return fid;
  }


  /**
   * Upload a file. If the file is larger than default size (4K), it will be segmented into 4K parts
   * and uploaded one at a time.  Override the config file host and node account.
   */
  public static FileID uploadFile(AccountID payerAccount, String filePath,
      List<PrivateKey> payerAccountPrivateKeys, String overrideHost,
      AccountID overrideNodeAccountID) throws Exception {
    LargeFileUploadIT tester = new LargeFileUploadIT();
    tester.init(overrideHost, overrideNodeAccountID);

    payerSeq = payerAccount.getAccountNum();
    recvSeq = defaultListeningNodeAccountID.getAccountNum();
    acc2keyMap.put(payerAccount.getAccountNum(), payerAccountPrivateKeys);

    //segment file into parts and append them in order
    byte[] bytes = CommonUtils.readBinaryFileAsResource(filePath);
    System.out.println("@@@ upload file at: " + filePath + "; size=" + bytes.length);

    tester.uploadFile(filePath, bytes);
    tester.cleanUp();
    return fid;
  }

  /**
   * Upload a file with string substitution. If the file is larger than default size (4K), it will
   * be segmented into 4K parts and uploaded one at a time.
   */
  public static FileID uploadFileWithStringSubstitution(AccountID payerAccount, String filePath,
      List<PrivateKey> payerAccountPrivateKeys, String regex, String replacement, String overridehost,
          AccountID overrideAccount) throws Exception {
    LargeFileUploadIT tester = new LargeFileUploadIT();
    tester.init( overridehost, overrideAccount);

    payerSeq = payerAccount.getAccountNum();
    recvSeq = defaultListeningNodeAccountID.getAccountNum();
    acc2keyMap.put(payerAccount.getAccountNum(), payerAccountPrivateKeys);

    //segment file into parts and append them in order
    byte[] bytes = CommonUtils.readBinaryFileAsResource(filePath);
    System.out.println("@@@ upload file at: " + filePath + "; size=" + bytes.length);

    // Convert to string, substitute, convert back. Ugly but effective.
    String thing = new String(bytes);
    String newThing = thing.replaceAll(regex, replacement);
    byte[] product = newThing.getBytes();
    System.out.println("@@@ After substitution, size=" + product.length);

    tester.uploadFile(filePath, product);
    return fid;
  }

  private void uploadFile(String filePath, byte[] bytes) throws Exception {
    int numParts = bytes.length / FILE_PART_SIZE;
    int remainder = bytes.length % FILE_PART_SIZE;
    System.out.println(
        "@@@ file size=" + bytes.length + "; FILE_PART_SIZE=" + FILE_PART_SIZE + "; numParts="
            + numParts + "; remainder=" + remainder);

    byte[] firstPartBytes = null;
    if (bytes.length <= FILE_PART_SIZE) {
      firstPartBytes = bytes;
      remainder = 0;
    } else {
      firstPartBytes = CommonUtils.copyBytes(0, FILE_PART_SIZE, bytes);
    }

    //create file with first part
    fileData = ByteString.copyFrom(firstPartBytes);
    test02CreateFile();
    System.out.println("@@@ create file with first part.");
    CommonUtils.nap(WAIT_IN_SEC);

    test03GetTxReceipt();
    test05GetFileInfo();
    CommonUtils.nap(WAIT_IN_SEC);

    //append the rest of the parts
    int i = 1;
    for (; i < numParts; i++) {
      byte[] partBytes = CommonUtils.copyBytes(i * FILE_PART_SIZE, FILE_PART_SIZE, bytes);
      fileData = ByteString.copyFrom(partBytes);
      test07AppendFile();
      System.out.println("@@@ append file count = " + i);
      CommonUtils.nap(WAIT_IN_SEC);
      test05GetFileInfo();
    }

    if (remainder > 0) {
      byte[] partBytes = CommonUtils.copyBytes(numParts * FILE_PART_SIZE, remainder, bytes);
      fileData = ByteString.copyFrom(partBytes);
      test07AppendFile();
      System.out.println("@@@ append file count = " + i);
      CommonUtils.nap(WAIT_IN_SEC);
      test05GetFileInfo();
    }

    // get file content and save to disk
    byte[] content = null;
    for (i = 0; i < 10; i++) {
      content = getFileContent().toByteArray();
      if (Arrays.equals(bytes, content)) {
        break;
      }
      CommonUtils.nap(WAIT_IN_SEC);
    }
    Assert.assertArrayEquals(bytes, content);
    saveFile(content, filePath);
  }

  protected void saveFile(byte[] content, String filePath) throws IOException {
    String path = "saved" + FileSystems.getDefault().getSeparator() + filePath;
    CommonUtils.writeToFile(path, content);
    String workDir = System.getProperty("user.dir");
    System.out.println(
        ":) file downloaded and saved at project root as: " + workDir + FileSystems.getDefault()
            .getSeparator() + path);
  }

  public ByteString getFileContent()
      throws Exception {
    long fileContentFee = FeeClient.getFeeByID(HederaFunctionality.FileGetContents);

    Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "FileGetContentCost",
        fileContentFee);
    Query fileGetContentCostQuery = RequestBuilder
        .getFileGetContentBuilder(paymentTxSigned, fid, ResponseType.COST_ANSWER);
    Response getFileCostInfoResponse = stub.getFileContent(fileGetContentCostQuery);
    long queryGetFileContentCostFee = getFileCostInfoResponse.getFileGetContents().getHeader()
        .getCost();

    paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "FileGetContent",
        queryGetFileContentCostFee);
    Query fileGetContentQuery = RequestBuilder
        .getFileGetContentBuilder(paymentTxSigned, fid, ResponseType.ANSWER_ONLY);
    System.out.println("\n-----------------------------------");
    System.out.println("FileGetContent: query = " + fileGetContentQuery);

    Response fileContentResp = stub.getFileContent(fileGetContentQuery);
    FileContents fileContent = fileContentResp.getFileGetContents().getFileContents();
    ByteString actualFileData = fileContent.getContents();
    System.out.println("FileGetContent: content = " + fileContent);
    System.out.println("FileGetContent: file size = " + actualFileData.size());
    return actualFileData;
  }

  public ByteString getFileContent(long fileNum, String filePath)
      throws Exception {
    fid = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(fileNum)
        .build();
    ByteString rv = getFileContent();
    saveFile(rv.toByteArray(), filePath);
    return rv;
  }

  public static void uploadLargeFileTest() throws Exception {
    LargeFileUploadIT tester = new LargeFileUploadIT();
    tester.init();
    tester.test01InitAccounts();

    // segment file into parts and append them in order
    String filePath = "hashgraph-hellofuture.jpg";
//    String filePath = "hg2.pdf";
    String localPath = UPLOAD_PATH + filePath;
    byte[] bytes = CommonUtils.readBinaryFileAsResource(localPath);
    System.out.println("@@@ upload file at: " + localPath + "; size=" + bytes.length);

    tester.uploadFile(filePath, bytes);
  }

  public static void checkFileContentTest(long fileNum, String filePath) throws Exception {
    LargeFileUploadIT tester = new LargeFileUploadIT();
    tester.init();
    tester.test01InitAccounts();
    tester.getFileContent(fileNum, filePath);
  }
}
