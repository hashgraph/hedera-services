pragma solidity ^0.8.0;

contract SelfDestructCallable {
    constructor() payable {
    }

    function destroy() public payable {
        selfdestruct(payable(msg.sender));
    }

    function destroy(address beneficiary) public payable {
        selfdestruct(payable(beneficiary));
    }
}
