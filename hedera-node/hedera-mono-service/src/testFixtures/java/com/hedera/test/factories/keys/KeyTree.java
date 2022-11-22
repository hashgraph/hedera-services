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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Key;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.apache.commons.codec.DecoderException;

public class KeyTree {
    private final KeyTreeNode root;

    private KeyTree(final KeyTreeNode root) {
        this.root = root;
    }

    public static KeyTree withRoot(final NodeFactory rootFactory) {
        return new KeyTree(KeyTreeNode.from(rootFactory));
    }

    public KeyTreeNode getRoot() {
        return root;
    }

    public int numLeaves() {
        return root.numLeaves();
    }

    @SuppressWarnings("unchecked")
    public void traverseLeaves(final Consumer<KeyTreeLeaf> visitor) {
        traverse(node -> node instanceof KeyTreeLeaf, node -> visitor.accept((KeyTreeLeaf) node));
    }

    public void traverse(
            final Predicate<KeyTreeNode> shouldVisit, final Consumer<KeyTreeNode> visitor) {
        root.traverse(shouldVisit, visitor);
    }

    public JKey asJKey() throws DecoderException {
        return JKey.mapKey(asKey());
    }

    public JKey asJKeyUnchecked() {
        return MiscUtils.asFcKeyUnchecked(asKey());
    }

    public Key asKey() {
        return asKey(KeyFactory.getDefaultInstance());
    }

    public Key asKey(final KeyFactory factoryToUse) {
        return root.asKey(factoryToUse);
    }
}
