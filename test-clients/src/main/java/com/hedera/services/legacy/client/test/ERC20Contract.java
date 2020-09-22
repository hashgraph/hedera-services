package com.hedera.services.legacy.client.test;

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

import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;


/**
 * Create ERC20 Contract, distributed tokens to new account
 * random transfer between account, keep tracking of token balance
 *
 * Contract created by thread0, then all thread randomly create
 * some accounts and start transfer among newly created accounts
 */
public class ERC20Contract extends ClientBaseThread {


  private final static Logger log = LogManager.getLogger(ERC20Contract.class);
  private static final String TOKEN_ERC20_CONSTRUCTOR_ABI = "{\"inputs\":[{\"name\":\"initialSupply\",\"type\":\"uint256\"},{\"name\":\"tokenName\",\"type\":\"string\"},{\"name\":\"tokenSymbol\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
  private static final String BALANCE_OF_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String DECIMALS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"decimals\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String TRANSFER_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String APPROVE_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_spender\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String TRANSFER_FROM_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_from\",\"type\":\"address\"},{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String SYMBOL_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"symbol\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";


  public static final long GAS_REQUIRED_GET = 25_000L;


  private static long localCallGas;

  private int numberOfTransfers = 1;
  private int TPS_TARGET = 10;
  private int numberOfAccounts = 20;  //number of token account creating

  private long initialBalance = TestHelper.getCryptoMaxFee() * 10L;

  private Map<AccountID, String> tokenOwners = new HashMap<>();
  private long contractDuration;

  private ArrayList<AccountID> accountsList = new ArrayList<>();

  /** keep track of account balance for checking later */
  private Map<AccountID, Long> accountBalance = new HashMap<>();

  // Shared variables amount all threads
  private static ContractID ocTokenContract;
  private static AccountID tokenIssuer;
  private static KeyPair tokenIssuerKeyPair;

