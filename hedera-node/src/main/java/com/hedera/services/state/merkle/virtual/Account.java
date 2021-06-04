package com.hedera.services.state.merkle.virtual;

import java.util.Objects;

/**
 * Simple record for a account
 */
public class Account {
    /** Site of an account when serialized to bytes */
    public static final int BYTES = Long.BYTES * 3;
    private final long shardNum;
    private final long realmNum;
    private final long accountNum;

    public Account(long shardNum, long realmNum, long accountNum) {
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.accountNum = accountNum;
    }

    public boolean isDefaultShardAndRealm() {
        return shardNum == 0 && realmNum == 0;
    }

    public long shardNum() {
        return shardNum;
    }

    public long realmNum() {
        return realmNum;
    }

    public long accountNum() {
        return accountNum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return shardNum == account.shardNum && realmNum == account.realmNum && accountNum == account.accountNum;
    }

    @Override
    public int hashCode() {
        return Objects.hash(shardNum, realmNum, accountNum);
    }

    public String toString() {
        return String.format("(%d, %d, %d)", this.shardNum, this.realmNum, this.accountNum);
    }
}
