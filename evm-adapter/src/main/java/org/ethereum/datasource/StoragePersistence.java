package org.ethereum.datasource;

public interface StoragePersistence {

  boolean storageExist(byte[] key);

  void persist(byte[] key, byte[] storageCache, long expirationTime, long currentTime);

  byte[] get(byte[] key);

}
