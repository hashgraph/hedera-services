// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.7.0;

import "./HederaResponseCodes.sol";

library HederaTokenService {

    address constant precompileAddress = address(0x167);

    /// Transfers tokens from the calling account to the recipient account.
    /// @param recipient the recipient account
    /// @param amount The amount transferred, in the smallest unit the token supports
    /// @return responseCode an int from HederaResponseCodes
    function transferToken(address token, address recipient, int64 amount) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(HederaTokenServicePrecompile.transferToken.selector,
            token, recipient, amount));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    /// Mints tokens into the treasury account fo the token. Caller must have existing authorization.
    /// @param token the tokenId, in solidity form
    /// @param amount The amount minted, in the smallest unit the token supports
    /// @return responseCode an int from HederaResponseCodes
    function mintToken(address token, int64 amount) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(HederaTokenServicePrecompile.mintToken.selector,
            token, amount));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    /// Burns tokens from the treasury account fo the token. Caller must have existing authorization.
    /// @param token the tokenId, in solidity form
    /// @param amount The amount burnt, in the smallest unit the token supports
    /// @return responseCode an int from HederaResponseCodes
    function burnToken(address token, int64 amount) internal returns (int responseCode){
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(HederaTokenServicePrecompile.burnToken.selector,
            token, amount));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    /// Associates the calling address with the tokenid
    /// @param token the tokenId, in solidity form
    /// @return responseCode an int from HederaResponseCodes
    function associateToken(address token) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(HederaTokenServicePrecompile.associateToken.selector,
            token));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }


    /// Dissociates the calling address from the tokenid
    /// @param token the tokenId, in solidity form
    /// @return responseCode an int from HederaResponseCodes
    function dissociateToken(address token) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(HederaTokenServicePrecompile.dissociateToken.selector,
            token));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

}


interface HederaTokenServicePrecompile {
    function transferToken(address token, address recipient, int64 amount) external returns (int responseCode);

    function mintToken(address token, int64 amount) external returns (int responseCode);

    function burnToken(address token, int64 amount) external returns (int responseCode);

    function associateToken(address token) external returns (int responseCode);

    function dissociateToken(address token) external returns (int responseCode);
}
