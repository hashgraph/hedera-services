package com.hedera.services.legacy.regression.umbrella;

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
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.ContractGetRecordsResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

/**
 * This class contains smart contract tests.
 *
 * @author hua Created on 2018-10-09
 */
public class SmartContractServiceTest extends FileServiceTest {

  private static final Logger log = LogManager.getLogger(SmartContractServiceTest.class);
  protected static SmartContractServiceGrpc.SmartContractServiceBlockingStub scstub = null;
  protected static Map<ContractID, String> contractIDMap = new ConcurrentHashMap<>();
  private static List<String> solidityIDList = new ArrayList<>();
  private static boolean isRandomSmartContract = true;
  protected static int CONTRACT_CALL_VALUE_BOUND = 1000;
  private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String SC_GET_BALANCE = "{\"constant\":true,\"inputs\":[],\"name\":\"getBalance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String SC_DEPOSIT = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
  private static final String CONTRACT_PAY_NAME = "PayTest.bin";
  private static final String CONTRACT_STORAGE_NAME = "simpleStorage.bin";
  private String[] smartContractFiles = {CONTRACT_STORAGE_NAME, CONTRACT_PAY_NAME};
  private long DEFAULT_CONTRACT_OP_GAS = 1000L;
  private long DEFAULT_CREATE_CONTRACT_OP_GAS = 1000L;

  public SmartContractServiceTest(String testConfigFilePath) {
    super(testConfigFilePath);
  }

  @Override
  protected void createStubs() throws URISyntaxException, IOException {
    super.createStubs();
    scstub = SmartContractServiceGrpc.newBlockingStub(channel);
  }

  /**
   * Creates a smart contract, note each unique contract can only be deployed once.
   */
  public ContractID createContract(FileID contractFile, String contractFileName,
      AccountID payerAccount, AccountID nodeAccount, boolean addAdminKey) throws Throwable {
    CustomPropertiesSingleton properties = CustomPropertiesSingleton.getInstance();
    long durationInSeconds = properties.getContractDuration();
    Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    Key adminKey = null;
    if (addAdminKey) {
      adminKey = genAdminKeyComplex();
    }
    Transaction createContractRequest = RequestBuilder
        .getCreateContractRequest(payerAccount.getAccountNum(),
            payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(), TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, true, "createContract",
            DEFAULT_CREATE_CONTRACT_OP_GAS, contractFile, ByteString.EMPTY, 0, contractAutoRenew,
            SignatureList.newBuilder().getDefaultInstanceForType(), "", adminKey);

    Key payerKey = acc2ComplexKeyMap.get(payerAccount);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    if (addAdminKey) {
      keys.add(adminKey);
    }
    createContractRequest = TransactionSigner
        .signTransactionComplex(createContractRequest, keys, pubKey2privKeyMap);

    log.debug("createContract: request=" + createContractRequest);
    TransactionResponse response = retryLoopTransaction(createContractRequest, "createContract");
    Assert.assertNotNull(response);
    log.info(
        "createContract: Pre Check Response=" + response.getNodeTransactionPrecheckCode().name());

    TransactionBody createContractBody = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(createContractRequest);
    TransactionID transactionID = createContractBody.getTransactionID();
    cache.addTransactionID(transactionID);

    ContractID createdContract = null;
    if (getReceipt) {
      TransactionReceipt contractCreateReceipt = TestHelperComplex
          .getTxReceipt(transactionID, cstub, log, host);
      Assert.assertNotNull(contractCreateReceipt);
      if (!contractCreateReceipt.getStatus().name().equals(ResponseCodeEnum.SUCCESS.name())) {
        throw new Exception(
            "Problem creating contract: receipt=" + contractCreateReceipt + ", request="
                + createContractRequest);
      }
      createdContract = contractCreateReceipt.getContractID();
      Assert.assertNotEquals(0, createdContract.getContractNum());
      contractIDMap.put(createdContract, contractFileName);
      if (addAdminKey) {
        contract2ComplexKeyMap.put(createdContract, adminKey);
      }

      //get solidity ID
      ContractInfo cInfo = getContractInfo(payerAccount, createdContract, nodeAccount);
      String solidityID = cInfo.getContractAccountID();
      solidityIDList.add(solidityID);
    }

    return createdContract;
  }

