package com.hedera.node.app.spi.fixtures.accounts;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.key.HederaKey;

public class FakeHederaKey implements HederaKey {
    private final Key key;

    public FakeHederaKey(Key key) {
        this.key = key;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