  public ERC20Contract(String host, int port, long nodeAccountNumber, String [] args, int index)
  {
    super(host, port, nodeAccountNumber, args, index);
    this.nodeAccountNumber = nodeAccountNumber;
    this.host = host;
    this.port = port;

    if ((args.length) > 0) {
      numberOfTransfers = Integer.parseInt(args[0]);
      log.info("Got numberOfTransfers as " + numberOfTransfers);
    }

    if ((args.length) > 1) {
      TPS_TARGET = Integer.parseInt(args[1]);
      log.info("Got TPS target as " + TPS_TARGET);
    }

    if ((args.length) > 2) {
      numberOfAccounts = Integer.parseInt(args[2]);
      log.info("Got numberOfAccounts as " + numberOfAccounts);
    }

    try {
      initAccountsAndChannels();

      Properties properties = TestHelper.getApplicationProperties();

      contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
      localCallGas = Long.parseLong(properties.getProperty("LOCAL_CALL_GAS"));

      // only thread 0 creates contract
      // then later all threads, including thread 0, can create account and do token transfer
      if(index == 0){

        tokenIssuerKeyPair = new KeyPairGenerator().generateKeyPair();
        Common.addKeyMap(tokenIssuerKeyPair, pubKey2privKeyMap);

        // Create the token issuing account
        tokenIssuer = Utilities
                .createSingleAccountAndReturnID(genesisAccount, nodeAccountNumber, 0L, 0L,
                        initialBalance,
                        genesisPrivateKey, stub, tokenIssuerKeyPair);
        accountKeys.put(tokenIssuer, Collections.singletonList(tokenIssuerKeyPair.getPrivate()));

        Assert.assertNotNull(tokenIssuer);
        Assert.assertNotEquals(0, tokenIssuer.getAccountNum());
        log.info("Token Issuer account created: " + tokenIssuer);

        // Create the contract
        String contractFileName = "octoken.bin";
        long initialTokenSupply = 10_000_000_000L;

        List<PrivateKey> keyList = new ArrayList<>();
        keyList.add(tokenIssuerKeyPair.getPrivate());

        FileID ocTokenCode = LargeFileUploadIT.uploadFile(tokenIssuer, contractFileName, keyList,
                host, nodeAccount);
        ocTokenContract = createTokenContract(tokenIssuer, ocTokenCode, initialTokenSupply,
                "OpenCrowd Token", "OCT"+nodeAccountNumber);
        Assert.assertNotNull(ocTokenContract);
        Assert.assertNotEquals(0, ocTokenContract.getContractNum());
        log.info("@@@ Contract Address is  " + ocTokenContract.toString());

      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  void demo() throws Exception {

    try {
      //only thread 0 created tokenIssuerKeyPair, but all thread need to added the key to their maps
      // so later be used for signing transactions
      Common.addKeyMap(tokenIssuerKeyPair, pubKey2privKeyMap);
      accountKeys.put(tokenIssuer, Collections.singletonList(tokenIssuerKeyPair.getPrivate()));

      long initialTokens = 200_000;

      // 1) Create test accounts (with the same keypair) and give them tokens
      long start = System.nanoTime();
      for (int accounts = 0; accounts < numberOfAccounts; accounts++) {
        try{
        AccountID newAccount = Utilities
                .createSingleAccountAndReturnID(genesisAccount, nodeAccountNumber, 0L, 0L,
                        initialBalance,
                        genesisPrivateKey, stub, tokenIssuerKeyPair);

        Assert.assertNotEquals(0, newAccount.getAccountNum());
        String newEthAddress =
                CommonUtils.calculateSolidityAddress(0, 0L, newAccount.getAccountNum());
        tokenOwners.put(newAccount, newEthAddress);

        accountKeys.put(newAccount, Collections.singletonList(tokenIssuerKeyPair.getPrivate()));

        accountsList.add(newAccount);

        //only last one in for loop wait for results
        transfer(ocTokenContract, tokenIssuer, newEthAddress, initialTokens, (accounts+1) == numberOfAccounts);
        accountBalance.put(newAccount, initialTokens);

        log.info("---------- Account " + accounts + " initialized: " +
                newAccount.getAccountNum());
        } catch (StatusRuntimeException e) {
          if (!tryReconnect(e)) {
			  return;
		  }
        }
      }

      checkBalances(ocTokenContract, tokenOwners, tokenIssuer);

      long end = System.nanoTime();
      long elapsedMillis = (end - start) / 1_000_000;
      log.info("Creating and priming " + numberOfAccounts + " accounts took " +
              elapsedMillis / 1000.0 + " seconds");


      // 2) Do random transfers
      int size = accountsList.size();
      long transferSize = 10;
      long accumulatedTransferCount = 0;
      long startTime = System.currentTimeMillis();

      for (int i = 0; i < numberOfTransfers; i++) {
        try {
          int from = ThreadLocalRandom.current().nextInt(size);
          // Be sure to pick a different index
          int to = (from + 1 + ThreadLocalRandom.current().nextInt(size - 1)) % size;

          AccountID fromId = accountsList.get(from);
          AccountID toId = accountsList.get(to);
          String toEthAddress = tokenOwners.get(toId);
          log.info("Transfer " + i + " from " + fromId.getAccountNum() + " to "
                  + toId.getAccountNum());
          transfer(ocTokenContract, fromId, toEthAddress, transferSize, (i + 1) == numberOfTransfers);
          accountBalance.put(fromId, accountBalance.get(fromId) - transferSize);
          accountBalance.put(toId, accountBalance.get(toId) + transferSize);

          accumulatedTransferCount++;
          float currentTPS = Common.tpsControl(startTime, accumulatedTransferCount, TPS_TARGET);

          if ((accumulatedTransferCount % 100) == 0) {
            log.info("{} currentTPS {}", getName(), currentTPS);
          }
        } catch (StatusRuntimeException e) {
          if (!tryReconnect(e)) {
			  return;
		  }
        }
      }

      // 3) Check balance
      while(!checkBalances(ocTokenContract, tokenOwners, tokenIssuer)){
        //if balance is incorrect try again
        log.error("balance check failed, try again");
        sleep(5000);
      }

    }finally {
      log.info("Test ending");
    }
  }


  private static CallTransaction.Function getTransferFunction() {
    String funcJson = TRANSFER_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeTransfer(String toAccountAddress, long valueToTransfer) {
    String retVal = "";
    CallTransaction.Function function = getTransferFunction();
    byte[] encodedFunc = function.encode(toAccountAddress, valueToTransfer);

    return encodedFunc;
  }

  private void transfer(ContractID contractId, AccountID payerAccount,
          String toAccountAddress, long valueToTransfer, boolean waitResult) throws Exception {

    byte[] dataToSet = encodeTransfer(toAccountAddress, valueToTransfer);
    //set value to simple storage smart contract

    // call contract
    TransactionID txID = callContract(payerAccount, contractId, dataToSet, 0, 250000);

    if(waitResult) {
      // wait receipt ready then get record
      TransactionGetReceiptResponse contractCallReceipt = Common.getReceiptByTransactionId(stub, txID);

      TransactionRecord trRecord = getTransactionRecord(payerAccount, txID, false);
      if (trRecord != null && trRecord.hasContractCallResult()) {
        ContractFunctionResult callResults = trRecord.getContractCallResult();
        String errMsg = callResults.getErrorMessage();
        if (StringUtils.isEmpty(errMsg)) {
          if (!callResults.getContractCallResult().isEmpty()) {
            byte[] dataToReturn = callResults.getContractCallResult().toByteArray();
            log.info("Contract call result {}", HexUtils.bytes2Hex(dataToReturn));
          }
        } else {
          log.info("@@@ Contract Call resulted in error: " + errMsg);
        }
      }
    }
  }

  private static CallTransaction.Function getConstructorFunction() {
    String funcJson = TOKEN_ERC20_CONSTRUCTOR_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] getEncodedConstructor(long initialSupply, String tokenName,
          String tokenSymbol) {
    String retStr = "";
    CallTransaction.Function func = getConstructorFunction();
    byte[] encodedFunc = func.encodeArguments(initialSupply, tokenName, tokenSymbol);

    return encodedFunc;
  }

  private ContractID createTokenContract(AccountID payerAccount, FileID contractFile,
          long initialTokensSupply, String tokenName, String tokenSymbol) throws Exception {
    byte[] constructorData = getEncodedConstructor(initialTokensSupply, tokenName, tokenSymbol);
    TransactionID txID = createContractOnly(payerAccount, contractFile, constructorData, contractDuration, null);
    ContractID createdContract = Common.getContractIDfromReceipt(stub, genesisAccount, txID);
    return createdContract;
  }

  private static CallTransaction.Function getDecimalsFunction() {
    String funcJson = DECIMALS_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeDecimals() {
    String retVal = "";
    CallTransaction.Function function = getDecimalsFunction();
    byte[] encodedFunc = function.encode();

    return encodedFunc;
  }

  private static long decodeDecimalsResult(byte[] value) {
    long decodedReturnedValue = 0;
    CallTransaction.Function function = getDecimalsFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.longValue();
    }
    return decodedReturnedValue;
  }

  public long decimals(ContractID contractAddress, AccountID payer) throws Exception {
    long decimalsToReturn = 0;

    byte[] dataEncodeDecimals = encodeDecimals();

    byte[] valueOfDecimals = callContractLocalGetResult(payer, contractAddress, dataEncodeDecimals, GAS_REQUIRED_GET, localCallGas);
    //decode value from results
    decimalsToReturn = decodeDecimalsResult(valueOfDecimals);
    return decimalsToReturn;

  }

  private boolean checkBalances(ContractID contractAddress, Map<AccountID, String> tokenOwners, AccountID payer) throws Exception {
    Map<AccountID, Long> balancePerOwner = new HashMap<>(tokenOwners.size());
    for (AccountID tokenOwner : tokenOwners.keySet()) {
      String tokenOwnerAccountEthFormat = tokenOwners.get(tokenOwner);
      long balance = balanceOf(contractAddress, tokenOwnerAccountEthFormat, payer);
      balancePerOwner.put(tokenOwner, balance);

    }
    log.info(
            "---------------------------------------------balances-----------------------------------------------------------");
    for (AccountID currentTokenOwner : balancePerOwner.keySet()) {
      long currBalance = balancePerOwner.get(currentTokenOwner);
      long expectedBalance = accountBalance.get(currentTokenOwner);
      log.info("accountNum {} has a balance of {} expected {}", currentTokenOwner.getAccountNum(), currBalance, expectedBalance);
      if(currBalance != expectedBalance) {
        log.error("Expected balance {} actual balance {}", expectedBalance, currBalance);
        return false;
      }

    }
    log.info(
            "----------------------------------------------------------------------------------------------------------------");
    return true;
  }


  public long balanceOf(ContractID contractAddress, String accountAdddressEthFormat,
          AccountID payer) throws Exception {
    long balance = 0;
    byte[] dataEncodeBalanceOF = encodeBalanceOf(accountAdddressEthFormat);

    byte[] valueOfBalance = callContractLocalGetResult(payer, contractAddress, dataEncodeBalanceOF, GAS_REQUIRED_GET, localCallGas);
    if (valueOfBalance != null) {
      //decode value from results
      balance = decodeBalanceOfResult(valueOfBalance);
    }
    return balance;

  }

  private static CallTransaction.Function getBalanceOfFunction() {
    String funcJson = BALANCE_OF_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeBalanceOf(String address) {
    String retVal = "";
    CallTransaction.Function function = getBalanceOfFunction();

    byte[] encodedFunc = function.encode(address);

    return encodedFunc;
  }

  private static long decodeBalanceOfResult(byte[] value) {
    long decodedReturnedValue = 0;
    CallTransaction.Function function = getBalanceOfFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.longValue();
    }
    return decodedReturnedValue;
  }


  private static CallTransaction.Function getSymbolFunction() {
    String funcJson = SYMBOL_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private static byte[] encodeSymbol() {
    String retVal = "";
    CallTransaction.Function function = getSymbolFunction();

    byte[] encodedFunc = function.encode();

    return encodedFunc;
  }

  private static String decodeSymbolResult(byte[] value) {
    String decodedReturnedValue = "";
    CallTransaction.Function function = getSymbolFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      decodedReturnedValue = (String) retResults[0];
    }
    return decodedReturnedValue;
  }

  public String symbol(ContractID contractAddress, AccountID payer) throws Exception {
    String symbolToReturn;
    byte[] dataEncodeSymbol = encodeSymbol();

    byte[] valueOfSymbol = callContractLocalGetResult(payer, contractAddress, dataEncodeSymbol, GAS_REQUIRED_GET, localCallGas);
    //decode value from results
    symbolToReturn = decodeSymbolResult(valueOfSymbol);
    return symbolToReturn;
  }

}
