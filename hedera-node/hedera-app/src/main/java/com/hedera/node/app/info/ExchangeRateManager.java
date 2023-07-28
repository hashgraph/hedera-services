package com.hedera.node.app.info;

import com.hedera.node.app.state.HederaState;
import com.swirlds.config.api.Configuration;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExchangeRateManager {
    private int currHbarEquiv;
    private int currCentEquiv;
    private long currExpiry;
    private int nextHbarEquiv;
    private int nextCentEquiv;
    private long nextExpiry;
    private boolean initialized = false;

    @Inject
    public ExchangeRateManager() {
        // For dagger
    }

    public int getCurrHbarEquiv() {
        return currHbarEquiv;
    }

    public int getCurrCentEquiv() {
        return currCentEquiv;
    }

    public long getCurrExpiry() {
        return currExpiry;
    }

    public int getNextHbarEquiv() {
        return nextHbarEquiv;
    }

    public int getNextCentEquiv() {
        return nextCentEquiv;
    }

    public long getNextExpiry() {
        return nextExpiry;
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void init(final HederaState state, final Configuration configuration){
    }

    public void reload(){}
}
