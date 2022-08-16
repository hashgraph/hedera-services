/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.state.validation;

import static com.hedera.services.exceptions.ValidationUtils.validateResourceLimit;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UsageLimits implements ContractStorageLimits, AccountUsageTracking {
    // We keep snapshots of the last seen entity counts, so we can update
    // the utilization stats without touching the working state
    private long numAccounts;
    private long numContracts;
    private long numFiles;
    private long numNfts;
    private long numSchedules;
    private long numStorageSlots;
    private long numTokens;
    private long numTokenRels;
    private long numTopics;

    private final MutableStateChildren stateChildren;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public UsageLimits(
            final MutableStateChildren stateChildren,
            final GlobalDynamicProperties dynamicProperties) {
        this.stateChildren = stateChildren;
        this.dynamicProperties = dynamicProperties;
    }

    public void refreshAccounts() {
        updatedNumAccounts();
    }

    public void resetNumContracts() {
        numContracts = 0;
    }

    public void recordContracts(final int n) {
        numContracts += n;
    }

    public void refreshNfts() {
        updatedNumNfts();
    }

    public void refreshFiles() {
        updatedNumFiles();
    }

    public void refreshSchedules() {
        updatedNumSchedules();
    }

    public void refreshStorageSlots() {
        updatedNumStorageSlots();
    }

    public void refreshTokens() {
        updatedNumTokens();
    }

    public void refreshTokenRels() {
        updatedNumTokenRels();
    }

    public void refreshTopics() {
        updatedNumTopics();
    }

    public void updateCounts() {
        // As a side effect these capture the snapshots; note the number of contracts cannot
        // be directly "read off" from anywhere, hence depends on calls to recordContracts()
        updatedNumAccounts();
        updatedNumFiles();
        updatedNumNfts();
        updatedNumSchedules();
        updatedNumStorageSlots();
        updatedNumTokens();
        updatedNumTokenRels();
        updatedNumTopics();
    }

    public void assertCreatableAccounts(final int n) {
        final var candidateNum = updatedNumAccounts() + n;
        ensure(candidateNum <= dynamicProperties.maxNumAccounts());
    }

    public boolean areCreatableAccounts(final int n) {
        return (updatedNumAccounts() + n) <= dynamicProperties.maxNumAccounts();
    }

    public void assertCreatableContracts(final int n) {
        final var candidateNum = numContracts + n;
        ensure(candidateNum <= dynamicProperties.maxNumContracts());
    }

    public boolean areCreatableFiles(final int n) {
        return (updatedNumFiles() + n) <= dynamicProperties.maxNumFiles();
    }

    public boolean areCreatableSchedules(final int n) {
        return (updatedNumSchedules() + n) <= dynamicProperties.maxNumSchedules();
    }

    public void assertCreatableTokens(final int n) {
        final var candidateNum = updatedNumTokens() + n;
        ensure(candidateNum <= dynamicProperties.maxNumTokens());
    }

    public void assertCreatableTokenRels(final int n) {
        final var candidateNum = updatedNumTokenRels() + n;
        ensure(candidateNum <= dynamicProperties.maxNumTokenRels());
    }

    public boolean areCreatableTokenRels(final int n) {
        return (updatedNumTokenRels() + n) <= dynamicProperties.maxNumTokenRels();
    }

    public void assertCreatableTopics(final int n) {
        final var candidateNum = updatedNumTopics() + n;
        ensure(candidateNum <= dynamicProperties.maxNumTopics());
    }

    public void assertMintableNfts(final int n) {
        final var candidateNum = updatedNumNfts() + n;
        validateResourceLimit(
                candidateNum <= dynamicProperties.maxNftMints(),
                MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED);
    }

    public void assertUsableTotalSlots(final long n) {
        validateResourceLimit(
                n <= dynamicProperties.maxAggregateContractKvPairs(),
                MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED);
    }

    public void assertUsableContractSlots(final long n) {
        validateResourceLimit(
                n <= dynamicProperties.maxIndividualContractKvPairs(),
                MAX_CONTRACT_STORAGE_EXCEEDED);
    }

    public double percentAccountsUsed() {
        return 100.0 * numAccounts / dynamicProperties.maxNumAccounts();
    }

    public double percentContractsUsed() {
        return 100.0 * numContracts / dynamicProperties.maxNumContracts();
    }

    public double percentFilesUsed() {
        return 100.0 * numFiles / dynamicProperties.maxNumFiles();
    }

    public double percentNftsUsed() {
        return 100.0 * numNfts / dynamicProperties.maxNftMints();
    }

    public double percentTokensUsed() {
        return 100.0 * numTokens / dynamicProperties.maxNumTokens();
    }

    public double percentTopicsUsed() {
        return 100.0 * numTopics / dynamicProperties.maxNumTopics();
    }

    public double percentStorageSlotsUsed() {
        return 100.0 * numStorageSlots / dynamicProperties.maxAggregateContractKvPairs();
    }

    public double percentTokenRelsUsed() {
        return 100.0 * numTokenRels / dynamicProperties.maxNumTokenRels();
    }

    public double percentSchedulesUsed() {
        return 100.0 * numSchedules / dynamicProperties.maxNumSchedules();
    }

    public long getNumAccounts() {
        return numAccounts;
    }

    public long getNumContracts() {
        return numContracts;
    }

    public long getNumFiles() {
        return numFiles;
    }

    public long getNumNfts() {
        return numNfts;
    }

    public long getNumTokens() {
        return numTokens;
    }

    public long getNumTopics() {
        return numTopics;
    }

    public long getNumStorageSlots() {
        return numStorageSlots;
    }

    public long getNumTokenRels() {
        return numTokenRels;
    }

    public long getNumSchedules() {
        return numSchedules;
    }

    // --- Internal helpers ---
    private long updatedNumAccounts() {
        numAccounts = stateChildren.numAccountAndContracts() - numContracts;
        return numAccounts;
    }

    private long updatedNumFiles() {
        // Each contract has a (non-file) bytecode blob; each file takes two blobs, one for
        // metadata, one for data
        numFiles = (stateChildren.numBlobs() - numContracts) / 2;
        return numFiles;
    }

    private long updatedNumNfts() {
        numNfts = stateChildren.numNfts();
        return numNfts;
    }

    private long updatedNumSchedules() {
        numSchedules = stateChildren.numSchedules();
        return numSchedules;
    }

    private long updatedNumTokens() {
        numTokens = stateChildren.numTokens();
        return numTokens;
    }

    private long updatedNumTokenRels() {
        numTokenRels = stateChildren.numTokenRels();
        return numTokenRels;
    }

    private long updatedNumTopics() {
        numTopics = stateChildren.numTopics();
        return numTopics;
    }

    private void updatedNumStorageSlots() {
        numStorageSlots = stateChildren.numStorageSlots();
    }

    private void ensure(final boolean test) {
        validateResourceLimit(test, MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
    }
}
