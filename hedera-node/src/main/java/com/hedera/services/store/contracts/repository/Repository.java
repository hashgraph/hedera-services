package com.hedera.services.store.contracts.repository;

import org.apache.tuweni.units.bigints.UInt256;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface Repository {
    boolean isExist(byte[] var1);

    BigInteger getBalance(byte[] var1);

    BigInteger getNonce(byte[] var1);

    byte[] getCode(byte[] var1);

    UInt256 getStorageValue(byte[] var1, UInt256 var2);

    int getStorageSize(byte[] var1);

    Set<UInt256> getStorageKeys(byte[] var1);

    Map<UInt256, UInt256> getStorage(byte[] var1, @Nullable Collection<UInt256> var2);
}