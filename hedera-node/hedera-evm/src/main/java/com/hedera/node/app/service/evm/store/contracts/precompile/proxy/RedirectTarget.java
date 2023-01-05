package com.hedera.node.app.service.evm.store.contracts.precompile.proxy;

import org.hyperledger.besu.datatypes.Address;

public record RedirectTarget(int descriptor, Address token) {}
