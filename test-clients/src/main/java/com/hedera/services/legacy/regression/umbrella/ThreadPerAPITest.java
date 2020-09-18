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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.AtomicCounter;

/**
 * All calls of the same Hedera API are run by a dedicated thread. For example, one use case is to
 * have one thread creating accounts, a second thread getting receipts, and a third thread
 * retrieving records.
 *
 * upgraded ThreadPerAPITest to run Hedera API multithreaded performance tests with a dedicated
 * thread to retrieve transaction receipts. It can be used for testing File, Smart contract, and
 * Crypto requests (both transactions and queries). At end of the test, it prints ther targeted TPS,
 * actual client TPS, and actual server TPS. The test is intended to run with command line arguments
 * as follows: 
 * [host]: node IP address 
 * [nodeAccNum]: node account number
 * [port]: node port 
 * [op|batchSize[,op|batchSize]]: a list of comma separated API and its batch size pairs, e.g. for single transaction API: fileCreate|100 or for a single query API: fileGetInfo|1000, or for mixed APIs like fileCreate|100,fileGetInfo|100 
 * [threadsPerContainer] : Each API will have these many threads to run on 
 * [TPS_perThread]: targeted TPS per thread 
 * [getReceipts]: whether or not get all receipts in the dedicated thread 
 * [markerWindowSize]: not used right now. 
 * [RetryFrequencyMillis]: retry wait interval when trying to get receipt 
 * [isExponentialBackoff]: whether or not to use exponential backoff for the retry wait time
 * 
 * Some example argument lists: 
 * localhost 3 50211 cryptoCreate|100 2 4 true 1000 50 false 
 * localhost 3 50211 cryptoGetInfo|1000 2 4 true 1000 50 false 
 * localhost 3 50211 fileCreate|100 2 4 true 1000 50 false 
 * localhost 3 50211 createContract|100,updateContract|100,contractCallMethod|200 2 4 true 100 50 false
 * 
 * @author Hua Li Created on 2018-11-19
 */
public class ThreadPerAPITest extends UmbrellaTest {
  private static String DEFAULT_NODE_ACCOUNT_ID_STR = "0.0.3";
  private static final Logger log = LogManager.getLogger(ThreadPerAPITest.class);

  /**
   * Runs tests with multiple threads where all calls of the same Hedera API are run by a dedicated
   * thread.
   * @throws Throwable 
   */
  public static void runMultiThreadedTests(String host, int port, long nodeAccount,
        String op_batchStr, int threadCounts, int maxTpsDesiredPerThread, boolean retrieveTxReceipt,
        int markerWindow, long retryFreq, boolean isExponentialBackoff) throws Throwable {

    if(op_batchStr != null) {
      // use command line to config test
      initTest(op_batchStr, threadCounts, maxTpsDesiredPerThread);
	} else {
      // use umbrellaTest.properties to config
      initTest();
	}
    SmartContractServiceTest rt = new SmartContractServiceTest(null);
    int apiCnt = UmbrellaServiceRunnable.activeAPIs.size(); // non get receipt apis
    if(UmbrellaServiceRunnable.activeAPIs.contains(UmbrellaServiceRunnable.ops.getTxReceipt.name())) {
		apiCnt -= 1;
	}
    log.info("Creating Executor Service with a thread pool of Size " + NUM_THREADS);
    ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS * apiCnt + 1);

    rt.setUp(host, port, nodeAccount, retryFreq, isExponentialBackoff);
    
    CryptoServiceTest.payerAccounts = rt.accountCreatBatch4Payer(CryptoServiceTest.numPayerAccounts); // accounts as payers
    
    if(UmbrellaServiceRunnable.activeAPIs.contains(UmbrellaServiceRunnable.ops.cryptoTransfer.name())) {
      CryptoServiceTest.transferAccounts = rt.accountCreatBatch(CryptoServiceTest.numTransferAccounts); // accounts for transfer's from and to parties
      CryptoServiceTest.initLocalLedgerWithAccountBalances();
    }      

    int numTestFiles = 0; // test files for file ops other than creation
    if(UmbrellaServiceRunnable.activeAPIs.contains(UmbrellaServiceRunnable.ops.fileAppend.name())) {
		numTestFiles++;
	}
    if(UmbrellaServiceRunnable.activeAPIs.contains(UmbrellaServiceRunnable.ops.fileUpdate.name())) {
		numTestFiles++;
	}
    if(UmbrellaServiceRunnable.activeAPIs.contains(UmbrellaServiceRunnable.ops.fileGetContent.name())) {
		numTestFiles++;
	}
    if(UmbrellaServiceRunnable.activeAPIs.contains(UmbrellaServiceRunnable.ops.fileGetInfo.name())) {
		numTestFiles++;
	}
    if(UmbrellaServiceRunnable.activeAPIs.contains(UmbrellaServiceRunnable.ops.fileDelete.name())) {
		numTestFiles += UmbrellaServiceRunnable.allLimitMap.get(UmbrellaServiceRunnable.ops.fileDelete.name()); // create enough files for delete API calls
	}
    
