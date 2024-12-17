pragma solidity ^0.8.0;

contract JustSend {
    function sendTo(uint64 account_num, uint64 value) public payable {
        address payable beneficiary = payable(address(uint160(account_num)));
        beneficiary.transfer(value);
    }
}
