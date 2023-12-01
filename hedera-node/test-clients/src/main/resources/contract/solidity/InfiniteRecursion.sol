pragma solidity ^0.5.0;
contract InfiniteRecursion {
    uint256 pickle = 1;

    function this1() public {
        that1();
    }

    function that1() public {
        pickle = pickle + 1;
        this1();
    }
}