  /**
   * Updates a smart contract.
   *
   * @return transaction Receipt
   */
  public TransactionReceipt updateContract(AccountID payerAccount, ContractID contractToUpdate,
      AccountID nodeAccount) throws Exception {
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    long durationInSeconds = DAY_SEC * 30;
    Duration autoRenewPeriod = RequestBuilder
        .getDuration(CustomPropertiesSingleton.getInstance().getUpdateDurationValue());
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);
    ContractInfo cInfo1 = getContractInfo(payerAccount, contractToUpdate, nodeAccount);
    Timestamp expirationTime = Timestamp.newBuilder()
        .setSeconds(cInfo1.getExpirationTime().getSeconds() + DAY_SEC * 30).build();
    Key newAdminKey = genAdminKeyComplex();
    Transaction updateContractRequest = RequestBuilder
        .getContractUpdateRequest(payerAccount, nodeAccount, TestHelper.getContractMaxFee(),
            timestamp, transactionDuration, true, "updateContract", contractToUpdate,
            autoRenewPeriod, newAdminKey, null, expirationTime,
            SignatureList.newBuilder().getDefaultInstanceForType(), "");

    List<Key> keys = new ArrayList<Key>();
    Key payerKey = acc2ComplexKeyMap.get(payerAccount);
    keys.add(payerKey);
    Key oldAdminKey = null;
    if (contract2ComplexKeyMap.containsKey(contractToUpdate)) {
      oldAdminKey = contract2ComplexKeyMap.remove(contractToUpdate);
      keys.add(oldAdminKey);
    }
    keys.add(newAdminKey);

    updateContractRequest = TransactionSigner.signTransactionComplex(updateContractRequest, keys,
        pubKey2privKeyMap);
    log.debug("updateContract: request=" + updateContractRequest);
    TransactionResponse response = retryLoopTransaction(updateContractRequest, "updateContract");
    Assert.assertNotNull(response);
    TransactionBody updateContractBody = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(updateContractRequest);
    log.info(
        "updateContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionID transactionID = updateContractBody.getTransactionID();
    cache.addTransactionID(transactionID);

    TransactionReceipt contractUpdateReceipt = null;
    if (getReceipt) {
      contractUpdateReceipt = TestHelperComplex
          .getTxReceipt(transactionID, cstub, log, host);
      log.debug("updateContract: receipt=" + contractUpdateReceipt);
      Assert.assertNotNull(contractUpdateReceipt);
      if (!contractUpdateReceipt.getStatus().name().equals(ResponseCodeEnum.SUCCESS.name())) {
        if (oldAdminKey != null) // restore original admin key
        {
          contract2ComplexKeyMap.put(contractToUpdate, oldAdminKey);
        }

        throw new Exception(
            "Problem updating contract: receipt=" + contractUpdateReceipt + ", request="
                + updateContractRequest);
      }
      contract2ComplexKeyMap.put(contractToUpdate, newAdminKey);
    }

    return contractUpdateReceipt;
  }

  /**
   * Gets contract info.
   *
   * @return ContractInfo object
   */
  public ContractInfo getContractInfo(AccountID payerAccount, ContractID contractId,
      AccountID nodeAccount)
      throws Exception {
    long contractInfoCost = getContractInfoCost(payerAccount, contractId, nodeAccount);
    Transaction paymentTx = getQueryPaymentSignedWithFee(payerAccount, nodeAccount, "getContractInfo", contractInfoCost);
    Query getContractInfoQuery = RequestBuilder.getContractGetInfoQuery(contractId, paymentTx,
        ResponseType.ANSWER_ONLY);
    log.debug("getContractInfo: request=" + getContractInfoQuery);
    Response respToReturn = retryLoopQuery(getContractInfoQuery, "getContractInfo");
    Assert.assertNotNull(respToReturn);
    log.debug("getContractInfo: Response=" + respToReturn);
    ContractInfo contractInfToReturn = null;
    contractInfToReturn = respToReturn.getContractGetInfo().getContractInfo();

    return contractInfToReturn;
  }

  private long getContractInfoCost(AccountID payerAccount, ContractID contractId, AccountID nodeAccount) throws Exception {
    Transaction paymentTxCost = getQueryPaymentSigned(payerAccount, nodeAccount, "getContractInfoCost");
    Query getContractInfoQueryCost = RequestBuilder.getContractGetInfoQuery(contractId, paymentTxCost,
            ResponseType.COST_ANSWER);
    Response respToReturnCost = retryLoopQuery(getContractInfoQueryCost, "getContractInfo");
    return respToReturnCost.getContractGetInfo().getHeader().getCost();
  }

