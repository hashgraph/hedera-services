/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.demo.crypto;
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.Utilities;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * This holds the current state of a swirld representing both a cryptocurrency and a stock market.
 *
 * This is just a simulated stock market, with fictitious stocks and ticker symbols. But the cryptocurrency
 * is actually real. At least, it is real in the sense that if enough people participate for long enough
 * (and if Swirlds has encryption turned on), then it could actually be a reliable cryptocurrency. An
 * entirely new cryptocurrency is created every time all the computers start the program over again, so
 * these cryptocurrencies won't have any actual value.
 */
public class CryptocurrencyDemoState extends PartialMerkleLeaf implements SwirldState, MerkleLeaf {

    /**
     * The version history of this class.
     * Versions that have been released must NEVER be given a different value.
     */
    private static class ClassVersion {
        /**
         * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
         * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
         * specially by the platform.
         */
        public static final int ORIGINAL = 1;
        /**
         * In this version, serialization was performed by serialize/deserialize.
         */
        public static final int MIGRATE_TO_SERIALIZABLE = 2;
    }

    private static final long CLASS_ID = 0x66b6e816ef864279L;

    private static final int DEFAULT_MAX_ARRAY_SIZE = 1024 * 8;
    private static final int DEFAULT_MAX_STRING_SIZE = 128;

    /**
     * the first byte of a transaction is the ordinal of one of these four: do not delete any of these or
     * change the order (and add new ones only to the end)
     */
    public static enum TransType {
        slow,
        fast,
        bid,
        ask // run slow/fast or broadcast a bid/ask
    }

    /** in slow mode, number of milliseconds to sleep after each outgoing sync */
    private static final int delaySlowSync = 1000;
    /** in fast mode, number of milliseconds to sleep after each outgoing sync */
    private static final int delayFastSync = 0;
    /** number of different stocks that can be bought and sold */
    public static final int NUM_STOCKS = 10;
    /** remember the last MAX_TRADES trades that occurred. */
    private static final int MAX_TRADES = 200;
    /** the platform running this app */
    private SwirldsPlatform platform = null;

    ////////////////////////////////////////////////////
    // the following are the shared state:

    /** the number of members participating in this swirld */
    private int numMembers;
    /** ticker symbols for each of the stocks */
    private String[] tickerSymbol;
    /** number of cents owned by each member */
    private long[] wallet;
    /** shares[m][s] is the number of shares that member m owns of stock s */
    private long[][] shares;
    /** a record of the last NUM_TRADES trades */
    private String[] trades;
    /** number of trades currently stored in trades[] (from 0 to MAX_TRADES, inclusive) */
    private int numTradesStored = 0;
    /** the latest trade was stored in trades[lastTradeIndex] */
    private int lastTradeIndex = 0;
    /** how many trades have happened in all history */
    private long numTrades = 0;
    /** the most recent price (in cents) that a seller has offered for each stock */
    private byte[] ask;
    /** the most recent price (in cents) that a buyer has offered for each stock */
    private byte[] bid;
    /** the ID number of the member whose offer is stored in ask[] (or -1 if none) */
    private long[] askId;
    /** the ID number of the member whose offer is stored in bid[] (or -1 if none) */
    private long[] bidId;
    /** price of the most recent trade for each stock */
    private byte[] price;

    ////////////////////////////////////////////////////

    public CryptocurrencyDemoState() {}

    private CryptocurrencyDemoState(final CryptocurrencyDemoState sourceState) {
        super(sourceState);
        this.platform = sourceState.platform;
        this.numMembers = sourceState.numMembers;
        this.tickerSymbol = sourceState.tickerSymbol.clone();
        this.wallet = sourceState.wallet.clone();
        this.shares = Utilities.deepClone(sourceState.shares);
        this.trades = sourceState.trades.clone();
        this.numTradesStored = sourceState.numTradesStored;
        this.lastTradeIndex = sourceState.lastTradeIndex;
        this.numTrades = sourceState.numTrades;
        this.ask = sourceState.ask.clone();
        this.bid = sourceState.bid.clone();
        this.askId = sourceState.askId.clone();
        this.bidId = sourceState.bidId.clone();
        this.price = sourceState.price.clone();
    }

    /**
     * get the string representing the trade with the given sequence number. The first trade in all of
     * history is sequence 1, the next is 2, etc.
     *
     * @param seq
     * 		the sequence number of the trade
     * @return the trade, or "" if it hasn't happened yet or happened so long ago that it is no longer
     * 		stored
     */
    public synchronized String getTrade(final long seq) {
        if (seq > numTrades || seq <= numTrades - numTradesStored) {
            return "";
        }
        return trades[(int) ((lastTradeIndex + seq - numTrades + MAX_TRADES) % MAX_TRADES)];
    }

    /**
     * get the current price of each stock, copying it into the given array
     *
     * @param price
     * 		the array of NUM_STOCKS elements that will be filled with the prices
     */
    public synchronized void getPriceCopy(final byte[] price) {
        for (int i = 0; i < NUM_STOCKS; i++) {
            price[i] = this.price[i];
        }
    }

