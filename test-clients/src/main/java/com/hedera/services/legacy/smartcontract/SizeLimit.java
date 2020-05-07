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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.regression.ServerAppConfigUtility;
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractGetRecordsResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
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
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

/**
 * Test enforcement of smart contract state storage upper bound
 *
 * @author Peter
 */
public class SizeLimit {

  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
  private final Logger log = LogManager.getLogger(SizeLimit.class);


  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final int ENTRIES_PER_KB = 16; // Number of contract smart storage entries per Kibibyte
  private static final int EMPTY_VALUE = 0; // Value if smart contract entry is empty
  private static final int FILLER_VALUE=17; // Value if smart contract entry is set
  private static int tooManyKB; // More than max allowed smart contract storage, in Kibibytes
  private static long gasLimit;

  private static final String BA_SETSIZEINKB_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_howManyKB\",\"type\":\"uint256\"}],\"name\":\"setSizeInKB\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String BA_BIGARRAY_ABI = "{\"constant\":true,\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],\"name\":\"bigArray\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

  private static final String TBA_SETSIZESINKB_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_howManyKB\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"_secondContractArrayKB\",\"type\":\"uint256\"}],\"name\":\"setSizesInKB\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String TBA_GETFIRSTSIZE_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"firstContractArraySize\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_get\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String TBA_GETSECONDSIZE_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"secondContractArraySize\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_get\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

  private static AccountID nodeAccount;
  private static long node_account_number;
  private static long node_shard_number;
  private static long node_realm_number;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private AccountID genesisAccount;
  private Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();
  private static String host;
  private static int port;
  private static long contractDuration;

  public static void main(String args[]) throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
    host = properties.getProperty("host");
    port = Integer.parseInt(properties.getProperty("port"));
    node_account_number = Utilities.getDefaultNodeAccount();
    node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
    node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
    nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
        .setRealmNum(node_shard_number).setShardNum(node_realm_number).build();

    // Read server configuration
    ServerAppConfigUtility serverConfig = ServerAppConfigUtility.getInstance(
        host, node_account_number);
    tooManyKB = serverConfig.getMaxFileSize() + 20;
    gasLimit = serverConfig.getMaxGasLimit() - 1;

    int numberOfReps = 1;
//    if ((args.length) > 0) {
//      numberOfReps = Integer.parseInt(args[0]);
//    }
    for (int i = 0; i < numberOfReps; i++) {
      SizeLimit scSs = new SizeLimit();
      scSs.demo();
    }

  }


  private void loadGenesisAndNodeAcccounts() throws Exception {
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

  private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
      throws Exception {
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
        accountKeys.get(payer).get(0), nodeAccount, transferAmt);
    //transferTx = TransactionSigner.signTransaction(transferTx, accountKeys.get(payer));
    return transferTx;

  }

  private AccountID createAccount(AccountID payerAccount, long initialBalance) throws Exception {

    KeyPair keyGenerated = new KeyPairGenerator().generateKeyPair();
    return createAccount(keyGenerated, payerAccount, initialBalance);
  }

  private AccountID createAccount(KeyPair keyPair, AccountID payerAccount, long initialBalance)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Transaction transaction = TestHelper
        .createAccountWithFee(payerAccount, nodeAccount, keyPair, initialBalance,
            accountKeys.get(payerAccount));
    //	Transaction signTransaction = TransactionSigner.signTransaction(transaction, accountKeys.get(payerAccount));
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    System.out.println(
        "Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    accountKeys.put(newlyCreateAccountId, Collections.singletonList(keyPair.getPrivate()));
    channel.shutdown();
    return newlyCreateAccountId;
  }

