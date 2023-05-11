// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract Create2PrecompileUser is HederaTokenService {
    User user;

    function createUser(bytes32 salt) public {
        address predictedAddress = address(uint160(uint(keccak256(abi.encodePacked(
                bytes1(0xff),
                address(this),
                salt,
                keccak256(abi.encodePacked(
                    type(User).creationCode
                ))
            )))));

        user = new User{salt: salt}();
        require(address(user) == predictedAddress);
    }

    function associateBothTo(address token_type) public {
        int myAssociation = HederaTokenService.associateToken(
            address(this), token_type);
        if (myAssociation != HederaResponseCodes.SUCCESS) {
            revert("Well, I never!");
        }
        user.associateTo(token_type);
    }

    function sendNftToUser(address nft_type, int64 sn) public returns (int) {
        return HederaTokenService.transferNFT(
            nft_type, address(this), address(user), sn);
    }

    function sendFtToUser(address ft_type, int64 amount) public returns (int) {
        return HederaTokenService.transferToken(
            ft_type, address(this), address(user), amount);
    }

    function dissociateBothFrom(address token_type) public {
        int myDissociation = HederaTokenService.dissociateToken(
            address(this), token_type);
        if (myDissociation != HederaResponseCodes.SUCCESS) {
            revert("Never ends well.");
        }
        user.dissociateFrom(token_type);
    }
}

contract User is HederaTokenService {
    function associateTo(address token_type) public {
        int userAssociation = HederaTokenService.associateToken(
            address(this), token_type);
        if (userAssociation != HederaResponseCodes.SUCCESS) {
            revert("It's unheard of...");
        }
    }

    function dissociateFrom(address token_type) public {
        int userDissociation = HederaTokenService.dissociateToken(
            address(this), token_type);
        if (userDissociation != HederaResponseCodes.SUCCESS) {
            revert("So unfair");
        }
    }

    function mintNft(address token_type, bytes[] memory metadata) external {
        (int rc, int64 newSupply, int64[] memory sns) = HederaTokenService.mintToken(token_type, 0, metadata);

        if (rc != HederaResponseCodes.SUCCESS) {
            revert("Can't!");
        }
    }

    function mintNftViaDelegate(address token_type, bytes[] memory metadata) external {
        Helper helper = new Helper();
        address(helper).delegatecall(
            abi.encodeWithSelector(Helper.mintNft.selector, token_type, metadata));
    }
}

contract Helper is HederaTokenService {
    function mintNft(address token_type, bytes[] memory metadata) external {
        (int rc, int64 newSupply, int64[] memory sns) = HederaTokenService.mintToken(token_type, 0, metadata);

        if (rc != HederaResponseCodes.SUCCESS) {
            revert("Can't even!");
        }
    }
}
