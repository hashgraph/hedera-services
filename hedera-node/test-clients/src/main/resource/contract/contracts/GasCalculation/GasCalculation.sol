//SPDX-License-Identifier: MIT
pragma solidity ^0.8.12;

contract GasCalculation {

    mapping(address => uint256) public contributions;

    function donate(address _to) external payable returns (bool success) {
        require(msg.sender != _to, "cannot donate to self");
        contributions[_to] += msg.value;
        return true;
    }
}