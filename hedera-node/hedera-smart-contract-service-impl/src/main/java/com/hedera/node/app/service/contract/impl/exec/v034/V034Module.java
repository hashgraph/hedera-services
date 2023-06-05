package com.hedera.node.app.service.contract.impl.exec.v034;

import dagger.Module;

/**
 * Provides the Services 0.34 EVM implementation, which consists of Paris operations and
 * Instanbul precompiles plus the Hedera gas calculator, system contracts, and operations
 * as they were configured in the 0.34 release (in particular, with the option for lazy
 * creation, but without special treatment to make system addresses "invisible").
 */
@Module
public interface V034Module {
}
