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

package com.hedera.services.bdd.spec.utilops.upgrade;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.junit.hedera.subprocess.SubProcessNetwork;
import com.hedera.services.bdd.junit.hedera.subprocess.UpgradeConfigTxt;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Adds the node with "classic" metadata implied by the given node id and refreshes the
 * {@link SubProcessNetwork} address book using the given {@link UpgradeConfigTxt} source.
 */
public class UpdateNodeOp extends UtilOp {
    private final NodeSelector selector;
    private final UpgradeConfigTxt upgradeConfigTxt;
    private final NodeMetadata nodeMetadata;
    private final boolean init;
    private final Predicate<HederaNode> filter;

    public UpdateNodeOp(
            @NonNull final NodeSelector selector,
            @NonNull final UpgradeConfigTxt upgradeConfigTxt,
            @NonNull NodeMetadata nodeMetadata,
            final boolean init,
            @NonNull final Predicate<HederaNode> filter) {
        this.selector = Objects.requireNonNull(selector);
        this.upgradeConfigTxt = requireNonNull(upgradeConfigTxt);
        this.nodeMetadata = requireNonNull(nodeMetadata);
        this.init = init;
        this.filter = filter;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        if (!(spec.targetNetworkOrThrow() instanceof SubProcessNetwork subProcessNetwork)) {
            throw new IllegalStateException("Can only remove nodes from a SubProcessNetwork");
        }
        subProcessNetwork.updateNode(selector, upgradeConfigTxt, nodeMetadata, init, filter);
        return false;
    }
}
