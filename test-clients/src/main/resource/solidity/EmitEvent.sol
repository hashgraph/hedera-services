pragma solidity ^0.5.3;

contract EmitEvent {

    event AnEvent(uint val);
    uint storedData;

    function set(uint x) public {

        storedData = x;

    }

    function get() public returns (uint) {

        emit AnEvent(7);
        return storedData;

    }

}

