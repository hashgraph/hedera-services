// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./IHederaTokenService.sol";

contract KeyHelper is HederaTokenService {

    address supplyContract;

    function getKeyType(uint8 keyType) internal pure returns (uint) {
        if(keyType == 1) {
            return HederaTokenService.ADMIN_KEY_TYPE;
        } else if(keyType == 2) {
            return HederaTokenService.KYC_KEY_TYPE;
        } else if(keyType == 3) {
            return HederaTokenService.FREEZE_KEY_TYPE;
        } else if(keyType == 4) {
            return HederaTokenService.WIPE_KEY_TYPE;
        } else if(keyType == 5) {
            return HederaTokenService.SUPPLY_KEY_TYPE;
        } else if(keyType == 6) {
            return HederaTokenService.FEE_SCHEDULE_KEY_TYPE;
        } else if(keyType == 7) {
            return HederaTokenService.PAUSE_KEY_TYPE;
        }

        return 0;
    }

    function getKeyValueType(uint8 keyValueType, bytes memory key) internal view returns (IHederaTokenService.KeyValue memory keyValue) {
        if(keyValueType == 1) {
            keyValue.inheritAccountKey = true;
        } else if(keyValueType == 2) {
            keyValue.contractId = supplyContract;
        } else if(keyValueType == 3) {
            keyValue.ed25519 = key;
        } else if(keyValueType == 4) {
            keyValue.ECDSA_secp256k1 = key;
        } else if(keyValueType == 5) {
            keyValue.delegatableContractId = supplyContract;
        }
    }
}