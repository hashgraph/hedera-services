// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.9;

contract NestedCreations {
    Inner child;

    function propagate() public {
        child = new Inner();
        child.propagate();
    }
}

contract Inner {
    InnerInner grandchild;

    function propagate() public {
        grandchild = new InnerInner();
        try grandchild.propagate_abortively() {
            /* No-op */
        } catch Error(string memory) {
            /* Ignore */
        }
    }
}

contract InnerInner {
    Innermost great_grandchild;

    function propagate_abortively() public {
        great_grandchild = new Innermost();
        revert("NOPE");
    }
}

contract Innermost {
}
