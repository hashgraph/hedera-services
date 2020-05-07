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
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetContentsResponse.FileContents;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.SignatureList.Builder;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.CustomProperties;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Integration tests for file APIs.
 *
 * @author hua
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Ignore
public class FileServiceIT {

  private static final long MAX_TX_FEE = TestHelper.getFileMaxFee();
  private static final int MAX_BUSY_RETRIES = 25;
  private static final int BUSY_RETRY_MS = 200;
  protected static final Logger log = LogManager.getLogger(FileServiceIT.class);
  protected static long DEFAULT_INITIAL_ACCOUNT_BALANCE = 100000000000000L;
  public static long TX_DURATION_SEC = 2 * 60; // 2 minutes for tx dedup
  public static long DAY_SEC = 24 * 60 * 60; // secs in a day
  protected static String[] files = {"1K.txt", "overview-frame.html"};
  protected static String UPLOAD_PATH = "testfiles/";
  public static Map<String, List<AccountKeyListObj>> hederaStartupAccount = null;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  protected String DEFAULT_NODE_ACCOUNT_ID_STR = "0.0.3";


  protected static ManagedChannel channel = null;
  protected static Map<Long, List<PrivateKey>> acc2keyMap = new LinkedHashMap<>();
  protected static Map<AccountID, List<KeyPair>> account2keyMap = new HashMap<>();
  protected static FileServiceBlockingStub stub = null; // default file service stub that connects to the default listening node
  protected static CryptoServiceGrpc.CryptoServiceBlockingStub cstub = null; // default crypto service stub that connects to the default listening node
  protected static ByteString fileData = null;
  protected static TransactionID txId = null;
  protected static FileID fid = null;
  protected static Duration transactionDuration = Duration.newBuilder().setSeconds(TX_DURATION_SEC).build();
  protected static SignatureList signatures = SignatureList.newBuilder()
      .getDefaultInstanceForType();
  public static Map<String, List<AccountKeyListObj>> hederaAccounts = null;
  public static Map<String, KeyPair> accountKeyPairHolder = new HashMap<String, KeyPair>();
  protected static List<AccountKeyListObj> genesisAccountList;
  protected static List<PrivateKey> genesisPrivateKeyList = new ArrayList<>();
  protected static AccountID genesisAccountID;
  private static KeyPairObj genesisKeyPair;
  private List<PrivateKey> waclPrivKeyList;
  private List<PrivateKey> newWaclPrivKeyList;
  public static int NUM_WACL_KEYS = 5;
  protected static int WAIT_IN_SEC = 5;
  protected static AccountID senderId;
  protected static AccountID recvId;
  protected static String localPath;
  protected static String host = "localhost";
  protected boolean hostOverridden = false;
  protected static int port = 50211;
  public static AccountID defaultListeningNodeAccountID = null; // The account ID of the default listening node
  protected boolean nodeAccountOverridden = false;
  protected static int uniqueListeningPortFlag = 0; // By default, all nodes are listening on the same port, i.e. 50211
  protected static long payerSeq = -1;
  protected static long recvSeq = -1;
  // A value of 1 is for production, where all nodes listen on same default port
  // A value of 0 is for development, i.e. running locally
  protected static int productionFlag = 0;
  protected static long fileDuration;

  @Before
  public void setUp()
      throws IOException, ClassNotFoundException, InvalidKeySpecException, DecoderException, URISyntaxException, Exception {
    init();
    String filePath = files[0];
    localPath = UPLOAD_PATH + filePath;
    byte[] bytes = CommonUtils.readBinaryFileAsResource(localPath, getClass());
//		byte[] bytes = TestHelper.readFileContent(localPath).getBytes();
    fileData = ByteString.copyFrom(bytes);
  }

  protected void init() throws URISyntaxException, IOException {
    init(null, null);
  }

  protected void init(String overrideHost, AccountID overrideNodeAccountID)
      throws URISyntaxException, IOException {
    log.info("Starting File Service Test...");
    readAppConfig();
    if (overrideHost != null) {
      host = overrideHost;
      hostOverridden = true;
    }
    if (overrideNodeAccountID != null) {
      defaultListeningNodeAccountID = overrideNodeAccountID;
      nodeAccountOverridden = true;
    }
    readGenesisInfo();
    createStubs();
  }

