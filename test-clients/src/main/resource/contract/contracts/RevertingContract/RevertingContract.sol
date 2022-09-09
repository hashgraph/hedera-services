// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

contract RevertingContract{

    constructor(uint256 _range){
        if(_range < 5)
            revert();
    }

    function createContract(uint256 _range) public {
        new TestContract(_range);
    }
}
contract TestContract
{
    constructor(uint256 _range){
        if(_range < 5)
            revert();
    }
}