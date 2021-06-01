package com.hedera.services.state.merkle.virtual;

public interface VirtualTreeNode {
    VirtualTreeInternal getParent();
    void adopt(Path path, VirtualTreeInternal parent);
    Path getPath();
}
