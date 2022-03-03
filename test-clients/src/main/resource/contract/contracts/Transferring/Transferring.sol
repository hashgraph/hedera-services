pragma solidity ^0.5.0;

contract TransferringContract {

    constructor() public payable {}

    function() external payable {}

    function transferToAddress(address payable _address, uint256 _amount) public payable {
        _address.transfer(_amount);
    }
}