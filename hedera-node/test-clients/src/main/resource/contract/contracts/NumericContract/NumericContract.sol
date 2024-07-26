// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

contract NumericContract {

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

}
