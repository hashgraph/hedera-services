package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.utilops.lifecycle.LifecycleOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An operation that starts the selected nodes. This operation *does not block* waiting for them to become active.
 * Use the {@link WaitForActiveOp} for that operation.
 */
public class StartupOp extends LifecycleOp {
    static final Logger logger = LogManager.getLogger(StartupOp.class);

    public StartupOp(@NonNull final NodeSelector selector) {
        super(selector);
    }

    @Override
    protected boolean run(@NonNull final HapiTestNode node) {
        logger.info("Starting up node {}", node);
        node.start();
        return false;
    }
}
