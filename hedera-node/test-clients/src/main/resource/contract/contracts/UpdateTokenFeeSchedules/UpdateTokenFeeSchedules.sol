// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./FeeHelper.sol";
import "./HederaResponseCodes.sol";
import "./HederaTokenService.sol";


contract UpdateTokenFeeSchedules is HederaTokenService, FeeHelper {

    function updateFungibleFixedHbarFee(address tokenAddress, int64 amount, address collector) external {

        IHederaTokenService.FixedFee[] memory fixedFees = new IHederaTokenService.FixedFee[](1);
        IHederaTokenService.FixedFee memory fixedHbarFee = createFixedHbarFee(amount, collector);
        fixedFees[0] = fixedHbarFee;
        IHederaTokenService.FractionalFee[] memory fractionalFees = new IHederaTokenService.FractionalFee[](0);
        int responseCode = updateFungibleTokenCustomFees(tokenAddress, fixedFees, fractionalFees);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ("Update of tokenInfo failed!");
        }
    }
}
