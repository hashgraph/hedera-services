package com.hedera.services.evm.contracts.initialization;

public interface LoadInitialStructures {
    default void loadObservableSystemFiles() {
        loadFeeSchedules();
        loadExchangeRates();
    }

    void loadExchangeRates();

    void loadFeeSchedules();
}
