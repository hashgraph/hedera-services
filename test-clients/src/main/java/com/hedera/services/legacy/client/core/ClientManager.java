package com.hedera.services.legacy.client.core;

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


import com.hedera.services.legacy.client.test.ClientBaseThread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.util.Arrays;

/**
 * ClientManager use arguments to dynamically instantiate test case thread
 */
public class ClientManager {

  private static final Logger log = LogManager.getLogger(ClientManager.class);

  private static int numberOfThreads = 1;

  private static final String PACKAGE_NAME_PREFIX = "com.hedera.services.legacy.client.test.";

  public static void main(String [] args)
          throws Exception {

    // extract test case class name from command line arguments
    // use Java reflection to get constructor of the specific test class
    String className = args[0];
    Class class1 = Class.forName(PACKAGE_NAME_PREFIX + className);

    Constructor<?>[] constructor = class1.getConstructors();

    numberOfThreads = Integer.parseInt(args[1]);

    final String host = args[2];
    final int port = Integer.parseInt(args[3]);
    final long nodeAccountNumber = Integer.parseInt(args[4]);

    ClientBaseThread[] threadClients = new ClientBaseThread[numberOfThreads];

    // remained arguments are passed to instantiated test
    String [] threadArgs = Arrays.copyOfRange(args, 5, args.length);

    // Dynamically instantiate test case thread and pass arguments to it
    for (int k = 0; k < numberOfThreads; k++){
      threadClients[k] = (ClientBaseThread) constructor[0].newInstance(host, port, nodeAccountNumber, threadArgs, k);
      threadClients[k].setName("thread" + k);
    }

    for (int k = 0; k < numberOfThreads; k++) {
      threadClients[k].start();
    }
    for (int k = 0; k < numberOfThreads; k++) {
      threadClients[k].join();
    }
  }
}
