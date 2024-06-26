// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract Assembly {
    fallback() external {
        address precompileAddress = address(0x16a);
        assembly {
            // Load the first 32 bytes of calldata
            let fullSelector := calldataload(0)
            // Mask to get the first 4 bytes (the function selector)
            let selector := shr(224, fullSelector)
            
            // Only redirect if the selector matches hbarAllowance(address) 
            // or hbarApprove(address,int256) 
            if or(eq(selector, 0xbbee989e), eq(selector, 0x86aff07c)) {
                // Store the 4-byte function selector redirectForToken(address,bytes) followed by
                // a placeholder for the redirect entity's address we will fix up at runtime before
                // turning the bytecode over to the EVM
                mstore(0, 0xe4cbd3a7fefefefefefefefefefefefefefefefefefefefe)

                // Start copying the delegatecall input at an 8-byte offset into memory so we skip
                // the 32 - (4 + 20) = 8 high-order bytes in the first word of memory; then copy 
                // the full calldata we received plus our 24-byte insertion above
                let result := delegatecall(gas(), precompileAddress, 8, add(24, calldatasize()), 0, 0)
                let size := returndatasize()
                returndatacopy(0, 0, size)

                switch result
                case 0 { revert(0, size) }
                default { return(0, size) }
            }
        }
    }
}
