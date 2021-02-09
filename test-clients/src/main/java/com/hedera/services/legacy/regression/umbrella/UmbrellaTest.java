package com.hedera.services.legacy.regression.umbrella;

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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.*;
import com.hedera.services.legacy.core.CustomProperties;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.FileServiceIT;
import com.hedera.services.legacy.proto.utils.AtomicCounter;
import com.hedera.services.legacy.proto.utils.CommonUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of a comprehensive test framework for Hedera services. It supports the following
 * features: 1. covers all Hedera APIs 2. multiple concurrent threads for request submission 3.
 * configurable properties for controlling tests 4. randomization of request parameters
 *
 * @author hua Created on 2018-10-31
 */
public class UmbrellaTest {

  private static final Logger log = LogManager.getLogger(UmbrellaTest.class);
  private static final Integer DEFAULT_LIMIT = -1;
  protected static int NUM_THREADS = 5;
  protected static int REQ_PER_SEC = 10;
  protected static String testConfigFilePath = "config/umbrellaTest.properties";
  private static final String[] groupAPIs = {"cryptoAPI", "fileAPI", "contractAPI"};
  protected static final String PAIR_SEPARATOR = "\\|\\s*";
  private static Map<String, Map<String, Integer>> allGroupMap = new LinkedHashMap<>();
  private static final double FAIL_TOLERANCE = 0.001;

  /**
   * Runs tests where each thread executes a single Hedera API call.
   */
  public static void runMultiThreadedTests() throws Throwable {
    runMultiThreadedTests(null, null);
  }
    
  /**
   * Runs tests where each thread executes a single Hedera API call.
   * 
   * @param host Receiving host IP, if null, default in application properties will be used
   * @param nodeAccount Receiving host node account number, if null, default in application properties will be used
   * @throws Throwable
   */
  public static void runMultiThreadedTests(String host, Long nodeAccount) throws Throwable {
    initTest();

    log.info("Creating Executor Service with a thread pool of Size " + NUM_THREADS);
    ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS);
    SmartContractServiceTest rt = new SmartContractServiceTest(testConfigFilePath);
    rt.setUp(host, nodeAccount);
    if (rt.changeGenesisKey) {
      rt.changeGenesisKey();
    }
    UmbrellaServiceRunnable.isSmallAccount=false;
    CryptoServiceTest.payerAccounts = rt.accountCreatBatch4Payer(CryptoServiceTest.numPayerAccounts); // accounts as payers
    UmbrellaServiceRunnable.isSmallAccount=true;
    CryptoServiceTest.transferAccounts = rt.accountCreatBatch(CryptoServiceTest.numTransferAccounts); // accounts for transfer's from and to parties
    UmbrellaServiceRunnable.isSmallAccount=false;
    // get all pre-run account balances
    Map<String, Long> prePayerAccountsTotalBalance = rt.getBalances(CryptoServiceTest.payerAccounts);
    Map<String, Long> preTransferAccountsTotalBalance = rt.getBalances(CryptoServiceTest.transferAccounts);
//    CryptoServiceTest.initLocalLedgerWithAccountBalances();
    int maxTxCnt = -1;
    if (!(CryptoServiceTest.MAX_REQUESTS_IN_K == -1)) {
      maxTxCnt = (int) (CryptoServiceTest.MAX_REQUESTS_IN_K * CryptoServiceTest.K);
    }

    for (int i = 0; (maxTxCnt == -1) || (i < maxTxCnt); i++) {
      if (UmbrellaServiceRunnable.isActiveAPIEmpty()) {
        break;
      }
      Runnable task = new UmbrellaServiceRunnable(i + 1, rt);
      executorService.submit(task);
      log.info("Submitted request #" + (i + 1) + " for execution...");
      CommonUtils.napMillis(1000 / REQ_PER_SEC);
    }

    executorService.shutdown();
    executorService.awaitTermination(48, TimeUnit.HOURS);
    
    //get the last transaction receipt
    TransactionID lastTxID = rt.cache.getLastTxID();
    TransactionReceipt receipt = rt.getTxReceipt(lastTxID);
    log.info("Last Transaction ID = " + lastTxID + "; receipt = " + receipt);

    // get all post-run account balances
    Map<String, Long> postPayerAccountsTotalBalance = rt.getBalances(CryptoServiceTest.payerAccounts);
    Map<String, Long> postTransferAccountsTotalBalance = rt.getBalances(CryptoServiceTest.transferAccounts);
    
    log.info("payer balances:  pre-run = " + prePayerAccountsTotalBalance);
    log.info("payer balances: post-run = " + postPayerAccountsTotalBalance);
    log.info("transfer account balances:  pre-run = " + preTransferAccountsTotalBalance);
    log.info("transfer account balances: post-run = " + postTransferAccountsTotalBalance);
    
    if(preTransferAccountsTotalBalance.get("total").equals(postTransferAccountsTotalBalance.get("total"))) {
      log.info(":) pre- and post- total balance of all transfer accounts match.");
    } else {
      log.warn("(: pre- and post- total balance of all transfer accounts DO NOT match!");
    }
    
