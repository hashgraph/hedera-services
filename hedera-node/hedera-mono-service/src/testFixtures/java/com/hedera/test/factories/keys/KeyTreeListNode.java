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

import static java.util.stream.Collectors.toList;

import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class KeyTreeListNode implements KeyTreeNode {
    protected final List<KeyTreeNode> children;

    public KeyTreeListNode(List<KeyTreeNode> children) {
        this.children = children;
    }

    public List<KeyTreeNode> getChildren() {
        return children;
    }

    @Override
    public Key asKey(KeyFactory factory) {
        return factory.newList(
                children.stream().map(node -> node.asKey(factory)).collect(toList()));
    }

    @Override
    public int numLeaves() {
        return children.stream().mapToInt(KeyTreeNode::numLeaves).sum();
    }

    @Override
    public void traverse(Predicate<KeyTreeNode> shouldVisit, Consumer<KeyTreeNode> visitor) {
        if (shouldVisit.test(this)) {
            visitor.accept(this);
        }
        for (KeyTreeNode child : children) {
            child.traverse(shouldVisit, visitor);
        }
    }
}