  public byte[] callContract(AccountID payerAccount, AccountID nodeAccount, ContractID contractID,
      int value)
      throws Throwable {
    String contractFile = contractIDMap.get(contractID);
    byte[] rv = null;
    if (contractFile.equals(CONTRACT_STORAGE_NAME)) {
      byte[] data = encodeSet(value);
      rv = callContractBase(payerAccount, nodeAccount, contractID, data, 0);
    } else if (contractFile.equals(CONTRACT_PAY_NAME)) {
      byte[] data = encodeDeposit(value);
      rv = callContractBase(payerAccount, nodeAccount, contractID, data, value);
    }

    return rv;
  }

  private byte[] callContractBase(AccountID payerAccount, AccountID nodeAccount,
      ContractID contractID, byte[] data,
      int valuetoSet) throws Throwable {
    byte[] dataToReturn = null;
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

    long node_account_number = nodeAccount.getAccountNum();
    Transaction callContractRequest = RequestBuilder
        .getContractCallRequest(payerAccount.getAccountNum(),
            payerAccount.getRealmNum(), payerAccount.getShardNum(), node_account_number, 0l, 0l,
                TestHelper.getContractMaxFee(), timestamp,
            transactionDuration, DEFAULT_CONTRACT_OP_GAS, contractID, dataBstr, valuetoSet,
            SignatureList.newBuilder().getDefaultInstanceForType());

    Key payerKey = acc2ComplexKeyMap.get(payerAccount);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    callContractRequest = TransactionSigner
        .signTransactionComplex(callContractRequest, keys, pubKey2privKeyMap);

    log.debug("callContract: request=" + callContractRequest);
    TransactionBody callContractBody = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(callContractRequest);
    TransactionResponse response = retryLoopTransaction(callContractRequest, "contractCallMethod");
    Assert.assertNotNull(response);
    // check precheck code
    ResponseCodeEnum code = response.getNodeTransactionPrecheckCode();
    if (!code.equals(ResponseCodeEnum.OK)) {
      String errorMessage = "callContract: Pre Check Response=" + code.name();
      log.warn(errorMessage + ", callContractBody=" + callContractBody);
      throw new InvalidNodeTransactionPrecheckCode(errorMessage);
    }
    TransactionID transactionID = callContractBody.getTransactionID();
    cache.addTransactionID(transactionID);
    TransactionReceipt contractCallReceipt = TestHelperComplex
        .getTxReceipt(transactionID, cstub, log, host);
    Assert.assertNotNull(contractCallReceipt);
    if (contractCallReceipt.getStatus().name().equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      TransactionRecord trRecord = getTransactionRecord(
          callContractBody.getTransactionID(),
          payerAccount, nodeAccount);
      if (trRecord != null && trRecord.hasContractCallResult()) {
        ContractFunctionResult callResults = trRecord.getContractCallResult();
        log.info("Gas used : " + callResults.getGasUsed());
        String errMsg = callResults.getErrorMessage();
        if (StringUtils.isEmpty(errMsg)) {
          if (!callResults.getContractCallResult().isEmpty()) {
            dataToReturn = callResults.getContractCallResult().toByteArray();
          }
        } else {
          log.error("@@@ Contract Call resulted in error: " + errMsg);
        }
      } else if (trRecord == null) {
        log.warn("callContract: tx record is null");
      }
    }

    return dataToReturn;
  }

  public TransactionRecord getTransactionRecord(TransactionID transactionId, AccountID payerAccount,
      AccountID nodeAccount) throws Exception {

    long transactionRecordCost = getTransactionRecordCost(transactionId, payerAccount, nodeAccount);
    Transaction paymentTx = getQueryPaymentSignedWithFee(payerAccount, nodeAccount,
        "getTransactionRecord", transactionRecordCost);
    Query getRecordQuery = RequestBuilder.getTransactionGetRecordQuery(transactionId, paymentTx,
        ResponseType.ANSWER_ONLY);
    Response recordResp = retryLoopQuery(getRecordQuery, "getTxRecordByTxID");
    Assert.assertNotNull(recordResp);
    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
    log.info("tx record = " + txRecord);
    return txRecord;
  }

  private long getTransactionRecordCost(TransactionID transactionId, AccountID payerAccount, AccountID nodeAccount) throws Exception {
    Transaction paymentTxCost = getQueryPaymentSigned(payerAccount, nodeAccount,
            "getTransactionRecordCost");
    Query getRecordQueryCost = RequestBuilder.getTransactionGetRecordQuery(transactionId, paymentTxCost,
            ResponseType.COST_ANSWER);
    Response recordRespCost = retryLoopQuery(getRecordQueryCost, "getTxRecordByTxID");
    return recordRespCost.getTransactionGetRecord().getHeader().getCost();
  }

