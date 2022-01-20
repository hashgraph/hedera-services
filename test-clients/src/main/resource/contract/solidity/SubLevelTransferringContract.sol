pragma solidity ^0.8.0;

contract SubLevel {

    constructor() payable {}

    function receiveTinybars() public payable returns (bool) {
        return true;
    }

    function nonPayableReceive() public pure returns (bool) {
        return true;
    }
}