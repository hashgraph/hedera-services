package com.hedera.services.store.schedule;

import com.hederahashgraph.api.proto.java.AccountID;

public class CompositeKey {
    int hash;
    AccountID id;

    public CompositeKey(int hash, AccountID id) {
        this.hash = hash;
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof CompositeKey))
            return false;
        CompositeKey other = (CompositeKey)o;

        boolean hashEquals = this.hash == other.hash;
        boolean idEquals = this.id.equals(other.id);

        return hashEquals && idEquals;
    }

    @Override
    public final int hashCode() {
        int result = hash;
        if (id != null) {
            result = 31 * result + id.hashCode();
        }
        return result;
    }
}