  /**
   * Gets a random smart contract ID.
   *
   * @return an existing smart contract ID or null of none available
   */
  public static ContractID getRandomSmartContractID() {
    synchronized (contractIDMap) {
      ContractID rv = null;
      ContractID[] cs = contractIDMap.keySet().toArray(new ContractID[0]);
      int size = cs.length;
      if (size > 0) {
        int index = 0;
        if (isRandomSmartContract) {
          index = rand.nextInt(size);
        }
        rv = cs[index];
      }
      return rv;
    }
  }

  /**
   * Gets a random smart contract map entry and removes it from the list to prevent concurrent use
   * by different threads. The entire entry is needed for later replacement.
   *
   * @return an existing smart contract ID or null of none available
   */
  public static ContractIDMapEntry takeRandomSmartContract() {
    synchronized (contractIDMap) {
      ContractID rv = null;
      ContractID[] cs = contractIDMap.keySet().toArray(new ContractID[0]);
      int size = cs.length;
      if (size > 0) {
        int index = 0;
        if (isRandomSmartContract) {
          index = rand.nextInt(size);
        }
        rv = cs[index];
      }
      if (rv == null) {
        return null;
      }
      return new ContractIDMapEntry(rv, contractIDMap.remove(rv));
    }
  }

  /***
   * Replaces a previously taken smart contract map entry
   *
   * @param entry The key and value of the map entry to put back
   */
  public static void replaceSmartContract(ContractIDMapEntry entry) {
    contractIDMap.put(entry.getId(), entry.getFileName());
  }

  private byte[] callContractLocalBase(AccountID payerAccount, AccountID nodeAccount,
      ContractID contractToCall, ByteString callData) throws Throwable {
    Transaction paymentTx = getQueryPaymentSigned(payerAccount, nodeAccount, "callContractLocal");
    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, DEFAULT_CONTRACT_OP_GAS, callData, 0L, 5000,
            paymentTx, ResponseType.ANSWER_ONLY);

