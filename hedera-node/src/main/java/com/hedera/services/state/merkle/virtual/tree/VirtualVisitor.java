package com.hedera.services.state.merkle.virtual.tree;

import com.swirlds.common.crypto.Hashable;

public class VirtualVisitor<K, V extends Hashable> {
    public void visitParent(VirtualTreeInternal<K, V> parent) {

    }

    public void visitLeaf(VirtualTreeLeaf<K, V> leaf) {

    }

    public void visitUncreated(VirtualTreePath uncreated) {

    }
}
