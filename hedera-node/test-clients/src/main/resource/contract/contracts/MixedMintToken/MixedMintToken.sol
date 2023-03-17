import "./IHederaTokenService.sol";

contract MixedMintTokenContract {

    address constant precompileAddress = address(0x167);

    function mintTokenCall(uint64 amount, address tokenAddress) public
        returns (bool success, bytes memory result) {
    (success, result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
            tokenAddress, amount, new bytes[](0)));
    }

    function mintTokenDelegateCall(uint64 amount, address tokenAddress) public
        returns (bool success, bytes memory result) {
    (success, result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
            tokenAddress, amount, new bytes[](0)));
    }
}