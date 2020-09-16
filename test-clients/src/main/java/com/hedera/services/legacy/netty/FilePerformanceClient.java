package com.hedera.services.legacy.netty;

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

import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.Utilities;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A Client that tests file service performance.
 *
 * @author Hua Li
 */

public class FilePerformanceClient {

  private static final Logger log = LogManager.getLogger(FilePerformanceClient.class);

  /**
   * Runs the performance client.
   * 
   * @param args support up to nine arguments in the following order:
   * [host] [nodeAccNum] [port] [batchSize] [threadsPerContainer] [TPS_perThread] [getReceipts] [markerWindowSize] [RetryFrequencyMillis]
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

    long nodeAccount = Utilities.getDefaultNodeAccount();
    if ((args.length) > 1) {
      try {
        nodeAccount = Long.parseLong(args[1]);
        log.info("node account: " + nodeAccount);
      } catch (Exception ex) {
        log.warn("Invalid data passed for node id, use default node account: " + nodeAccount);
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

    String batchStr = properties.getProperty("BATCH_SIZE");
    if ((args.length) > 3) {
      batchStr = args[3];
    }     
    int batchSize = 1000; // 500000;
    try {
      batchSize = Integer.parseInt(batchStr);
      log.info("batch size: " + batchSize);
    } catch (NumberFormatException e) {
      log.warn("Error parsing batch size string: " + batchStr + ", set to default value " + batchSize);
    }

    int tCounts = 5;
    if ((args.length) > 4) {
      tCounts = Integer.parseInt(args[4]);
    }
    log.info("Spawning " + tCounts + " Threads per container");
    
    // tpsDesired is the TPS per thread, actual it would be tCounts x 5 x number clients
    int maxTpsDesired = 20;
    if ((args.length) > 5) {
      maxTpsDesired = Integer.parseInt(args[5]);
    }
    log.info("TPS per thread: " + maxTpsDesired);
    
    boolean retrieveTxReceipt = false;
    if ((args.length) > 6) {
      retrieveTxReceipt = Boolean.parseBoolean(args[6]);
    }
    log.info("retrieveTxReceipt: " + retrieveTxReceipt);
    
    //  in terms of number of Tx has executed.
    int markerWindow = 10000;
    if ((args.length) > 7) {
      markerWindow = Integer.parseInt(args[7]);
    }
    log.info("Marker window for fetch receipts: " + markerWindow);
    
    // Retry frequency in millisec for getting receipts
    long retryFreq = 1000; // 1 sec
    if ((args.length) > 8) {
      retryFreq = Long.parseLong(args[8]);
    }
    log.info("Retry frequency in millisec for getting receipts: " + retryFreq);

    boolean isExponentialBackoff = false;
    if ((args.length) > 9) {
      isExponentialBackoff = Boolean.parseBoolean(args[9]);
    }
    log.info("Apply exponential backoff: " + isExponentialBackoff);
    
    for (int i = 0; i < tCounts; i++) {
      FilePerformanceThread perf = new FilePerformanceThread(port, host, batchSize,
          retrieveTxReceipt, nodeAccount, maxTpsDesired);
      perf.setMarkerWindow(markerWindow);
      perf.setRetryFreq(retryFreq);
      perf.setExpBackoff(isExponentialBackoff);
      perf.start();
    }

    log.info("****** RUN THREADS RUN ********");
  }
}
