package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.utilops.lifecycle.LifecycleOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Take a node down for long enough to cause a reconnect to happen, then starts it up again. This operation does not
 * block. It will immediately shutdown the node (relatively gracefully), and then start a background thread to initiate
 * the restart after {@code downSeconds}. It is up to the test to {@link WaitForActiveOp} if it wants to. It may be
 * that the test wants to run other operations concurrently while the node is down, and therefore we don't want to
 * cause this reconnect operation to be a blocking operation.
 */
public class ReconnectOp extends LifecycleOp {
    private static final Logger logger = LogManager.getLogger(ReconnectOp.class);
    /** The number of seconds to remain down. Should be long enough to cause the node to fall behind */
    private final int downSeconds;

    public ReconnectOp(@NonNull final NodeSelector selector, final int downSeconds) {
        super(selector);
        this.downSeconds = downSeconds;
    }

    @Override
    protected boolean run(@NonNull final HapiTestNode node) {
        logger.info("Taking node {} down for {}s, hoping it falls behind...", node, downSeconds);
        node.shutdown();

        CompletableFuture.delayedExecutor(downSeconds, TimeUnit.SECONDS).execute(() -> {
            logger.info("Restarting node {}..., hoping it reconnects", node);
            node.start();
        });

        return false;
    }
}
