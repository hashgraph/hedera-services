package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.utilops.lifecycle.LifecycleOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Blocks waiting until the selected node or nodes are shut down, or until a timeout of {@code waitSeconds} has
 * happened.
 */
public class WaitForShutdownOp extends LifecycleOp {
    private static final Logger logger = LogManager.getLogger(WaitForShutdownOp.class);

    /** The number of seconds to wait for the node(s) to shut down */
    private final int waitSeconds;

    public WaitForShutdownOp(@NonNull final NodeSelector selector, int waitSeconds) {
        super(selector);
        this.waitSeconds = waitSeconds;
    }

    @Override
    protected boolean run(@NonNull final HapiTestNode node) {
        logger.info("Waiting for node {} to shut down, waiting up to {}s...", node, waitSeconds);
        try {
            node.waitForShutdown(waitSeconds);
            logger.info("Node {} is shut down", node);
            return false; // Do not stop the test, all is well.
        } catch (TimeoutException e) {
            logger.info("Node {} did not shut down within {}s", node, waitSeconds);
            return true; // Stop the test, we're toast.
        }
    }
}
