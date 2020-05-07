package com.hedera.services.legacy.smartcontract;

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

import com.hedera.services.legacy.regression.ServerAppConfigUtility;
import com.hedera.services.legacy.regression.Utilities;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.KeyPairGenerator;

/**
 * Testing creation and invocation of contract that creates another contract
 *
 * @author Constantin
 */
public class ChainOfContracts {

	  private static long TX_DURATION_SEC = 3 * 60; // 3 minutes for tx dedup
	  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	  private static String[] files = {"config.txt", "hg4.JPG", "hg2.pdf", "Enrollment_kit_2018.pdf"};
	  private static String UPLOAD_PATH = "/testfiles/";

	  private static final Logger log = LogManager.getLogger(ChainOfContracts.class);


	  private static final int MAX_RECEIPT_RETRIES = 60;
	  private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	  private static final String SC_CT_GETADDRESS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getAddress\",\"outputs\":[{\"name\":\"retval\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	  private static AccountID nodeAccount = AccountID.newBuilder()
	      .setAccountNum(Utilities.getDefaultNodeAccount()).setRealmNum(0l).setShardNum(0l).build();
	  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	  private static AccountID genesisAccount;
	  private static Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();
	  private static String host;
	  private static int port;
	  private static long node_account_number;
	  private static long node_shard_number;
	  private static long node_realm_number;
	  private static long contractDuration;
    private static long gasToOffer;

  private static void loadGenesisAndNodeAcccounts() throws Exception {
	    Map<String, List<AccountKeyListObj>> hederaAccounts = null;
	    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

	    // Get Genesis Account key Pair
	    List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");
	    ;

	    // get Private Key
	    PrivateKey genesisPrivateKey = null;

	    genesisPrivateKey = genesisAccountList.get(0).getKeyPairList().get(0).getPrivateKey();

	    // get the Account Object
	    genesisAccount = genesisAccountList.get(0).getAccountId();
	    List<PrivateKey> genesisKeyList = new ArrayList<PrivateKey>(1);
	    genesisKeyList.add(genesisPrivateKey);
	    accountKeys.put(genesisAccount, genesisKeyList);


	  }

	  public static void main(String args[]) throws Exception {
			/* byte[] arrTest = {2,3,4,5,6,};
			 byte[] arrTest2 = {7,8,9,10};
			 DataWord dw = new DataWord(arrTest);
			 DataWord dw1 = new DataWord(arrTest);
			 Map<byte[],DataWord> mapToSerialize = new HashMap<byte[],DataWord>();
			 mapToSerialize.put(arrTest, dw);
			 mapToSerialize.put(arrTest2, dw1);
			 byte[] serializedDataWordMap = SerializationUtils.serialize((Serializable)mapToSerialize);
			 try {
		            Path folderpath = Paths.get("/persisttest");
		            Path filePath =  Paths.get("/persisttest/testpersist.data");
		            if(Files.notExists(folderpath)) {
		            	Files.createDirectory(folderpath);
		            }
		            Files.write(filePath, serializedDataWordMap);
		            Files.readAllBytes(filePath);
		        } catch (IOException e) {
		            e.printStackTrace();
		        }
			 
			 
			 Map<byte[],DataWord>  deserializedMap = SerializationUtils.deserialize(serializedDataWordMap);*/
	    Properties properties = TestHelper.getApplicationProperties();
			contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
	    host = properties.getProperty("host");
	    port = Integer.parseInt(properties.getProperty("port"));
	    node_account_number = Utilities.getDefaultNodeAccount();
	    node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
	    node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
	    nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
	        .setRealmNum(node_shard_number).setShardNum(node_realm_number).build();

	    loadGenesisAndNodeAcccounts();
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true)
	        .build();
	    FileServiceBlockingStub stub = FileServiceGrpc.newBlockingStub(channel);
	    CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub = CryptoServiceGrpc
	        .newBlockingStub(channel);

      ServerAppConfigUtility appConfig = ServerAppConfigUtility.getInstance(host, node_account_number);
      gasToOffer = appConfig.getMaxGasLimit() - 1;


      KeyPair keyPair = new KeyPairGenerator().generateKeyPair();
	    AccountID crAccount = createAccount(genesisAccount, TestHelper.getCryptoMaxFee() * 10L, keyPair);

