package com.hedera.node.app.state;

import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface HederaAddressBook {
    @NonNull
    AccountID getNodeOperatorAccountID(long nodeId);
}
