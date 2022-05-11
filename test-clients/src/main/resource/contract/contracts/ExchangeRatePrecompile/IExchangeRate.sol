// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;

interface IExchangeRate {
    function toTinybars(uint256 tinycents) external returns (uint256);
    function toTinycents(uint256 tinybars) external returns (uint256);
}