    numTestFiles *= NUM_THREADS;
      
    if(numTestFiles > 0) {
		rt.fileCreateBatch(numTestFiles, UmbrellaServiceRunnable.FILE_CREATE_SIZE);
	}

    String[] iter = UmbrellaServiceRunnable.activeAPIs.toArray(new String[1]);

    long startTime = System.currentTimeMillis();
    int requestCnts = 0;
    int txCnts = 0;
    int thdId = 0;
    for(int j = 0; j < NUM_THREADS; j++) {
      for(int i = 0; i < iter.length; i++) {
        String opstr = iter[i];
        UmbrellaServiceRunnable.ops op = UmbrellaServiceRunnable.ops.valueOf(opstr);
        int limit = UmbrellaServiceRunnable.allLimitMap.get(opstr);

        if(op.equals(UmbrellaServiceRunnable.ops.getTxReceipt)) {
			continue;
		}
        
        if(isTransactionAPI(op)) {
			txCnts += limit;
		}
        requestCnts += limit;
        Runnable task = new ThreadPerAPIRunnable(op, limit, ++thdId, rt);
        executorService.submit(task);
        log.info("Started thread #" + thdId  + " for API " + opstr);
      }
    }
    
    if(retrieveTxReceipt) {
      ThreadPerAPIRunnable task = new ThreadPerAPIRunnable(UmbrellaServiceRunnable.ops.getTxReceipt, txCnts, ++thdId, rt);
      task.setMarkerWindow(markerWindow);
      UmbrellaServiceRunnable.activeSubmitCountMap.put(UmbrellaServiceRunnable.ops.getTxReceipt.name(), new AtomicCounter());
      UmbrellaServiceRunnable.activeFailCountMap.put(UmbrellaServiceRunnable.ops.getTxReceipt.name(), new AtomicCounter());
      executorService.submit(task);
      log.info("Started thread #" + thdId  + " for API " + UmbrellaServiceRunnable.ops.getTxReceipt.name());
    }
    
    executorService.shutdown();
    executorService.awaitTermination(48, TimeUnit.HOURS);
    
    //get the last transaction receipt
    TransactionID lastTxID = rt.cache.getLastTxID();
    if(lastTxID != null) {
      TransactionReceipt receipt = rt.getTxReceipt(lastTxID);
      log.info("Last Transaction ID = " + lastTxID + "; receipt = " + receipt);
    }
    
    long endTime = System.currentTimeMillis();
    long totalTime = endTime  - startTime;
    double tps = (requestCnts * 1.0D / totalTime) * 1000;

