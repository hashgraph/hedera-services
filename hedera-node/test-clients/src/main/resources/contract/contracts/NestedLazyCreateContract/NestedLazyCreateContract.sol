// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract NestedLazyCreateContract is HederaTokenService {

    SenderContract senderContract;

    constructor() {
        senderContract = new SenderContract();
    }

    function createTooManyHollowAccounts(address[] memory _addresses) public payable {
        uint size = _addresses.length;
        for (uint i = 0; i < size; i++) {
            _addresses[i].call{value: msg.value / size}("");
        }
    }

    function nestedLazyCreateThenSendMore(address payable _to) public payable  {

        senderContract.sendSome{value: msg.value / 2}(_to);

        (bool sent, ) = _to.call{value: msg.value / 4}("");
        require(sent, "Failed to send Ether");

        (sent, ) = _to.call{value: msg.value / 4}("");
        require(sent, "Failed to send Ether");
    }

    function nestedLazyCreateThenRevert(address payable _to) public payable  {
        senderContract.sendSomeThenRevert{value: msg.value}(_to);
    }
}

contract SenderContract {
    function sendSome(address payable _to) public payable {
        (bool sent, ) = _to.call{value: msg.value}("");
        require(sent, "Failed to send Ether");
    }

    function sendSomeThenRevert(address payable _to) public payable {
        (bool sent, ) = _to.call{value: msg.value}("");
        require(sent, "Failed to send Ether");
        revert("Sorry, not gonna happen!");
    }
}
