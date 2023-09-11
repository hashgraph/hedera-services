package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.utilops.lifecycle.LifecycleOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bounces a node (stops and starts it again). This operation does not wait for the start to complete.
 */
public class BounceOp extends LifecycleOp {
    static final Logger logger = LogManager.getLogger(BounceOp.class);

    public BounceOp(@NonNull final NodeSelector selector) {
        super(selector);
    }

    @Override
    protected boolean run(@NonNull final HapiTestNode node) {
        logger.info("Bouncing node {}...", node);
        node.shutdown();
        node.start();
        return false;
    }
}
