// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract MakeReverter {
    function doIt(uint32 l) public {
        bytes32 salt = "WTF";
        new Reverter{salt: salt}(l);
    }
}

contract Reverter {
    constructor(uint32 l) {
        if (l > 10) {
            assembly {
                revert(0, 0)
            }
        }
    }
}

