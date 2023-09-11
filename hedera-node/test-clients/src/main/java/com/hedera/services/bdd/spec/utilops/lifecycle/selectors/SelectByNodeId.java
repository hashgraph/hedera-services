package com.hedera.services.bdd.spec.utilops.lifecycle.selectors;

import com.hedera.services.bdd.junit.HapiTestNode;
import edu.umd.cs.findbugs.annotations.NonNull;

public class SelectByNodeId implements NodeSelector {
    private final long nodeId;

    public SelectByNodeId(final long nodeId) {
        if (nodeId < 0) {
            throw new IllegalArgumentException("Node IDs are non-negative. Cannot be " + nodeId);
        }
        this.nodeId = nodeId;
    }

    @Override
    public boolean test(@NonNull final HapiTestNode hapiTestNode) {
        return hapiTestNode.getId() == nodeId;
    }

    @Override
    public String toString() {
        return "by nodeId '" + nodeId + "'";
    }
}
