pragma solidity ^0.8.12;

contract Whitelister {
    mapping(address => bool) public whitelist;

    function addToWhitelist(address _toBePermitted) public {
        whitelist[_toBePermitted] = true;
    }

    function isWhitelisted(address whitelister) public returns(bool) {
        return whitelist[whitelister];
    }
}