	    TestHelper.initializeFeeClient(channel, crAccount, keyPair, nodeAccount);
			String fileName = "WrapperStorage.bin";

	    if (crAccount != null) {

//				FileID simpleStorageFileId = uploadFile(crAccount, fileName);
	      FileID simpleStorageFileId = LargeFileUploadIT.uploadFile(crAccount, fileName, keyPair);
	      if (simpleStorageFileId != null) {
	        ContractID storageWrapperContractId = createContract(crAccount, simpleStorageFileId,
	            contractDuration);
	        Assert.assertNotNull(storageWrapperContractId);
	        Assert.assertNotEquals(0, storageWrapperContractId.getContractNum()); 

	        ContractID simpleStorageContractID = getAddressFromContract(crAccount,storageWrapperContractId);
	        //underling simple storage is created in a chain of 2 so it should have contract id of wrapper +2
	        Assert.assertEquals(storageWrapperContractId.getContractNum() +2, simpleStorageContractID.getContractNum());
	        
	        int valuePassedContract1 = ThreadLocalRandom.current().nextInt(1, 1000000 + 1);

	        setValueToContract(crAccount, storageWrapperContractId, valuePassedContract1);
	        
	        int actualStoredValueC1 = getValueFromContract(crAccount, storageWrapperContractId);
	        actualStoredValueC1 = getValueFromContract(crAccount, storageWrapperContractId);
	        Assert.assertEquals(valuePassedContract1, actualStoredValueC1);

	        valuePassedContract1 = ThreadLocalRandom.current().nextInt(1, 1000000 + 1);;
	        setValueToContract(crAccount, storageWrapperContractId, valuePassedContract1);
	        actualStoredValueC1 = getValueFromContract(crAccount, storageWrapperContractId);
	        Assert.assertEquals(valuePassedContract1, actualStoredValueC1);

	        


	      }


	    }


	  }

	  private static Transaction createQueryHeaderTransfer(AccountID payer) throws Exception {
	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
	    Duration transactionDuration = RequestBuilder.getDuration(30);

	    //KeyPair pair = new KeyPairGenerator().generateKeyPair();
	    //byte[] pubKeyBytes = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
	    //String pubKey = HexUtils.bytes2Hex(pubKeyBytes);
	    //Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey.getBytes())).build(); // used later
	    SignatureList sigList = SignatureList.getDefaultInstance();
			 /* Transaction transferTx = RequestBuilder.getCryptoTransferRequest(
						 payer.getAccountNum(), payer.getRealmNum(), payer.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
						 50, timestamp, transactionDuration, false, "test", sigList,
						 payer.getAccountNum(), -100l, nodeAccount.getAccountNum(), 100l);*/

	    Transaction transferTx = TestHelper.createTransfer(payer, accountKeys.get(payer).get(0),
	        nodeAccount, payer,
	        accountKeys.get(payer).get(0), nodeAccount, TestHelper.getCryptoMaxFee());
	    //transferTx = TransactionSigner.signTransaction(transferTx, accountKeys.get(payer));
	    return transferTx;

	  }

	  private static AccountID createAccount(AccountID payerAccount, long initialBalance,
	      KeyPair keyGenerated) throws Exception {
	    AccountID createdAccount = null;
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
	    Transaction transaction = TestHelper
	        .createAccountWithFee(payerAccount, nodeAccount, keyGenerated, initialBalance,
	            accountKeys.get(payerAccount));
//			  Transaction signTransaction = TransactionSigner.signTransaction(transaction, accountKeys.get(payerAccount));
	    TransactionResponse response = stub.createAccount(transaction);
	    Assert.assertNotNull(response);
	    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
	    System.out.println(
	        "Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
	            .name());

	    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
	    AccountID newlyCreateAccountId = TestHelper
	        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
	    accountKeys.put(newlyCreateAccountId, Collections.singletonList(keyGenerated.getPrivate()));

	    channel.shutdown();
	    return newlyCreateAccountId;
	  }

	  private static TransactionGetReceiptResponse getReceipt(TransactionID transactionId)
	      throws Exception {
	    TransactionGetReceiptResponse receiptToReturn = null;
	    Query query = Query.newBuilder()
	        .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
	            transactionId, ResponseType.ANSWER_ONLY)).build();
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
	    Response transactionReceipts = stub.getTransactionReceipts(query);
	    int attempts = 1;
	    while (attempts <= MAX_RECEIPT_RETRIES && !transactionReceipts.getTransactionGetReceipt()
	        .getReceipt()
	        .getStatus().name().equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
	      Thread.sleep(1000);
	      transactionReceipts = stub.getTransactionReceipts(query);
	      System.out.println("waiting to getTransactionReceipts as Success..." +
	          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
	      attempts++;
	    }
	    if (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
	        .equals(ResponseCodeEnum.SUCCESS)) {
	      receiptToReturn = transactionReceipts.getTransactionGetReceipt();
	    }
	    channel.shutdown();
	    return transactionReceipts.getTransactionGetReceipt();

	  }


	  private static FileID uploadFile(AccountID payerAccount, String fileName) throws Exception {
	    FileID fileIdToReturn = null;
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true)
	        .build();
	    FileServiceBlockingStub stub = FileServiceGrpc.newBlockingStub(channel);

	    //create file test
	    //
	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
	    Timestamp fileExp = RequestBuilder.getTimestamp(Instant.now());
	    Duration transactionDuration = RequestBuilder.getDuration(30);

	    SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();

	    List<Key> waclKeyList = new ArrayList<>();
	    KeyPair pair = new KeyPairGenerator().generateKeyPair();
	    Key waclKey = Key.newBuilder()
	        .setEd25519(ByteString.copyFrom(pair.getPublic().toString().getBytes())).build();
	    waclKeyList.add(waclKey);

	    log.info("@@@ upload file at: " + fileName);
	    Path path = Paths.get(OCTokenIT.class.getClassLoader().getResource(fileName).toURI());
	    byte[] bytes = Files.readAllBytes(path);

	    String fileContent = new String(bytes);
	    log.info("File Content: " + fileContent);
	    ByteString fileData = ByteString.copyFrom(bytes);
	    Transaction FileCreateRequest = RequestBuilder
	        .getFileCreateBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
	            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
	            nodeAccount.getShardNum(), 100l, timestamp,
	            transactionDuration, true, "FileCreate", signatures, fileData, fileExp, waclKeyList);
	    FileCreateRequest = TransactionSigner
	        .signTransaction(FileCreateRequest, accountKeys.get(payerAccount));

	    TransactionResponse response = stub.createFile(FileCreateRequest);
	    System.out.println("FileCreate Response :: " + response.getNodeTransactionPrecheckCodeValue());
	    Assert.assertNotNull(response);
	    Assert.assertEquals(response.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);

	    TransactionBody fileCreateBody = TransactionBody.parseFrom(FileCreateRequest.getBodyBytes());
	    TransactionGetReceiptResponse fileUploadReceipt = getReceipt(
	    		fileCreateBody.getTransactionID());
	    if (fileUploadReceipt != null) {
	      fileIdToReturn = fileUploadReceipt.getReceipt().getFileID();
	    }
	    channel.shutdown();
	    return fileIdToReturn;
	  }

	  private static ContractID createContract(AccountID payerAccount, FileID contractFile,
	      long durationInSeconds) throws Exception {
	    ContractID createdContract = null;
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();

	    Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);

	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
	    Duration transactionDuration = RequestBuilder.getDuration(30);
	    Transaction createContractRequest = TestHelper
	        .getCreateContractRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
	            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
	            nodeAccount.getShardNum(), TestHelper.getContractMaxFee(), timestamp,
	            transactionDuration, true, "", gasToOffer, contractFile, ByteString.EMPTY, 0,
	            contractAutoRenew, accountKeys.get(payerAccount), "");

	    TransactionResponse response = stub.createContract(createContractRequest);
	    System.out.println(
	        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
	            .name());

	    TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
	    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
	    		createContractBody.getTransactionID());
	    if (contractCreateReceipt != null) {
	      createdContract = contractCreateReceipt.getReceipt().getContractID();
	    }
	    TransactionRecord trRecord = getTransactionRecord(payerAccount,
	        createContractBody.getTransactionID());
	    Assert.assertNotNull(trRecord);
	    Assert.assertTrue(trRecord.hasContractCreateResult());
	    Assert.assertEquals(trRecord.getContractCreateResult().getContractID(),
	        contractCreateReceipt.getReceipt().getContractID());

	    channel.shutdown();

	    return createdContract;
	  }


	  private static TransactionID uploadFileNoReceipt(AccountID payerAccount, String filePath)
	      throws Exception {
	    FileID fileIdToReturn = null;
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true)
	        .build();
	    FileServiceBlockingStub stub = FileServiceGrpc.newBlockingStub(channel);
	    CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub = CryptoServiceGrpc
	        .newBlockingStub(channel);

	    //create file test
	    //
	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
	    Timestamp fileExp = RequestBuilder.getTimestamp(Instant.now());
	    Duration transactionDuration = RequestBuilder.getDuration(30);

	    SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();

	    List<Key> waclKeyList = new ArrayList<>();
	    KeyPair pair = new KeyPairGenerator().generateKeyPair();
	    Key waclKey = Key.newBuilder()
	        .setEd25519(ByteString.copyFrom(pair.getPublic().toString().getBytes())).build();
	    waclKeyList.add(waclKey);

	    log.info("@@@ upload file at: " + filePath);
	    Path path = Paths.get(filePath);
	    byte[] bytes = Files.readAllBytes(path);
	    String fileContent = new String(bytes);
	    log.info("File Content: " + fileContent);
	    ByteString fileData = ByteString.copyFrom(bytes);
	    Transaction FileCreateRequest = RequestBuilder
	        .getFileCreateBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
	            payerAccount.getShardNum(), Utilities.getDefaultNodeAccount(), 0l, 0l,
	            TestHelper.getFileMaxFee(), timestamp,
	            transactionDuration, true, "FileCreate", signatures, fileData, fileExp, waclKeyList);
	    TransactionBody fileCreateBody = TransactionBody.parseFrom(FileCreateRequest.getBodyBytes());
	    TransactionID txId = fileCreateBody.getTransactionID();
	    TransactionResponse response = stub.createFile(FileCreateRequest);
	    System.out.println("FileCreate Response :: " + response.getNodeTransactionPrecheckCodeValue());
	    Assert.assertNotNull(response);

	    channel.shutdown();
	    return txId;
	  }


	  private static byte[] encodeSet(int valueToAdd) {
	    String retVal = "";
	    CallTransaction.Function function = getSetFunction();
	    byte[] encodedFunc = function.encode(valueToAdd);

	    return encodedFunc;
	  }

	  private static CallTransaction.Function getSetFunction() {
	    String funcJson = SC_SET_ABI.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }

	  private static byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
	      throws Exception {
	    byte[] dataToReturn = null;
	    ContractID createdContract = null;
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);

	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
	    Duration transactionDuration = RequestBuilder.getDuration(30);
	    //payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, timestamp, txDuration, gas, contractId, functionData, value, signatures
	    ByteString dataBstr = ByteString.EMPTY;
	    if (data != null) {
	      dataBstr = ByteString.copyFrom(data);
	    }
	    Transaction callContractRequest = TestHelper
	        .getContractCallRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
	            payerAccount.getShardNum(), Utilities.getDefaultNodeAccount(), 0l, 0l,
	            TestHelper.getContractMaxFee(), timestamp,
	            transactionDuration, gasToOffer, contractToCall, dataBstr, 0,
	            accountKeys.get(payerAccount));

	    TransactionResponse response = stub.contractCallMethod(callContractRequest);
	    System.out.println(
	        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
	            .name());
	    Thread.sleep(1000);
	    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
	    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
	    		callContractBody.getTransactionID());
	    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
	        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
	      TransactionRecord trRecord = getTransactionRecord(payerAccount,
	    		  callContractBody.getTransactionID());
	      if (trRecord != null && trRecord.hasContractCallResult()) {
	        ContractFunctionResult callResults = trRecord.getContractCallResult();
	        String errMsg = callResults.getErrorMessage();
	        if (StringUtils.isEmpty(errMsg)) {
	          if (!callResults.getContractCallResult().isEmpty()) {
	            dataToReturn = callResults.getContractCallResult().toByteArray();
	          }
	        } else {
	          log.info("@@@ Contract Call resulted in error: " + errMsg);
	        }
	      }
	    }
	    channel.shutdown();

	    return dataToReturn;
	  }

	  private static TransactionRecord getTransactionRecord(AccountID payerAccount,
	      TransactionID transactionId) throws Exception {
	    AccountID createdAccount = null;
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount);
	    Query getRecordQuery = RequestBuilder
	        .getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.ANSWER_ONLY);
	    Response recordResp = stub.getTxRecordByTxID(getRecordQuery);
	    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
	    System.out.println("tx record = " + txRecord);
	    channel.shutdown();
	    return txRecord;
	  }

	  private static CallTransaction.Function getGetValueFunction() {
	    String funcJson = SC_GET_ABI.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }

	  private static byte[] encodeGetValue() {
	    String retVal = "";
	    CallTransaction.Function function = getGetValueFunction();
	    byte[] encodedFunc = function.encode();
	    return encodedFunc;
	  }

	  private static int decodeGetValueResult(byte[] value) {
	    int decodedReturnedValue = 0;
	    CallTransaction.Function function = getGetValueFunction();
	    Object[] retResults = function.decodeResult(value);
	    if (retResults != null && retResults.length > 0) {
	      BigInteger retBi = (BigInteger) retResults[0];
	      decodedReturnedValue = retBi.intValue();
	    }
	    return decodedReturnedValue;
	  }


	  private static byte[] callContractLocal(AccountID payerAccount, ContractID contractToCall,
	      byte[] data) throws Exception {
	    byte[] dataToReturn = null;
	    AccountID createdAccount = null;
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount);
	    ByteString callData = ByteString.EMPTY;
	    if (data != null) {
	      callData = ByteString.copyFrom(data);
	    }
	    Query contractCallLocal = RequestBuilder
	        .getContractCallLocalQuery(contractToCall, gasToOffer, callData, 0L, 5000, paymentTx,
	            ResponseType.ANSWER_ONLY);

	    Response callResp = stub.contractCallLocalMethod(contractCallLocal);
	    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
	        .getContractCallResult();

	    System.out.println("callContractLocal response = " + callResp);
	    channel.shutdown();
	    return functionResults.toByteArray();
	  }

	  private static int getValueFromContract(AccountID payerAccount, ContractID contractId)
	      throws Exception {
	    int retVal = 0;
	    byte[] getValueEncodedFunction = encodeGetValue();
	    byte[] result = callContractLocal(payerAccount, contractId, getValueEncodedFunction);
	    if (result != null && result.length > 0) {
	      retVal = decodeGetValueResult(result);
	    }
	    return retVal;
	  }


	  private static void setValueToContract(AccountID payerAccount, ContractID contractId,
	      int valuetoSet) throws Exception {
	    byte[] dataToSet = encodeSet(valuetoSet);
	    //set value to simple storage smart contract
	    byte[] retData = callContract(payerAccount, contractId, dataToSet);
	  }


	  private static ContractInfo getContractInfo(AccountID payerAccount,
	      ContractID contractId) throws Exception {
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount);

	    Query getContractInfoQuery = RequestBuilder
	        .getContractGetInfoQuery(contractId, paymentTx, ResponseType.ANSWER_ONLY);

	    Response respToReturn = stub.getContractInfo(getContractInfoQuery);
	    ContractInfo contractInfToReturn = null;
	    contractInfToReturn = respToReturn.getContractGetInfo().getContractInfo();
	    channel.shutdown();

	    return contractInfToReturn;
	  }

	  public static void updateContract(AccountID payerAccount, ContractID contractToUpdate,
	      Duration autoRenewPeriod, Timestamp expirationTime) throws Exception {

	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);

	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
	    Duration transactionDuration = RequestBuilder.getDuration(30);
	    Transaction updateContractRequest = RequestBuilder
	        .getContractUpdateRequest(payerAccount, nodeAccount, TestHelper.getContractMaxFee(), timestamp,
	            transactionDuration, true, "", contractToUpdate, autoRenewPeriod, null, null,
	            expirationTime, SignatureList.newBuilder().addSigs(Signature.newBuilder()
	                .setEd25519(ByteString.copyFrom("testsignature".getBytes()))).build(), "");

	    updateContractRequest = TransactionSigner
	        .signTransaction(updateContractRequest, accountKeys.get(payerAccount));
	    TransactionResponse response = stub.updateContract(updateContractRequest);
	    System.out.println(
	        " update contract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
	            .name());
	    Thread.sleep(1000);
	    TransactionBody updateContractBody = TransactionBody.parseFrom(updateContractRequest.getBodyBytes());
	    TransactionGetReceiptResponse contractUpdateReceipt = getReceipt(
	    		updateContractBody.getTransactionID());
	    Assert.assertNotNull(contractUpdateReceipt);
	    channel.shutdown();

	  }


	  private static String getContractByteCode(AccountID payerAccount,
	      ContractID contractId) throws Exception {
	    String byteCode = "";
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount);

	    Query getContractBytecodeQuery = RequestBuilder
	        .getContractGetBytecodeQuery(contractId, paymentTx, ResponseType.ANSWER_ONLY);

	    Response respToReturn = stub.contractGetBytecode(getContractBytecodeQuery);
	    ByteString contractByteCode = null;
	    contractByteCode = respToReturn.getContractGetBytecodeResponse().getBytecode();
	    if (contractByteCode != null && !contractByteCode.isEmpty()) {
	      byteCode = ByteUtil.toHexString(contractByteCode.toByteArray());
	    }
	    channel.shutdown();

	    return byteCode;
	  }

	  private static AccountInfo getCryptoGetAccountInfo(
	      AccountID accountID) throws Exception {
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

	    Transaction paymentTx = createQueryHeaderTransfer(accountID);
	    Query cryptoGetInfoQuery = RequestBuilder.getCryptoGetInfoQuery(
	        accountID, paymentTx, ResponseType.ANSWER_ONLY);

	    Response respToReturn = stub.getAccountInfo(cryptoGetInfoQuery);
	    AccountInfo accInfToReturn = null;
	    accInfToReturn = respToReturn.getCryptoGetInfo().getAccountInfo();
	    channel.shutdown();

	    return accInfToReturn;
	  }


	  private static GetBySolidityIDResponse getBySolidityID(AccountID payerAccount,
	      String solidityId) throws Exception {
	    int port = 50211;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount);
	    Query getBySolidityIdQuery = RequestBuilder.getBySolidityIDQuery(
	        solidityId, paymentTx, ResponseType.ANSWER_ONLY);

	    Response respToReturn = stub.getBySolidityID(getBySolidityIdQuery);
	    GetBySolidityIDResponse bySolidityReturn = null;
	    bySolidityReturn = respToReturn.getGetBySolidityID();
	    channel.shutdown();

	    return bySolidityReturn;
	  }
	  
		public static byte[] encodeCreateTrivialGetAddress() {
			CallTransaction.Function function = getCreateTrivialGetAddressFunction();
			byte[] encodedFunc = function.encode();
			return encodedFunc;
		}

		public static CallTransaction.Function getCreateTrivialGetAddressFunction() {
			String funcJson = SC_CT_GETADDRESS_ABI.replaceAll("'", "\"");
			CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
			return function;
		}

		public static ContractID decodeCreateTrivialGetAddress(byte[] value) {
			byte[] retVal = new byte[0];
			CallTransaction.Function function = getCreateTrivialGetAddressFunction();
			Object[] retResults = function.decodeResult(value);
			if (retResults != null && retResults.length > 0) {
				retVal = (byte[]) retResults[0];

				long realm = ByteUtil.byteArrayToLong(Arrays.copyOfRange(retVal, 4, 12));
				long accountNum = ByteUtil.byteArrayToLong(Arrays.copyOfRange(retVal, 12, 20));
				ContractID contractID = ContractID.newBuilder().setContractNum(accountNum).setRealmNum(realm).setShardNum(0)
						.build();
				return contractID;
			}
			return null;
		}

		private static ContractID getAddressFromContract(AccountID payerAccount, ContractID contractId) throws Exception {
			ContractID retVal = null;
			byte[] dataToGet = encodeCreateTrivialGetAddress();
			byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
			if (result != null && result.length > 0) {
				retVal = decodeCreateTrivialGetAddress(result);
			}
			return retVal;
		}
}
