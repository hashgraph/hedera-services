/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.stats;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.HashSet;
import java.util.Set;

public abstract class OpObs {
    private long responseLatency;
    private boolean accepted;
    private Set<String> tags = new HashSet<>();

    abstract HederaFunctionality functionality();

    public Set<String> getTags() {
        return tags;
    }

    public boolean wasAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public long getResponseLatency() {
        return responseLatency;
    }

    public void setResponseLatency(long responseLatency) {
        this.responseLatency = responseLatency;
    }

    @Override
    public String toString() {
        return functionality().toString();
    }
}
