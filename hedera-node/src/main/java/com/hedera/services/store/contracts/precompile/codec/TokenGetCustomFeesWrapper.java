package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.TokenID;

public record TokenGetCustomFeesWrapper (TokenID tokenID) {}
