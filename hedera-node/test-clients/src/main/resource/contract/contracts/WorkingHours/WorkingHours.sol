// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import './hip-206/HederaTokenService.sol';
import './hip-206/HederaResponseCodes.sol';

contract WorkingHours is HederaTokenService {

    uint256 internal constant WORK_INTERVAL = 5 * 60; // 5 seconds

    address tokenAddress;
    address treasury;

    uint256 nextWorkTime;
    int nextTicket = 1;

    event OrderHandled(address indexed served, int indexed ticketNumber);
    event OrderSkipped(int indexed ticketNumber);

    constructor(address _tokenAddress, address _treasury)  {
        tokenAddress = _tokenAddress;
        treasury = _treasury;
    }

    function takeTicket() external returns (int64 serialNumber) {
//        require(isWorkday(), "Not Open on Weekends");
//        require(isWorkingHours(), "Only Business Hours");

//        bytes memory metadata = "Wait in line...";
//        bytes[] memory metadatas = new bytes[](1);
//        metadatas[1] = metadata;

        (int mintResponse, , int64[] memory serialNum) = HederaTokenService.mintToken(tokenAddress, 0, new bytes[](1));
        if (mintResponse != HederaResponseCodes.SUCCESS) {
            // minting failed
            return 0;
        }

        int associateResponse = HederaTokenService.associateToken(msg.sender, tokenAddress);
        if (associateResponse != HederaResponseCodes.SUCCESS
            && associateResponse != HederaResponseCodes.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT) {
            // Could not associate account
            return 0;
        }

        //TODO query owner, right now we presume treasury
        int transferResponse = HederaTokenService.transferNFT(tokenAddress, treasury, msg.sender, serialNum[0]);
        if (transferResponse != HederaResponseCodes.SUCCESS) {
            // Could not transfer NFT
            return 0;
        }

        return serialNum[0];
    }


    function workTicket(int64 ticketNum) external {
//        require(isWorkday(), "Not Open on Weekends");
//        require(isWorkingHours(), "Only Business Hours");
//        require(block.timestamp >= nextWorkTime, "Worker is busy");
        //TODO validate existance of NFT
        //TODO validate ownership of NFT
        if (nextTicket == ticketNum) {
            emit OrderHandled(msg.sender, ticketNum);
        } else {
            emit OrderSkipped(nextTicket);
        }
        nextTicket++;
        if (ticketNum <= nextTicket) {
            // take possession of the NFT
            int transferResponse = HederaTokenService.transferNFT(tokenAddress, msg.sender, treasury, ticketNum);
            if (transferResponse != HederaResponseCodes.SUCCESS) {
                // Could not transfer NFT
                // user now has a souvenir
                return;
            }

            // burn it
            int64[] memory toBurn = new int64[](1);
            toBurn[0] = ticketNum;
            (int burnResponse, uint64 newSupply) = HederaTokenService.burnToken(tokenAddress, 0, toBurn);
        }
        nextWorkTime = block.timestamp + WORK_INTERVAL;
    }

    // monday to friday?
    function isWorkday() public view returns (bool workday) {
        // use monday = 0, so first day of epoch (Thursday) was 3.
        uint256 day = ((block.timestamp / 86400) + 3) % 7;
        return day < 5;
    }

    // are we 9 to 5 eastern standard time?
    function isWorkingHours() public view returns (bool workingHours) {
        uint256 hour = block.timestamp % 86400 / 3600;
        return hour >= 14 && hour < 22;
    }

}
