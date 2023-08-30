package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import org.apache.tuweni.bytes.Bytes;

/**
 * Event signatures used by the HTS system contract.
 */
public class HtsEventSignatures {
    private HtsEventSignatures() {
        throw new UnsupportedOperationException("Utility class");
    }

    // Transfer(address indexed from, address indexed to, uint256 indexed tokenId)
    // Transfer(address indexed from, address indexed to, uint256 value)
    public static final Bytes TRANSFER_EVENT =
            Bytes.fromHexString("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
    // Approval(address indexed owner, address indexed spender, uint256 value)
    // Approval(address indexed owner, address indexed approved, uint256 indexed tokenId)
    public static final Bytes APPROVAL_EVENT =
            Bytes.fromHexString("8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925");
    // ApprovalForAll(address indexed owner, address indexed operator, bool approved)
    public static final Bytes APPROVAL_FOR_ALL_EVENT =
            Bytes.fromHexString("17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31");
}
