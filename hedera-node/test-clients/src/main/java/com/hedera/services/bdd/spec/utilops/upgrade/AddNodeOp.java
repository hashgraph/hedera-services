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

import com.hedera.services.bdd.junit.hedera.subprocess.SubProcessNetwork;
import com.hedera.services.bdd.junit.hedera.subprocess.UpgradeConfigTxt;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Adds the node with "classic" metadata implied by the given node id and refreshes the
 * {@link SubProcessNetwork} address book using the given {@link UpgradeConfigTxt} source.
 */
public class AddNodeOp extends UtilOp {
    private final long nodeId;
    private final UpgradeConfigTxt upgradeConfigTxt;

    public AddNodeOp(final long nodeId, @NonNull final UpgradeConfigTxt upgradeConfigTxt) {
        this.nodeId = nodeId;
        this.upgradeConfigTxt = requireNonNull(upgradeConfigTxt);
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        if (!(spec.targetNetworkOrThrow() instanceof SubProcessNetwork subProcessNetwork)) {
            throw new IllegalStateException("Can only add nodes to a SubProcessNetwork");
        }
        subProcessNetwork.addNode(nodeId, upgradeConfigTxt);
        return false;
    }
}
