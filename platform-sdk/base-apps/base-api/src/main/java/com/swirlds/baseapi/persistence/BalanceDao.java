package com.swirlds.baseapi.persistence;

import com.swirlds.baseapi.domain.Balance;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BalanceDao {

    private static class InstanceHolder {
        private static final BalanceDao INSTANCE = new BalanceDao();
    }

    public static @NonNull BalanceDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static final Map<String, Balance> BALANCE_REPOSITORY = new ConcurrentHashMap<>();

    public Balance save(final @NonNull Balance balance) {
        BALANCE_REPOSITORY.computeIfPresent(balance.wallet().address(), (k, b) -> {
            final Version version = b.version().checkAgainst(balance.version());
            return new Balance(b.wallet(), balance.amount(), version);
        });
        BALANCE_REPOSITORY.putIfAbsent(balance.wallet().address(), balance);
        return balance;
    }


    public Balance findById(final @NonNull String id) {
        return BALANCE_REPOSITORY.get(id);
    }

}
