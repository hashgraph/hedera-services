package com.hedera.services.txns.contract.helpers;


import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.plugin.data.BlockHeader;

import java.util.Optional;

/** A block header capable of being processed. */
public class ProcessableBlockHeader implements BlockHeader {

    protected final Hash parentHash;

    protected final Address coinbase;

    protected final Difficulty difficulty;

    protected final long number;

    protected final long gasLimit;

    // The block creation timestamp (seconds since the unix epoch)
    protected final long timestamp;
    // base fee is included for post EIP-1559 blocks
    protected final Long baseFee;

    public ProcessableBlockHeader(
            final Hash parentHash,
            final Address coinbase,
            final Difficulty difficulty,
            final long number,
            final long gasLimit,
            final long timestamp,
            final Long baseFee) {
        this.parentHash = parentHash;
        this.coinbase = coinbase;
        this.difficulty = difficulty;
        this.number = number;
        this.gasLimit = gasLimit;
        this.timestamp = timestamp;
        this.baseFee = baseFee;
    }

    /**
     * Returns the block parent block hash.
     *
     * @return the block parent block hash
     */
    public Hash getParentHash() {
        return parentHash;
    }

    @Override
    public org.hyperledger.besu.plugin.data.Hash getOmmersHash() {
        return null;
    }

    /**
     * Returns the block coinbase address.
     *
     * @return the block coinbase address
     */
    public Address getCoinbase() {
        return coinbase;
    }

    @Override
    public org.hyperledger.besu.plugin.data.Hash getStateRoot() {
        return null;
    }

    @Override
    public org.hyperledger.besu.plugin.data.Hash getTransactionsRoot() {
        return null;
    }

    @Override
    public org.hyperledger.besu.plugin.data.Hash getReceiptsRoot() {
        return null;
    }

    @Override
    public Bytes getLogsBloom() {
        return null;
    }

    /**
     * Returns the block difficulty.
     *
     * @return the block difficulty
     */
    public Difficulty getDifficulty() {
        return difficulty;
    }

    /**
     * Returns the block number.
     *
     * @return the block number
     */
    public long getNumber() {
        return number;
    }

    /**
     * Return the block gas limit.
     *
     * @return the block gas limit
     */
    public long getGasLimit() {
        return gasLimit;
    }

    @Override
    public long getGasUsed() {
        return 0;
    }

    /**
     * Return the block timestamp.
     *
     * @return the block timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public Bytes getExtraData() {
        return null;
    }

    @Override
    public org.hyperledger.besu.plugin.data.Hash getMixHash() {
        return null;
    }

    @Override
    public long getNonce() {
        return 0;
    }

    @Override
    public org.hyperledger.besu.plugin.data.Hash getBlockHash() {
        return null;
    }

    /**
     * Returns the basefee of the block.
     *
     * @return the raw bytes of the extra data field
     */
    public Optional<Long> getBaseFee() {
        return Optional.ofNullable(baseFee);
    }
}
