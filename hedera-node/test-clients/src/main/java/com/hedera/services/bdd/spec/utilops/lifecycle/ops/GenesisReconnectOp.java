package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.utilops.lifecycle.LifecycleOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Take a node down, clear out its state, and bring it back up. This will cause the node to reconnect from genesis.
 */
public class GenesisReconnectOp extends LifecycleOp {
    private static final Logger logger = LogManager.getLogger(GenesisReconnectOp.class);

    public GenesisReconnectOp(@NonNull final NodeSelector selector) {
        super(selector);
    }

    @Override
    protected boolean run(@NonNull final HapiTestNode node) {
        logger.info("Taking node {} down for genesis reconnect...", node);
        node.shutdown();
        node.clearState();
        node.start();
        return false;
    }
}
