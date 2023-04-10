package com.swirlds.demo.consistency;

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.SecureRandom;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A testing app for guaranteeing proper handling of transactions after a restart
 */
public class ConsistencyTestingToolMain implements SwirldMain {

    private static final Logger logger = LogManager.getLogger(ConsistencyTestingToolMain.class);

    /**
     * The default software version of this application
     */
    private static final BasicSoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

    /**
     * The platform instance
     */
    private Platform platform;

    /**
     * The number of transactions to generate per second.
     */
    private static final int TRANSACTIONS_PER_SECOND = 100;

    /**
     * Constructor
     */
    public ConsistencyTestingToolMain() {
        logger.info(STARTUP.getMarker(), "constructor called in Main.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId nodeId) {
        throwArgNull(platform, "platform");
        throwArgNull(nodeId, "nodeId");

        logger.info(STARTUP.getMarker(), "init called in Main for node {}.", nodeId);
        this.platform = platform;

        parseArguments(((PlatformWithDeprecatedMethods) platform).getParameters());
    }

    /**
     * Parses the arguments
     * <p>
     * Currently, no arguments are expected
     *
     * @param args the arguments
     * @throws IllegalArgumentException if the arguments array has length other than 0
     */
    private void parseArguments(@NonNull final String[] args) {
        Objects.requireNonNull(args, "The arguments must not be null.");
        if (args.length != 0) {
            throw new IllegalArgumentException("Expected no arguments. See javadocs for details.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        logger.info(STARTUP.getMarker(), "run called in Main.");
        new TransactionGenerator(new SecureRandom(), platform, TRANSACTIONS_PER_SECOND).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SwirldState newState() {
        return new ConsistencyTestingToolState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicSoftwareVersion getSoftwareVersion() {
        logger.info(STARTUP.getMarker(), "returning software version {}", softwareVersion);
        return softwareVersion;
    }
}
