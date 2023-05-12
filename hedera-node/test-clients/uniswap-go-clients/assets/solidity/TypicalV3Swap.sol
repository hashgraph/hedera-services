pragma solidity =0.7.6;
pragma abicoder v2;

import './interfaces/ISwapRouter.sol';
import './libraries/TransferHelper.sol';

contract TypicalV3Swap {
  ISwapRouter public immutable swapRouter;

  uint24 public constant poolFee = 500;

  constructor(ISwapRouter _swapRouter) {
    swapRouter = _swapRouter;
  }

  function swapExactInputSingle(
    address token0,
    address token1,
    uint256 amountIn
  ) external returns (uint256 amountOut) {
    TransferHelper.safeApprove(
      token0, address(swapRouter), amountIn);
    
    ISwapRouter.ExactInputSingleParams memory params =
      ISwapRouter.ExactInputSingleParams({
        tokenIn: token0,
        tokenOut: token1,
        fee: poolFee,
        recipient: address(this),
        deadline: block.timestamp,
        amountIn: amountIn,
        amountOutMinimum: 0,
        sqrtPriceLimitX96: 0
      });
    
    amountOut = swapRouter.exactInputSingle(params);
  }

  function swapExactOutputSingle(
    address token0,
    address token1,
    uint256 amountOut,
    uint256 maxAmountIn
  ) external returns (uint256 amountIn) {
    TransferHelper.safeApprove(token0, address(swapRouter), maxAmountIn); 

    ISwapRouter.ExactOutputSingleParams memory params =
      ISwapRouter.ExactOutputSingleParams({
        tokenIn: token0,
        tokenOut: token1,
        fee: poolFee,
        recipient: address(this),
        deadline: block.timestamp,
        amountOut: amountOut,
        amountInMaximum: maxAmountIn,
        sqrtPriceLimitX96: 0
      });
    
    amountIn = swapRouter.exactOutputSingle(params);
  }
}
