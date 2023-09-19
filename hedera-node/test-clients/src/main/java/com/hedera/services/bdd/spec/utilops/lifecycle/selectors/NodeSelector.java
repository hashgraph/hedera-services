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

package com.hedera.services.bdd.spec.utilops.lifecycle.selectors;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.services.bdd.junit.HapiTestNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;

/**
 * Defines the criteria by which {@link HapiTestNode}s will be used by the operations of this package.
 */
public interface NodeSelector extends Predicate<HapiTestNode> {

    /**
     * Returns true if the given node should be selected.
     *
     * @param hapiTestNode the input argument
     * @return true if the node should be selected.
     */
    @Override
    boolean test(@NonNull HapiTestNode hapiTestNode);

    /** Gets a {@link NodeSelector} that selects nodes by name in a case-insensitive way, such as Alice or Bob.  */
    static NodeSelector byName(@NonNull final String name) {
        return new SelectByName(name);
    }

    /** Gets a {@link NodeSelector} that selects nodes by operator account ID. Does not work with aliases. */
    static NodeSelector byOperatorAccountId(@NonNull final AccountID operatorAccountID) {
        return new SelectByOperatorAccountId(operatorAccountID);
    }

    /** Gets a {@link NodeSelector} that selects nodes by nodeId in a case-insensitive way */
    static NodeSelector byNodeId(final long nodeId) {
        return new SelectByNodeId(nodeId);
    }

    /** Gets a {@link NodeSelector} that selects all nodes */
    static NodeSelector allNodes() {
        return new SelectAll();
    }
}
