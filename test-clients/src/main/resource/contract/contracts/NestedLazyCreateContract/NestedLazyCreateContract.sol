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

    function createTooManyHollowAccountsViaSend(address payable[] memory _addresses) public payable {
        uint size = _addresses.length;
        for (uint i = 0; i < size; i++) {
            _addresses[i].send(msg.value / size);
        }
    }

    function createTooManyHollowAccountsViaTransfer(address payable[] memory _addresses) public payable {
        uint size = _addresses.length;
        for (uint i = 0; i < size; i++) {
            _addresses[i].transfer(msg.value / size);
        }
    }

    function nestedLazyCreateThenSendMore(address payable _to) public payable  {

        senderContract.sendSome{value: msg.value / 2}(_to);

        (bool sent, ) = _to.call{value: msg.value / 4}("");
        require(sent, "Failed to send Ether");

        (sent, ) = _to.call{value: msg.value / 4}("");
        require(sent, "Failed to send Ether");
    }

    function nestedLazyCreateThenSendMoreViaSend(address payable _to) public payable  {

        senderContract.sendSomeViaSend{value: msg.value / 2}(_to);

        bool sent = _to.send(msg.value / 4);
        require(sent, "Failed to send Ether");

        sent = _to.send(msg.value / 4);
        require(sent, "Failed to send Ether");
    }


    function nestedLazyCreateThenSendMoreViaTransfer(address payable _to) public payable  {
        senderContract.sendSomeViaTransfer{value: msg.value / 2}(_to);

        _to.transfer(msg.value / 4);

        _to.transfer(msg.value / 4);
    }

    function nestedLazyCreateThenRevert(address payable _to) public payable  {
        senderContract.sendSomeThenRevert{value: msg.value}(_to);
    }

    function nestedLazyCreateViaSendThenRevert(address payable _to) public payable  {
        senderContract.sendSomeThenRevertViaSend{value: msg.value}(_to);
    }

    function nestedLazyCreateViaTransferThenRevert(address payable _to) public payable  {
        senderContract.sendSomeThenRevertViaTransfer{value: msg.value}(_to);
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

    function sendSomeViaSend(address payable _to) public payable {
        bool sent = _to.send(msg.value);
        require(sent, "Failed to send Ether");
    }

    function sendSomeThenRevertViaSend(address payable _to) public payable {
        bool sent = _to.send(msg.value);
        require(sent, "Failed to send Ether");
        revert("Sorry, not gonna happen!");
    }

    function sendSomeViaTransfer(address payable _to) public payable {
        // This function is no longer recommended for sending Ether.
        _to.transfer(msg.value);
    }

    function sendSomeThenRevertViaTransfer(address payable _to) public payable {
        _to.transfer(msg.value);
        revert("Sorry, not gonna happen!");
    }
}
