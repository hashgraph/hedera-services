package com.hedera.node.app.spi;

import com.hederahashgraph.api.proto.java.TransactionBody;

public interface PreHandleDispatcher {
    void dispatch(TransactionBody transactionBody);
}
