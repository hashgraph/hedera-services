// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./SafeHTS.sol";

contract SafeOperations is SafeHTS {

    event TokenCreated(address token);
    event TokenInfoEvent(IHederaTokenService.TokenInfo tokenInfo);
    event FungibleTokenInfoEvent(IHederaTokenService.FungibleTokenInfo fungibleTokenInfo);
    event NftMinted(int64 newTotalSupply, int64[] serialNumbers);


    function safeCreateNonFungibleToken(address contractIdAsAddress) external payable returns (address tokenAddress){
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);

        IHederaTokenService.KeyValue memory keyValueAdmin;
        keyValueAdmin.contractId = contractIdAsAddress;
        IHederaTokenService.TokenKey memory keyAdmin = IHederaTokenService.TokenKey(1, keyValueAdmin);

        IHederaTokenService.KeyValue memory keyValueKyc;
        keyValueKyc.contractId = contractIdAsAddress;
        IHederaTokenService.TokenKey memory keyKyc = IHederaTokenService.TokenKey(2, keyValueKyc);

        IHederaTokenService.KeyValue memory keyValueFreeze;
        keyValueFreeze.contractId = contractIdAsAddress;
        IHederaTokenService.TokenKey memory keyFreeze = IHederaTokenService.TokenKey(4, keyValueFreeze);

        IHederaTokenService.KeyValue memory keyValueWipe;
        keyValueWipe.contractId = contractIdAsAddress;
        IHederaTokenService.TokenKey memory keyWipe = IHederaTokenService.TokenKey(8, keyValueWipe);

        IHederaTokenService.KeyValue memory keyValueSupply;
        keyValueSupply.contractId = contractIdAsAddress;
        IHederaTokenService.TokenKey memory keySupply = IHederaTokenService.TokenKey(16, keyValueSupply);

        keys[0] = keyAdmin;
        keys[1] = keyKyc;
        keys[2] = keyFreeze;
        keys[3] = keyWipe;
        keys[4] = keySupply;

        IHederaTokenService.Expiry memory expiry = IHederaTokenService.Expiry(
            0, msg.sender, 8000000
        );

        IHederaTokenService.HederaToken memory token = IHederaTokenService.HederaToken(
            "tokenName", "tokenSymbol", msg.sender, "memo", true, 1000, false, keys, expiry
        );

        (tokenAddress) = safeCreateNonFungibleToken(token);
        emit TokenCreated(tokenAddress);
    }

    function mint(address token, int64 amount, bytes[] memory metadata) external returns (int64 newTotalSupply, int64[] memory serialNumbers)
    {
        (newTotalSupply, serialNumbers) = safeMintToken(token, amount, metadata);
        emit NftMinted(newTotalSupply, serialNumbers);
    }

}
