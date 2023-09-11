package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.utilops.lifecycle.LifecycleOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An operation that forcibly terminates the selected nodes. This operation *does not block* waiting for them to shutdown.
 */
public class TerminateOp extends LifecycleOp {
    private static final Logger logger = LogManager.getLogger(TerminateOp.class);

    public TerminateOp(@NonNull final NodeSelector selector) {
        super(selector);
    }

    @Override
    protected boolean run(@NonNull HapiTestNode node) {
        logger.info("Terminating node forcibly {}", node);
        node.terminate();
        return false;
    }
}
