/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

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

    /** number of different stocks that can be bought and sold */
    public static final int NUM_STOCKS = 10;
    /** remember the last MAX_TRADES trades that occurred. */
    private static final int MAX_TRADES = 200;
    /** the platform running this app */
    private SwirldsPlatform platform = null;

    ////////////////////////////////////////////////////
    // the following are the shared state:

    /** ticker symbols for each of the stocks */
    private String[] tickerSymbol;
    /** number of cents owned by each member */
    private Map<NodeId, AtomicLong> wallet;
    /** shares[m][s] is the number of shares that member m owns of stock s */
    private Map<NodeId, List<AtomicLong>> shares;
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
    private NodeId[] askId;
    /** the ID number of the member whose offer is stored in bid[] (or -1 if none) */
    private NodeId[] bidId;
    /** price of the most recent trade for each stock */
    private byte[] price;

    ////////////////////////////////////////////////////

    public CryptocurrencyDemoState() {}

    private CryptocurrencyDemoState(final CryptocurrencyDemoState sourceState) {
        super(sourceState);
        this.platform = sourceState.platform;
        this.tickerSymbol = sourceState.tickerSymbol.clone();
        this.wallet = new HashMap<>(sourceState.wallet);
        this.shares = new HashMap<>();
        sourceState.shares.forEach((nodeId, sharesForNode) -> this.shares.put(nodeId, new ArrayList<>(sharesForNode)));
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
    public void handleConsensusRound(final Round round, final PlatformStateModifier platformState) {
        throwIfImmutable();
        round.forEachEventTransaction((event, transaction) -> handleTransaction(event.getCreatorId(), transaction));
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
    private void handleTransaction(@NonNull final NodeId id, @NonNull final Transaction transaction) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(transaction, "transaction must not be null");
        if (transaction.isSystem()) {
            return;
        }
        final Bytes contents = transaction.getApplicationTransaction();
        if (contents.length() < 3) {
            return;
        }
        if (contents.getByte(0) == TransType.slow.ordinal() || contents.getByte(0) == TransType.fast.ordinal()) {
            return;
        }
        final int askBid = contents.getByte(0);
        final int tradeStock = contents.getByte(1);
        int tradePrice = contents.getByte(2);

        if (tradePrice < 1 || tradePrice > 127) {
            return; // all asks and bids must be in the range 1 to 127
        }

        if (askBid == TransType.ask.ordinal()) { // it is an ask
            // if they're trying to sell something they don't have, then ignore it
            if (shares.get(id).get(tradeStock).get() == 0) {
                return;
            }
            // if previous member with bid no longer has enough money, then forget them
            if (bidId[tradeStock] != null && wallet.get(bidId[tradeStock]).get() < bid[tradeStock]) {
                bidId[tradeStock] = null;
            }
            // if this is the lowest ask for this stock since its last trade, then remember it
            if (askId[tradeStock] == null || tradePrice < ask[tradeStock]) {
                askId[tradeStock] = id;
                ask[tradeStock] = (byte) tradePrice;
            }
        } else { // it is a bid
            // if they're trying to buy but don't have enough money, then ignore it
            if (shares.get(id).get(tradeStock).get() == 0) {
                return;
            }
            // if previous member with ask no longer has the share, then forget them
            if (askId[tradeStock] != null
                    && shares.get(askId[tradeStock]).get(tradeStock).get() == 0) {
                askId[tradeStock] = null;
            }
            // if this is the highest bid for this stock since its last trade, then remember it
            if (bidId[tradeStock] == null || tradePrice > bid[tradeStock]) {
                bidId[tradeStock] = id;
                bid[tradeStock] = (byte) tradePrice;
            }
        }
        // if there is not yet a match for this stock, then don't create a trade yet
        if (askId[tradeStock] == null || bidId[tradeStock] == null || ask[tradeStock] > bid[tradeStock]) {
            return;
        }

        // there is a match, so create the trade

        // the trade occurs at the mean of the ask and bid
        // if the mean is a non-integer, round to the nearest event integer
        tradePrice = ask[tradeStock] + bid[tradeStock];
        tradePrice = (tradePrice / 2) + ((tradePrice % 4) / 3);

        // perform the trade (exchanging money for a share)
        wallet.get(askId[tradeStock]).addAndGet(tradePrice); // seller gets money
        wallet.get(bidId[tradeStock]).addAndGet(-tradePrice); // buyer gives money
        shares.get(askId[tradeStock]).get(tradeStock).addAndGet(-1); // seller gives 1 share
        shares.get(bidId[tradeStock]).get(tradeStock).addAndGet(1); // buyer gets 1 share

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
                wallet.get(id).get() / 100.,
                shares.get(id));

        // record the trade, and say there are now no pending asks or bids
        trades[lastTradeIndex] = tradeDescription;
        price[tradeStock] = (byte) tradePrice;
        askId[tradeStock] = null;
        bidId[tradeStock] = null;
    }

    /**
     * Do setup at genesis
     */
    public void genesisInit() {
        tickerSymbol = new String[NUM_STOCKS];
        wallet = new HashMap<>();
        shares = new HashMap<>();
        trades = new String[MAX_TRADES];
        numTradesStored = 0;
        lastTradeIndex = 0;
        numTrades = 0;
        ask = new byte[NUM_STOCKS];
        bid = new byte[NUM_STOCKS];
        askId = new NodeId[NUM_STOCKS];
        bidId = new NodeId[NUM_STOCKS];
        price = new byte[NUM_STOCKS];

        // seed 0 so everyone gets same ticker symbols on every run
        final Random rand = new Random(0);
        for (int i = 0; i < NUM_STOCKS; i++) {
            tickerSymbol[i] = "" // each ticker symbol is 4 random capital letters (ASCII 65 is 'A')
                    + (char) (65 + rand.nextInt(26))
                    + (char) (65 + rand.nextInt(26))
                    + (char) (65 + rand.nextInt(26))
                    + (char) (65 + rand.nextInt(26));
            askId[i] = bidId[i] = null; // no one has offered to buy or sell yet
            ask[i] = bid[i] = price[i] = 64; // start the trading around 64 cents
        }

        final AddressBook addressBook = platform.getAddressBook();
        for (final Address address : addressBook) {
            final NodeId id = address.getNodeId();
            // each member starts with $200 dollars (20,000 cents)
            wallet.put(id, new AtomicLong(20000L));
            final List<AtomicLong> sharesForID = new ArrayList<>();
            for (int i = 0; i < NUM_STOCKS; i++) {
                // each member starts with 200 shares of each stock
                sharesForID.add(new AtomicLong(200L));
            }
            shares.put(id, sharesForID);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousSoftwareVersion) {
        this.platform = (SwirldsPlatform) platform;

        if (trigger == InitTrigger.GENESIS) {
            genesisInit();
        }
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeStringArray(tickerSymbol);

        out.writeInt(wallet.size());
        for (Map.Entry<NodeId, AtomicLong> entry : wallet.entrySet()) {
            out.writeSerializable(entry.getKey(), false);
            out.writeLong(entry.getValue().get());
        }

        out.writeInt(shares.size());
        for (Map.Entry<NodeId, List<AtomicLong>> entry : shares.entrySet()) {
            out.writeSerializable(entry.getKey(), false);
            out.writeInt(entry.getValue().size());
            for (int i = 0; i < entry.getValue().size(); i++) {
                out.writeLong(entry.getValue().get(i).get());
            }
        }

        out.writeStringArray(trades);
        out.writeInt(numTradesStored);
        out.writeInt(lastTradeIndex);
        out.writeLong(numTrades);
        out.writeByteArray(ask);
        out.writeByteArray(bid);
        out.writeSerializableArray(askId, false, true);
        out.writeSerializableArray(bidId, false, true);
        out.writeByteArray(price);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        tickerSymbol = in.readStringArray(DEFAULT_MAX_ARRAY_SIZE, DEFAULT_MAX_STRING_SIZE);

        int walletSize = in.readInt();
        for (int i = 0; i < walletSize; i++) {
            final NodeId id = in.readSerializable(false, NodeId::new);
            final AtomicLong amount = new AtomicLong(in.readLong());
            wallet.put(id, amount);
        }

        final int sharesSize = in.readInt();
        for (int i = 0; i < sharesSize; i++) {
            final NodeId id = in.readSerializable(false, NodeId::new);
            final int numShares = in.readInt();
            final List<AtomicLong> sharesForID = new ArrayList<>(numShares);
            for (int j = 0; j < numShares; j++) {
                sharesForID.add(new AtomicLong(in.readLong()));
            }
            shares.put(id, sharesForID);
        }

        trades = in.readStringArray(DEFAULT_MAX_ARRAY_SIZE, DEFAULT_MAX_STRING_SIZE);
        numTradesStored = in.readInt();
        lastTradeIndex = in.readInt();
        numTrades = in.readLong();
        ask = in.readByteArray(DEFAULT_MAX_ARRAY_SIZE);
        bid = in.readByteArray(DEFAULT_MAX_ARRAY_SIZE);
        askId = in.readSerializableArray(NodeId[]::new, DEFAULT_MAX_ARRAY_SIZE, false, NodeId::new);
        bidId = in.readSerializableArray(NodeId[]::new, DEFAULT_MAX_ARRAY_SIZE, false, NodeId::new);
        price = in.readByteArray(DEFAULT_MAX_ARRAY_SIZE);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }
}