  protected void readGenesisInfo() throws URISyntaxException, IOException {
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

    // Get Genesis Account key Pair
    genesisAccountList = keyFromFile.get("START_ACCOUNT");
    genesisKeyPair = genesisAccountList.get(0).getKeyPairList().get(0);
    getGenesisPrivateKeyList().add(genesisKeyPair.getPrivateKey());
    genesisAccountID = genesisAccountList.get(0).getAccountId();
    payerSeq = genesisAccountID.getAccountNum();
    recvId = defaultListeningNodeAccountID;
    recvSeq = defaultListeningNodeAccountID.getAccountNum();
    acc2keyMap.put(genesisAccountID.getAccountNum(), getGenesisPrivateKeyList());
  }

  protected void createStubs() throws URISyntaxException, IOException {
    if(channel!=null ) {
      channel.shutdownNow();
      try {
        channel.awaitTermination(10, TimeUnit.SECONDS);
      }catch (Exception e) {}
    }
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
    stub = FileServiceGrpc.newBlockingStub(channel);
    cstub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  protected void readAppConfig() {
    CustomProperties properties = TestHelper.getApplicationPropertiesNew();
    host = properties.getString("host", "localhost");
    port = properties.getInt("port", 50211);
    String nodeAccIDStr = properties
        .getString("defaultListeningNodeAccount", DEFAULT_NODE_ACCOUNT_ID_STR);
    defaultListeningNodeAccountID = extractAccountID(nodeAccIDStr);

    uniqueListeningPortFlag = properties.getInt("uniqueListeningPortFlag", 0);
    productionFlag = properties.getInt("productionFlag", 0);
    fileDuration = properties.getLong("FILE_DURATION", DAY_SEC * 30);
  }

  @Test
  public void test01InitAccounts() throws Exception {
    senderId = createAccount(cstub, acc2keyMap, genesisAccountID);
    payerSeq = senderId.getAccountNum();
  }

  @Test
  public void test01Transfer() throws Exception {
    long transferAmt = 100l;
    Transaction transferTxSigned = getPaymentSigned(payerSeq, recvSeq, "Transfer", transferAmt);
    log.info("\n-----------------------------------");
    log.info("Transfer: request = " + transferTxSigned);
    TransactionResponse response = cstub.cryptoTransfer(transferTxSigned);
    log.info("Transfer Response :: " + response.getNodeTransactionPrecheckCode().name());
    Assert.assertNotNull(response);
  }

  @Test
  public void test02CreateFile()
      throws InvalidKeySpecException, DecoderException, InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException, SignatureException, Exception {
    log.info("@@@ upload file at: " + localPath + "; file size in byte = " + fileData.size());
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Timestamp fileExp = ProtoCommonUtils.addSecondsToTimestamp(timestamp, fileDuration);
    SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();
    List<Key> waclPubKeyList = new ArrayList<>();
    waclPrivKeyList = new ArrayList<>();
    genWacl(NUM_WACL_KEYS, waclPubKeyList, waclPrivKeyList);

    // fetching private key of payer account
    List<PrivateKey> privKeys = getPayerPrivateKey(payerSeq);

    long nodeAccountNumber;
    if (nodeAccountOverridden) {
      nodeAccountNumber = defaultListeningNodeAccountID.getAccountNum();
    } else {
      nodeAccountNumber = Utilities.getDefaultNodeAccount();
    }
    Transaction FileCreateRequest = RequestBuilder
        .getFileCreateBuilder(payerSeq, 0l, 0l, nodeAccountNumber, 0l, 0l, MAX_TX_FEE,
            timestamp, transactionDuration, true, "FileCreate", signatures, fileData, fileExp,
            waclPubKeyList);
    TransactionBody body = TransactionBody.parseFrom(FileCreateRequest.getBodyBytes());
    txId = body.getTransactionID();
    Transaction filesignedByPayer = TransactionSigner.signTransaction(FileCreateRequest, privKeys);

    // append wacl sigs
    Transaction filesigned = appendSignature(filesignedByPayer, waclPrivKeyList);
    log.info("\n-----------------------------------");
    log.info("FileCreate: request = " + filesigned);

    TransactionResponse response = null;
    for (int i = 0; i < MAX_BUSY_RETRIES + 1; i++) {
      response = stub.createFile(filesigned);
      log.info("FileCreate Response :: " + response.getNodeTransactionPrecheckCode().name());
      if (ResponseCodeEnum.OK.equals(response.getNodeTransactionPrecheckCode())) {
        break;
      }
      Assert.assertEquals(ResponseCodeEnum.BUSY,response.getNodeTransactionPrecheckCode());
      Thread.sleep(BUSY_RETRY_MS);
    }
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
  }

  /**
   * Generates wacls.
   *
   * @param numKeys number of keys to generate
   * @param waclPubKeyList for storing generated public keys
   * @param waclPrivKeyList for storing generated private keys
   */
  public void genWacl(int numKeys, List<Key> waclPubKeyList, List<PrivateKey> waclPrivKeyList) {
    for (int i = 0; i < numKeys; i++) {
      KeyPair pair = new KeyPairGenerator().generateKeyPair();
      byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
      Key waclKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
      waclPubKeyList.add(waclKey);
      waclPrivKeyList.add(pair.getPrivate());
    }
  }

  /**
   * Gets the payer private keys for signing.
   */
  protected static List<PrivateKey> getPayerPrivateKey(long payerSeqNum)
      throws InvalidKeySpecException, DecoderException {
    AccountID accountID = RequestBuilder.getAccountIdBuild(payerSeqNum, 0l, 0l);
    List<PrivateKey> privKey = getAccountPrivateKeys(accountID);

    return privKey;
  }

  @Test
  public void test03GetTxReceipt() throws Exception {
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(
            RequestBuilder.getTransactionGetReceiptQuery(txId, ResponseType.ANSWER_ONLY))
        .build();
    log.info("\n-----------------------------------");
    Response transactionReceipts = fetchReceipts(query, cstub);
    fid = transactionReceipts.getTransactionGetReceipt().getReceipt().getFileID();
    log.info("GetTxReceipt: file ID = " + fid);
    Assert.assertNotNull(fid);
    Assert.assertNotEquals(0, fid.getFileNum());
  }

  @Test
  public void test04GetTxRecord()
      throws Exception {
    long feeForTxRecordCost = FeeClient.getCostForGettingTxRecord();
    Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "FileGetRecordCost",
        feeForTxRecordCost);
    Query query = RequestBuilder
        .getTransactionGetRecordQuery(txId, paymentTxSigned, ResponseType.COST_ANSWER);

    log.info("\n-----------------------------------");
    log.info("FileGetRecordCost: query = " + query);

    CommonUtils.nap(3);
    Response recordResp = cstub.getTxRecordByTxID(query);
    long feeForTxRecord = recordResp.getTransactionGetRecord().getHeader().getCost();
    paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "FileGetRecord", feeForTxRecord);
    query = RequestBuilder
        .getTransactionGetRecordQuery(txId, paymentTxSigned, ResponseType.ANSWER_ONLY);

