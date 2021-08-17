package com.hedera.services.context;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.swirlds.common.AddressBook;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import java.util.Objects;

/**
 * Manages the state of the services. This gets updated by {@link ServicesContext} on a regular
 * interval. The intention of this class is to avoid making repetitive calls to get the state when
 * we know it has not yet been updated.
 */
public class StateChildren {
  private FCMap<MerkleEntityId, MerkleAccount> accounts;
  private FCMap<MerkleEntityId, MerkleTopic> topics;
  private FCMap<MerkleEntityId, MerkleToken> tokens;
  private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> uniqueTokens;
  private FCMap<MerkleEntityId, MerkleSchedule> schedules;
  private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage;
  private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations;
  private FCOneToManyRelation<PermHashInteger, Long> uniqueTokenAssociations;
  private FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipAssociations;
  private FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipTreasuryAssociations;
  private MerkleNetworkContext networkCtx;
  private AddressBook addressBook;
  private MerkleDiskFs diskFs;

  public FCMap<MerkleEntityId, MerkleAccount> getAccounts() {
    Objects.requireNonNull(accounts);
    return accounts;
  }

  public void setAccounts(FCMap<MerkleEntityId, MerkleAccount> accounts) {
    this.accounts = accounts;
  }

  public FCMap<MerkleEntityId, MerkleTopic> getTopics() {
    Objects.requireNonNull(topics);
    return topics;
  }

  public void setTopics(FCMap<MerkleEntityId, MerkleTopic> topics) {
    this.topics = topics;
  }

  public FCMap<MerkleEntityId, MerkleToken> getTokens() {
    Objects.requireNonNull(tokens);
    return tokens;
  }

  public void setTokens(FCMap<MerkleEntityId, MerkleToken> tokens) {
    this.tokens = tokens;
  }

  public FCMap<MerkleEntityId, MerkleSchedule> getSchedules() {
    Objects.requireNonNull(schedules);
    return schedules;
  }

  public void setSchedules(FCMap<MerkleEntityId, MerkleSchedule> schedules) {
    this.schedules = schedules;
  }

  public FCMap<MerkleBlobMeta, MerkleOptionalBlob> getStorage() {
    Objects.requireNonNull(storage);
    return storage;
  }

  public void setStorage(FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage) {
    this.storage = storage;
  }

  public FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> getTokenAssociations() {
    Objects.requireNonNull(tokenAssociations);
    return tokenAssociations;
  }

  public void setTokenAssociations(
      FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations) {
    this.tokenAssociations = tokenAssociations;
  }

  public MerkleNetworkContext getNetworkCtx() {
    Objects.requireNonNull(networkCtx);
    return networkCtx;
  }

  public void setNetworkCtx(MerkleNetworkContext networkCtx) {
    this.networkCtx = networkCtx;
  }

  public AddressBook getAddressBook() {
    Objects.requireNonNull(addressBook);
    return addressBook;
  }

  public void setAddressBook(AddressBook addressBook) {
    this.addressBook = addressBook;
  }

  public MerkleDiskFs getDiskFs() {
    Objects.requireNonNull(diskFs);
    return diskFs;
  }

  public void setDiskFs(MerkleDiskFs diskFs) {
    this.diskFs = diskFs;
  }

  public FCMap<MerkleUniqueTokenId, MerkleUniqueToken> getUniqueTokens() {
    Objects.requireNonNull(uniqueTokens);
    return uniqueTokens;
  }

  public void setUniqueTokens(FCMap<MerkleUniqueTokenId, MerkleUniqueToken> uniqueTokens) {
    this.uniqueTokens = uniqueTokens;
  }

  public FCOneToManyRelation<PermHashInteger, Long> getUniqueTokenAssociations() {
    Objects.requireNonNull(uniqueTokenAssociations);
    return uniqueTokenAssociations;
  }

  public void setUniqueTokenAssociations(
      FCOneToManyRelation<PermHashInteger, Long> uniqueTokenAssociations) {
    this.uniqueTokenAssociations = uniqueTokenAssociations;
  }

  public FCOneToManyRelation<PermHashInteger, Long> getUniqueOwnershipAssociations() {
    Objects.requireNonNull(uniqueOwnershipAssociations);
    return uniqueOwnershipAssociations;
  }

  public void setUniqueOwnershipAssociations(
      FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipAssociations) {
    this.uniqueOwnershipAssociations = uniqueOwnershipAssociations;
  }

  public FCOneToManyRelation<PermHashInteger, Long> getUniqueOwnershipTreasuryAssociations() {
    Objects.requireNonNull(uniqueOwnershipTreasuryAssociations);
    return uniqueOwnershipTreasuryAssociations;
  }

  public void setUniqueOwnershipTreasuryAssociations(
      FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipTreasuryAssociations) {
    this.uniqueOwnershipTreasuryAssociations = uniqueOwnershipTreasuryAssociations;
  }
}
