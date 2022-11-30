package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.AccountID;

public record BalanceOfWrapper(AccountID accountId) {}
