/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle.singleton;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.StateUtils;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.utility.Labeled;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A merkle node with a string (the label) as the left child, and the merkle node value as the right
 * child. We actually support a raw type (any type!) as the value, and we serialize it and put it
 * into a simple merkle node.
 *
 * @param <T> The value type
 */
public class SingletonNode<T> extends PartialBinaryMerkleInternal implements Labeled, MerkleInternal {
    private static final long CLASS_ID = 0x3832CC837AB77BFL;
    public static final int CLASS_VERSION = 1;

    // Only exists for constructable registry as it works today. Remove ASAP!
    @Deprecated(forRemoval = true)
    public SingletonNode() {
        setLeft(new StringLeaf());
        setRight(null);
    }

    public SingletonNode(@NonNull final StateMetadata<?, T> md, @NonNull final T value) {
        setLeft(new StringLeaf(
                StateUtils.computeLabel(md.serviceName(), md.stateDefinition().stateKey())));
        setRight(new ValueLeaf<T>(md, value));
    }

    private SingletonNode(@NonNull final SingletonNode<T> other) {
        this.setLeft(other.getLeft().copy());
        this.setRight(other.getRight().copy());
    }

    @Override
    public SingletonNode<T> copy() {
        return new SingletonNode<>(this);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    @Override
    public String getLabel() {
        final StringLeaf left = getLeft();
        return left.getLabel();
    }

    public T getValue() {
        final ValueLeaf<T> right = getRight();
        return right.getValue();
    }

    public void setValue(T value) {
        ValueLeaf<T> right = getRight();
        right.setValue(value);
    }
}
