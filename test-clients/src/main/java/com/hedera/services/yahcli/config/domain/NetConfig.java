/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.yahcli.config.domain;

import static java.util.stream.Collectors.joining;

import com.google.common.base.MoreObjects;
import com.hedera.services.yahcli.output.CommonMessages;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetConfig {
    public static final Integer TRADITIONAL_DEFAULT_NODE_ACCOUNT = 3;

    private String defaultPayer;
    private Integer defaultNodeAccount = TRADITIONAL_DEFAULT_NODE_ACCOUNT;
    private List<Long> allowedReceiverAccountIds;
    private List<NodeConfig> nodes;

    public String getDefaultPayer() {
        return defaultPayer;
    }

    public void setDefaultPayer(String defaultPayer) {
        this.defaultPayer = defaultPayer;
    }

    public Integer getDefaultNodeAccount() {
        return defaultNodeAccount;
    }

    public void setDefaultNodeAccount(Integer defaultNodeAccount) {
        this.defaultNodeAccount = defaultNodeAccount;
    }

    public List<NodeConfig> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeConfig> nodes) {
        this.nodes = nodes;
    }

    public List<Long> getAllowedReceiverAccountIds() {
        return allowedReceiverAccountIds;
    }

    public void setAllowedReceiverAccountIds(List<Long> allowedReceiverAccountIds) {
        this.allowedReceiverAccountIds = allowedReceiverAccountIds;
    }

    public String fqDefaultNodeAccount() {
        return CommonMessages.COMMON_MESSAGES.fq(defaultNodeAccount);
    }

    public Map<String, String> toSpecProperties() {
        Map<String, String> customProps = new HashMap<>();
        customProps.put("nodes", nodes.stream().map(NodeConfig::asNodesItem).collect(joining(",")));
        return customProps;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("defaultPayer", defaultPayer)
                .add("defaultNodeAccount", "0.0." + defaultNodeAccount)
                .add("nodes", nodes)
                .add("allowedReceiverAccountIds", allowedReceiverAccountIds)
                .omitNullValues()
                .toString();
    }
}
