// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;

import "./SelfFunding.sol";

contract ExchangeRatePrecompile is SelfFunding {
    // The USD in cents that must be sent as msg.value
    uint256 toll;

    constructor(uint256 _toll) {
        toll = _toll;
    }

    function gatedAccess() external payable costsCents(toll) {
        // Hope it was worth it!
    }

    function approxUsdValue() external payable returns (uint256 tinycents) {
        tinycents = tinybarsToTinycents(msg.value);
    }

    function callWithValue(uint256 _value) external payable {
        callToPrecompileWithValue(_value);
    }

    function invalidCall() external payable {
        // Should fail, this is not a valid selector 
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.call(
            abi.encodeWithSelector(ExchangeRatePrecompile.approxUsdValue.selector));
        require(success);
    }
}
