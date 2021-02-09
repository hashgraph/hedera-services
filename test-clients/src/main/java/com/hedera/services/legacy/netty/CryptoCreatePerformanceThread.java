package com.hedera.services.legacy.netty;

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

import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.Utilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

/**
 * Executes N number of CryptoCreatePerformance Threads
 *
 * @author oc
 */
public class CryptoCreatePerformanceThread {

    private String host;
    private int port;
    private static long nodeAccount;
    private static final Logger log = LogManager.getLogger(CryptoCreatePerformanceThread.class);

    /**
     * arg[0] hostIp
     * arg[1] nodeAccount
     * arg[2] retriveReceipts
     * arg[3] Threads
     * @param args
     */
    public static void main(String [] args) {

        boolean retrieveTxReceipt = true;
        boolean retrieveRecords = false;
        int numTransfer = 1000;
        int numThreads = 1;
        String host;
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
                log.error("Using Defaults for Threads",ex);
            }

        }

        if ((args.length) > 5) {
            try{
                retrieveRecords = Boolean.parseBoolean(args[5]);}
            catch(Exception ex){
                log.error("Using Defaults for Retrieve records as True",ex);
            }

        }

        int port = Integer.parseInt(properties.getProperty("port"));
        log.info("Connecting host = " + host + "; port = " + port);

        log.info("Spawning " + numThreads + " Threads per container");
        CryptoCreatePerformance perfThread;
        for (int i=0;i<numThreads;i++) {
            perfThread = new CryptoCreatePerformance(port, host, numTransfer, retrieveTxReceipt,nodeAccount,retrieveRecords);
            perfThread.start();
        }

        log.info("****** Process COMPLETE ********");
    }
}
