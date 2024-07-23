// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

contract NumericContract {

    /*´:°•.°+.*•´.*:˚.°*.˚•´.°:°•.°•.*•´.*:˚.°*.˚•´.°:°•.°+.*•´.*:*/
    /*               Non-static Simple HTS functions              */
    /*.•°:°.´+˚.*°.˚:*.´•*.+°.•°:´*.´•*.•°.•°:°.´:•˚°.*°.˚:*.´+°.•*/
    function approveRedirect(address token, address account, uint256 amount) public {
        (bool success, bytes memory result) = address(token).call(abi.encodeWithSignature("approve(address,uint256)", account, amount));

        if (success == false) {
            revert();
        }
    }

    function approve(address token, address account, uint256 amount) public {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("approve(address,address,uint256)", token, account, amount));

        if (success == false) {
            revert();
        }
    }

    function approveNFT(address token, address account, uint256 amount) public {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("approveNFT(address,address,uint256)", token, account, amount));

        if (success == false) {
            revert();
        }
    }

    function burnTokenV1(address token, uint64 amount, int64[] memory serialNumbers) public {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("burnToken(address,uint64,int64[])", token, amount, serialNumbers));

        if (success == false) {
            revert();
        }
    }

    function burnTokenV2(address token, int64 amount, int64[] memory serialNumbers) public {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("burnToken(address,int64,int64[])", token, amount, serialNumbers));

        if (success == false) {
            revert();
        }
    }

    function mintTokenV1(address token, uint64 amount, bytes[] memory metadata) public {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("mintToken(address,uint64,bytes[])", token, amount, metadata));

        if (success == false) {
            revert();
        }
    }

    function mintTokenV2(address token, int64 amount, bytes[] memory metadata) public {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("mintToken(address,int64,bytes[])", token, amount, metadata));

        if (success == false) {
            revert();
        }
    }

    function wipeFungibleV1(address token, address account, uint32 amount) public {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("wipeTokenAccount(address,address,uint32)", token, account, amount));

        if (success == false) {
            revert();
        }
    }

    function wipeFungibleV2(address token, address account, int64 amount) public {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("wipeTokenAccount(address,address,int64)", token, account, amount));

        if (success == false) {
            revert();
        }
    }

    function wipeNFT(address token, address account, int64[] memory serialNumbers) public {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("wipeTokenAccountNFT(address,address,int64[])", token, account, serialNumbers));

        if (success == false) {
            revert();
        }
    }

    /*´:°•.°+.*•´.*:˚.°*.˚•´.°:°•.°•.*•´.*:˚.°*.˚•´.°:°•.°+.*•´.*:*/
    /*                    Static HTS functions                    */
    /*.•°:°.´+˚.*°.˚:*.´•*.+°.•°:´*.´•*.•°.•°:°.´:•˚°.*°.˚:*.´+°.•*/
    function getTokenKey(address token, uint keyType) public view {
        (bool success, bytes memory result) = address(0x167).staticcall(abi.encodeWithSignature("getTokenKey(address,uint)", token, keyType));

        if (success == false) {
            revert();
        }
    }

    function getNonFungibleTokenInfo(address token, int64 serialNumber) public view {
        (bool success, bytes memory result) = address(0x167).staticcall(abi.encodeWithSignature("getNonFungibleTokenInfo(address,int64)", token, serialNumber));

        if (success == false) {
            revert();
        }
    }

    function getApproved(address token, uint256 serialNumber) public view {
        (bool success, bytes memory result) = address(0x167).staticcall(abi.encodeWithSignature("getApproved(address,uint256)", token, serialNumber));

        if (success == false) {
            revert();
        }
    }

    function getApprovedERC(address token, uint256 serialNumber) public view {
        (bool success, bytes memory result) = token.staticcall(abi.encodeWithSignature("getApproved(uint256)", serialNumber));

        if (success == false) {
            revert();
        }
    }

    function tokenURI(address token, uint256 _tokenId) public view {
        (bool success, bytes memory result) = token.staticcall(abi.encodeWithSignature("tokenURI(uint256)", _tokenId));

        if (success == false) {
            revert();
        }
    }

    function ownerOf(address token, uint256 _tokenId) public view {
        (bool success, bytes memory result) = token.staticcall(abi.encodeWithSignature("ownerOf(uint256)", _tokenId));

        if (success == false) {
            revert();
        }
    }

}