  private TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
    TransactionGetReceiptResponse receiptToReturn = null;
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
            transactionId, ResponseType.ANSWER_ONLY)).build();
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Response transactionReceipts = stub.getTransactionReceipts(query);
    int attempts = 1;
    while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt()
        .getReceipt()
        .getStatus() == ResponseCodeEnum.UNKNOWN) {
      Thread.sleep(1000);
      transactionReceipts = stub.getTransactionReceipts(query);
      System.out.println("waiting to getTransactionReceipts as not Unknown..." +
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

  private ContractID createContract(AccountID payerAccount, FileID contractFile,
      long durationInSeconds) throws Exception {
    ContractID createdContract = null;
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
            nodeAccount.getShardNum(), 100l, timestamp,
            transactionDuration, true, "", 1600000, contractFile, ByteString.EMPTY, 0,
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

  private byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data,
      ResponseCodeEnum expectedStatus)
      throws Exception {
    byte[] dataToReturn = null;
    ContractID createdContract = null;
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
            payerAccount.getShardNum(), node_account_number, 0l, 0l, 100l, timestamp,
            transactionDuration, gasLimit, contractToCall, dataBstr, 0,
            accountKeys.get(payerAccount));

    TransactionResponse response = stub.contractCallMethod(callContractRequest);
    System.out.println(
        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(1000);
    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
        callContractBody.getTransactionID());
    Assert.assertEquals(expectedStatus, contractCallReceipt.getReceipt().getStatus());
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

  private TransactionRecord getTransactionRecord(AccountID payerAccount,
      TransactionID transactionId) throws Exception {
    AccountID createdAccount = null;
    int port = 50211;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    long fee = FeeClient.getCostForGettingTxRecord();
    Response recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
        ResponseType.COST_ANSWER);
    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
        ResponseType.ANSWER_ONLY);
    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
    System.out.println("tx record = " + txRecord);
    channel.shutdown();
    return txRecord;
  }

  private Response executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
      CryptoServiceGrpc.CryptoServiceBlockingStub stub, long fee, ResponseType responseType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, responseType);
    Response recordResp = stub.getTxRecordByTxID(getRecordQuery);
    return recordResp;
  }

  private byte[] callContractLocal(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    byte[] dataToReturn = null;
    AccountID createdAccount = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);
    ByteString callData = ByteString.EMPTY;
    int callDataSize = 0;
    if (data != null) {
      callData = ByteString.copyFrom(data);
      callDataSize = callData.size();
    }
    long fee = FeeClient.getCostContractCallLocalFee(callDataSize);
    Response callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
        ResponseType.COST_ANSWER);
    fee = callResp.getContractCallLocal().getHeader().getCost() * 2;
    callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
        ResponseType.ANSWER_ONLY);
    System.out.println("callContractLocal response = " + callResp);
    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
        .getContractCallResult();

    channel.shutdown();
    return functionResults.toByteArray();
  }

  private Response executeContractCall(AccountID payerAccount, ContractID contractToCall,
      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, ByteString callData, long fee,
      ResponseType resposeType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            resposeType);

    Response callResp = stub.contractCallLocalMethod(contractCallLocal);
    return callResp;
  }


  public void updateContract(AccountID payerAccount, ContractID contractToUpdate,
      Duration autoRenewPeriod, Timestamp expirationTime) throws Exception {

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);
    Transaction updateContractRequest = RequestBuilder
        .getContractUpdateRequest(payerAccount, nodeAccount, 100L, timestamp, transactionDuration,
            true, "", contractToUpdate, autoRenewPeriod, null, null, expirationTime,
            SignatureList.newBuilder().addSigs(Signature.newBuilder()
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

  private String getContractByteCode(AccountID payerAccount,
      ContractID contractId) throws Exception {
    String byteCode = "";
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();

    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetBytecode);
    Response respToReturn = executeContractByteCodeQuery(payerAccount, contractId, stub, fee,
        ResponseType.COST_ANSWER);

    fee = respToReturn.getContractGetBytecodeResponse().getHeader().getCost();
    respToReturn = executeContractByteCodeQuery(payerAccount, contractId, stub, fee,
        ResponseType.ANSWER_ONLY);
    ByteString contractByteCode = null;
    contractByteCode = respToReturn.getContractGetBytecodeResponse().getBytecode();
    if (contractByteCode != null && !contractByteCode.isEmpty()) {
      byteCode = ByteUtil.toHexString(contractByteCode.toByteArray());
    }
    channel.shutdown();

    return byteCode;
  }

  private Response executeContractByteCodeQuery(AccountID payerAccount, ContractID contractId,
      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, long fee,
      ResponseType responseType) throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getContractBytecodeQuery = RequestBuilder
        .getContractGetBytecodeQuery(contractId, paymentTx, responseType);
    Response respToReturn = stub.contractGetBytecode(getContractBytecodeQuery);
    return respToReturn;
  }

  private AccountInfo getCryptoGetAccountInfo(
      AccountID accountID) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

    long fee = FeeClient.getFeeByID(HederaFunctionality.CryptoGetInfo);

    Response respToReturn = executeGetAcctInfoQuery(accountID, stub, fee, ResponseType.COST_ANSWER);

    fee = respToReturn.getCryptoGetInfo().getHeader().getCost();
    respToReturn = executeGetAcctInfoQuery(accountID, stub, fee, ResponseType.ANSWER_ONLY);
    AccountInfo accInfToReturn = null;
    accInfToReturn = respToReturn.getCryptoGetInfo().getAccountInfo();
    channel.shutdown();

    return accInfToReturn;
  }

  private Response executeGetAcctInfoQuery(AccountID accountID,
      CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      long fee, ResponseType responseType) throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(accountID, fee);
    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTx, responseType);

    Response respToReturn = stub.getAccountInfo(cryptoGetInfoQuery);
    return respToReturn;
  }

  private GetBySolidityIDResponse getBySolidityID(AccountID payerAccount,
      String solidityId) throws Exception {
    int port = 50211;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);
    long fee = FeeClient.getFeegetBySolidityID();
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getBySolidityIdQuery = RequestBuilder
        .getBySolidityIDQuery(solidityId, paymentTx, ResponseType.ANSWER_ONLY);

    Response respToReturn = stub.getBySolidityID(getBySolidityIdQuery);
    GetBySolidityIDResponse bySolidityReturn = null;
    bySolidityReturn = respToReturn.getGetBySolidityID();
    channel.shutdown();

    return bySolidityReturn;
  }

  /**
   * Get Tx records by contract ID.
   *
   * @return list of Tx records
   */
  public List<TransactionRecord> getTxRecordByContractID(AccountID payerID, ContractID contractId)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();

    SmartContractServiceGrpc.SmartContractServiceBlockingStub scstub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    Transaction paymentTxSigned = createQueryHeaderTransfer(payerID, TestHelper.getCryptoMaxFee());
    Query query = RequestBuilder
        .getContractRecordsQuery(contractId, paymentTxSigned, ResponseType.ANSWER_ONLY);
    Response transactionRecord = scstub.getTxRecordByContractID(query);
    channel.shutdown();

    Assert.assertNotNull(transactionRecord);
    ContractGetRecordsResponse response = transactionRecord.getContractGetRecordsResponse();
    Assert.assertNotNull(response);
    List<TransactionRecord> records = response.getRecordsList();
    return records;
  }

  /*
  Methods to run setSizeInKB method
 */
  private byte[] doSetSizeInKB(ContractID contractId, AccountID payerAccount, int sizeInKB,
      ResponseCodeEnum expectedStatus)
      throws Exception {
    byte[] dataToSet = encodeSetSizeInKB(sizeInKB);
    return callContract(payerAccount, contractId, dataToSet, expectedStatus);
  }

  private byte[] encodeSetSizeInKB(int sizeInKB) {
    String retVal = "";
    CallTransaction.Function function = getSetSizeInKBFunction();
    byte[] encodedFunc = function.encode(sizeInKB);

    return encodedFunc;
  }

  private CallTransaction.Function getSetSizeInKBFunction() {
    String funcJson = BA_SETSIZEINKB_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
  Methods to run setSizesInKB method
 */
  private byte[] doSetSizesInKB(ContractID contractId, AccountID payerAccount, int sizeInKB,
      int secondSizeInKB, ResponseCodeEnum expectedStatus)
      throws Exception {
    byte[] dataToSet = encodeSetSizesInKB(sizeInKB, secondSizeInKB);
    return callContract(payerAccount, contractId, dataToSet, expectedStatus);
  }

  private byte[] encodeSetSizesInKB(int sizeInKB, int secondSizeInKb) {
    String retVal = "";
    CallTransaction.Function function = getSetSizesInKBFunction();
    byte[] encodedFunc = function.encode(sizeInKB, secondSizeInKb);

    return encodedFunc;
  }

  private CallTransaction.Function getSetSizesInKBFunction() {
    String funcJson = TBA_SETSIZESINKB_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
  Methods to run getArrayElement method
 */

  private int getGetArrayElement(AccountID payerAccount, ContractID contractId, int index) throws Exception {
    int retVal = 0;
    byte[] getValueEncodedFunction = encodeGetArrayElement(index);
    byte[] result = callContractLocal(payerAccount, contractId, getValueEncodedFunction);
    if (result != null && result.length > 0) {
      retVal = decodeGetArrayElement(result);
    }
    return retVal;
  }

  private byte[] encodeGetArrayElement(int index) {
    String retVal = "";
    CallTransaction.Function function = getGetArrayElementFunction();
    byte[] encodedFunc = function.encode(index);

    return encodedFunc;
  }

  private int decodeGetArrayElement(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = getGetArrayElementFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  private CallTransaction.Function getGetArrayElementFunction() {
    String funcJson = BA_BIGARRAY_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
  Methods to run getFirstContractArraySize method
 */

  private int getFirstContractArraySize(AccountID payerAccount, ContractID contractId) throws Exception {
    int retVal = 0;
    byte[] getValueEncodedFunction = encodeFirstContractArraySize();
    byte[] result = callContractLocal(payerAccount, contractId, getValueEncodedFunction);
    if (result != null && result.length > 0) {
      retVal = decodeFirstContractArraySize(result);
    }
    return retVal;
  }

  private byte[] encodeFirstContractArraySize() {
    String retVal = "";
    CallTransaction.Function function = firstContractArraySizeFunction();
    byte[] encodedFunc = function.encode();

    return encodedFunc;
  }

  private int decodeFirstContractArraySize(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = firstContractArraySizeFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  private CallTransaction.Function firstContractArraySizeFunction() {
    String funcJson = TBA_GETFIRSTSIZE_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
  Methods to run getSecondContractArraySize method
 */

  private int getSecondContractArraySize(AccountID payerAccount, ContractID contractId) throws Exception {
    int retVal = 0;
    byte[] getValueEncodedFunction = encodeSecondContractArraySize();
    byte[] result = callContractLocal(payerAccount, contractId, getValueEncodedFunction);
    if (result != null && result.length > 0) {
      retVal = decodeSecondContractArraySize(result);
    }
    return retVal;
  }

  private byte[] encodeSecondContractArraySize() {
    String retVal = "";
    CallTransaction.Function function = secondContractArraySizeFunction();
    byte[] encodedFunc = function.encode();

    return encodedFunc;
  }

  private int decodeSecondContractArraySize(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = secondContractArraySizeFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  private CallTransaction.Function secondContractArraySizeFunction() {
    String funcJson = TBA_GETSECONDSIZE_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public void demo() throws Exception {
    loadGenesisAndNodeAcccounts();
    FileID storageFileId;
    ContractID sampleStorageContractId;

    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);
    log.info("Account created successfully");


    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    TestHelper.initializeFeeClient(channel, crAccount, crAccountKeyPair, nodeAccount);
    channel.shutdown();
    String fileName = "testfiles/BigArray.bin";
    Assert.assertNotEquals(0,crAccount.getAccountNum());

    log.info("about to create file");
    storageFileId = LargeFileUploadIT
        .uploadFile(crAccount, fileName, crAccountKeyPair);
    log.info("file created");
    Assert.assertNotEquals(0, storageFileId.getFileNum());
    log.info("Smart Contract file uploaded successfully");

    log.info("about to create contract");
    sampleStorageContractId = createContract(crAccount, storageFileId,
        contractDuration);
    log.info("contract created");
    Assert.assertNotNull(sampleStorageContractId);
    log.info("Contract created successfully");


    // First set a size that will be allowed
    log.info("about to do first set size");
    doSetSizeInKB(sampleStorageContractId, crAccount, 1, ResponseCodeEnum.SUCCESS);
    log.info("did first set size");
    int left = getGetArrayElement(crAccount, sampleStorageContractId,0);
    Assert.assertEquals(FILLER_VALUE, left);
    log.info("Passed first left test");
    int right = getGetArrayElement(crAccount, sampleStorageContractId,ENTRIES_PER_KB - 1);
    Assert.assertEquals(FILLER_VALUE, right);
    log.info("Passed first right test");
    int outer = getGetArrayElement(crAccount, sampleStorageContractId,ENTRIES_PER_KB);
    Assert.assertEquals(EMPTY_VALUE, outer);
    log.info("Passed first outer test");

    // Now set a size that is too big. The set should be refused, and the storage size should not
    // be increased.
    log.info("about to do second set size");
    doSetSizeInKB(sampleStorageContractId, crAccount, tooManyKB, ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED);
    log.info("did second set size");
    left = getGetArrayElement(crAccount, sampleStorageContractId,0);
    Assert.assertEquals(FILLER_VALUE, left);
    log.info("Passed left test");
    right = getGetArrayElement(crAccount, sampleStorageContractId,ENTRIES_PER_KB - 1);
    Assert.assertEquals(FILLER_VALUE, right);
    log.info("Passed right test");
    outer = getGetArrayElement(crAccount, sampleStorageContractId,ENTRIES_PER_KB);
    Assert.assertEquals(EMPTY_VALUE, outer);
    log.info("Passed outer test");
    int limit = getGetArrayElement(crAccount, sampleStorageContractId,(tooManyKB * ENTRIES_PER_KB) - 1);
    Assert.assertEquals(EMPTY_VALUE, limit);
    log.info("Passed limit test");


    // Two-contract tests:
    String nestedFileName = "testfiles/TwoBigArrays.bin";
    log.info("about to create second file");
    storageFileId = LargeFileUploadIT
        .uploadFile(crAccount, nestedFileName, crAccountKeyPair);
    log.info("file created");
    Assert.assertNotEquals(0, storageFileId.getFileNum());
    log.info("Smart Contract file uploaded successfully");

    log.info("about to create nested contracts");
    sampleStorageContractId = createContract(crAccount, storageFileId,
        contractDuration);
    log.info("contract created");
    Assert.assertNotNull(sampleStorageContractId);
    Assert.assertNotEquals(0, sampleStorageContractId.getContractNum());
    log.info("Contract created successfully: " + sampleStorageContractId.getContractNum());

    // First set a size that will be allowed
    log.info("about to do third set size");
    doSetSizesInKB(sampleStorageContractId, crAccount, 1, 2, ResponseCodeEnum.SUCCESS);
    log.info("did third set size");

    // Check the array lengths
    int first = getFirstContractArraySize(crAccount, sampleStorageContractId);
    Assert.assertEquals(ENTRIES_PER_KB, first);
    int second = getSecondContractArraySize(crAccount, sampleStorageContractId);
    Assert.assertEquals(ENTRIES_PER_KB * 2, second);
    log.info("Passed first array sizes test");

    // Set size of both to a total that's too big
    log.info("about to do fourth set size");
    doSetSizesInKB(sampleStorageContractId, crAccount, tooManyKB / 10, 9 * tooManyKB / 10,
        ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED);
    log.info("did fourth set size");

    // Check that neither array size changed
    first = getFirstContractArraySize(crAccount, sampleStorageContractId);
    Assert.assertEquals(ENTRIES_PER_KB, first);
    second = getSecondContractArraySize(crAccount, sampleStorageContractId);
    Assert.assertEquals(ENTRIES_PER_KB * 2, second);
    log.info("Passed final array sizes test; neither size changed");
  }
}
