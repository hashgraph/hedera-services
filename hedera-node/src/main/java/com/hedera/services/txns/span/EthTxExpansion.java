package com.hedera.services.txns.span;

import com.hedera.services.sigs.order.LinkedRefs;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.annotation.Nullable;

public record EthTxExpansion(@Nullable LinkedRefs linkedRefs, ResponseCodeEnum result) {
}
