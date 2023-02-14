// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract RecursiveCreate2 {
    RecursiveCreator recursiveCreator;

    function createRecursiveCreator(bytes32 salt) public {
        address source = address(this);
        recursiveCreator = new RecursiveCreator{salt: salt}(source, salt);
    }
}

contract RecursiveCreator {
    constructor(address source, bytes32 salt) {
        RecursiveCreate2 parent = RecursiveCreate2(source);
        parent.createRecursiveCreator(salt);
    }    
}

