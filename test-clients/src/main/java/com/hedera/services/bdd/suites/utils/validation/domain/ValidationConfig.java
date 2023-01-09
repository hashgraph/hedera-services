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
package com.hedera.services.bdd.suites.utils.validation.domain;

import java.util.Map;

public class ValidationConfig {
    public static final long DEFAULT_SLEEP_MS_BEFORE_NEXT_NODE = 5_000L;

    Long sleepMsBeforeNextNode = DEFAULT_SLEEP_MS_BEFORE_NEXT_NODE;
    Map<String, Network> networks;

    public Map<String, Network> getNetworks() {
        return networks;
    }

    public void setNetworks(Map<String, Network> networks) {
        this.networks = networks;
    }

    public Long getSleepMsBeforeNextNode() {
        return sleepMsBeforeNextNode;
    }

    public void setSleepMsBeforeNextNode(Long sleepMsBeforeNextNode) {
        this.sleepMsBeforeNextNode = sleepMsBeforeNextNode;
    }
}
