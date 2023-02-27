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

import com.swirlds.common.test.benchmark.AbstractBenchmarkOperation;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.map.test.benchmark.BenchmarkAccount;
import com.swirlds.merkle.map.test.benchmark.BenchmarkKey;
import com.swirlds.merkle.map.test.benchmark.MerkleMapBenchmarkMetadata;
import java.util.Random;

/**
 * Simulate the transfer of a balance between two accounts.
 *
 * @param <A>
 * 		the type of the account
 * @param <M>
 * 		the type of the metadata
 */
public class TransferOperation<A extends BenchmarkAccount, M extends MerkleMapBenchmarkMetadata>
        extends AbstractBenchmarkOperation<MerkleMap<BenchmarkKey, A>, M> {

    private BenchmarkKey fromKey;
    private long newFromBalance;

    private BenchmarkKey toKey;
    private long newToBalance;

    private BenchmarkKey nodeFeeKey;
    private long newNodeBalance;

    private BenchmarkKey networkFeeKey;
    private long newNetworkBalance;

    public TransferOperation(final double weight) {
        super(weight);
    }

    private TransferOperation(final TransferOperation<A, M> that) {
        super(that.getWeight());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "transfer";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(final M metadata, final Random random) {
        fromKey = metadata.getRandomKey(random);
        toKey = metadata.getRandomKey(random);

        if (fromKey == null || toKey == null || fromKey == toKey) {
            abort();
            return;
        }

        newFromBalance = random.nextLong();
        newToBalance = random.nextLong();

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

        final BenchmarkAccount fromAccount = state.getForModify(fromKey);
        final BenchmarkAccount toAccount = state.getForModify(toKey);
        final BenchmarkAccount nodeAccount = state.getForModify(nodeFeeKey);
        final BenchmarkAccount networkAccount = state.getForModify(networkFeeKey);

        fromAccount.setBalance(newFromBalance);
        toAccount.setBalance(newToBalance);
        nodeAccount.setBalance(newNodeBalance);
        networkAccount.setBalance(newNetworkBalance);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransferOperation<A, M> copy() {
        return new TransferOperation<>(this);
    }
}
