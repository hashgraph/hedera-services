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
public class ChildStorage {

  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
  private final Logger log = LogManager.getLogger(ChildStorage.class);

  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final int GROWTH_STEP = 16; // # of Kibibytes to grow child storage per step, to stay below gas limit.
  private static int almostFullKB; // More than half the max allowed smart contract storage, in Kibibytes
  private static long gasLimit;

  private static final String GROWCHILD_ABI =  "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_childId\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"_howManyKB\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"growChild\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}\n";
  private static final String SETZEROREADONE_ABI =  "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"setZeroReadOne\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_getOne\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}\n";
  private static final String SETBOTH_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"setBoth\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String GETCHILDVALUE_ABI =  "{\"constant\":true,\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_childId\",\"type\":\"uint256\"}],\"name\":\"getChildValue\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_get\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}\n";
  private static final String GETMYVALUE_ABI =  "{\"constant\":true,\"inputs\":[],\"name\":\"getMyValue\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_get\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}\n";

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
    almostFullKB = (serverConfig.getMaxContractStateSize()) * 3 / 4;
    gasLimit = serverConfig.getMaxGasLimit() - 1;

    int numberOfReps = 1;
//    if ((args.length) > 0) {
//      numberOfReps = Integer.parseInt(args[0]);
//    }
    for (int i = 0; i < numberOfReps; i++) {
      ChildStorage scSs = new ChildStorage();
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
            transactionDuration, true, "", gasLimit, contractFile, ByteString.EMPTY, 0,
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
  Methods to run getMyValue method
 */

  private int getMyValue(AccountID payerAccount, ContractID contractId) throws Exception {
    int retVal = 0;
    byte[] getValueEncodedFunction = encodeMyValue();
    byte[] result = callContractLocal(payerAccount, contractId, getValueEncodedFunction);
    if (result != null && result.length > 0) {
      retVal = decodeMyValue(result);
    }
    return retVal;
  }

  private byte[] encodeMyValue() {
    CallTransaction.Function function = MyValueFunction();
    byte[] encodedFunc = function.encode();

    return encodedFunc;
  }

  private int decodeMyValue(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = MyValueFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  private CallTransaction.Function MyValueFunction() {
    String funcJson = GETMYVALUE_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
  Methods to run getChildValue method
 */

  private int getChildValue(AccountID payerAccount, ContractID contractId, int childId) throws Exception {
    int retVal = 0;
    byte[] getValueEncodedFunction = encodeChildValue(childId);
    byte[] result = callContractLocal(payerAccount, contractId, getValueEncodedFunction);
    if (result != null && result.length > 0) {
      retVal = decodeChildValue(result);
    }
    return retVal;
  }

  private byte[] encodeChildValue(int childId) {
    CallTransaction.Function function = ChildValueFunction();
    byte[] encodedFunc = function.encode(childId);

    return encodedFunc;
  }

  private int decodeChildValue(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = ChildValueFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  private CallTransaction.Function ChildValueFunction() {
    String funcJson = GETCHILDVALUE_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
  Methods to run growChild
   */

  private byte[] doGrowChild(AccountID payerAccount, ContractID contractId, int childId,
      int howManyKB, int value, ResponseCodeEnum expectedStatus)
      throws Exception {
    byte[] dataToSet = encodeGrowChild(childId, howManyKB, value);
    return callContract(payerAccount, contractId, dataToSet, expectedStatus);
  }

  private byte[] encodeGrowChild(int childId, int howManyKB, int value) {
    CallTransaction.Function function = getGrowChildFunction();
    byte[] encodedFunc = function.encode(childId, howManyKB, value);

    return encodedFunc;
  }

  private CallTransaction.Function getGrowChildFunction() {
    String funcJson = GROWCHILD_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
  Methods to run setZeroReadOne
   */

  private byte[] doSetZeroReadOne(AccountID payerAccount, ContractID contractId, int value,
      ResponseCodeEnum expectedStatus)
      throws Exception {
    byte[] dataToSet = encodeSetZeroReadOne(value);
    return callContract(payerAccount, contractId, dataToSet, expectedStatus);
  }

  private byte[] encodeSetZeroReadOne(int value) {
    CallTransaction.Function function = getSetZeroReadOneFunction();
    byte[] encodedFunc = function.encode(value);

    return encodedFunc;
  }

  private CallTransaction.Function getSetZeroReadOneFunction() {
    String funcJson = SETZEROREADONE_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
  Methods to run setBoth
   */

  private byte[] doSetBoth(AccountID payerAccount, ContractID contractId, int value,
      ResponseCodeEnum expectedStatus)
      throws Exception {
    byte[] dataToSet = encodeSetBoth(value);
    return callContract(payerAccount, contractId, dataToSet, expectedStatus);
  }

  private byte[] encodeSetBoth(int value) {
    CallTransaction.Function function = getSetBothFunction();
    byte[] encodedFunc = function.encode(value);

    return encodedFunc;
  }

  private CallTransaction.Function getSetBothFunction() {
    String funcJson = SETBOTH_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
  End ABI methods
   */

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
    String fileName = "testfiles/ChildStorage.bin";
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


    int myValue = getMyValue(crAccount, sampleStorageContractId);
    Assert.assertEquals(73, myValue);
    log.info("Saw expected initial parent value");

    for (int kidsize = 0; kidsize <= almostFullKB; kidsize += GROWTH_STEP) {
      log.info("About to grow children from size " + kidsize);
      doGrowChild(crAccount, sampleStorageContractId, 0, GROWTH_STEP, 17,
          ResponseCodeEnum.SUCCESS);
      doGrowChild(crAccount, sampleStorageContractId, 1, GROWTH_STEP, 19,
          ResponseCodeEnum.SUCCESS);
    }

    int chZeroValue = getChildValue(crAccount, sampleStorageContractId, 0);
    int chOneValue = getChildValue(crAccount, sampleStorageContractId, 1);
    Assert.assertEquals(17, chZeroValue);
    Assert.assertEquals(19, chOneValue);
    log.info("Found expected values at offset zero in children");

    // Set one child and just read from the other. Should work when the code fix is complete,
    // because the read-only child's data should not be stored.
    doSetZeroReadOne(crAccount, sampleStorageContractId, 23, ResponseCodeEnum.SUCCESS);
    chZeroValue = getChildValue(crAccount, sampleStorageContractId, 0);
    chOneValue = getChildValue(crAccount, sampleStorageContractId, 1);
    myValue = getMyValue(crAccount, sampleStorageContractId);
    // Parent's value and child zero's value[0] should have changed
    Assert.assertEquals(23, chZeroValue);
    Assert.assertEquals(19, chOneValue);
    Assert.assertEquals(23, myValue);
    log.info("Changed value only in child zero, read from child one");

    // Set values in both children. Should exceed the storage size limit.
    doSetBoth(crAccount, sampleStorageContractId, 29, ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED);
    // Values should not have changed
    chZeroValue = getChildValue(crAccount, sampleStorageContractId, 0);
    chOneValue = getChildValue(crAccount, sampleStorageContractId, 1);
    myValue = getMyValue(crAccount, sampleStorageContractId);
    Assert.assertEquals(23, chZeroValue);
    Assert.assertEquals(19, chOneValue);
    Assert.assertEquals(23, myValue);
    log.info("Changed value in both children, failed due to max_contract_storage_exceeded");
  }
}