    long clientSubmissionEndTime = endTime;
    if(lastTxID != null) {
		clientSubmissionEndTime = rt.cache.getLastTxIDSubmitTimeMillis();
	}
    totalTime = clientSubmissionEndTime - startTime;
    double clientTps = (requestCnts * 1.0D / totalTime) * 1000;
    log.info("****** Server TPS = " + tps + "; client actual TPS = " + clientTps + "; target TPS = " + REQ_PER_SEC * NUM_THREADS * apiCnt);
  }

  /**
   * Check if an API call is a transaction (i.e. not a query).
   * @param op
   * @return true if the API is a transaction, false otherwise
   */
  private static boolean isTransactionAPI(UmbrellaServiceRunnable.ops op) {
    if(op.equals(UmbrellaServiceRunnable.ops.fileUpload)
        || op.equals(UmbrellaServiceRunnable.ops.fileCreate)
        || op.equals(UmbrellaServiceRunnable.ops.fileAppend)
        || op.equals(UmbrellaServiceRunnable.ops.fileUpdate)
        || op.equals(UmbrellaServiceRunnable.ops.fileDelete)
        || op.equals(UmbrellaServiceRunnable.ops.cryptoCreate)
        || op.equals(UmbrellaServiceRunnable.ops.cryptoTransfer)
        || op.equals(UmbrellaServiceRunnable.ops.cryptoUpdate)
        || op.equals(UmbrellaServiceRunnable.ops.createContract)
        || op.equals(UmbrellaServiceRunnable.ops.updateContract)
        || op.equals(UmbrellaServiceRunnable.ops.contractCallMethod)
        ) {
		return true;
	} else {
		return false;
	}
  }

  /**
   * Runs the performance client.
   * 
   * @param args support up to nine arguments in the following order:
   * [host] [nodeAccNum] [port] [op|batchSize] [threadsPerContainer] [TPS_perThread] [getReceipts] [markerWindowSize] [RetryFrequencyMillis] [isExponentialBackoff]
   * e.g. localhost 3 50211 cryptoTransfer|20 2 4 true 1000 50 false
   * @throws Throwable
   */
  public static void main(String[] args) throws Throwable {
    String host;
    Properties properties = TestHelper.getApplicationProperties();
  
    if ((args.length) > 0) {
      host = args[0];
    } else {
      host = properties.getProperty("host");
    }
  
    long nodeAccount = Long.parseLong(DEFAULT_NODE_ACCOUNT_ID_STR.substring(DEFAULT_NODE_ACCOUNT_ID_STR.lastIndexOf('.') + 1));
    if ((args.length) > 1) {
      try {
        nodeAccount = Long.parseLong(args[1]);
        log.info("node account: " + nodeAccount);
      } catch (Exception ex) {
        log.warn("Invalid data passed for node id, use default node account: " + nodeAccount, ex);
      }
    } else {
      String nodeAccIDStr = properties
          .getProperty("defaultListeningNodeAccount", DEFAULT_NODE_ACCOUNT_ID_STR);
      try {
        nodeAccount = Long.parseLong(nodeAccIDStr.substring(nodeAccIDStr.lastIndexOf('.') + 1));
      } catch (NumberFormatException e) {
        log.warn("incorrect format of defaultListeningNodeAccount in application.properties, using default nodeAccountId=" + DEFAULT_NODE_ACCOUNT_ID_STR,
                e);
      }
    }
  
    String portStr = properties.getProperty("port");
    if ((args.length) > 2) {
      portStr = args[2];
    }
    int port = 50211;
    try {
      port = Integer.parseInt(portStr);
    } catch (NumberFormatException e) {
      log.warn("Error parsing port string: " + portStr + ", set to default value " + port);
    }
    log.info("Connecting host = " + host + "; port = " + port);
  
    String op_batchStr = "fileDelete|500";// "fileCreate|20,fileAppend|20,fileUpdate|20,fileGetInfo|20,fileGetContent|20";
    if ((args.length) > 3) {
      op_batchStr = args[3];
    }     
    log.info("op batch str: " + op_batchStr);
  
    int threadCounts = 2;
    if ((args.length) > 4) {
      threadCounts = Integer.parseInt(args[4]);
    }
    log.info("Spawning " + threadCounts + " Threads per container");
    
    int maxTpsDesiredPerThread = 4;
    if ((args.length) > 5) {
      maxTpsDesiredPerThread = Integer.parseInt(args[5]);
    }
    log.info("TPS per thread: " + maxTpsDesiredPerThread);
    
    boolean retrieveTxReceipt = true;
    if ((args.length) > 6) {
      retrieveTxReceipt = Boolean.parseBoolean(args[6]);
    }
    log.info("retrieveTxReceipt: " + retrieveTxReceipt);
    
    //  in terms of number of Tx has executed.
    int markerWindow = 100;
    if ((args.length) > 7) {
      markerWindow = Integer.parseInt(args[7]);
    }
    log.info("Marker window for fetch receipts: " + markerWindow);
    
    // Retry frequency in millisec for getting receipts
    long retryFreq = 100; // 1 sec
    if ((args.length) > 8) {
      retryFreq = Long.parseLong(args[8]);
    }
    log.info("Retry frequency in millisec for getting receipts: " + retryFreq);
  
    boolean isExponentialBackoff = false;
    if ((args.length) > 9) {
      isExponentialBackoff = Boolean.parseBoolean(args[9]);
    }
    log.info("Apply exponential backoff: " + isExponentialBackoff);
    runMultiThreadedTests(host, port, nodeAccount, op_batchStr, threadCounts, maxTpsDesiredPerThread, retrieveTxReceipt, markerWindow, retryFreq, isExponentialBackoff);
  }

  /**
   * Initialize the test
   * @param threadCounts 
   * @param maxTpsDesiredPerThread 
   */
  protected static void initTest(String singleInclusionsStr, int threadCounts, int maxTpsDesiredPerThread) throws Exception {
    NUM_THREADS = threadCounts;
    REQ_PER_SEC = maxTpsDesiredPerThread;
    String[] singleInclusions = singleInclusionsStr.split(FileServiceTest.CONFIG_LIST_SEPARATOR);
    if (singleInclusions.length == 1 && singleInclusions[0].isEmpty()) {// Not specified in config.
      singleInclusions = new String[0];
    }
    Map<String, Integer> allLimitMap = new HashMap<>();
  
    //add single inclusions to active api list, overwrite existing values from group
    Properties properties = TestHelper.getApplicationProperties();
    String sizeStr = properties.getProperty("BATCH_SIZE");
    int limit = Integer.parseInt(sizeStr);
  
    for (int i = 0; i < singleInclusions.length; i++) {
      String entry = singleInclusions[i];
      String[] pair = entry.split(PAIR_SEPARATOR);
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

}
