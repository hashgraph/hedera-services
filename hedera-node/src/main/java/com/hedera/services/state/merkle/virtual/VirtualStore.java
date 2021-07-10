package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;

public interface VirtualStore<K> {

    public RootNode getRootNode(K key);

    public LeafNode getLeafByKey(K key);

    public interface Node {
        long getPath();
        Hash getHash();
        int getParentCount();
        int setParentCount();
    }

    public interface InternalNode extends Node {
        Node getLeftChild();
        Node getRightChild();
    }

    public interface LeafNode<V> extends Node {
        V getValue();
    }

    public interface RootNode extends InternalNode, SelfSerializable {
        void setVirtualStore(VirtualStore store);
    }
}
