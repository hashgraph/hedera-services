// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./hip-206/HederaTokenService.sol";
import "./hip-206/HederaResponseCodes.sol";


contract NestedLazyCreateViaConstructor is HederaTokenService {

    constructor(address _address) public payable {
        (bool sent, ) = _address.call{value: msg.value}("");
        require(sent, "Failed to send value");
    }
}
