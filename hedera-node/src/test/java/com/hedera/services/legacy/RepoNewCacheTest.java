package com.hedera.services.legacy;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.FCMapBackingAccounts;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.mocks.StorageSourceFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.contracts.sources.LedgerAccountsSource;

import java.math.BigInteger;

import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.DecoderException;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.Source;
import org.ethereum.db.ServicesRepositoryImpl;
import org.ethereum.db.ServicesRepositoryRoot;
import org.junit.Ignore;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import static org.mockito.BDDMockito.*;

public class RepoNewCacheTest {
  @Ignore
  public void test() {
    FCMap<MerkleEntityId, MerkleAccount> accountMap =
            new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
    FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap = new FCMap<>(new MerkleBlobMeta.Provider(), new MerkleOptionalBlob.Provider());
    DbSource<byte[]> repDBFile = StorageSourceFactory.from(storageMap);

    TransactionalLedger<AccountID, AccountProperty, MerkleAccount> delegate = new TransactionalLedger<>(
            AccountProperty.class,
            () -> new MerkleAccount(),
            new FCMapBackingAccounts(() -> accountMap),
            new ChangeSummaryManager<>());
    HederaLedger ledger = new HederaLedger(
            mock(TokenStore.class),
            mock(ScheduleStore.class),
            mock(EntityIdSource.class),
            mock(ExpiringCreations.class),
            mock(AccountRecordsHistorian.class),
            delegate);
    Source<byte[], AccountState> repDatabase = new LedgerAccountsSource(ledger, new MockGlobalDynamicProps());
    ServicesRepositoryRoot repository = new ServicesRepositoryRoot(repDatabase, repDBFile);
    String key = Hex.toHexString(EntityIdUtils.asSolidityAddress(0, 0, 1));
    byte[] keyByte = null;
    try {
      keyByte = MiscUtils.commonsHexToBytes(key);
    } catch (DecoderException e) {
    }
    repository.addBalance(keyByte, BigInteger.TEN);
    repository.commit();

    Repository track1 = repository.startTracking();

    Repository track2 = track1.startTracking();
    track2.addBalance(keyByte, BigInteger.TEN);
    assertEquals(20, track2.getBalance(keyByte).longValue());
    assertEquals(10, track1.getBalance(keyByte).longValue());
    assertEquals(10, repository.getBalance(keyByte).longValue());
    track2.commit();

    assertEquals(20, track2.getBalance(keyByte).longValue());
    assertEquals(20, track1.getBalance(keyByte).longValue());
    assertEquals(10, repository.getBalance(keyByte).longValue());

    track1.commit();

    assertEquals(20, track2.getBalance(keyByte).longValue());
    assertEquals(20, track1.getBalance(keyByte).longValue());
    assertEquals(20, repository.getBalance(keyByte).longValue());

    repository.commit();
    assertEquals(20, track2.getBalance(keyByte).longValue());
    assertEquals(20, track1.getBalance(keyByte).longValue());
    assertEquals(20, repository.getBalance(keyByte).longValue());

    track1.addBalance(keyByte, BigInteger.valueOf(-5l));

    assertEquals(15, track2.getBalance(keyByte).longValue());
    assertEquals(15, track1.getBalance(keyByte).longValue());
    assertEquals(20, repository.getBalance(keyByte).longValue());

    track1.commit();

    assertEquals(15, track2.getBalance(keyByte).longValue());
    assertEquals(15, track1.getBalance(keyByte).longValue());
    assertEquals(15, repository.getBalance(keyByte).longValue());
    repository.commit();

    repository.saveCode(keyByte, "Test Code for SmartContract".getBytes());

    byte[] code = repository.getCode(keyByte);
    String codeStr = new String(code);
    assertEquals("Test Code for SmartContract", codeStr);
    repository.commit();

    repository.saveCode(keyByte, "Test Code for SmartContract..New".getBytes());
    repository.commit();

    code = repository.getCode(keyByte);
    codeStr = new String(code);
  }

  @Test
  public void rollbackTest() {
    FCMap<MerkleEntityId, MerkleAccount> accountMap =
            new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
    FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap = new FCMap<>(new MerkleBlobMeta.Provider(), new MerkleOptionalBlob.Provider());
    DbSource<byte[]> repDBFile = StorageSourceFactory.from(storageMap);

    FCMapBackingAccounts backingAccounts = new FCMapBackingAccounts(() -> accountMap);
    TransactionalLedger<AccountID, AccountProperty, MerkleAccount> delegate = new TransactionalLedger<>(
            AccountProperty.class,
            () -> new MerkleAccount(),
            backingAccounts,
            new ChangeSummaryManager<>());
    MerkleAccount someAccount = new MerkleAccount();
    MerkleAccount someOtherAccount = new MerkleAccount();
    try {
      someAccount.setBalance(100_000_000L);
      someOtherAccount.setBalance(0L);
      new HederaAccountCustomizer()
              .key(new JContractIDKey(0, 0, 1))
              .customizing(someAccount);
      new HederaAccountCustomizer()
              .key(new JContractIDKey(0, 0, 2))
              .customizing(someOtherAccount);
    } catch (Exception impossible) {}
    backingAccounts.put(IdUtils.asAccount("0.0.1"), someAccount);
    backingAccounts.put(IdUtils.asAccount("0.0.2"), someOtherAccount);
    HederaLedger ledger = new HederaLedger(
            mock(TokenStore.class),
            mock(ScheduleStore.class),
            mock(EntityIdSource.class),
            mock(ExpiringCreations.class),
            mock(AccountRecordsHistorian.class),
            delegate);
    Source<byte[], AccountState> accountSource = new LedgerAccountsSource(ledger, new MockGlobalDynamicProps());
    ServicesRepositoryRoot repository = new ServicesRepositoryRoot(accountSource, repDBFile);

    String someKey = Hex.toHexString(EntityIdUtils.asSolidityAddress(0, 0, 1));
    byte[] someKeyBytes = null;
    try {
      someKeyBytes = MiscUtils.commonsHexToBytes(someKey);
    } catch (DecoderException e) {
    }

    ledger.begin();
    repository.increaseNonce(someKeyBytes);
    ServicesRepositoryImpl track1 = repository.startTracking();
    track1.addBalance(someKeyBytes, BigInteger.TEN.negate());

    // To show under debug that the two AccountStates are the same object.
    AccountState info1 = track1.getAccount(someKeyBytes);
    AccountState info2 = repository.getAccount(someKeyBytes);

    assertEquals(99_999_990L, track1.getBalance(someKeyBytes).longValue());
    assertEquals(100_000_000L, repository.getBalance(someKeyBytes).longValue());

    track1.rollback();

    repository.commit();
    ledger.commit();

    assertEquals(100_000_000L, repository.getBalance(someKeyBytes).longValue());
  }

}
