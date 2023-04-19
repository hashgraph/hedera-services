package com.hedera.node.app.fixtures.state;

import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.ReceiptCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;

public class FakeHederaState implements HederaState {
    private final ReceiptCache receiptCache = new FakeReceiptCache();
    // Key is Service, value is Map of state name to ReadableKVState
    private final Map<String, Map<String, ReadableKVState<?, ?>>> data = new HashMap<>();

    public void addService(@NonNull final String serviceName, @NonNull final Map<String, ReadableKVState<?, ?>> data) {
        this.data.put(serviceName, data);
    }

    public void addService(@NonNull final String serviceName, @NonNull final ReadableKVState<?, ?>... states) {
        var serviceStates = data.get(serviceName);
        if (serviceStates == null) {
            serviceStates = new HashMap<>();
            data.put(serviceName, serviceStates);
        }
        for (final var state : states) {
            serviceStates.put(state.getStateKey(), state);
        }
    }

    @NonNull
    @Override
    public ReadableStates createReadableStates(@NonNull String serviceName) {
        return new MapReadableStates(data.get(serviceName));
    }

    @NonNull
    @Override
    public WritableStates createWritableStates(@NonNull String serviceName) {
        return new MapWritableStates(data.get(serviceName));
    }

    @NonNull
    @Override
    public ReceiptCache getReceiptCache() {
        return receiptCache;
    }
}
