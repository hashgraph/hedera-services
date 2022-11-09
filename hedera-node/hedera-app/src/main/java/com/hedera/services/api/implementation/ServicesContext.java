package com.hedera.services.api.implementation;

import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.StateRegistry;

public record ServicesContext<S extends Service> (S service, StateRegistry stateRegistry) {
}
