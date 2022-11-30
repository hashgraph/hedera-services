package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.TokenID;

public record GetApprovedWrapper(TokenID tokenId, long serialNo) {}
