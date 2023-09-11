package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.utilops.lifecycle.LifecycleOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Blocks waiting until the selected node or nodes are active, or until a timeout of {@code waitSeconds} has happened.
 */
public class WaitForActiveOp extends LifecycleOp {
    private static final Logger logger = LogManager.getLogger(WaitForActiveOp.class);

    /** The number of seconds to wait for the node(s) to become active */
    private final int waitSeconds;

    public WaitForActiveOp(@NonNull final NodeSelector selector, int waitSeconds) {
        super(selector);
        this.waitSeconds = waitSeconds;
    }

    @Override
    protected boolean run(@NonNull final HapiTestNode node) {
        logger.info("Waiting for node {} to become active, waiting up to {}s...", node, waitSeconds);
        try {
            node.waitForActive(waitSeconds);
            logger.info("Node {} started and is active", node);
            return false; // Do not stop the test, all is well.
        } catch (TimeoutException e) {
            logger.info("Node {} did not become active within {}s", node, waitSeconds);
            return true; // Stop the test, we're toast.
        }
    }
}
