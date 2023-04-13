package com.hedera.node.app.state.merkle;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.state.HederaAddressBook;

public class MerkleAddressBook implements HederaAddressBook {
    @Override
    public AccountID getNodeOperatorAccountID(long nodeId) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }
}
