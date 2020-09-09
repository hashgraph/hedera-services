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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.legacy.proto.utils.AtomicCounter;
import com.hedera.services.legacy.proto.utils.CommonUtils;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * A runnable object that executes a random Hedera service API.
 *
 * @author Hua Li Created on 2018-11-19
 */
public class UmbrellaServiceRunnable implements Runnable {

  private static final Logger log = LogManager.getLogger(UmbrellaServiceRunnable.class);
  protected static final String ALL_API_TAG = "ALL_API";
  protected static boolean IS_FIRST_TIME = true;
  protected int taskNum = 0;
  protected SmartContractServiceTest fit = null;
  protected Random random = new Random();
  private int GRPC_UNAVAILABLE_WAIT_SEC = 1;
  protected static int FILE_CREATE_SIZE = 1000;
  protected static Map<String, AtomicCounter> activeSubmitCountMap = new ConcurrentHashMap<>();
  protected static Map<String, AtomicCounter> activeFailCountMap = new ConcurrentHashMap<>();
  protected static Map<String, Integer> allLimitMap = new LinkedHashMap<>();
  protected static Set<String> activeAPIs = null;
  public static boolean isSmallAccount = false;

  protected static enum ops {
    fileUpload, fileGetInfo, fileGetContent, fileUpdate, fileDelete, cryptoCreate, cryptoTransfer, cryptoUpdate,
    cryptoGetInfo, cryptoGetBalance, cryptoGetRecords, createContract, updateContract, contractCallMethod,
    contractCallLocalMethod, contractGetBytecode, getContractInfo, getBySolidityID, getTxRecordByContractID,
    getTxReceipt, getTxFastRecord, getTxRecord, fileCreate, fileAppend
  }

  public UmbrellaServiceRunnable(int taskNum, SmartContractServiceTest fit) {
    this.taskNum = taskNum;
    this.fit = fit;
  }

  /**
   * Initialize active APIs and counters.
   *
   * @param allLimitMap2 mapping of active APIs and their call limits
   */
  public static void init(Map<String, Integer> allLimitMap2) {
    allLimitMap = allLimitMap2;
    UmbrellaServiceRunnable.activeAPIs = new HashSet<String>();

    for (String op : allLimitMap.keySet()) {
      if (allLimitMap.get(op) != 0) {
        activeAPIs.add(op);
        activeSubmitCountMap.put(op, new AtomicCounter());
        activeFailCountMap.put(op, new AtomicCounter());
      }
    }

    activeSubmitCountMap.put(ALL_API_TAG, new AtomicCounter());
    activeFailCountMap.put(ALL_API_TAG, new AtomicCounter());

    log.info("initial activeSubmitCountMap={}", activeSubmitCountMap);
  }

  @Override
  public void run() {
    AccountID payerID = FileServiceTest.getRandomPayerAccount();
    AccountID nodeID = FileServiceTest.getRandomNodeAccount();

    int[] op_and_total_counts = new int[2];
    ops op = getRandomOp(op_and_total_counts);
    if (op == null) {
      if (IS_FIRST_TIME) {
        log.info("activeSubmitCountMap={}", activeSubmitCountMap);
        log.info("activeFailCountMap={}", activeFailCountMap);
        log.info("No more active APIs.");
        IS_FIRST_TIME = false;
      }
      return;
    }

    String msg = "task #" + taskNum + " ...op=" + op + " >>> " + Thread.currentThread().getName();
    log.info("Begin {}", msg);

    exec(op, payerID, nodeID);
    msg += "-> op cnt = " + op_and_total_counts[0] + ", cumulated API call cnt = "
        + op_and_total_counts[1];
    log.info("  End {}\n.. activeSubmitCountMap={}\n.. activeFailCountMap={}\n.. remainingAPIs={}",
            msg, activeSubmitCountMap, activeFailCountMap, activeAPIs);

  }

