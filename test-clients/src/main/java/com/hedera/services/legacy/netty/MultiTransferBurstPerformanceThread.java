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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

/**
 * Temp util class for inintiating many threads
 *
 * @author oc
 */

public class MultiTransferBurstPerformanceThread {

    private String host;
    private int port;
    private static long nodeAccount;
    private static final Logger log = LogManager.getLogger("BurstModeT");

    public static void main(String [] args) {
        String host;
        boolean retrieveTxReceipt = true;
        boolean retrieveRecords = false;
        int numTransfer = 1000;
        int numThreads = 1;
        int tpsDesired = 20;
        int numAccountsIterations =100;

        Properties properties = TestHelper.getApplicationProperties();

        if ((args.length) > 0) {
            host = args[0];
        }
        else
        {
            host =  properties.getProperty("host");
        }

        if ((args.length) > 1) {
            try {
                nodeAccount = Long.parseLong(args[1]);
            }
            catch(Exception ex){
                log.info("Invalid data passed for node id");
                nodeAccount = Utilities.getDefaultNodeAccount();
            }
        }
        else
        {
            nodeAccount = Utilities.getDefaultNodeAccount();
        }

        if ((args.length) > 2) {
            try{
                retrieveTxReceipt = Boolean.parseBoolean(args[2]);}
            catch(Exception ex){
                log.error("Using Defaults for Retrieve Receipt as True",ex);
            }

        }

        if ((args.length) > 3) {
            try{
                numThreads = Integer.parseInt(args[3]);}
            catch(Exception ex){
                log.error("Using Defaults for Threads",ex);
            }

        }

        if ((args.length) > 4) {
            try{
                numTransfer = Integer.parseInt(args[4]);}
            catch(Exception ex){
                log.error("Using Defaults for NumTransfers",ex);
            }

        }

        if ((args.length) > 5) {
            try{
                tpsDesired = Integer.parseInt(args[5]);}
            catch(Exception ex){
                log.error("Using Defaults for TPS",ex);
            }

        }

        if ((args.length) > 6) {
            try{
                retrieveRecords = Boolean.parseBoolean(args[6]);}
            catch(Exception ex){
                log.error("Using Defaults for Retrieve records as True",ex);
            }

        }

        if ((args.length) > 7) {
            try{
                numAccountsIterations = Integer.parseInt(args[7]);}
            catch(Exception ex){
                log.error("Using Defaults for numAccountsIterations",ex);
            }

        }
        int port = Integer.parseInt(properties.getProperty("port"));
        log.info("Connecting host = " + host + "; port = " + port);

        log.info("Spawning " + numThreads +" Threads per container");
        //tpsDesired is the TPS per thread, actual it would be tCounts x 5 x number clients

        for(int i=0 ; i< numThreads ; i++)
        {
           MultiAccountBurstTransferSeqThread perf1 = new MultiAccountBurstTransferSeqThread(port, host, numTransfer, retrieveTxReceipt,retrieveRecords, nodeAccount,tpsDesired,numAccountsIterations);
           perf1.start();
        }

        log.warn("****** GO THREADS GO ********");
    }
}
