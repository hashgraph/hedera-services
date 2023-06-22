package com.swirlds.common.system.status;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An object to get and set the current status in a threadsafe manner
 */
public class PlatformStatusNexus {
    /**
     * The current status
     */
    private PlatformStatus currentStatus;

    /**
     * Constructor
     *
     * @param initialStatus the initial status
     */
    public PlatformStatusNexus(@NonNull final PlatformStatus initialStatus) {
        this.currentStatus = initialStatus;
    }

    /**
     * Get the current status
     *
     * @return the current status
     */
    public synchronized PlatformStatus getCurrentStatus() {
        return currentStatus;
    }

    /**
     * Set the current status
     *
     * @param newStatus the new status
     */
    public synchronized void setCurrentStatus(@NonNull final PlatformStatus newStatus) {
        this.currentStatus = newStatus;
    }
}
