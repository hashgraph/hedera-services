//SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./IHederaTokenService.sol";
import "./NumericHelper.sol";
import "./KeyHelper.sol";

contract NumericContractComplex is KeyHelper {

    int32 public constant SUCCESS_CODE = 22;

    /*´:°•.°+.*•´.*:˚.°*.˚•´.°:°•.°•.*•´.*:˚.°*.˚•´.°:°•.°+.*•´.*:*/
    /*             Utilities for building HederaToken             */
    /*.•°:°.´+˚.*°.˚:*.´•*.+°.•°:´*.´•*.•°.•°:°.´:•˚°.*°.˚:*.´+°.•*/
    function buildFixedFeeV1(uint32 amount) private returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee = IHederaTokenService.FixedFee(amount, address(0), true, true, msg.sender);
    }

    function buildFixedFeeV2(int64 amount) private returns (NumericHelper.FixedFeeV2 memory fixedFee) {
        fixedFee = NumericHelper.FixedFeeV2(amount, address(0), true, true, msg.sender);
    }

    function buildTokenV1(uint32 expirySecond, uint32 expiryRenew, uint32 maxSupply)
    private
    returns (IHederaTokenService.HederaToken memory token)
    {
        token = IHederaTokenService.HederaToken({
            name: "NAME",
            symbol: "SYMBOL",
            treasury: address(this),
            memo: "MEMO",
            tokenSupplyType: true,
            maxSupply: maxSupply,
            freezeDefault: false,
            tokenKeys: super.getDefaultKeys(),
            expiry: IHederaTokenService.Expiry(expirySecond, address(this), expiryRenew)
        });
    }

    function buildTokenV2(uint32 expirySecond, uint32 expiryRenew, int64 maxSupply)
    private
    returns (NumericHelper.HederaTokenV2 memory token)
    {
        token = NumericHelper.HederaTokenV2({
            name: "NAME",
            symbol: "SYMBOL",
            treasury: address(this),
            memo: "MEMO",
            tokenSupplyType: true,
            maxSupply: maxSupply,
            freezeDefault: false,
            tokenKeys: super.getDefaultKeys(),
            expiry: IHederaTokenService.Expiry(expirySecond, address(this), expiryRenew)
        });
    }

    function buildTokenV3(int64 expirySecond, int64 expiryRenew, int64 maxSupply)
    private
    returns (NumericHelper.HederaTokenV3 memory token)
    {
        token = NumericHelper.HederaTokenV3({
            name: "NAME",
            symbol: "SYMBOL",
            treasury: address(this),
            memo: "MEMO",
            tokenSupplyType: true,
            maxSupply: maxSupply,
            freezeDefault: false,
            tokenKeys: super.getDefaultKeys(),
            expiry: NumericHelper.ExpiryV2(expirySecond, address(this), expiryRenew)
        });
    }

    /*´:°•.°+.*•´.*:˚.°*.˚•´.°:°•.°•.*•´.*:˚.°*.˚•´.°:°•.°+.*•´.*:*/
    /*         Non-static Complex HTS functions - Create          */
    /*.•°:°.´+˚.*°.˚:*.´•*.+°.•°:´*.´•*.•°.•°:°.´:•˚°.*°.˚:*.´+°.•*/
    function createFungibleTokenWithCustomFeesFixedFee(address token, uint32 fixedFee) public {
        IHederaTokenService.HederaToken memory token = buildTokenV1({
            expirySecond: 0, expiryRenew: 10000, maxSupply: 10000});

        IHederaTokenService.FixedFee memory _fixedFee = buildFixedFeeV1(fixedFee);
        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(IHederaTokenService.createFungibleTokenWithCustomFees.selector, token, uint(100), uint(2), _fixedFee, new IHederaTokenService.FractionalFee[](0))
        );

        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    function createFungibleTokenWithCustomFeesFractionalFeeEmptyExpiry(address token) public {
        IHederaTokenService.HederaToken memory token = buildTokenV1({
            expirySecond: 0, expiryRenew: 0, maxSupply: 10000});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(IHederaTokenService.createFungibleTokenWithCustomFees.selector, token, uint(100), uint(2), buildFixedFeeV1(1), new IHederaTokenService.FractionalFee[](0))
        );

        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    function createFungibleTokenWithCustomFeesFractionalFee(address token, uint32 numerator, uint32 denominator) public {
        IHederaTokenService.HederaToken memory token = buildTokenV1({
            expirySecond: 0, expiryRenew: 10000, maxSupply: 10000});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(IHederaTokenService.createFungibleTokenWithCustomFees.selector, token, uint(100), uint(2), new IHederaTokenService.FixedFee[](0), new IHederaTokenService.FractionalFee[](0))
        );

        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    function createFungibleTokenWithCustomFeesV3WithNegativeFixedFee(address token) public {
        NumericHelper.HederaTokenV3 memory token = buildTokenV3({
            expirySecond: 0, expiryRenew: 10_000, maxSupply: 10000});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(NumericHelper.createFungibleTokenWithCustomFeesV3.selector, token, int64(100), int64(2), buildFixedFeeV2(int64(-1)), new NumericHelper.FractionalFeeV2[](0))
        );

        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    // Note: We skip V2 test, as its already validated via normal create flow.
    function createFungibleTokenWithCustomFeesV3FractionalFee(address token, int64 numerator, int64 denominator, int64 minimumAmount, int64 maximumAmount) public {
        NumericHelper.HederaTokenV3 memory token = buildTokenV3({
            expirySecond: 0, expiryRenew: 0, maxSupply: 10000});

        NumericHelper.FractionalFeeV2[] memory fractionalFees = new NumericHelper.FractionalFeeV2[](1);
        fractionalFees[0] = NumericHelper.FractionalFeeV2(numerator, denominator, minimumAmount, maximumAmount, false, address(this));

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(NumericHelper.createFungibleTokenWithCustomFeesV3.selector, token, int64(100), int64(2), new NumericHelper.FixedFeeV2[](0), fractionalFees));
        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    function createFungibleToken(uint32 _expirySecond, uint32 _expiryRenew, uint32 _maxSupply, uint initialTotalSupply, uint decimals) public {
        IHederaTokenService.HederaToken memory token = buildTokenV1({
            expirySecond: _expirySecond, expiryRenew: _expiryRenew, maxSupply: _maxSupply});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(IHederaTokenService.createFungibleToken.selector, token, initialTotalSupply, decimals)
        );
        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    function createFungibleTokenV2(int64 _maxSupply, uint64 initialTotalSupply, uint32 decimals) public {
        NumericHelper.HederaTokenV2 memory token = buildTokenV2({
            expirySecond: 0, expiryRenew: 10000, maxSupply: _maxSupply});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(NumericHelper.createFungibleTokenV2.selector, token, initialTotalSupply, decimals)
        );
        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    function createFungibleTokenV3(int64 _expirySecond, int64 _expiryRenew, int64 _maxSupply, int64 initialTotalSupply, int32 decimals) public {
        NumericHelper.HederaTokenV3 memory token = buildTokenV3({
            expirySecond: _expirySecond, expiryRenew: _expiryRenew, maxSupply: _maxSupply});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(NumericHelper.createFungibleTokenV3.selector, token, initialTotalSupply, decimals)
        );
        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    function createNonFungibleToken(uint32 _expirySecond, uint32 _expiryRenew, uint32 _maxSupply) public {
        IHederaTokenService.HederaToken memory token = buildTokenV1({
            expirySecond: _expirySecond, expiryRenew: _expiryRenew, maxSupply: _maxSupply});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(IHederaTokenService.createNonFungibleToken.selector, token)
        );
        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    function createNonFungibleTokenV2(uint32 _expirySecond, uint32 _expiryRenew, int64 _maxSupply) public {
        NumericHelper.HederaTokenV2 memory token = buildTokenV2({
            expirySecond: _expirySecond, expiryRenew: _expiryRenew, maxSupply: _maxSupply});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(NumericHelper.createNonFungibleTokenV2.selector, token)
        );
        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    function createNonFungibleTokenV3(int64 _expirySecond, int64 _expiryRenew, int64 _maxSupply) public {
        NumericHelper.HederaTokenV3 memory token = buildTokenV3({
            expirySecond: _expirySecond, expiryRenew: _expiryRenew, maxSupply: _maxSupply});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(NumericHelper.createNonFungibleTokenV3.selector, token)
        );
        (int32 responseCode, address addressToken) =
            success
                ? abi.decode(result, (int32, address))
                : (int32(0), address(0));
        require(responseCode == SUCCESS_CODE);
    }

    /*´:°•.°+.*•´.*:˚.°*.˚•´.°:°•.°•.*•´.*:˚.°*.˚•´.°:°•.°+.*•´.*:*/
    /*         Non-static Complex HTS functions - Update          */
    /*.•°:°.´+˚.*°.˚:*.´•*.+°.•°:´*.´•*.•°.•°:°.´:•˚°.*°.˚:*.´+°.•*/
    function updateTokenInfoV1(address token, uint32 _expirySecond, uint32 _expiryRenew, uint32 _maxSupply) public {
        IHederaTokenService.HederaToken memory newToken = buildTokenV1({
            expirySecond: _expirySecond, expiryRenew: _expiryRenew, maxSupply: _maxSupply});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(IHederaTokenService.updateTokenInfo.selector, token, newToken));

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

    function updateTokenInfoV2(address token, uint32 _expirySecond, uint32 _expiryRenew, int64 _maxSupply) public {
        NumericHelper.HederaTokenV2 memory newToken = buildTokenV2({
            expirySecond: _expirySecond, expiryRenew: _expiryRenew, maxSupply: _maxSupply});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(NumericHelper.updateTokenInfoV2.selector, token, newToken));

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

    function updateTokenInfoV3(address token, int64 _expirySecond, int64 _expiryRenew, int64 _maxSupply) public {
        NumericHelper.HederaTokenV3 memory newToken = buildTokenV3({
            expirySecond: _expirySecond, expiryRenew: _expiryRenew, maxSupply: _maxSupply});

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(NumericHelper.updateTokenInfoV3.selector, token, newToken));

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

    /*´:°•.°+.*•´.*:˚.°*.˚•´.°:°•.°•.*•´.*:˚.°*.˚•´.°:°•.°+.*•´.*:*/
    /*        Non-static Complex HTS functions - Transfer         */
    /*.•°:°.´+˚.*°.˚:*.´•*.+°.•°:´*.´•*.•°.•°:°.´:•˚°.*°.˚:*.´+°.•*/
    function cryptoTransferFungibleV1(address tokenToTransfer, int64[] memory amounts, address sender, address receiver) public {
        IHederaTokenService.AccountAmount[] memory accountAmounts = new IHederaTokenService.AccountAmount[](2);
        accountAmounts[0] = IHederaTokenService.AccountAmount(sender, amounts[0]);
        accountAmounts[1] = IHederaTokenService.AccountAmount(receiver, amounts[1]);

        IHederaTokenService.TokenTransferList[] memory tokenTransfers = new IHederaTokenService.TokenTransferList[](1);
        tokenTransfers[0] = IHederaTokenService.TokenTransferList(tokenToTransfer, accountAmounts, new IHederaTokenService.NftTransfer[](0));

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(IHederaTokenService.cryptoTransfer.selector, tokenTransfers));

        int64 responseCode = abi.decode(result, (int64));
        require(responseCode == SUCCESS_CODE);
    }

    /* CryptoTransferV2 allows to specify Hbars for the transfer, instead of just tokens */
    function cryptoTransferV2(int64[] memory amounts, address sender, address receiver) public {
        NumericHelper.AccountAmount[] memory accountAmounts = new NumericHelper.AccountAmount[](2);
        accountAmounts[0] = NumericHelper.AccountAmount(sender, amounts[0], false);
        accountAmounts[1] = NumericHelper.AccountAmount(receiver, amounts[1], false);

        NumericHelper.TransferList memory hbarTransfers = NumericHelper.TransferList(accountAmounts);

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(NumericHelper.cryptoTransferV2.selector, hbarTransfers, new IHederaTokenService.TokenTransferList[](0)));

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

    function cryptoTransferNonFungible(address token, address sender, address receiver, int64 serialNumber) public {
        IHederaTokenService.NftTransfer[] memory nftTransfers = new IHederaTokenService.NftTransfer[](1);
        nftTransfers[0] = IHederaTokenService.NftTransfer(sender, receiver, serialNumber);

        IHederaTokenService.TokenTransferList[] memory tokenTransfers = new IHederaTokenService.TokenTransferList[](1);
        tokenTransfers[0] = IHederaTokenService.TokenTransferList(token, new IHederaTokenService.AccountAmount[](0), nftTransfers);

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(IHederaTokenService.cryptoTransfer.selector, tokenTransfers));

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

    function transferTokenTest(address token, address sender, address receiver, int64 amount) public {
        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSignature("transferToken(address,address,address,int64)", token, sender, receiver, amount)
        );

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

    function transferTokenERC(address token, address sender, address receiver, uint256 amount) public {
        (bool success, bytes memory result) =
                                address(token).call(abi.encodeWithSignature("transfer(address,address,uint256)", token, receiver, amount));

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

    function transferNFTs(
        address token,
        address sender,
        address receiver,
        int64[] memory serialNumbers
    ) public {
        address[] memory senders = new address[](1);
        senders[0] = sender;

        address[] memory receivers = new address[](1);
        receivers[0] = receiver;

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSignature(
                "transferNFTs(address,address[],address[],int64[])", token, senders, receivers, serialNumbers
            )
        );

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

    function transferNFTTest(address token, address sender, address receiver, int64 serialNumber) public {
        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSignature("transferNFT(address,address,address,int64)", token, sender, receiver, serialNumber)
        );

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

    function transferFrom(address token, address from, address to, uint256 amount) public {
        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSignature("transferFrom(address,address,address,uint256)", token, from, to, amount)
        );

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

    function transferFromERC(address token, address from, address to, uint256 amount) public {
        (bool success, bytes memory result) =
                                address(token).call(abi.encodeWithSignature("transferFrom(address,address,uint256)", from, to, amount));

        bool resultBool = abi.decode(result, (bool));
        require(resultBool);
    }

    function transferFromNFT(address token, address from, address to, uint256 serialNumber) public {
        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSignature("transferFromNFT(address,address,address,uint256)", token, from, to, serialNumber)
        );

        int32 responseCode = abi.decode(result, (int32));
        require(responseCode == SUCCESS_CODE);
    }

}