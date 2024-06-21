package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.MarkerFile;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.spec.utilops.lifecycle.AbstractLifecycleOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Waits for the selected node or nodes specified by the {@link NodeSelector} to
 * have written the specified marker file within the given timeout.
 */
public class WaitForMarkerFileOp extends AbstractLifecycleOp {
    private static final Logger log = LogManager.getLogger(WaitForMarkerFileOp.class);

    private final Duration timeout;
    private final MarkerFile markerFile;

    public WaitForMarkerFileOp(@NonNull NodeSelector selector, @NonNull final MarkerFile markerFile, @NonNull final Duration timeout) {
        super(selector);
        this.timeout = requireNonNull(timeout);
        this.markerFile = requireNonNull(markerFile);
    }

    @Override
    protected void run(@NonNull final HederaNode node) {
        log.info("Waiting for node '{}' to write marker file '{}' within {}", node.getName(), markerFile.fileName(), timeout);
        node.mfFuture(markerFile).orTimeout(timeout.toMillis(), MILLISECONDS).join();
        log.info("Node '{}' wrote marker file '{}'", node.getName(), markerFile.fileName());
    }
}
