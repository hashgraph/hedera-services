// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract OuterCreator {
    bytes32 constant outerSalt = 0x0101010101010100101010101010100101010101010101110101010101010101;

    function startChain(bytes memory logMessage) public {
        new InnerCreator{salt: outerSalt}(logMessage);
    }
}

contract InnerCreator {
    bytes32 constant innerSalt = 0x0202020202020200202020202020200202020202020202220202020202020202;

    constructor(bytes memory logMessage) {
        assembly {
            log0(logMessage, 0x01)
            log1(logMessage, 0x01, 0xAA)
            log2(logMessage, 0x01, 0xAA, 0xBB)
        }
        new FinalCreation{salt: innerSalt}(logMessage);
        selfdestruct(payable(msg.sender));
    }
}

contract FinalCreation {
    constructor(bytes memory logMessage) {
        assembly {
            log3(logMessage, 0x01, 0xAA, 0xBB, 0xCC)
            log4(logMessage, 0x01, 0xAA, 0xBB, 0xCC, 0xDD)
        }
    }
}
