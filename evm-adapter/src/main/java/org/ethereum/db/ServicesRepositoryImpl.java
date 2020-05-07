/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.db;

import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.CachedSource;
import org.ethereum.datasource.MultiCache;
import org.ethereum.datasource.SimplifiedWriteCache;
import org.ethereum.datasource.Source;
import org.ethereum.datasource.WriteCache;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;

/**
 * @author anurag
 */
public class ServicesRepositoryImpl extends RepositoryImpl implements Repository, org.ethereum.facade.Repository {

     protected ServicesRepositoryImpl parent;


     protected ServicesRepositoryImpl() {
          super();
     }

     public ServicesRepositoryImpl(Source<byte[], AccountState> accountStateCache, Source<byte[], byte[]> codeCache,
                                      MultiCache<? extends CachedSource<DataWord, DataWord>> storageCache) {
          super.init(accountStateCache, codeCache, storageCache);
     }


     @Override
     public synchronized AccountState createAccount(byte[] addr) {
          AccountState state = new AccountState(BigInteger.ZERO, BigInteger.ZERO);
          accountStateCache.put(addr, state);
          return state;
     }

     @Override
     public synchronized ServicesRepositoryImpl startTracking() {
          Source<byte[], AccountState> trackAccountStateCache = new SimplifiedWriteCache.BytesKey<>(accountStateCache);
          Source<byte[], byte[]> trackCodeCache = new SimplifiedWriteCache.BytesKey<>(codeCache);
          MultiCache<CachedSource<DataWord, DataWord>> trackStorageCache = new MultiCache(storageCache) {
                @Override
                protected CachedSource create(byte[] key, CachedSource srcCache) {
                     return new WriteCache<>(srcCache, WriteCache.CacheType.SIMPLE);
                }

          };

          ServicesRepositoryImpl ret = new ServicesRepositoryImpl(trackAccountStateCache, trackCodeCache, trackStorageCache);
          ret.parent = this;
          return ret;
     }


     public long getExpirationTime(byte[] addr) {
          AccountState accountState = getOrCreateAccountState(addr);
          return accountState == null ? 0l : accountState.getExpirationTime();
     }

     public void setExpirationTime(byte[] addr, long expirationTime) {
          AccountState accountState = getOrCreateAccountState(addr);
          accountState.setExpirationTime(expirationTime);
          accountStateCache.put(addr, accountState);
     }

 
     public MultiCache<? extends CachedSource<DataWord, DataWord>> getStorageCache() {
          return storageCache;
     }
     
     public synchronized void setSenderThreshold(byte[] addr, long senderThreshold) {
       AccountState accountState = getOrCreateAccountState(addr);
       accountState.setSenderThreshold(senderThreshold);
       accountStateCache.put(addr, accountState);
  }

  public synchronized void setReceiverSigRequired(byte[] addr, boolean receiverSigRequired) {
       AccountState accountState = getOrCreateAccountState(addr);
       accountState.setReceiverSigRequired(receiverSigRequired);
       accountStateCache.put(addr, accountState);
  }
  public synchronized void setReceiverThreshold(byte[] addr, long receiverThreshold) {
    AccountState accountState = getOrCreateAccountState(addr);
    accountState.setSenderThreshold(receiverThreshold);
    accountStateCache.put(addr, accountState);
  }
  
  public synchronized void setHGAccountId(byte[] addr, long accountNum) {
    AccountState accountState = getOrCreateAccountState(addr);
    accountState.setHGAccountId(accountNum);
    accountStateCache.put(addr, accountState);
  }
  
  public synchronized void setHGRealmId(byte[] addr, long accountRealm) {
    AccountState accountState = getOrCreateAccountState(addr);
    accountState.setHGRealmId(accountRealm);;
    accountStateCache.put(addr, accountState);
  }
  
  public synchronized void setHGShardId(byte[] addr, long accountShard) {
    AccountState accountState = getOrCreateAccountState(addr);
    accountState.setHGShardId(accountShard);
    accountStateCache.put(addr, accountState);
  }
  
  public long getAutoRenewPeriod(byte[] addr) {
    AccountState accountState = getOrCreateAccountState(addr);
    return accountState == null ? 0l : accountState.getAutoRenewPeriod();
}

  public void setAutoRenewPeriod(byte[] addr, long autoRenewPeriod) {
      AccountState accountState = getOrCreateAccountState(addr);
      accountState.setAutoRenewPeriod(autoRenewPeriod);
      accountStateCache.put(addr, accountState);
  }
  
  public synchronized void setHGAccount(byte[] addr, long accountId, long shardId, long realmId) {
    AccountState accountState = getOrCreateAccountState(addr);
    accountState.setHGAccountId(accountId);
    accountState.setHGRealmId(realmId);
    accountState.setHGShardId(shardId);
    accountStateCache.put(addr, accountState);
}
  
  public synchronized AccountState getHGCAccount(byte[] addr) {
    AccountState accountState = getAccountState(addr);
    return accountState;
}
  
  public void setCreateTimeMs(byte[] addr, long createTimeMs) {
    AccountState accountState = getOrCreateAccountState(addr);
    accountState.setCreateTimeMs(createTimeMs);
    accountStateCache.put(addr, accountState);
  }

  public long getCreateTimeSec(byte[] addr) {
      AccountState accountState = getOrCreateAccountState(addr);
      return accountState == null ? 0 : accountState.getCreateTimeMs();
  }

  public void setDeleted(byte[] addr, boolean deleted) {
    AccountState accountState = getOrCreateAccountState(addr);
    accountState.setDeleted(deleted);
    accountStateCache.put(addr, accountState);
  }
  
  public boolean isDeleted(byte[] addr) {
    AccountState accountState = getOrCreateAccountState(addr);
    return accountState == null ? false : accountState.isDeleted();
  }

  public void setSmartContract(byte[] addr, boolean smartContract) {
    AccountState accountState = getOrCreateAccountState(addr);
    accountState.setSmartContract(smartContract);
    accountStateCache.put(addr, accountState);
  }
  
  public boolean isSmartContract(byte[] addr) {
    AccountState accountState = getOrCreateAccountState(addr);
    return accountState == null ? false : accountState.isSmartContract();
  }

}   
    
    
    
   
    
    
    
   
    
    
    
    

