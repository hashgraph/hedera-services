package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.utilops.lifecycle.LifecycleOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Blocks waiting until the selected node or nodes are frozen, or until a timeout of {@code waitSeconds} has
 * happened.
 */
public class WaitForFreezeOp extends LifecycleOp {
    private static final Logger logger = LogManager.getLogger(WaitForFreezeOp.class);

    /** The number of seconds to wait for the node(s) to freeze */
    private final int waitSeconds;

    public WaitForFreezeOp(@NonNull final NodeSelector selector, int waitSeconds) {
        super(selector);
        this.waitSeconds = waitSeconds;
    }

    @Override
    protected boolean run(@NonNull final HapiTestNode node) {
        logger.info("Waiting for node {} to freeze, waiting up to {}s...", node, waitSeconds);
        try {
            node.waitForFreeze(waitSeconds);
            logger.info("Node {} is frozen", node);
            return false; // Do not stop the test, all is well.
        } catch (TimeoutException e) {
            logger.info("Node {} did not freeze within {}s", node, waitSeconds);
            return true; // Stop the test, we're toast.
        }
    }
}
