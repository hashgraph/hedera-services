package com.hedera.node.app.spi.fixtures;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.spi.accounts.Account;

public record TestNode(long nodeNumber, AccountID nodeAccountID, Account account, TestKeyInfo keyInfo) {
}
