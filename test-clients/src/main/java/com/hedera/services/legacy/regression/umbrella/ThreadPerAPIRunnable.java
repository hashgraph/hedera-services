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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.legacy.proto.utils.CommonUtils;

/**
 * A runnable object that executes a fixed Hedera service API.
 *
 * @author hua
 */
public class ThreadPerAPIRunnable extends UmbrellaServiceRunnable {

  private static final Logger log = LogManager.getLogger(ThreadPerAPIRunnable.class);
  private ops op = null;
  private int numCalls = 0;
  private int markerWindow = 1; // how often to get receipts

  public ThreadPerAPIRunnable(ops op, int numCalls, int taskNum, SmartContractServiceTest fit) {
    super(taskNum, fit);
    this.op = op;
    this.numCalls = numCalls;
  }

  @Override
  public void run()  {
    int txSubmitted = 0;
    long startTime = System.currentTimeMillis();
    for (int i = 0; (numCalls == -1 || i < numCalls); i++) {
      AccountID payerID = FileServiceTest.getRandomPayerAccount();
      AccountID nodeID = FileServiceTest.getRandomNodeAccount();

      String msg = "task #" + taskNum + " ...op=" + op + " >>> " + Thread.currentThread().getName();
      log.info("Begin {} ", msg);

      txSubmitted++;
      boolean shouldSumbit = true;
      if (op == ops.getTxReceipt) { 
        if(txSubmitted % markerWindow == 0) {
          shouldSumbit = true;
        } else {
          fit.cache.pollOldestTransactionID4Receipt();
          shouldSumbit = false;
        }
      }

      if (shouldSumbit) {
        exec(op, payerID, nodeID);
        incOpCount(op.name());
        incOpCount(ALL_API_TAG);
        msg +=
            "-> op cnt = " + txSubmitted + ", cumulated API call cnt = " + getOpCount(ALL_API_TAG);
        log.info( " End {}\n.. activeSubmitCountMap={}\n.. activeFailCountMap={}",
                msg, activeSubmitCountMap, activeFailCountMap);
        if (op != ops.getTxReceipt) {
          if (txSubmitted % UmbrellaTest.REQ_PER_SEC == 0) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            long time2Nap = 1000 - elapsedTime;

            if (time2Nap > 0) {
              log.info("time to nap: op = {}, txSubmitted = {}, time2Nap = {}, REQ_PER_SEC = {}",
                      op, txSubmitted, time2Nap, UmbrellaTest.REQ_PER_SEC);
              try {
                CommonUtils.napMillis(time2Nap);
              } catch (Exception e) {
                log.warn("Exception caught while napping: ", e);
              }
            } else {
              log.warn("No time to nap: op = {}, txSubmitted = {}, time2Nap = {}, REQ_PER_SEC = {}",
                      op, txSubmitted, time2Nap, UmbrellaTest.REQ_PER_SEC);

            }
            startTime = System.currentTimeMillis();
          }
        }
      }
    }
    
    String msg = "Finished thread #" + taskNum + ": op=" + op + ", actual calls = " + txSubmitted + ", expected calls = " + numCalls;
    if(txSubmitted == numCalls) {
		log.info("==> :) Success: {}", msg);
	} else {
		log.warn("==> (: Not all calls finished: {}", msg);
	}
  }

  /**
   * Sets how often to get receipts.
   * 
   * @param markerWindow how many receipt calls to skip
   */
  public void setMarkerWindow(int markerWindow) {
    this.markerWindow = markerWindow;
  }
}
