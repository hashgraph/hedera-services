package com.swirlds.platform.gossip.sync;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Snarfs up sync permits when the intake queue files up.
 */
public class PermitSnarfer implements Startable, Stoppable {

    private final SyncPermitProvider syncPermitProvider;
    private final int maxAcquirablePermitCount;
    private final IntSupplier intakeQueueSizeSupplier;
    private final int lowerSnarfThreshold = 500; // TODO setting
    private final int upperSnarfThreshold = 2000; // TODO setting
    private final int snarfRange;
    private int currentPermitCount = 0;

    private final StoppableThread thread;

    public PermitSnarfer(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final SyncPermitProvider syncPermitProvider,
            @NonNull final IntSupplier intakeQueueSizeSupplier) {

        this.syncPermitProvider = Objects.requireNonNull(syncPermitProvider);
        this.intakeQueueSizeSupplier = Objects.requireNonNull(intakeQueueSizeSupplier);

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        maxAcquirablePermitCount = syncConfig.syncProtocolPermitCount(); // TODO setting to reduce this

        snarfRange = upperSnarfThreshold - lowerSnarfThreshold;

        thread = new StoppableThreadConfiguration<>(threadManager)
                .setComponent("platform")
                .setThreadName("permit-snarfer")
                .setMinimumPeriod(Duration.ofMillis(10)) // TODO setting
                .setWork(this::doWork)
                .build();
    }

    /**
     * Acquire and release permits.
     */
    private void doWork() {
        int desiredPermitCount = getDesiredPermitCount();
        while (desiredPermitCount != currentPermitCount) {
            if (desiredPermitCount > currentPermitCount) {
                syncPermitProvider.acquire();
                currentPermitCount++;
            } else if (desiredPermitCount < currentPermitCount) {
                syncPermitProvider.release();
                currentPermitCount--;
            }

            desiredPermitCount = getDesiredPermitCount();
        }
    }

    /**
     * Get the number of permits that the snarfer should attempt to hold.
     */
    private int getDesiredPermitCount() {
        final int currentQueueSize = intakeQueueSizeSupplier.getAsInt();

        if (currentQueueSize < lowerSnarfThreshold) {
            return 0;
        }

        if (currentQueueSize > upperSnarfThreshold) {
            return maxAcquirablePermitCount;
        }

        final int excessSize = currentQueueSize - lowerSnarfThreshold;
        final double permitFractionToAcquire = excessSize / ((double) snarfRange);
        return (int) (permitFractionToAcquire * maxAcquirablePermitCount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        thread.start();
    }

    @Override
    public void stop() {
        thread.stop();
    }
}
