// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IHRC904UnlimitedAutoAssociations {
    function setUnlimitedAutomaticAssociations(bool enableAutoAssociations) external returns (int64 responseCode);
}