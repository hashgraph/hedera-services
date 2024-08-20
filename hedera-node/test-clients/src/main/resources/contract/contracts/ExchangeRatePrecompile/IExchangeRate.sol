// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;

interface IExchangeRate {
    // Given a value in tinycents (1e-8 US cents or 1e-10 USD), returns the 
    // equivalent value in tinybars (1e-8 HBAR) at the current exchange rate 
    // stored in system file 0.0.112. 
    // 
    // This rate is a weighted median of the the recent" HBAR-USD exchange 
    // rate on major exchanges, but should _not_ be treated as a live price 
    // oracle! It is important primarily because the network will use it to 
    // compute the tinybar fees for the active transaction. 
    // 
    // So a "self-funding" contract can use this rate to compute how much 
    // tinybar its users must send to cover the Hedera fees for the transaction.
    function tinycentsToTinybars(uint256 tinycents) external returns (uint256);

    // Given a value in tinybars (1e-8 HBAR), returns the equivalent value in 
    // tinycents (1e-8 US cents or 1e-10 USD) at the current exchange rate 
    // stored in system file 0.0.112. 
    // 
    // This rate tracks the the HBAR-USD rate on public exchanges, but 
    // should _not_ be treated as a live price oracle! This conversion is
    // less likely to be needed than the above conversion from tinycent to
    // tinybars, but we include it for completeness.
    function tinybarsToTinycents(uint256 tinybars) external returns (uint256);
}
