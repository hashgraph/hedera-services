// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;
pragma experimental ABIEncoderV2;

interface IHederaAccountService {

    /// Returns the amount of hbar that the spender has been authorized to spend on behalf of the owner.
    /// @param owner The account that has authorized the spender
    /// @param spender The account that has been authorized by the owner
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    /// @return amount The amount of hbar that the spender has been authorized to spend on behalf of the owner.
    function hbarAllowance(address owner, address spender)
    external
    returns (int64 responseCode, int256 amount);

    /// Allows spender to withdraw hbars from the owner account multiple times, up to the value amount. If this function is called
    /// again it overwrites the current allowance with the new amount.
    /// @param owner The owner of the hbars
    /// @param spender the account address authorized to spend
    /// @param amount the amount of tokens authorized to spend.
    /// @return responseCode The response code for the status of the request. SUCCESS is 22.
    function hbarApprove(
        address owner,
        address spender,
        int256 amount
    ) external returns (int64 responseCode);

    /// Determines if the signature is valid for the given message hash and account.
    /// It is assumed that the signature is composed of a single EDCSA or ED25519 key.
    /// @param account The account to check the signature against
    /// @param messageHash The hash of the message to check the signature against
    /// @param signature The signature to check
    /// @return response True if the signature is valid, false otherwise
    function isAuthorizedRaw(
        address account,
        bytes memory messageHash,
        bytes memory signature) external returns (bool response);
}
