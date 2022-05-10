// SPDX-License-Identifier: Apache-2.0

import "./RateAware.sol";

contract ExchangeRatePrecompile is RateAware {
    // The USD in cents that must be sent as msg.value
    uint64 v;

    constructor(uint64 _v) {
        v = _v;
    }

    function gatedAccess() external payable costsCents(v) {
        // Hope it was worth it!
    }

    function approxUsdValue() external payable returns (int64 tinycents) {
        (, tinycents) = toTinycents(int64(uint64(msg.value)));
    }
}
