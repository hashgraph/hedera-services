/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.test.benchmark.operations;

import static com.swirlds.common.test.RandomUtils.randomByteArray;

import com.swirlds.common.test.benchmark.AbstractBenchmarkOperation;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.map.test.benchmark.AccountFactory;
import com.swirlds.merkle.map.test.benchmark.BenchmarkAccount;
import com.swirlds.merkle.map.test.benchmark.BenchmarkKey;
import com.swirlds.merkle.map.test.benchmark.MerkleMapBenchmarkMetadata;
import java.util.Random;

/**
 * Simulate the creation of an account.
 *
 * @param <A>
 * 		the type of the account
 * @param <M>
 * 		the type of the metadata
 */
public class CreateAccountOperation<A extends BenchmarkAccount, M extends MerkleMapBenchmarkMetadata>
        extends AbstractBenchmarkOperation<MerkleMap<BenchmarkKey, A>, M> {

    private BenchmarkKey fromKey;
    private long newFromBalance;

    private BenchmarkKey newAccountKey;
    private A newAccount;

    private BenchmarkKey nodeFeeKey;
    private long newNodeBalance;

    private BenchmarkKey networkFeeKey;
    private long newNetworkBalance;

    private final int dataSize;

    private AccountFactory<A> accountFactory;

    public CreateAccountOperation(final double weight, final int dataSize, final AccountFactory<A> accountFactory) {
        super(weight);
        this.dataSize = dataSize;
        this.accountFactory = accountFactory;
    }

    private CreateAccountOperation(final CreateAccountOperation<A, M> that) {
        super(that.getWeight());
        this.dataSize = that.dataSize;
        this.accountFactory = that.accountFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "createAccount";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(final M metadata, final Random random) {
        fromKey = metadata.getRandomKey(random);
        newFromBalance = random.nextLong();

        newAccountKey = metadata.getNewKey();
        newAccount = accountFactory.buildAccount(random.nextLong(), randomByteArray(random, dataSize));

        nodeFeeKey = metadata.getRandomNodeFeeKey(random);
        newNodeBalance = random.nextLong();

        networkFeeKey = metadata.getNetworkFeeKey();
        newNetworkBalance = random.nextLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final MerkleMap<BenchmarkKey, A> state) {

        // Simulate fees for the account creation
        final A fromAccount = state.getForModify(fromKey);
        final A nodeAccount = state.getForModify(nodeFeeKey);
        final A networkAccount = state.getForModify(networkFeeKey);

        fromAccount.setBalance(newFromBalance);
        nodeAccount.setBalance(newNodeBalance);
        networkAccount.setBalance(newNetworkBalance);

        // Actually create the account
        state.put(newAccountKey, newAccount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CreateAccountOperation<A, M> copy() {
        return new CreateAccountOperation<>(this);
    }
}