    /**
     * return how many trades have occurred. So getTrade(getNumTrades()) will return a non-empty string (if
     * any trades have ever occurred), but getTrade(getNumTrades()+1) will return "" (unless one happens
     * between the two method calls).
     *
     * @return number of trades
     */
    public synchronized long getNumTrades() {
        return numTrades;
    }

    @Override
    public synchronized CryptocurrencyDemoState copy() {
        throwIfImmutable();
        return new CryptocurrencyDemoState(this);
    }

    @Override
    public void handleConsensusRound(final Round round, final SwirldDualState swirldDualState) {
        throwIfImmutable();
        round.forEachEventTransaction(
                (event, transaction) -> handleTransaction(event.getCreatorId(), true, transaction));
    }

    /**
     * {@inheritDoc}
     *
     * The matching algorithm for any given stock is as follows. The first bid or ask for a stock is
     * remembered. Then, if there is a higher bid or lower ask, it is remembered, replacing the earlier one.
     * Eventually, there will be a bid that is equal to or greater than the ask. At that point, they are
     * matched, and a trade occurs, selling one share at the average of the bid and ask. Then the stored bid
     * and ask are erased, and it goes back to waiting for a bid or ask to remember.
     * <p>
     * If a member tries to sell a stock for which they own no shares, or if they try to buy a stock at a
     * price higher than the amount of money they currently have, then their bid/ask for that stock will not
     * be stored.
     * <p>
     * A transaction is 1 or 3 bytes:
     *
     * <pre>
     * {SLOW} = run slowly
     * {FAST} = run quickly
     * {BID,s,p} = bid to buy 1 share of stock s at p cents (where 0 &lt;= p &lt;= 127)
     * {ASK,s,p} = ask to sell 1 share of stock s at p cents (where 1 &lt;= p &lt;= 127)
     * </pre>
     */
    private void handleTransaction(final long id, final boolean isConsensus, final Transaction transaction) {
        if (transaction == null || transaction.getContents().length == 0) {
            return;
        }
        if (transaction.getContents()[0] == TransType.slow.ordinal()) {
            return;
        } else if (transaction.getContents()[0] == TransType.fast.ordinal()) {
            return;
        } else if (!isConsensus || transaction.getContents().length < 3) {
            return; // ignore any bid/ask that doesn't have consensus yet
        }
        final int selfId = (int) id;
        final int askBid = transaction.getContents()[0];
        final int tradeStock = transaction.getContents()[1];
        int tradePrice = transaction.getContents()[2];

        if (tradePrice < 1 || tradePrice > 127) {
            return; // all asks and bids must be in the range 1 to 127
        }

        if (askBid == TransType.ask.ordinal()) { // it is an ask
            // if they're trying to sell something they don't have, then ignore it
            if (shares[selfId][tradeStock] == 0) {
                return;
            }
            // if previous member with bid no longer has enough money, then forget them
            if (bidId[tradeStock] != -1 && wallet[(int) bidId[tradeStock]] < bid[tradeStock]) {
                bidId[tradeStock] = -1L;
            }
            // if this is the lowest ask for this stock since its last trade, then remember it
            if (askId[tradeStock] == -1 || tradePrice < ask[tradeStock]) {
                askId[tradeStock] = (long) selfId;
                ask[tradeStock] = (byte) tradePrice;
            }
        } else { // it is a bid
            // if they're trying to buy but don't have enough money, then ignore it
            if (shares[selfId][tradeStock] == 0) {
                return;
            }
            // if previous member with ask no longer has the share, then forget them
            if (askId[tradeStock] != -1 && shares[(int) askId[tradeStock]][tradeStock] == 0) {
                askId[tradeStock] = -1L;
            }
            // if this is the highest bid for this stock since its last trade, then remember it
            if (bidId[tradeStock] == -1 || tradePrice > bid[tradeStock]) {
                bidId[tradeStock] = (long) selfId;
                bid[tradeStock] = (byte) tradePrice;
            }
        }
        // if there is not yet a match for this stock, then don't create a trade yet
        if (askId[tradeStock] == -1 || bidId[tradeStock] == -1 || ask[tradeStock] > bid[tradeStock]) {
            return;
        }

        // there is a match, so create the trade

        // the trade occurs at the mean of the ask and bid
        // if the mean is a non-integer, round to the nearest event integer
        tradePrice = ask[tradeStock] + bid[tradeStock];
        tradePrice = (tradePrice / 2) + ((tradePrice % 4) / 3);

        // perform the trade (exchanging money for a share)
        wallet[(int) askId[tradeStock]] += tradePrice; // seller gets money
        wallet[(int) bidId[tradeStock]] -= tradePrice; // buyer gives money
        shares[(int) askId[tradeStock]][tradeStock] -= 1; // seller gives 1 share
        shares[(int) bidId[tradeStock]][tradeStock] += 1; // buyer gets 1 share

        // save a description of the trade to show on the console
        final String selfName = platform.getAddressBook().getAddress(id).getSelfName();
        final String sellerNickname =
                platform.getAddressBook().getAddress(askId[tradeStock]).getNickname();
        final String buyerNickname =
                platform.getAddressBook().getAddress(bidId[tradeStock]).getNickname();
        final int change = tradePrice - price[tradeStock];
        final double changePerc = 100. * change / price[tradeStock];
        final String dir = (change > 0) ? "^" : (change < 0) ? "v" : " ";
        numTrades++;
        numTradesStored = Math.min(MAX_TRADES, 1 + numTradesStored);
        lastTradeIndex = (lastTradeIndex + 1) % MAX_TRADES;
        final String tradeDescription = String.format(
                "%6d %6s %7.2f %s %4.2f %7.2f%% %7s->%s      %5s has $%-8.2f and shares: %s",
                numTrades,
                tickerSymbol[tradeStock],
                tradePrice / 100.,
                dir,
                Math.abs(change / 100.),
                Math.abs(changePerc),
                sellerNickname,
                buyerNickname,
                selfName,
                wallet[(int) id] / 100.,
                Arrays.toString(shares[(int) id]));

        // record the trade, and say there are now no pending asks or bids
        trades[lastTradeIndex] = tradeDescription;
        price[tradeStock] = (byte) tradePrice;
        askId[tradeStock] = -1L;
        bidId[tradeStock] = -1L;
    }