    log.info("FileGetRecord: query = " + query);
    recordResp = cstub.getTxRecordByTxID(query);
    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
    log.info("FileGetRecord: tx record = " + txRecord);
    FileID actualFid = txRecord.getReceipt().getFileID();
    System.out.println(actualFid);
    System.out.println(fid + ":: is the fid");
    Assert.assertEquals(fid, actualFid);
  }

  @Test
  public void test05GetFileInfo()
      throws Exception {
    long feeForFileInfoCost = TestHelper.getFileMaxFee();
    Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "fileGetInfoQueryCost",
        feeForFileInfoCost);
    Query query = RequestBuilder
        .getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.COST_ANSWER);
    log.info("\n-----------------------------------");
    log.info("fileGetInfoQuery: query = " + query);
    Response fileInfoResp = stub.getFileInfo(query);
    Assert.assertEquals(ResponseCodeEnum.OK, fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());

    long feeForFileInfo = fileInfoResp.getFileGetInfo().getHeader().getCost();
    paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "fileGetInfoQuery", feeForFileInfo);
    query = RequestBuilder.getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.ANSWER_ONLY);
    fileInfoResp = stub.getFileInfo(query);
    Assert.assertEquals(ResponseCodeEnum.OK, fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());

    FileInfo fileInfo = fileInfoResp.getFileGetInfo().getFileInfo();
    log.info("fileGetInfoQuery: info = " + fileInfo);
    FileID actualFid = fileInfo.getFileID();
    log.info("File Info deleted  response: " + fileInfo.getDeleted());
    Assert.assertEquals(fid, actualFid);
  }

  @Test
  public void test06GetFileContent()
      throws Exception {
    long fee = FeeClient.getFeeByID(HederaFunctionality.FileGetContents);
    Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "FileGetContentCost", fee);
    Query query = RequestBuilder
        .getFileGetContentBuilder(paymentTxSigned, fid, ResponseType.COST_ANSWER);
    log.info("\n-----------------------------------");
    log.info("FileGetContentCost: query = " + query);

    Response fileContentResp = stub.getFileContent(query);

    fee = fileContentResp.getFileGetContents().getHeader().getCost();
    paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "FileGetContent", fee);
    query = RequestBuilder.getFileGetContentBuilder(paymentTxSigned, fid, ResponseType.ANSWER_ONLY);
    fileContentResp = stub.getFileContent(query);
    FileContents fileContent = fileContentResp.getFileGetContents().getFileContents();
    ByteString actualFileData = fileContent.getContents();
    log.info("FileGetContent: content = " + fileContent + "; file size = " + actualFileData.size());
    Assert.assertEquals(fileData, actualFileData);
  }

  @Test
  public void test07AppendFile()
      throws InterruptedException, InvalidKeySpecException, DecoderException, InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException, SignatureException, Exception {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();

    long nodeAccountNumber;
    if (nodeAccountOverridden) {
      nodeAccountNumber = defaultListeningNodeAccountID.getAccountNum();
    } else {
      nodeAccountNumber = Utilities.getDefaultNodeAccount();
    }
    Transaction fileAppendRequest = RequestBuilder
        .getFileAppendBuilder(payerSeq, 0l, 0l, nodeAccountNumber, 0l, 0l, MAX_TX_FEE,
            timestamp, transactionDuration, true, "FileAppend", signatures, fileData, fid);

    TransactionBody body = TransactionBody.parseFrom(fileAppendRequest.getBodyBytes());
    txId = body.getTransactionID();
    List<PrivateKey> privKey = getPayerPrivateKey(payerSeq);
    Transaction txSignedByPayer = TransactionSigner.signTransaction(fileAppendRequest, privKey);
    Transaction txSigned = appendSignature(txSignedByPayer, waclPrivKeyList);
    log.info("\n-----------------------------------");
    log.info("FileAppend: request = " + txSigned);

    TransactionResponse response = null;
    for (int i = 0; i < MAX_BUSY_RETRIES + 1; i++) {
      response = stub.appendContent(txSigned);
      log.info("FileAppend: Response = " + response.getNodeTransactionPrecheckCode().name());
      if (ResponseCodeEnum.OK.equals(response.getNodeTransactionPrecheckCode())) {
        break;
      }
      Assert.assertEquals(ResponseCodeEnum.BUSY,response.getNodeTransactionPrecheckCode());
      Thread.sleep(BUSY_RETRY_MS);
    }
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
  }

  @Test
  public void test08UpdateFile()
      throws InterruptedException, InvalidKeySpecException, DecoderException, InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException, SignatureException {
    Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(DAY_SEC * 10);
    List<Key> newWaclPubKeyList = new ArrayList<>();
    newWaclPrivKeyList = new ArrayList<>();
    genWacl(NUM_WACL_KEYS, newWaclPubKeyList, newWaclPrivKeyList);
    KeyList wacl = KeyList.newBuilder().addAllKeys(newWaclPubKeyList).build();

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    long nodeAccountNumber;
    if (nodeAccountOverridden) {
      nodeAccountNumber = defaultListeningNodeAccountID.getAccountNum();
    } else {
      nodeAccountNumber = Utilities.getDefaultNodeAccount();
    }
    Transaction FileUpdateRequest = RequestBuilder
        .getFileUpdateBuilder(payerSeq, 0l, 0l, nodeAccountNumber, 0l, 0l, MAX_TX_FEE,
            timestamp, fileExp, transactionDuration, true, "FileUpdate", signatures, fileData, fid,
            wacl);

    List<PrivateKey> privKey = getPayerPrivateKey(payerSeq);
    Transaction txSignedByPayer = TransactionSigner
        .signTransaction(FileUpdateRequest, privKey); // sign with payer keys
    Transaction txSignedByCreationWacl = appendSignature(txSignedByPayer,
        waclPrivKeyList); // sign with creation wacl keys
    Transaction txSigned = appendSignature(txSignedByCreationWacl,
        newWaclPrivKeyList); // sign with new wacl keys

    log.info("\n-----------------------------------");
    log.info(
        "FileUpdate: input data = " + fileData + "\nexpirationTime = " + fileExp + "\nWACL keys = "
            + newWaclPubKeyList);
    log.info("FileUpdate: request = " + txSigned);

    TransactionResponse response = stub.updateFile(txSigned);
    log.info("FileUpdate with data, exp, and wacl respectively, Response :: "
        + response.getNodeTransactionPrecheckCode().name());
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
  }

  @Test
  public void test09DeleteFile()
      throws InterruptedException, InvalidKeySpecException, DecoderException, InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException, SignatureException {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    long nodeAccountNumber;
    if (nodeAccountOverridden) {
      nodeAccountNumber = defaultListeningNodeAccountID.getAccountNum();
    } else {
      nodeAccountNumber = Utilities.getDefaultNodeAccount();
    }
    Transaction FileDeleteRequest = RequestBuilder
        .getFileDeleteBuilder(payerSeq, 0l, 0l, nodeAccountNumber, 0l, 0l, MAX_TX_FEE,
            timestamp, transactionDuration, true, "FileDelete", signatures, fid);
    List<PrivateKey> privKey = getPayerPrivateKey(payerSeq);
    Transaction txSignedByPayer = TransactionSigner.signTransaction(FileDeleteRequest, privKey);
    Transaction txSigned = appendSignature(txSignedByPayer, newWaclPrivKeyList);
    log.info("\n-----------------------------------");
    log.info("FileDelete: request = " + txSigned);

    TransactionResponse response = stub.deleteFile(txSigned);
    log.info("FileDelete Response :: " + response.getNodeTransactionPrecheckCode().name());
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
  }

  @Test
  public void test10GetFileInfoAfterDelete()
      throws Exception {
    long feeForFileInfoCost = FeeClient.getFeeByID(HederaFunctionality.FileGetInfo);
    Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "fileGetInfoQueryCost",
        feeForFileInfoCost);
    Query query = RequestBuilder
        .getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.COST_ANSWER);
    log.info("\n-----------------------------------");
    log.info("fileGetInfoQuery: query = " + query);
    Response fileInfoResp = stub.getFileInfo(query);

    log.info(fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());
    Assert.assertEquals(ResponseCodeEnum.OK,
            fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());
  }

  @After
  public void cleanUp() throws IOException {
    log.info("Finished File Service Test. Goodbye!");
    channel.shutdown();
  }

  /**
   * Gets the account info for a given account ID.
 * @throws Exception 
   */
  public AccountInfo getAccountInfo(AccountID accountID, long fromAccountNum, long toAccountNum)
      throws Exception {
    // get the cost for getting account info
    long queryCostAcctInfo = FeeClient.getCostForGettingAccountInfo();
    Transaction paymentTxSigned = getPaymentSigned(fromAccountNum, toAccountNum,
        "getCostCryptoGetAccountInfo", queryCostAcctInfo);
    Query cryptoCostGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.COST_ANSWER);
    Response getCostInfoResponse = cstub.getAccountInfo(cryptoCostGetInfoQuery);
    long queryGetAcctInfoFee = getCostInfoResponse.getCryptoGetInfo().getHeader().getCost();

    paymentTxSigned = getPaymentSigned(fromAccountNum, toAccountNum, "getCryptoGetAccountInfo",
        queryGetAcctInfoFee);
    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.ANSWER_ONLY);
    Response getInfoResponse = cstub.getAccountInfo(cryptoGetInfoQuery);
    log.info("Pre Check Response of getAccountInfo:: "
        + getInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode().name());
    Assert.assertNotNull(getInfoResponse);
    Assert.assertNotNull(getInfoResponse.getCryptoGetInfo());
    log.info("getInfoResponse :: " + getInfoResponse.getCryptoGetInfo());

    AccountInfo accInfo = getInfoResponse.getCryptoGetInfo().getAccountInfo();
    return accInfo;
  }

  /**
   * Gets account info.
   *
   * @param accountID the account to get info for
 * @throws Exception 
   */
  public AccountInfo getAccountInfo(AccountID accountID, AccountID payerID, AccountID nodeID)
      throws Exception {
    return getAccountInfo(accountID, payerID.getAccountNum(), nodeID.getAccountNum());
  }

  /**
   * Gets account info using genesis as payer and default listening node as receiving node.
   *
   * @param accountID the account to get info for
 * @throws Exception 
   */
  public AccountInfo getAccountInfo(AccountID accountID)
      throws Exception {
    return getAccountInfo(accountID, genesisAccountID.getAccountNum(),
        defaultListeningNodeAccountID.getAccountNum());
  }

  /**
   * Gets balance of an account using genesis as payer and default listening node as receiving
   * node.
 * @throws Exception 
   */
  public long getAccountBalance(AccountID accountID)
      throws Exception {
    return getAccountBalance(accountID, genesisAccountID, defaultListeningNodeAccountID);
  }

  /**
   * Gets balance of an account.
   *
   * @param accountID the account to get balance for
   * @return the balance
 * @throws Exception 
   */
  public long getAccountBalance(AccountID accountID, AccountID payerID, AccountID nodeID)
      throws Exception {
    long balanceFee = FeeClient.getBalanceQueryFee();
    Transaction paymentTxSigned = getPaymentSigned(payerID.getAccountNum(), nodeID.getAccountNum(),
        "getAccountBalance", balanceFee);
    Query cryptoGetBalanceQuery = RequestBuilder
        .getCryptoGetBalanceQuery(accountID, paymentTxSigned, ResponseType.ANSWER_ONLY);
    Response getBalanceResponse = cstub.cryptoGetBalance(cryptoGetBalanceQuery);
    log.info("Pre Check Response of getAccountBalance:: "
        + getBalanceResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode()
        .name());
    Assert.assertNotNull(getBalanceResponse);
    Assert.assertNotNull(getBalanceResponse.getCryptoGetInfo());
    log.info("getAccountBalance :: " + getBalanceResponse.getCryptoGetInfo());

    long balance = getBalanceResponse.getCryptogetAccountBalance().getBalance();
    return balance;
  }

  /**
   * Gets the hex string of the public key.
   */
  public static String getPubKeyHex(KeyPair keypair) {
    byte[] pubKey0 = ((EdDSAPublicKey) keypair.getPublic()).getAbyte();
    String pubKeyHex = HexUtils.bytes2Hex(pubKey0);
    return pubKeyHex;
  }

  /**
   * Fetches the receipts, wait if necessary.
   */
  protected static Response fetchReceipts(Query query, CryptoServiceBlockingStub cstub2)
      throws Exception {
    return TestHelper.fetchReceipts(query, cstub2, log, host);
  }

  /**
   * Creates a crypto account.
   *
   * @param account2keyMap maps from account id to corresponding key pair
   * @return the account ID of the created account
   */
  public AccountID createAccount(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      Map<Long, List<PrivateKey>> account2keyMap, AccountID payerAccount) throws Exception {
    KeyPair pair = new KeyPairGenerator().generateKeyPair();
    Transaction createAccountRequest = TestHelper
        .createAccountWithFee(payerAccount, defaultListeningNodeAccountID, pair,
            DEFAULT_INITIAL_ACCOUNT_BALANCE, getGenesisPrivateKeyList());
    //  Transaction txFirstSigned = TransactionSigner.signTransaction(createAccountRequest, getGenesisPrivateKeyList());

    log.info("\n-----------------------------------");
    log.info("createAccount: request = " + createAccountRequest);
    TransactionResponse response = cstub.createAccount(createAccountRequest);
    log.info("createAccount Response :: " + response.getNodeTransactionPrecheckCode().name());
    Assert.assertNotNull(response);

    // get transaction receipt
    log.info("preparing to getTransactionReceipts....");
    TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
    TransactionID transactionID = body.getTransactionID();
    Query query = Query.newBuilder().setTransactionGetReceipt(
        RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
        .build();
    Response transactionReceipts = fetchReceipts(query, cstub);
    AccountID accountID = transactionReceipts.getTransactionGetReceipt().getReceipt()
        .getAccountID();
    List<PrivateKey> pKeys = new ArrayList<>();
    pKeys.add(pair.getPrivate());
    account2keyMap.put(accountID.getAccountNum(), pKeys);
    log.info("Account created: account num :: " + accountID.getAccountNum());

    // get account info
    CommonUtils.nap(WAIT_IN_SEC);
    AccountInfo accInfo = getAccountInfo(accountID);
    log.info("Created account info = " + accInfo);
   
    Assert.assertEquals(body.getCryptoCreateAccount().getInitialBalance(),
        accInfo.getBalance());

    return accountID;
  }

  /**
   * Append signatures to existing ones.
   *
   * @param transaction tx to append signatures.
   * @param privKeys private keys
   * @return transaction with appended sigs
   */
  public static Transaction appendSignature(Transaction transaction, List<PrivateKey> privKeys)
      throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException, SignatureException, DecoderException {
   
    byte[] txByteArray = transaction.getBodyBytes().toByteArray();

    List<Signature> currSigs = transaction.getSigs().getSigsList();
    Builder allSigListBuilder = SignatureList.newBuilder();
    Builder waclSigListBuilder = SignatureList.newBuilder();
    allSigListBuilder.addAllSigs(currSigs);
    for (PrivateKey privKey : privKeys) {
      String payerAcctSig = null;
      payerAcctSig = HexUtils
          .bytes2Hex(TransactionSigner.signBytes(txByteArray, privKey).toByteArray());
      Signature signaturePayeeAcct = null;
      signaturePayeeAcct = Signature.newBuilder()
          .setEd25519(ByteString.copyFrom(HexUtils.hexToBytes(payerAcctSig))).build();
      waclSigListBuilder.addSigs(signaturePayeeAcct);
    }

    Signature waclSigs = Signature.newBuilder().setSignatureList(waclSigListBuilder.build())
        .build();
    allSigListBuilder.addSigs(waclSigs);
    Transaction txSigned = Transaction.newBuilder().setBodyBytes(transaction.getBodyBytes())
        .setSigs(allSigListBuilder.build()).build();
    return txSigned;
  }

  /**
   * Creates a transaction signed by a single private key.
   *
   * @param transaction tx to be signed
   * @param privatekey private key for signing
   */
  public static Transaction getSignedTransaction(Transaction transaction, PrivateKey privatekey) {
   
    byte[] txByteArray = transaction.getBodyBytes().toByteArray();
    // Payer Account will sign this transaction
    String payerAcctSig = null;
    payerAcctSig = HexUtils
        .bytes2Hex(TransactionSigner.signBytes(txByteArray, privatekey).toByteArray());
    // get this signature and add to the Transaction with body.
    Signature signaturePayeeAcct = null;
    try {
      signaturePayeeAcct = Signature.newBuilder()
          .setEd25519(ByteString.copyFrom(HexUtils.hexToBytes(payerAcctSig))).build();
    } catch (DecoderException e) {
      e.printStackTrace();
    }

    SignatureList sigList = SignatureList.newBuilder().addSigs(signaturePayeeAcct).build();
    Transaction txFirstSigned = Transaction.newBuilder().setBodyBytes(transaction.getBodyBytes()).setSigs(sigList).build();
    return txFirstSigned;
  }

  /**
   * Gets account info.
 * @throws Exception 
   */
  private static Response getCryptoGetAccountInfo(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      AccountID accountID) throws Exception {
    // first get the cost for getting AccountInfo
    long queryCostGetInfo = FeeClient.getCostForGettingAccountInfo();
    Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "getCostCryptoGetAccountInfo",
        queryCostGetInfo);
    Query cryptoGetCostInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.COST_ANSWER);
    Response acctInfo = stub.getAccountInfo(cryptoGetCostInfoQuery);

    long transactionFee = acctInfo.getCryptoGetInfo().getHeader().getCost();
    paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "getCryptoGetAccountInfo",
        transactionFee);
    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.ANSWER_ONLY);
    return stub.getAccountInfo(cryptoGetInfoQuery);
  }

  /**
   * Gets the account balance.
 * @throws Exception 
   */
  protected static long getAccountBalance(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      AccountID accountID) throws Exception {
    Response response = getCryptoGetAccountInfo(stub, accountID);
    long balance = response.getCryptoGetInfo().getAccountInfo().getBalance();
    return balance;
  }

  public static void main(String[] args) throws Exception {
    FileServiceIT tester = new FileServiceIT();
    tester.setUp();
    tester.test01InitAccounts();
    CommonUtils.nap(WAIT_IN_SEC);
    tester.test02CreateFile();
    CommonUtils.nap(WAIT_IN_SEC);
    tester.test03GetTxReceipt();
    tester.test04GetTxRecord();
    tester.test05GetFileInfo();
    tester.test06GetFileContent();
    tester.test07AppendFile();
    CommonUtils.nap(WAIT_IN_SEC);
    tester.test05GetFileInfo();
    tester.test08UpdateFile();
    CommonUtils.nap(WAIT_IN_SEC);
    tester.test05GetFileInfo();
    tester.test09DeleteFile();
    CommonUtils.nap(WAIT_IN_SEC);
    CommonUtils.nap(WAIT_IN_SEC);
    tester.test10GetFileInfoAfterDelete();
  }

  public List<PrivateKey> getGenesisPrivateKeyList() {
    return genesisPrivateKeyList;
  }

  public void setGenesisPrivateKeyList(List<PrivateKey> genesisPrivateKeyList) {
    this.genesisPrivateKeyList = genesisPrivateKeyList;
  }

  /**
   * Extract account ID from memo string, e.g. "0.0.5".
   */
  protected AccountID extractAccountID(String memo) {
    AccountID rv = null;
    String[] parts = memo.split("\\.");
    rv = AccountID.newBuilder().setShardNum(Long.parseLong(parts[0]))
        .setRealmNum(Long.parseLong(parts[1])).setAccountNum(Long.parseLong(parts[2])).build();
    return rv;
  }

  /**
   * Creates a signed transfer tx, used both for a CryptoTransfer transaction or a Query payment.
   *
   * @param payerID payer account ID, as the payer of the tx and the from account for the transfer
   * @param nodeID node account ID, as the node account that should process the tx
   * @param toID to account for the transfer.
   * @return the signed transaction
 * @throws Exception 
   */
  protected static Transaction getPaymentSigned(AccountID payerID, AccountID nodeID,
      AccountID fromID, AccountID toID, String memo, long transferAmmount)
      throws Exception {
    List<PrivateKey> payerPrivKeys = getPayerPrivateKey(payerID.getAccountNum());
    List<PrivateKey> fromPrivKeys = getPayerPrivateKey(fromID.getAccountNum());
    Transaction paymentTxSigned = getSignedTransferTx(payerID, nodeID, fromID, toID,
        transferAmmount, payerPrivKeys, fromPrivKeys, memo);
    return paymentTxSigned;
  }

  /**
   * Creates a signed Query payment tx using default listening node as the processing node and payer
   * as the from account.
   *
   * @param payerSeq payer account number, as the payer and the from account
   * @param toSeq node account number, as the node account and the to account
   * @return the signed transaction
 * @throws Exception 
   */
  protected static Transaction getPaymentSigned(long payerSeq, long toSeq, String memo,
      long transferFeeAmt) throws Exception {
    AccountID payerAccountID = AccountID.newBuilder().setAccountNum(payerSeq).setRealmNum(0)
        .setShardNum(0).build();
    AccountID toID = AccountID.newBuilder().setAccountNum(toSeq).setRealmNum(0).setShardNum(0)
        .build();
    return getPaymentSigned(payerAccountID, defaultListeningNodeAccountID, payerAccountID, toID,
        memo, transferFeeAmt);
  }

  /**
   * Creates a signed transfer tx.
 * @throws Exception 
   */
  protected static Transaction getSignedTransferTx(AccountID payerAccountID,
      AccountID nodeAccountID,
      AccountID fromAccountID, AccountID toAccountID, long amount, List<PrivateKey> payerPrivKeys,
      List<PrivateKey> fromPrivKeys, String memo)
      throws Exception {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Transaction paymentTx = RequestBuilder.getCryptoTransferRequest(payerAccountID.getAccountNum(),
        payerAccountID.getRealmNum(), payerAccountID.getShardNum(), nodeAccountID.getAccountNum(),
        nodeAccountID.getRealmNum(), nodeAccountID.getShardNum(), TestHelper.getCryptoMaxFee(), timestamp,
        transactionDuration, true,
        memo, signatures, fromAccountID.getAccountNum(), -amount, toAccountID.getAccountNum(),
        amount);
    List<List<PrivateKey>> privKeysList = new ArrayList<>();
    privKeysList.add(payerPrivKeys);
    privKeysList.add(fromPrivKeys);
    Transaction paymentTxSigned = TransactionSigner.signTransactionNew(paymentTx, privKeysList);

    long transferFee = TestHelper.getCryptoMaxFee();

    paymentTx = RequestBuilder.getCryptoTransferRequest(payerAccountID.getAccountNum(),
        payerAccountID.getRealmNum(), payerAccountID.getShardNum(), nodeAccountID.getAccountNum(),
        nodeAccountID.getRealmNum(), nodeAccountID.getShardNum(), transferFee, timestamp,
        transactionDuration, true,
        memo, signatures, fromAccountID.getAccountNum(), -amount, toAccountID.getAccountNum(),
        amount);
    paymentTxSigned = TransactionSigner.signTransactionNew(paymentTx, privKeysList);

    return paymentTxSigned;
  }

  /**
   * Gets the account key pairs.
   *
   * @return key pairs of the given account
   */
  protected static List<KeyPair> getAccountKeyPairs(AccountID accountID)
      throws InvalidKeySpecException, DecoderException {
    List<KeyPair> keypairs;
    keypairs = account2keyMap.get(accountID);
    return keypairs;
  }

  /**
   * Gets the account private keys.
   *
   * @return private key of the given account
   */
  public static List<PrivateKey> getAccountPrivateKeys(AccountID accountID)
      throws InvalidKeySpecException, DecoderException {
    List<PrivateKey> rv = new ArrayList<>();
    long seqNum = accountID.getAccountNum();
    if (acc2keyMap.containsKey(seqNum)) {
      rv = acc2keyMap.get(seqNum);
    } else {
      List<KeyPair> keypairs = getAccountKeyPairs(accountID);
      for (int i = 0; i < keypairs.size(); i++) {
        rv.add(keypairs.get(i).getPrivate());
      }
    }
    return rv;
  }
}
