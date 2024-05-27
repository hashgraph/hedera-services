/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.hedera.remote;

import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.suites.TargetNetworkType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.List;

/**
 * A network of Hedera nodes already running on remote processes and accessed via gRPC.
 */
public class RemoteNetwork implements HederaNetwork {
    @NonNull
    @Override
    public Response send(
            @NonNull Query query, @NonNull HederaFunctionality functionality, @NonNull AccountID nodeAccountId) {
        return null;
    }

    @Override
    public TargetNetworkType type() {
        return null;
    }

    @Override
    public List<HederaNode> nodes() {
        return List.of();
    }

    @Override
    public List<HederaNode> nodesFor(@NonNull NodeSelector selector) {
        return List.of();
    }

    @Override
    public HederaNode getRequiredNode(@NonNull NodeSelector selector) {
        return null;
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public void start() {}

    @Override
    public void terminate() {}

    @Override
    public void awaitReady(@NonNull Duration timeout) {}
}
