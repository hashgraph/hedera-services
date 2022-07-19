// SPDX-License-Identifier: GPL-2.0-or-later
pragma solidity =0.7.6;
pragma abicoder v2;

import './interfaces/callback/IUniswapV3MintCallback.sol';
import './interfaces/IERC20Minimal.sol';
import './libraries/PoolAddress.sol';
import './libraries/Path.sol';
import './UniswapV3Pool.sol';

contract MinimalV3LP is IUniswapV3MintCallback {
    int24 internal constant MIN_TICK = -887272;
    int24 internal constant MAX_TICK = -MIN_TICK;

    address factory;

    constructor(address _factory) {
        factory = _factory;
    }

    function uniswapV3MintCallback(
        uint256 amount0Owed,
        uint256 amount1Owed,
        bytes calldata path
    ) external override {
        (address token0, address token1, ) = 
            Path.decodeFirstPool(path);
        address pool = address(msg.sender);        
        IERC20Minimal(token0).transfer(pool, amount0Owed);
        IERC20Minimal(token1).transfer(pool, amount1Owed);
    }

    function mint(
        address token0,
        address token1,
        uint64 _amount,
        uint32 workAroundFee
    ) external {
        if (token0 > token1) (token0, token1) = (token1, token0);
        uint24 fee = uint24(workAroundFee);                
        PoolAddress.PoolKey memory key = PoolAddress.getPoolKey(token0, token1, fee);
        address poolAddress = PoolAddress.computeAddress(factory, key);
        UniswapV3Pool pool = UniswapV3Pool(poolAddress);
        uint128 amount = uint128(_amount);
        bytes memory path = abi.encodePacked(token0, fee, token1);
        pool.mint(address(this), MIN_TICK, MAX_TICK, amount, path);
    }
}
