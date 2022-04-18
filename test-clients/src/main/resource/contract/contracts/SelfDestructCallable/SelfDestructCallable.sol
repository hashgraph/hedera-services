pragma solidity ^0.8.0;

contract SelfDestructCallable {
    constructor() payable {
    }

    function destroy() public payable {
        selfdestruct(payable(msg.sender));
    }
}