    log.debug("callContractLocal: request = " + contractCallLocal);
    Response callResp = retryLoopQuery(contractCallLocal, "contractCallLocalMethod");
    Assert.assertNotNull(callResp);
    log.info("callContractLocal: response = " + callResp);
    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
        .getContractCallResult();
    return functionResults.toByteArray();
  }

  public int decodeGetValueResult(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = getGetValueFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  public byte[] encodeGetValue() {
    CallTransaction.Function function = getGetValueFunction();
    byte[] encodedFunc = function.encode();
    return encodedFunc;
  }

  public byte[] encodeSet(int valueToAdd) {
    CallTransaction.Function function = getSetFunction();
    byte[] encodedFunc = function.encode(valueToAdd);

    return encodedFunc;
  }

  private CallTransaction.Function getGetValueFunction() {
    String funcJson = SC_GET_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private CallTransaction.Function getSetFunction() {
    String funcJson = SC_SET_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public String getRandomSmartContractFile() {
    int index = 0;
    index = rand.nextInt(smartContractFiles.length);
    return smartContractFiles[index];
  }

  /**
   * Gets contract bytecode.
   *
   * @param contractId the contract to get the byte code for
   * @return bytecode in hex
   */
  public String getContractByteCode(AccountID payerAccount,
      ContractID contractId, AccountID nodeAccount) throws Exception {
    String byteCode = "";

    long getContractByteCodeCost = getContractBytecodeCost(payerAccount, contractId, nodeAccount);
    Transaction paymentTx = getQueryPaymentSignedWithFee(payerAccount, nodeAccount, "getContractByteCode", getContractByteCodeCost);
    Query getContractBytecodeQuery = RequestBuilder
        .getContractGetBytecodeQuery(contractId, paymentTx, ResponseType.ANSWER_ONLY);
    Response respToReturn = retryLoopQuery(getContractBytecodeQuery, "contractGetBytecode");
    Assert.assertNotNull(respToReturn);
    ByteString contractByteCode = null;
    contractByteCode = respToReturn.getContractGetBytecodeResponse().getBytecode();
    if (contractByteCode != null && !contractByteCode.isEmpty()) {
      byteCode = ByteUtil.toHexString(contractByteCode.toByteArray());
    }

    return byteCode;
  }

  private long getContractBytecodeCost(AccountID payerAccount, ContractID contractId, AccountID nodeAccount) throws Exception {
    Transaction paymentTxCost = getQueryPaymentSigned(payerAccount, nodeAccount, "getContractByteCodeCost");
    Query getContractBytecodeQueryCost = RequestBuilder
            .getContractGetBytecodeQuery(contractId, paymentTxCost, ResponseType.COST_ANSWER);
    Response respToReturnCost = retryLoopQuery(getContractBytecodeQueryCost, "contractGetBytecode");
    return respToReturnCost.getContractGetBytecodeResponse().getHeader().getCost();
  }

  /**
   * Gets contract ID by solidity ID.
   *
   * @return contract ID
   */
  public ContractID getBySolidityID(AccountID payerAccount, String solidityId,
      AccountID nodeAccount) throws Exception {
    Transaction paymentTx = getQueryPaymentSigned(payerAccount, nodeAccount, "getBySolidityID");
    Query getBySolidityIdQuery = RequestBuilder.getBySolidityIDQuery(
        solidityId, paymentTx, ResponseType.ANSWER_ONLY);

    Response respToReturn = retryLoopQuery(getBySolidityIdQuery, "getBySolidityID");
    Assert.assertNotNull(respToReturn);
    GetBySolidityIDResponse bySolidityReturn = null;
    bySolidityReturn = respToReturn.getGetBySolidityID();
    ContractID contractID = bySolidityReturn.getContractID();

    return contractID;
  }

  /**
   * Gets a random solidity ID.
   *
   * @return solidity ID string, null if none available
   */
  public static String getRandomSmartSolidityID() {
    String rv = null;
    int size = solidityIDList.size();
    if (size > 0) {
      int index = 0;
      index = rand.nextInt(size);
      rv = solidityIDList.get(index);
    }
    return rv;
  }

  /**
   * Get Tx records by contract ID.
   *
   * @return list of Tx records
   */
  public List<TransactionRecord> getTxRecordByContractID(AccountID payerID, ContractID contractId,
      AccountID nodeID) throws Exception {

    long getTxRecordByContractIDCost = getTxRecordByContractIDCost(payerID, contractId, nodeID);

    Transaction paymentTxSigned = getSignedTransferTx(payerID, nodeID, payerID, nodeID,
            getTxRecordByContractIDCost, "getTxRecordByContractId");
    Query query = RequestBuilder
        .getContractRecordsQuery(contractId, paymentTxSigned, ResponseType.ANSWER_ONLY);
    Response transactionRecord = retryLoopQuery(query, "getTxRecordByContractID");
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord);
    ContractGetRecordsResponse response = transactionRecord.getContractGetRecordsResponse();
    Assert.assertNotNull(response);
    List<TransactionRecord> records = response.getRecordsList();
    return records;
  }

  private long getTxRecordByContractIDCost(AccountID payerID, ContractID contractId, AccountID nodeID) throws Exception {
    Transaction paymentTxSignedCost = getSignedTransferTx(payerID, nodeID, payerID, nodeID,
            QUERY_PAYMENT_AMOUNT, "getTxRecordByContractIdCost");
    Query queryCost = RequestBuilder
            .getContractRecordsQuery(contractId, paymentTxSignedCost, ResponseType.COST_ANSWER);
    Response transactionResponseCost = retryLoopQuery(queryCost, "getTxRecordByContractID");
    return transactionResponseCost.getContractGetRecordsResponse().getHeader().getCost();
  }


  private byte[] encodeDeposit(int valueToDeposit) {
    CallTransaction.Function function = getDepositFunction();
    byte[] encodedFunc = function.encode(valueToDeposit);

    return encodedFunc;
  }

  private CallTransaction.Function getDepositFunction() {
    String funcJson = SC_DEPOSIT.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public int callContractLocal(AccountID payerAccount, AccountID nodeAccount, ContractID contractID)
      throws Throwable {
    String contractFile = contractIDMap.get(contractID);
    int rv = -1;
    if (contractFile.equals(CONTRACT_STORAGE_NAME)) {
      byte[] getValueEncodedFunction = encodeGetValue();
      ByteString callData = ByteString.EMPTY;
      if (getValueEncodedFunction != null) {
        callData = ByteString.copyFrom(getValueEncodedFunction);
      }
      byte[] result = callContractLocalBase(payerAccount, nodeAccount, contractID, callData);
      if (result != null && result.length > 0) {
        rv = decodeGetValueResult(result);
      } else {
        throw new Exception(
            "callContractLocal error: result is null or empty! " + result + "; contractID="
                + contractID + "; " + " contractFile=" + contractFile);
      }
    } else if (contractFile.equals(CONTRACT_PAY_NAME)) {
      byte[] getBalanceEncodedFunction = encodeGetBalance();
      ByteString callData = ByteString.EMPTY;
      if (getBalanceEncodedFunction != null) {
        callData = ByteString.copyFrom(getBalanceEncodedFunction);
      }
      byte[] result = callContractLocalBase(payerAccount, nodeAccount, contractID, callData);
      if (result != null && result.length > 0) {
        rv = decodeGetBalanceResult(result);
      } else {
        throw new Exception(
            "callContractLocal error: result is null or empty! " + result + "; contractID="
                + contractID + "; " + " contractFile=" + contractFile);
      }
    }

    return rv;
  }

  private byte[] encodeGetBalance() {
    CallTransaction.Function function = getGetBalanceFunction();
    byte[] encodedFunc = function.encode();
    return encodedFunc;
  }

  private int decodeGetBalanceResult(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = getGetBalanceFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  private CallTransaction.Function getGetBalanceFunction() {
    String funcJson = SC_GET_BALANCE.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /**
   * Generates admin key based on the account type in configuration.
   *
   * @return generated key
   */
  public Key genAdminKeyComplex() throws Exception {
    String accountKeyType = getRandomAccountKeyType();
    Key key = genComplexKey(accountKeyType);

    return key;
  }

  private TransactionResponse retryLoopTransaction(Transaction transaction, String apiName)
      throws InterruptedException {
    TransactionResponse response = null;
    for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {

      if (i > 0) {
        log.info("retrying api call " + apiName);
      }

      try {
        switch (apiName) {
          case "createContract":
            response = scstub.createContract(transaction);
            break;
          case "updateContract":
            response = scstub.updateContract(transaction);
            break;
          case "contractCallMethod":
            response = scstub.contractCallMethod(transaction);
            break;
          default:
            throw new IllegalArgumentException(apiName);
        }
      } catch (StatusRuntimeException ex) {
        log.error("Platform exception ...", ex);
        Status status = ex.getStatus();
        String errorMsg = status.getDescription();
        if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
          try {
            createStubs();
          } catch (Exception e) {
            log.error("Reconnect channel failed..");
            break;
          }
        }
        Thread.sleep(BUSY_RETRY_MS);
        continue;
      }

      if (!ResponseCodeEnum.BUSY.equals(response.getNodeTransactionPrecheckCode())) {
        break;
      }
      Thread.sleep(BUSY_RETRY_MS);
    }
    return response;
  }

  private Response retryLoopQuery(Query query, String apiName)
      throws InterruptedException {
    Response response = null;
    for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {

      if (i > 0) {
        log.info("retrying api call " + apiName);
      }
      ResponseCodeEnum precheckCode;
      try {
        switch (apiName) {
          case "getContractInfo":
            response = scstub.getContractInfo(query);
            precheckCode = response.getContractGetInfo()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "contractCallLocalMethod":
            response = scstub.contractCallLocalMethod(query);
            precheckCode = response.getContractCallLocal()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "contractGetBytecode":
            response = scstub.contractGetBytecode(query);
            precheckCode = response.getContractGetBytecodeResponse()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "getBySolidityID":
            response = scstub.getBySolidityID(query);
            precheckCode = response.getGetBySolidityID()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "getTxRecordByContractID":
            response = scstub.getTxRecordByContractID(query);
            precheckCode = response.getContractGetRecordsResponse()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          case "getTxRecordByTxID":
            // The use of cstub here, instead of scstub, is deliberate.
            response = cstub.getTxRecordByTxID(query);
            precheckCode = response.getTransactionGetRecord()
                .getHeader().getNodeTransactionPrecheckCode();
            break;
          default:
            throw new IllegalArgumentException(apiName);
        }
      } catch (StatusRuntimeException ex) {
        log.error("Platform exception ...", ex);
        Status status = ex.getStatus();
        String errorMsg = status.getDescription();
        if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
          try {
            createStubs();
          } catch (Exception e) {
            log.error("Reconnect channel failed..");
            break;
          }
        }
        Thread.sleep(BUSY_RETRY_MS);
        continue;
      }

      if (!ResponseCodeEnum.BUSY.equals(precheckCode)) {
        break;
      }
      Thread.sleep(BUSY_RETRY_MS);
    }
    return response;
  }
}
