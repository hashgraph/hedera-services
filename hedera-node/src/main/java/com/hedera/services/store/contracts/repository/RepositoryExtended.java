package com.hedera.services.store.contracts.repository;

import org.apache.tuweni.units.bigints.UInt256;

import java.math.BigInteger;
import java.util.Set;

public interface RepositoryExtended extends Repository {

    boolean isExist(byte[] var1);

    void delete(byte[] var1);

    BigInteger increaseNonce(byte[] var1);

    BigInteger setNonce(byte[] var1, BigInteger var2);

    BigInteger getNonce(byte[] var1);

    ContractDetails getContractDetails(byte[] var1);

    boolean hasContractDetails(byte[] var1);

    void saveCode(byte[] var1, byte[] var2);

    byte[] getCode(byte[] var1);

    byte[] getCodeHash(byte[] var1);

    void addStorageRow(byte[] var1, UInt256 var2, UInt256 var3);

    UInt256 getStorageValue(byte[] var1, UInt256 var2);

    BigInteger getBalance(byte[] var1);

    BigInteger addBalance(byte[] var1, BigInteger var2);

    Set<byte[]> getAccountsKeys();

    AccountState createAccount(byte[] addr);

    AccountState getAccountState(byte[] addr);

    Repository startTracking();

    void flush();

    void flushNoReconnect();

    void commit();

    void rollback();

    void syncToRoot(byte[] var1);

    boolean isClosed();

    void close();

    void reset();

//    void updateBatch(HashMap<ByteArrayWrapper, AccountState> var1, HashMap<ByteArrayWrapper, ContractDetails> var2);

    byte[] getRoot();

//    void loadAccount(byte[] var1, HashMap<ByteArrayWrapper, AccountState> var2, HashMap<ByteArrayWrapper, ContractDetails> var3);

    Repository getSnapshotTo(byte[] var1);

    Repository clone();
}
