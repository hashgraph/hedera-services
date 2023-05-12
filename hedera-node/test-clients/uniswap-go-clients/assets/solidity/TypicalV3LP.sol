pragma solidity =0.7.6;
pragma abicoder v2;

import './core/interfaces/IUniswapV3Pool.sol';
import './core/libraries/TickMath.sol';
import './token/ERC721/IERC721Receiver.sol';
import './interfaces/ISwapRouter.sol';
import './interfaces/INonfungiblePositionManager.sol';
import './libraries/TransferHelper.sol';
import './base/LiquidityManagement.sol';

contract TypicalV3LP is IERC721Receiver, LiquidityManagement {
  INonfungiblePositionManager public immutable manager;
  
  struct Deposit {
    address owner;
    uint128 liquidity;
    address token0;
    address token1;
  }

  uint24 public constant poolFee = 500;

  mapping(uint256 => Deposit) public deposits;

  constructor(
    address _manager,
    address _factory,
    address _WETH9
  ) PeripheryImmutableState(_factory, _WETH9) {
    manager = INonfungiblePositionManager(_manager);
  }

  function onERC721Received(
    address operator,
    address,
    uint256 tokenId,
    bytes calldata
  ) external override returns (bytes4) {
    _createDeposit(operator, tokenId);
    return this.onERC721Received.selector;
  }

  function _createDeposit(address owner, uint256 tokenId) internal {
    (, , address token0, address token1, , , , uint128 liquidity, , , , ) =
      manager.positions(tokenId);
    deposits[tokenId] = Deposit({
      owner: owner,
      liquidity: liquidity,
      token0: token0,
      token1: token1    
    });
  }

  function mintNewPosition(
    address token0,
    address token1,
    uint256 amount0ToMint,
    uint256 amount1ToMint
  ) external returns (
    uint256 tokenId,
    uint128 liquidity,
    uint256 amount0,
    uint256 amount1
  ) {
    if (token0 > token1) {
      (token0, token1) = (token1, token0);
      (amount0, amount1) = (amount1, amount0);
    }

    TransferHelper.safeApprove(token0, address(manager), amount0ToMint);
    TransferHelper.safeApprove(token1, address(manager), amount1ToMint);

    int24 minTick = -887270;
    int24 maxTick = -minTick;
    INonfungiblePositionManager.MintParams memory params =
      INonfungiblePositionManager.MintParams({
        token0: token0,
        token1: token1,
        fee: poolFee,
        tickLower: minTick,
        tickUpper: maxTick,
        amount0Desired: amount0ToMint,
        amount1Desired: amount1ToMint,
        amount0Min: 0,
        amount1Min: 0,
        recipient: address(this),
        deadline: block.timestamp
      });

    (tokenId, liquidity, amount0, amount1) = manager.mint(params);

    _createDeposit(msg.sender, tokenId);

    // Remove allowance in both assets.
    if (amount0 < amount0ToMint) {
      TransferHelper.safeApprove(token0, address(manager), 0);
    }
    if (amount1 < amount1ToMint) {
      TransferHelper.safeApprove(token1, address(manager), 0);
    }
  }
}

