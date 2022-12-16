/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.test.factories.keys;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface KeyTreeNode {
    static KeyTreeNode from(NodeFactory factory) {
        if (factory == LeafFactory.DEFAULT_FACTORY) {
            return new KeyTreeLeaf();
        } else if (factory instanceof LeafFactory) {
            LeafFactory typedFactory = (LeafFactory) factory;
            return new KeyTreeLeaf(
                    typedFactory.isUsedToSign(),
                    typedFactory.getLabel(),
                    typedFactory.getSigType());
        } else if (factory instanceof ThresholdFactory) {
            ThresholdFactory typedFactory = (ThresholdFactory) factory;
            List<KeyTreeNode> children =
                    typedFactory.childFactories.stream()
                            .collect(mapping(KeyTreeNode::from, toList()));
            return new KeyTreeThresholdNode(children, typedFactory.M);
        } else if (factory instanceof ListFactory) {
            ListFactory typedFactory = (ListFactory) factory;
            List<KeyTreeNode> children =
                    typedFactory.childFactories.stream()
                            .collect(mapping(KeyTreeNode::from, toList()));
            return new KeyTreeListNode(children);
        }
        throw new AssertionError("Impossible factory implementation: " + factory.getClass());
    }

    int numLeaves();

    Key asKey(KeyFactory factory);

    void traverse(Predicate<KeyTreeNode> shouldVisit, Consumer<KeyTreeNode> visitor);

    default Key asKey() {
        return asKey(KeyFactory.getDefaultInstance());
    }
}
