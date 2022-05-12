pragma solidity ^0.8.12;

import './Whitelister.sol';

contract Permit {
    function isWhitelisted(address whitelister) public returns (bool){
        return Whitelister(whitelister).isWhitelisted();
    }
}
