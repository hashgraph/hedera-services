package com.hedera.node.app.spi;

import com.hederahashgraph.api.proto.java.AccountID;

public interface AccountKeyLookup {
    KeyOrLookupFailureReason getKey(final AccountID idOrAlias);
    KeyOrLookupFailureReason getKeyIfReceiverSigRequired(final AccountID idOrAlias);
}
