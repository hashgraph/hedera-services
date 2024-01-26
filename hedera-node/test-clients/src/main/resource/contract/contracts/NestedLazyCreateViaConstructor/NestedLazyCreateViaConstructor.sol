// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract NestedLazyCreateViaConstructor {

    constructor(address _address) public payable {
        (bool sent, ) = _address.call{value: msg.value}("");
        require(sent, "Failed to send value");
    }
}