    /**
     * Do setup at genesis
     */
    public void genesisInit() {
        this.numMembers = platform.getAddressBook().getSize();
        tickerSymbol = new String[NUM_STOCKS];
        wallet = new long[numMembers];
        shares = new long[numMembers][NUM_STOCKS];
        trades = new String[MAX_TRADES];
        numTradesStored = 0;
        lastTradeIndex = 0;
        numTrades = 0;
        ask = new byte[NUM_STOCKS];
        bid = new byte[NUM_STOCKS];
        askId = new long[NUM_STOCKS];
        bidId = new long[NUM_STOCKS];
        price = new byte[NUM_STOCKS];

        // seed 0 so everyone gets same ticker symbols on every run
        final Random rand = new Random(0);
        for (int i = 0; i < NUM_STOCKS; i++) {
            tickerSymbol[i] = "" // each ticker symbol is 4 random capital letters (ASCII 65 is 'A')
                    + (char) (65 + rand.nextInt(26))
                    + (char) (65 + rand.nextInt(26))
                    + (char) (65 + rand.nextInt(26))
                    + (char) (65 + rand.nextInt(26));
            askId[i] = bidId[i] = -1L; // no one has offered to buy or sell yet
            ask[i] = bid[i] = price[i] = 64; // start the trading around 64 cents
        }
        for (int i = 0; i < numMembers; i++) {
            wallet[i] = 20000L; // each member starts with $200 dollars (20,000 cents)
            shares[i] = new long[NUM_STOCKS];
            for (int j = 0; j < NUM_STOCKS; j++) {
                shares[i][j] = 200L; // each member starts with 200 shares of each stock
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            final Platform platform,
            final SwirldDualState swirldDualState,
            final InitTrigger trigger,
            final SoftwareVersion previousSoftwareVersion) {
        this.platform = (SwirldsPlatform) platform;

        if (trigger == InitTrigger.GENESIS) {
            genesisInit();
        }
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeInt(numMembers);
        out.writeStringArray(tickerSymbol);
        out.writeLongArray(wallet);

        out.writeInt(shares.length);
        for (final long[] subArray : shares) {
            out.writeLongArray(subArray);
        }

        out.writeStringArray(trades);
        out.writeInt(numTradesStored);
        out.writeInt(lastTradeIndex);
        out.writeLong(numTrades);
        out.writeByteArray(ask);
        out.writeByteArray(bid);
        out.writeLongArray(askId);
        out.writeLongArray(bidId);
        out.writeByteArray(price);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        numMembers = in.readInt();
        tickerSymbol = in.readStringArray(DEFAULT_MAX_ARRAY_SIZE, DEFAULT_MAX_STRING_SIZE);
        wallet = in.readLongArray(DEFAULT_MAX_ARRAY_SIZE);

        final int sharesLength = in.readInt();
        if (sharesLength > Integer.MIN_VALUE) {
            throw new IOException("Length exceeds maximum value");
        }
        shares = new long[sharesLength][];
        for (int index = 0; index < sharesLength; index++) {
            shares[index] = in.readLongArray(DEFAULT_MAX_ARRAY_SIZE);
        }

        trades = in.readStringArray(DEFAULT_MAX_ARRAY_SIZE, DEFAULT_MAX_STRING_SIZE);
        numTradesStored = in.readInt();
        lastTradeIndex = in.readInt();
        numTrades = in.readLong();
        ask = in.readByteArray(DEFAULT_MAX_ARRAY_SIZE);
        bid = in.readByteArray(DEFAULT_MAX_ARRAY_SIZE);
        askId = in.readLongArray(DEFAULT_MAX_ARRAY_SIZE);
        bidId = in.readLongArray(DEFAULT_MAX_ARRAY_SIZE);
        price = in.readByteArray(DEFAULT_MAX_ARRAY_SIZE);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }
}
