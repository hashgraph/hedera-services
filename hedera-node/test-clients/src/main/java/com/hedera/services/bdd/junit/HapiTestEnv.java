/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.services.bdd.junit;

import java.util.List;
import java.util.concurrent.TimeoutException;

public interface HapiTestEnv {
    String[] NODE_NAMES = new String[] {"Alice", "Bob", "Carol", "Dave"};
    int CLUSTER_SIZE = 4;
    int FIRST_GOSSIP_PORT = 60000;
    int FIRST_GRPC_PORT = 50211;
    int FIRST_GOSSIP_TLS_PORT = 60001;
    int CAPTIVE_NODE_STARTUP_TIME_LIMIT = 300;

    void start() throws TimeoutException;

    void terminate();

    boolean started();

    String getNodeInfo();

    List<HapiTestNode> getNodes();
}