  /**
   * Executes a single API operation.
   *
   * @param op api operation to be performed
   */
  protected void exec(ops op, AccountID payerID, AccountID nodeID) {
    // exec op based on type
    String opn = op.name();
    switch (op) {
      case fileUpload:
        int fileSizeK = getRandomFileSizeK();
        String fileType = getRandomFileType();
        log.info("\n>>> WORKING ON: fileSizesK={}; fileType={}", fileSizeK, fileType);
        byte[] fileContents;
        try {
          fileContents = fit.genFileContent(fileSizeK, fileType);
          List<Key> waclPubKeyList = fit.genWaclComplex(FileServiceTest.NUM_WACL_KEYS);
          String savePath = fileSizeK + "K." + fileType;
          FileID fid = fit.uploadFile(savePath, fileContents, payerID, nodeID, waclPubKeyList);
          FileServiceTest.fid2waclMap.put(fid, waclPubKeyList);
        } catch (Throwable e) {
          log.warn("fileUpload error: ", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case fileCreate:
        log.info("in fileCreate: file size = {}", FILE_CREATE_SIZE);
        fileContents = new byte[FILE_CREATE_SIZE];
        random.nextBytes(fileContents);
        ByteString fileData = ByteString.copyFrom(fileContents);
        try {
          List<Key> waclPubKeyList = fit.genWaclComplex(FileServiceTest.NUM_WACL_KEYS);
          fit.createFile(payerID, nodeID, fileData, waclPubKeyList, true, CryptoServiceTest.getReceipt);
        } catch (Throwable e) {
          log.warn("fileCreate error: ", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
          handleGrpcException(e);
        }
        break;
      case fileAppend:
        FileID fid = fit.getRandomFileID();
        Entry<FileID, List<Key>> entry = fit
            .removeRandomFileID2waclEntry(); //remove fid so that it's unavailable for other file crud
        log.info("in fileAppend: entry={}", entry);
        if (entry == null) {
          log.warn("fileAppend: no file available!");
          break;
        }
        fid = entry.getKey();
        List<Key> oldWaclPubKeyList = entry.getValue();
        fileContents = new byte[FILE_CREATE_SIZE];
        random.nextBytes(fileContents);
        fileData = ByteString.copyFrom(fileContents);
        
        try {
          fit.appendFile(payerID, nodeID, fid, fileData, oldWaclPubKeyList);
        } catch (Throwable e) {
          log.warn("fileAppend error: ", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
          handleGrpcException(e);
        }
        FileServiceTest.fid2waclMap.put(fid, oldWaclPubKeyList); //make fid available for file crud
        break;
      case fileGetInfo:
        fid = fit.getRandomFileID();
        log.info("in fileGetInfo: fid={}", fid);
        if (fid == null) {
          log.warn("fileGetInfo: no file available!");
          break;
        }

        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:fileGetInfo");
          fit.getFileInfo(fid, payerID, nodeID);
//          stopWatch.stop();
        } catch (Throwable e) {
          if(FileServiceTest.fid2waclMap.containsKey(fid)){
            incFailCountIfClientProbablyNotToBlameFor(opn, e);
          }
          log.warn("fileGetInfo error! fid={}, payerID={}, nodeID={}: {}  ",
                  fid, payerID, nodeID, e);
          handleGrpcException(e);
        }
        break;
      case fileGetContent:
        fid = fit.getRandomFileID();
        log.info("in fileGetContent: fid={}", fid);
        if (fid == null) {
          log.warn("fileGetContent: no file available!");
          break;
        }

        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:fileGetContent");
          fit.getFileContent(fid, payerID, nodeID);
//          stopWatch.stop();
        } catch (Throwable e) {
          log.warn("fileGetContent error! fid={}, payerID={}, nodeID={}: {}  ",
                  fid, payerID, nodeID, e);

          incFailCountIfClientProbablyNotToBlameFor(opn, e);
          handleGrpcException(e);
        }
        break;
      case fileUpdate:
        fileType = getRandomFileType();
        entry = fit
            .removeRandomFileID2waclEntry(); //remove fid so that it's unavailable for other file crud
        log.info("in fileUpdate: entry={}", entry);
        if (entry == null) {
          log.warn("fileUpdate: no file available!");
          break;
        }

        fid = entry.getKey();
        oldWaclPubKeyList = entry.getValue();
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:fileUpdate");
          List<Key> newWaclPubKeyList = fit.genWaclComplex(FileServiceTest.NUM_WACL_KEYS);
          fit.updateFile(fid, fileType, payerID, nodeID, oldWaclPubKeyList, newWaclPubKeyList);
//          stopWatch.stop();
          FileServiceTest.fid2waclMap
              .put(fid, newWaclPubKeyList); //make fid available for file crud
        } catch (Throwable e) {
          log.warn("fileUpdate error! Error message = ", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
          handleGrpcException(e);
        }
        break;
      case fileDelete:
        entry = fit
            .removeRandomFileID2waclEntry(); //remove fid so that it's unavailable for other file crud
        log.info("in fileDelete: entry={}", entry);
        if (entry == null) {
          log.warn("fileDelete: no file available!");
          break;
        }

        fid = entry.getKey();
        List<Key> waclPubKeyList = entry.getValue();
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:fileDelete");
          fit.deleteFile(fid, payerID, nodeID, waclPubKeyList);
//          stopWatch.stop();
          // commenting the line out as file delete flag has been implemented
//          FileServiceTest.fid2waclMap.put(fid, waclPubKeyList); //make fid available for file crud
          FileServiceTest.fid2waclMap.remove(fid);
        } catch (Throwable e) {
          log.warn("fileDelete error!", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
          handleGrpcException(e);
        }
        break;
      case cryptoCreate:
//			payerID = fit.getGenesisAccountID(); //use genesis to create new account
        isSmallAccount = true;
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:cryptoCreate");
          AccountID accountId = fit.createAccount(payerID, nodeID, true);
//          stopWatch.stop();
          if(CryptoServiceTest.getReceipt) {
			  log.info("account created: account = {}", accountId);
		  }
        } catch (Throwable e) {
          log.warn("cryptoCreate error!", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        isSmallAccount = false;
        break;
      case getTxReceipt:
//        TransactionID txId = fit.cache.getRandomTransactionID4Receipt();
        TransactionID txId = fit.cache.pollOldestTransactionID4Receipt();
        if (txId == null) {
          log.warn("getTxReceipt: no txId available!");
          break;
        }
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:getTxReceipt");
          TransactionReceipt receipt = fit.getTxReceipt(txId);
//          stopWatch.stop();
          log.info("getTxReceipt: txId = {} ==> receipt = {}", txId, receipt);
        } catch (Throwable e) {
          log.warn("getTxReceipt error!", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case getTxFastRecord:
//        txId = fit.cache.getRandomTransactionID4Receipt();
        txId = fit.cache.pollOldestTransactionID4Receipt();
        if (txId == null) {
          log.warn("getTxFastRecord: no txId available!");
          break;
        }
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:getTxFastRecord");
          TransactionReceipt receipt = fit.getTxFastRecord(txId);
//          stopWatch.stop();
          log.info("getTxFastRecord: txId = {} ==> receipt = {}", txId, receipt);

        } catch (Throwable e) {
          log.warn("getTxFastRecord error!", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case getTxRecord:
//        txId = fit.cache.getRandomTransactionID4Record();
        txId = fit.cache.pollOldestTransactionID4Record();
        if (txId == null) {
          log.warn("getTxRecord: no txId available!");
          break;
        }
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:getTxRecord");
          TransactionRecord record = fit.getTransactionRecord(txId, payerID, nodeID);
//          stopWatch.stop();
          log.info("getTxRecord: txId = {} ==> record = {}", txId, record);
        } catch (Throwable e) {
          log.warn("getTxRecord error!", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case cryptoTransfer:
        AccountID fromAccountID = FileServiceTest.getRandomTransferAccount();
        AccountID toAccountID = FileServiceTest.getRandomTransferAccount();

        if (fromAccountID.equals(toAccountID) && FileServiceTest.transferAccounts.length == 2) {
          fromAccountID = FileServiceTest.transferAccounts[0];
          toAccountID = FileServiceTest.transferAccounts[1];
        }

        if (fromAccountID.equals(toAccountID)) {
          log.warn("Ignore transfer because from account is the same as to account: fromAccountID={}, toAccountID={}",
                  fromAccountID, toAccountID);
          break;
        }

        long amount = CryptoServiceTest.getRandomTransferAmount();
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:cryptoTransfer");
          TransactionReceipt receipt = fit.transfer(payerID, nodeID, fromAccountID, toAccountID, amount);
//          if(CryptoServiceTest.getReceipt & ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name()) ) {
//            boolean fromAccFlag = CryptoServiceTest.uplodateLocalLedger(fromAccountID, -amount);
//            boolean toAccFlag = CryptoServiceTest.uplodateLocalLedger(toAccountID, amount);
//            if (!fromAccFlag || !toAccFlag) {
//              incFailCountIfClientProbablyNotToBlameFor(op.toString());
//            }
//          }
//          stopWatch.stop();
        } catch (Throwable e) {
          log.warn("cryptoTransfer error!", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case cryptoGetBalance:
        AccountID accountID = FileServiceTest.getRandomPayerAccount();
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:cryptoGetBalance");
          long balance = fit.getAccountBalance(accountID, payerID, nodeID);
//          stopWatch.stop();
          log.info("cryptoGetBalance: balance = {}", balance);
        } catch (Throwable e) {
          log.warn("cryptoGetBalance error!", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case cryptoGetRecords:
        accountID = FileServiceTest.getRandomPayerAccount();
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:cryptoGetRecords");
          List<TransactionRecord> records = fit
              .getTransactionRecordsByAccountId(accountID, payerID, nodeID);
//          stopWatch.stop();
          log.info("cryptoGetRecords: records = {}", records);
        } catch (Throwable e) {
          log.warn("cryptoGetRecords error!", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case cryptoGetInfo:
        accountID = FileServiceTest.getRandomPayerAccount();
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:cryptoGetInfo");
          AccountInfo accInfo = fit.getAccountInfo(accountID, payerID, nodeID);
//          stopWatch.stop();
          log.info("cryptoGetInfo: info = {}", accInfo);
        } catch (Throwable e) {
          log.warn("cryptoGetInfo error!", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case cryptoUpdate:
        try {
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:cryptoUpdate");
          AccountInfo accInfo = null;
//		        int coinFlip = CryptoServiceTest.rand.nextInt(2);
//		        if(coinFlip == 0) {
          // update auto renew period
          accountID = FileServiceTest.getRandomPayerAccount();
          if (accountID == null) {
            log.warn("cryptoUpdate account auto renew: no accountID available!");
            break;
          }
          accInfo = fit.updateAccount(accountID, payerID, nodeID);
//		    		// update account key
//		        	accountID = fit.getRandomNonPayerAccount();
//		        	if(accountID == null) {
//						log.warn("cryptoUpdate account key: no accountID available!");
//						break;
//		    		}
//		    		String accountKeyType = fit.getRandomAccountKeyType();
//		    		Key newKey = fit.genComplexKey(accountKeyType);
//					accInfo = fit.updateAccountKey(accountID, payerID, nodeID, newKey);
//		        }

//          stopWatch.stop();
          if(CryptoServiceTest.getReceipt) {
			  log.info("cryptoUpdate: updated account info = {}", accInfo);
		  }
        } catch (Throwable e) {
          log.warn("cryptoUpdate error!", e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
//		case cryptoDelete: //service not implemented
//			accountID = ServiceRegressionTestsIT.getRandomPayerAccount();
//			AccountID transferAccountID = fit.getGenesisAccountID();
//			try {
//		        StopWatch stopWatch = new Log4JStopWatch("RoundTrip:cryptoDelete");
//		        TransactionReceipt receipt = fit.deleteAccount(accountID, transferAccountID, payerID, nodeID);
//				stopWatch.stop();
//				log.info("cryptoDelete: receipt = " + receipt);
//				counter.increment(); // for account update itself
//			} catch (InvalidKeySpecException | DecoderException e) {
//				log.warn("cryptoDelete error!", e);
//			}
//			break;
      case createContract:
        String fileName = fit.getRandomSmartContractFile();
        try {
          File file = new File(fileName);
          if(!file.exists()) {
            fileName = "src/main/resource/" + fileName;
          }
          Path path = Paths.get(fileName);
          byte[] bytes = Files.readAllBytes(path);
          log.info("createContract: upload contract file at: {}; size={}", fileName, bytes.length);
          List<Key> waclKeys = fit.genWaclComplex(FileServiceTest.NUM_WACL_KEYS);
          String savePath = "saved" + FileSystems.getDefault().getSeparator() + fileName;
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:createContract");
//          StopWatch stopWatchInner = new Log4JStopWatch("RoundTrip:fileUpload");
          FileID contractFid = fit.uploadFile(savePath, bytes, payerID, nodeID, waclKeys);
//          stopWatchInner.stop();

          ContractID contractID = fit.createContract(contractFid, fileName, payerID, nodeID, true);
//          stopWatch.stop();
          if(CryptoServiceTest.getReceipt) {
			  log.info("createContract: contractID={}", contractID);
		  }
        } catch (Throwable e) {
          log.warn("createContract error! contract file name={}", fileName, e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case updateContract:
        ContractIDMapEntry toReplace = SmartContractServiceTest.takeRandomSmartContract();
        ContractID contractId = (toReplace == null) ? null : toReplace.getId();
        try {
          if (toReplace == null) {
            log.warn("updateContract: no smart contract available!");
            break;
          }

//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:updateContract");
          TransactionReceipt receipt = fit.updateContract(payerID, contractId, nodeID);
//          stopWatch.stop();
          if(CryptoServiceTest.getReceipt) {
			  log.info("updateContract: receipt = {}", receipt);
		  }
        } catch (Throwable e) {
          log.warn("updateContract error! contractId={}", contractId, e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        if (toReplace != null) {
          SmartContractServiceTest.replaceSmartContract(toReplace);
        }
        break;
      case contractCallMethod:
        contractId = SmartContractServiceTest.getRandomSmartContractID();
        String contractFile = null;
        try {
          if (contractId == null) {
            log.warn("contractCallMethod: no smart contract available!");
            break;
          }
          contractFile = SmartContractServiceTest.contractIDMap.get(contractId);
          int valueToSet =
              FileServiceTest.rand.nextInt(SmartContractServiceTest.CONTRACT_CALL_VALUE_BOUND) + 1;
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:contractCallMethod");
          //set value to simple storage smart contract
          fit.callContract(payerID, nodeID, contractId, valueToSet);
//          stopWatch.stop();
          log.info("contractCallMethod: contractId={}; contractFile={}; valueToSet={}",
                  contractId, contractFile, valueToSet);

        } catch (Throwable e) {
          log.warn("contractCallMethod error! contractId={}; contractFile={}",
                  contractId, contractFile, e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case contractCallLocalMethod:
        contractId = SmartContractServiceTest.getRandomSmartContractID();
        contractFile = null;
        try {
          if (contractId == null) {
            log.warn("contractCallLocalMethod: no smart contract available!");
            break;
          }
          contractFile = SmartContractServiceTest.contractIDMap.get(contractId);
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:contractCallLocalMethod");
          int result = fit.callContractLocal(payerID, nodeID, contractId);
//          stopWatch.stop();
          log.info("contractCallLocalMethod: contractId={}; contractFile={}; result={}",
                  contractId, contractFile, result);
        } catch (Throwable e) {
          log.warn("contractCallLocalMethod error! contractId={}; contractFile={} ",
                  contractId, contractFile, e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case contractGetBytecode:
        contractId = SmartContractServiceTest.getRandomSmartContractID();
        try {
          if (contractId == null) {
            log.warn("contractGetBytecode: no smart contract available!");
            break;
          }
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:contractGetBytecode");
          String retData = fit.getContractByteCode(payerID, contractId, nodeID);
//          stopWatch.stop();
          if (retData.length() == 0) {
            log.warn("contractGetBytecode: returned data = {}", retData);
          } else {
            log.info("contractGetBytecode: returned data = {}", retData);
          }
        } catch (Throwable e) {
          log.warn("contractGetBytecode error! contractId={}", contractId, e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case getContractInfo:
        contractId = SmartContractServiceTest.getRandomSmartContractID();
        try {
          if (contractId == null) {
            log.warn("getContractInfo: no smart contract available!");
            break;
          }
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:getContractInfo");
          ContractInfo retData = fit.getContractInfo(payerID, contractId, nodeID);
//          stopWatch.stop();
          if (retData == null) {
            log.warn("getContractInfo: returned data = {}", retData);
          } else {
            log.info("getContractInfo: returned data = {}", retData);
          }
        } catch (Throwable e) {
          log.warn("getContractInfo error! contractId={}", contractId, e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case getBySolidityID:
        String solidityId = SmartContractServiceTest.getRandomSmartSolidityID();
        try {
          if (solidityId == null) {
            log.warn("getBySolidityID: no smart contract available!");
            break;
          }
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:getBySolidityID");
          ContractID retData = fit.getBySolidityID(payerID, solidityId, nodeID);
//          stopWatch.stop();
          log.info("getBySolidityID: solidity ID = {}, returned contract ID = {}",
              solidityId, retData);
        } catch (Throwable e) {
          log.warn("getBySolidityID error! solidity ID={}", solidityId, e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      case getTxRecordByContractID:
        contractId = SmartContractServiceTest.getRandomSmartContractID();
        try {
          if (contractId == null) {
            log.warn("getTxRecordByContractID: no smart contract available!");
            break;
          }
//          StopWatch stopWatch = new Log4JStopWatch("RoundTrip:getTxRecordByContractID");
          List<TransactionRecord> retData = fit
              .getTxRecordByContractID(payerID, contractId, nodeID);
//          stopWatch.stop();
          log.info(
              "getTxRecordByContractID: contractId={}; returned data = ", contractId, retData);
        } catch (Throwable e) {
          log.warn("getTxRecordByContractID error! contractId={}", contractId, e);
          incFailCountIfClientProbablyNotToBlameFor(opn, e);
        }
        break;
      default:
        break;
    }
  }

  /**
   * Handles GRPC exceptions, in particular, wait some time if GRPC throws UNAVAILABLE exception.
   * 
   * @param e
   */
  private void handleGrpcException(Throwable e) {
    if (e.getMessage() != null && e.getMessage().contains("UNAVAILABLE")) {
//      log.error("GRPC UNAVAILABLE DUE TO PLATFORM");
      try {
        CommonUtils.nap(GRPC_UNAVAILABLE_WAIT_SEC);
      } catch (Exception e1) {
        log.error("Error when handling GrpcException. ", e1);
      }
    }
  }

  /**
   * Increase API call count by 1.
   *
   * @param op API call to be incremented
   */
  protected void incOpCount(String op) {
    activeSubmitCountMap.get(op).increment();
  }

  /**
   * Gets the current API call count.
   *
   * @param op API call
   * @return count performed
   */
  protected int getOpCount(String op) {
    int rv = activeSubmitCountMap.get(op).value();
    return rv;
  }

  /**
   * Gets the random file size.
   */
  private int getRandomFileSizeK() {
    int index = FileServiceTest.rand.nextInt(FileServiceTest.fileSizesK.length);
    return FileServiceTest.fileSizesK[index];
  }

  /**
   * Gets the random file size.
   */
  private String getRandomFileType() {
    int index = FileServiceTest.rand.nextInt(FileServiceTest.fileTypes.length);
    return FileServiceTest.fileTypes[index];
  }

  private void incFailCountIfClientProbablyNotToBlameFor(String op, Throwable t) {
    if (!isLikelyClientError(t)) {
      incFailCount(op);
    }
  }

  private boolean isLikelyClientError(Throwable t) {
    return (t instanceof java.security.InvalidKeyException);
  }

  /**
   * Increase API call failure count by 1.
   *
   * @param op API call and total call to be incremented
   */
  private void incFailCount(String op) {
    synchronized (activeFailCountMap) {
      if (activeFailCountMap.containsKey(op)) {
        activeFailCountMap.get(op).increment();
        activeFailCountMap.get(ALL_API_TAG).increment();
      }
    }
  }

  /**
   * Gets a random operation.
   *
   * @param op_and_total_counts 2-element array tracks op count and total API count, respectively
   * @return a a random operation
   */
  protected ops getRandomOp(int[] op_and_total_counts) {
    synchronized (activeAPIs) {
      ops op = null;
      if (activeAPIs.isEmpty()) {
        return null;
      }

      String[] opstrs = activeAPIs.toArray(new String[0]);
      int index = FileServiceTest.rand.nextInt(opstrs.length);
      String opstr = opstrs[index];
      op = ops.valueOf(opstr);

      String opn = op.name();
      // check limit
      int limit = allLimitMap.get(opn);
      int count = getOpCount(opn);
      if ((limit >= 0) && (count >= (limit - 1))) {
        log.info("API reached limit: op = {}, limit = {}, #calls = {}", opn, limit, count);
        // remove api from active list
        activeAPIs.remove(opn);
      }
      incOpCount(opn);
      incOpCount(ALL_API_TAG);
      op_and_total_counts[0] = getOpCount(opn);
      op_and_total_counts[1] = getOpCount(ALL_API_TAG);
      return op;
    }
  }

  /**
   * Check if there is any more active API.
   *
   * @return true if no more active API, false otherwise
   */
  public static boolean isActiveAPIEmpty() {
    synchronized (activeAPIs) {
      boolean rv = false;
      rv = activeAPIs.isEmpty();
      return rv;
    }
  }
}
