package com.hedera.services.store.contracts.repository;

import org.apache.tuweni.units.bigints.UInt256;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ContractDetails {
    void put(UInt256 var1, UInt256 var2);

    UInt256 get(UInt256 var1);

    byte[] getCode();

    byte[] getCode(byte[] var1);

    void setCode(byte[] var1);

    byte[] getStorageHash();

    void decode(byte[] var1);

    void setDirty(boolean var1);

    void setDeleted(boolean var1);

    boolean isDirty();

    boolean isDeleted();

    byte[] getEncoded();

    int getStorageSize();

    Set<UInt256> getStorageKeys();

    void deleteStorage();

    Map<UInt256, UInt256> getStorage(@Nullable Collection<UInt256> var1);

    Map<UInt256, UInt256> getStorage();

    void setStorage(List<UInt256> var1, List<UInt256> var2);

    void setStorage(Map<UInt256, UInt256> var1);

    byte[] getAddress();

    void setAddress(byte[] var1);

    ContractDetails clone();

    String toString();

    void syncStorage();

    ContractDetails getSnapshotTo(byte[] var1);
}