    if (rt.changeGenesisKey) {
      rt.revertGenesisKey();
    }

    int totalFailCount = UmbrellaServiceRunnable.activeFailCountMap.get("ALL_API").value();
    log.info(totalFailCount + " :: is the fail count map size");

    int tolerableFailCount = (int)(FAIL_TOLERANCE * UmbrellaServiceRunnable.activeSubmitCountMap.get("ALL_API").value());
    printCountMaps(UmbrellaServiceRunnable.activeSubmitCountMap,UmbrellaServiceRunnable.activeFailCountMap);
    cleanupAllCreatedAccounts(postPayerAccountsTotalBalance, rt);
    cleanupAllCreatedAccounts(postTransferAccountsTotalBalance, rt);

    if (totalFailCount > tolerableFailCount) {
      System.exit(TestHelper.getErrorReturnCode());
    }
  }

  private static void cleanupAllCreatedAccounts(Map<String, Long> map, SmartContractServiceTest rt) throws Throwable{
    for(String accNo: map.keySet()) {
      long lngAccNo = 0;
      try{
        lngAccNo = Long.parseLong(accNo);
      } catch (Exception e) {
        continue;
      }
      AccountID fromAccountID = AccountID.newBuilder().setAccountNum(lngAccNo).setRealmNum(0).setShardNum(0).build();
      TransactionReceipt transferReceipt = rt.cryptoDelete(fromAccountID,CryptoServiceTest.genesisAccountID, CryptoServiceTest.getDefaultNodeAccount(), TestHelper.getCryptoMaxFee());
//      TransactionReceipt transferReceipt = rt.transfer(CryptoServiceTest.genesisAccountID, CryptoServiceTest.getDefaultNodeAccount(),
//              fromAccountID, CryptoServiceTest.genesisAccountID, postTransferAccountsTotalBalance.get(accNo));
      log.info(accNo+": "+transferReceipt);
    }
  }

  public static void printCountMaps(Map<String, AtomicCounter> submittedCountMap, Map<String, AtomicCounter> failCountMap) {

    int NAME_MAXWIDTH = 25;
    int COL_MAXWIDTH=11;
    StringBuilder table = new StringBuilder("----------------------------------------------\n");
    table.append(StringUtils.rightPad("API Name", NAME_MAXWIDTH)+ "|"+
            StringUtils.rightPad(" Submitted", COL_MAXWIDTH)+ "|"
            + StringUtils.rightPad(" Failed", COL_MAXWIDTH) + "\n");
    table.append("----------------------------------------------\n");
    AtomicCounter allApiSubmittedCount= submittedCountMap.remove("ALL_API");
    AtomicCounter allApiFailedCount= failCountMap.remove("ALL_API");
    if(submittedCountMap.containsKey("updateContract")) {
      submittedCountMap.put("contractUpdate", submittedCountMap.remove("updateContract"));
      failCountMap.put("contractUpdate", failCountMap.remove("updateContract"));
    }
    Set<String> keys = submittedCountMap.keySet();
    keys.stream().sorted().forEach(key->{
      table.append(StringUtils.rightPad(key, NAME_MAXWIDTH) + "|"+
              StringUtils.rightPad(" "+submittedCountMap.get(key), COL_MAXWIDTH)+ "|"
              + StringUtils.rightPad(" "+failCountMap.get(key), COL_MAXWIDTH) + "\n");
    } );
    table.append("----------------------------------------------\n");
    table.append(StringUtils.rightPad("ALL_API", NAME_MAXWIDTH)+ "|"+
            StringUtils.rightPad(" "+allApiSubmittedCount, COL_MAXWIDTH)+ "|"
            + StringUtils.rightPad(" "+allApiFailedCount, COL_MAXWIDTH) + "\n");
    table.append("----------------------------------------------\n");
    System.out.println(table.toString());
    table.insert(0, "Summary:\n");
    log.info(table.toString());
  }
  /**
   * Initialize the test
   */
  protected static void initTest() throws Exception {
    Map<String, Integer> cryptoLimitMap = new LinkedHashMap<>();
    cryptoLimitMap.put("cryptoCreate", DEFAULT_LIMIT);
    cryptoLimitMap.put("cryptoTransfer", DEFAULT_LIMIT);
    cryptoLimitMap.put("cryptoUpdate", DEFAULT_LIMIT);
    cryptoLimitMap.put("cryptoGetInfo", DEFAULT_LIMIT);
    cryptoLimitMap.put("cryptoGetBalance", DEFAULT_LIMIT);
    cryptoLimitMap.put("cryptoGetRecords", DEFAULT_LIMIT);
    cryptoLimitMap.put("getTxReceipt", DEFAULT_LIMIT);
    cryptoLimitMap.put("getTxFastRecord", DEFAULT_LIMIT);
    cryptoLimitMap.put("getTxRecord", DEFAULT_LIMIT);
    allGroupMap.put(groupAPIs[0], cryptoLimitMap);

    Map<String, Integer> fileLimitMap = new LinkedHashMap<>();
    fileLimitMap.put("fileUpload", DEFAULT_LIMIT);
    fileLimitMap.put("fileGetInfo", DEFAULT_LIMIT);
    fileLimitMap.put("fileGetContent", DEFAULT_LIMIT);
    fileLimitMap.put("fileUpdate", DEFAULT_LIMIT);
    fileLimitMap.put("fileDelete", DEFAULT_LIMIT);
    allGroupMap.put(groupAPIs[1], fileLimitMap);

    Map<String, Integer> contractLimitMap = new LinkedHashMap<>();
    contractLimitMap.put("createContract", DEFAULT_LIMIT);
    contractLimitMap.put("updateContract", DEFAULT_LIMIT);
    contractLimitMap.put("contractCallMethod", DEFAULT_LIMIT);
    contractLimitMap.put("contractCallLocalMethod", DEFAULT_LIMIT);
    contractLimitMap.put("contractGetBytecode", DEFAULT_LIMIT);
    contractLimitMap.put("getContractInfo", DEFAULT_LIMIT);
    contractLimitMap.put("getBySolidityID", DEFAULT_LIMIT);
    contractLimitMap.put("getTxRecordByContractID", DEFAULT_LIMIT);
    allGroupMap.put(groupAPIs[2], contractLimitMap);

    CustomProperties testProps = new CustomProperties(testConfigFilePath, false);
    NUM_THREADS = testProps.getInt("numThreads", 5);
    REQ_PER_SEC = testProps.getInt("requestPerSec", 10);
    // get api inclusions and update op list
    String[] singleInclusions = testProps.getString("singleInclusions", "")
        .split(FileServiceTest.CONFIG_LIST_SEPARATOR);
    if (singleInclusions.length == 1 && singleInclusions[0].isEmpty()) {// Not specified in config.
      singleInclusions = new String[0];
    }
    String[] groupInclusions = testProps
        .getString("groupInclusions", "cryptoAPI,fileAPI,contractAPI")
        .split(FileServiceTest.CONFIG_LIST_SEPARATOR);

    Map<String, Integer> allLimitMap = new HashMap<>();
    //add grouop inclusions to active api list
    for (int i = 0; i < groupInclusions.length; i++) {
      String entry = groupInclusions[i];
      String[] pair = entry.split(PAIR_SEPARATOR);
      int limit = DEFAULT_LIMIT;
      if (pair.length == 2) {
        limit = Integer.parseInt(pair[1]);
      }
      String group = pair[0];
      boolean isValidGroup = false;
      for (int k = 0; k < groupAPIs.length; k++) {
        if (group.equals(groupAPIs[k])) {
          Map<String, Integer> groupMap = allGroupMap.get(group);
          Iterator<String> iter = groupMap.keySet().iterator();
          while (iter.hasNext()) {
            String op = iter.next();
            allLimitMap.put(op, limit);
          }
          isValidGroup = true;
          break;
        }
      }

      if (!isValidGroup) {
        throw new Exception(
            "Invalid API group: " + group + "; valid options are: " + Arrays.asList(groupAPIs));
      }
    }

    //add single inclusions to active api list, overwrite existing values from group
    for (int i = 0; i < singleInclusions.length; i++) {
      String entry = singleInclusions[i];
      String[] pair = entry.split(PAIR_SEPARATOR);
      int limit = DEFAULT_LIMIT;
      if (pair.length == 2) {
        limit = Integer.parseInt(pair[1]);
      }
      String api = pair[0];
      UmbrellaServiceRunnable.ops op = UmbrellaServiceRunnable.ops.valueOf(api);
      if (op == null) {
        throw new Exception(
            "Invalid API call: " + api + "; valid options are: " + UmbrellaServiceRunnable.ops
                .values());
      }
      allLimitMap.put(api, limit);
    }

    log.info("Config: API and limits = " + allLimitMap);
    UmbrellaServiceRunnable.init(allLimitMap);
  }

  public static NodeAddressBook getAddressBook() throws Throwable {
    FileServiceTest rt = new FileServiceTest();
    rt.setUp();
    rt.accountCreatBatch(FileServiceTest.numPayerAccounts);

    FileID fid = FileID.newBuilder().setFileNum(FileServiceTest.ADDRESS_FILE_ACCOUNT_NUM)
        .setRealmNum(0).setShardNum(0).build();
    AccountID payerID = FileServiceTest.getRandomPayerAccount();
    ByteString content = rt
        .getFileContent(fid, payerID, FileServiceIT.defaultListeningNodeAccountID);
    NodeAddressBook book = NodeAddressBook.newBuilder().mergeFrom(content.toByteArray()).build();
    return book;
  }

  public static void main(String[] args) throws Throwable {
      String host = null;
      if ((args.length) > 0) {
        host = args[0];
      }
  
      Long nodeAccount = null;
      if ((args.length) > 1) {
        try {
          nodeAccount = Long.parseLong(args[1]);
        }
        catch(Exception ex){
          log.info("Invalid data passed for node id, default node account will be used!");
        }
      }
      
      runMultiThreadedTests(host, nodeAccount);
  }
}
