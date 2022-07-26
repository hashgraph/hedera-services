package com.hedera.services.state.validation;

public interface AccountUsageTracking {
    void refreshAccounts();

    void recordContracts(int n);
}
