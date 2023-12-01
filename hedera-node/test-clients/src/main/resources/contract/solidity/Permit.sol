pragma solidity ^0.8.7;

import './Whitelister.sol';

contract Permit {
    function isWhitelisted(address whitelister) public returns (bool){
        return Whitelister(whitelister).isSenderWhitelisted();
    }
}
