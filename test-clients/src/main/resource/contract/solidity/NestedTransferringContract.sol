pragma solidity ^0.5.0;

contract NestedTransferringContract {

    NestedContract1 nestedContract1;
    NestedContract2 nestedContract2;

    constructor(address payable _nestedContract1, address payable _nestedContract2) public payable {
        nestedContract1 = NestedContract1(_nestedContract1);
        nestedContract2 = NestedContract2(_nestedContract2);
    }

    function() external payable {}


    function transferFromDifferentAddressesToAddress(address payable _address, uint256 _amount) public payable {
        _address.transfer(_amount);
        nestedContract1.transferToAddress(_address, _amount/2);
        nestedContract2.transferToAddress(_address, _amount/2);
    }

    function transferFromAndToDifferentAddresses(address payable receiver1, address payable receiver2,
        address payable receiver3, uint256 _amount) public payable {
        receiver1.transfer(_amount);
        nestedContract1.transferToAddress(receiver1, _amount/2);
        nestedContract2.transferToAddress(receiver1, _amount/2);

        receiver2.transfer(_amount);
        nestedContract1.transferToAddress(receiver2, _amount/2);
        nestedContract2.transferToAddress(receiver2, _amount/2);

        receiver3.transfer(_amount);
        nestedContract1.transferToAddress(receiver3, _amount/2);
        nestedContract2.transferToAddress(receiver3, _amount/2);
    }
}

contract NestedContract1 {

    constructor() public payable {}

    function() external payable {}

    function transferToAddress(address payable _address, uint256 _amount) public payable {
        _address.transfer(_amount);
    }
}

contract NestedContract2 {

    constructor() public payable {}

    function() external payable {}

    function transferToAddress(address payable _address, uint256 _amount) public payable {
        _address.transfer(_amount);
    }
}