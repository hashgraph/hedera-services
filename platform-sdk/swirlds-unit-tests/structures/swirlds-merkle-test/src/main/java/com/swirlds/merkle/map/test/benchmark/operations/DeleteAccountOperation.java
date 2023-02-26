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
 * Simulate the deletion of an account.
 *
 * @param <A>
 * 		the type of the account
 * @param <M>
 * 		the type of the metadata
 */
public class DeleteAccountOperation<A extends BenchmarkAccount, M extends MerkleMapBenchmarkMetadata>
        extends AbstractBenchmarkOperation<MerkleMap<BenchmarkKey, A>, M> {

    private BenchmarkKey deleteKey;

    public DeleteAccountOperation(final double weight) {
        super(weight);
    }

    private DeleteAccountOperation(final DeleteAccountOperation<A, M> that) {
        super(that.getWeight());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "deleteAccount";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(final M metadata, final Random random) {
        deleteKey = metadata.getRandomKey(random);
        if (metadata.isKeyUndeletable(deleteKey)) {
            abort();
            return;
        }
        metadata.deleteKey(deleteKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final MerkleMap<BenchmarkKey, A> state) {
        state.remove(deleteKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DeleteAccountOperation<A, M> copy() {
        return new DeleteAccountOperation<>(this);
    }
}
