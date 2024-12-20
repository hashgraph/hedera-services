// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaResponseCodes.sol";
import "./IHederaScheduleService.sol";

abstract contract HederaScheduleService {
    address constant precompileAddress = address(0x16b);

    /// Authorizes the calling contract as a signer to the schedule transaction.
    /// @param schedule the address of the schedule transaction.
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    function authorizeSchedule(address schedule) internal returns (int64 responseCode) {
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.authorizeSchedule.selector, schedule));
        responseCode = success ? abi.decode(result, (int64)) : HederaResponseCodes.UNKNOWN;
    }

    /// Allows for the signing of a schedule transaction given a protobuf encoded signature map
    /// The message signed by the keys is defined to be the concatenation of the shard, realm, and schedule transaction ID.
    ///
    /// @param schedule the address of the schedule transaction.
    /// @param signatureMap the protobuf encoded signature map
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    function signSchedule(address schedule, bytes memory signatureMap) internal returns (int64 responseCode) {
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaScheduleService.signSchedule.selector, schedule, signatureMap));
        responseCode = success ? abi.decode(result, (int64)) : HederaResponseCodes.UNKNOWN;
    }
}
