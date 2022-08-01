/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

public class ExchangeRates implements SelfSerializable {
    static final int MERKLE_VERSION = 1;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0x5dfb7b68d7473416L;

    private int currHbarEquiv;
    private int currCentEquiv;
    private long currExpiry;

    private int nextHbarEquiv;
    private int nextCentEquiv;
    private long nextExpiry;

    private boolean initialized = false;

    public ExchangeRates() {}

    public ExchangeRates(
            int currHbarEquiv,
            int currCentEquiv,
            long currExpiry,
            int nextHbarEquiv,
            int nextCentEquiv,
            long nextExpiry) {
        this.currHbarEquiv = currHbarEquiv;
        this.currCentEquiv = currCentEquiv;
        this.currExpiry = currExpiry;

        this.nextHbarEquiv = nextHbarEquiv;
        this.nextCentEquiv = nextCentEquiv;
        this.nextExpiry = nextExpiry;

        initialized = true;
    }

    /* --- SelfSerializable --- */

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        currHbarEquiv = in.readInt();
        currCentEquiv = in.readInt();
        currExpiry = in.readLong();
        nextHbarEquiv = in.readInt();
        nextCentEquiv = in.readInt();
        nextExpiry = in.readLong();

        initialized = true;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeInt(currHbarEquiv);
        out.writeInt(currCentEquiv);
        out.writeLong(currExpiry);
        out.writeInt(nextHbarEquiv);
        out.writeInt(nextCentEquiv);
        out.writeLong(nextExpiry);
    }

    /* --- Object --- */

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || ExchangeRates.class != o.getClass()) {
            return false;
        }
        var that = (ExchangeRates) o;

        return currHbarEquiv == that.currHbarEquiv
                && currCentEquiv == that.currCentEquiv
                && currExpiry == that.currExpiry
                && nextHbarEquiv == that.nextHbarEquiv
                && nextCentEquiv == that.nextCentEquiv
                && nextExpiry == that.nextExpiry;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(MERKLE_VERSION);
        result = result * 31 + Long.hashCode(RUNTIME_CONSTRUCTABLE_ID);
        result = result * 31 + Integer.hashCode(currHbarEquiv);
        result = result * 31 + Integer.hashCode(currCentEquiv);
        result = result * 31 + Long.hashCode(currExpiry);
        result = result * 31 + Integer.hashCode(nextHbarEquiv);
        result = result * 31 + Integer.hashCode(nextCentEquiv);
        return result * 31 + Long.hashCode(nextExpiry);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("currHbarEquiv", currHbarEquiv)
                .add("currCentEquiv", currCentEquiv)
                .add("currExpiry", currExpiry)
                .add("nextHbarEquiv", nextHbarEquiv)
                .add("nextCentEquiv", nextCentEquiv)
                .add("nextExpiry", nextExpiry)
                .toString();
    }

    public String readableRepr() {
        return new StringBuilder()
                .append(currHbarEquiv)
                .append("ℏ <-> ")
                .append(currCentEquiv)
                .append("¢ til ")
                .append(currExpiry)
                .append(" | ")
                .append(nextHbarEquiv)
                .append("ℏ <-> ")
                .append(nextCentEquiv)
                .append("¢ til ")
                .append(nextExpiry)
                .toString();
    }

    /* --- Bean --- */

    public boolean isInitialized() {
        return initialized;
    }

    public int getCurrHbarEquiv() {
        return currHbarEquiv;
    }

    public int getCurrCentEquiv() {
        return currCentEquiv;
    }

    public int getNextHbarEquiv() {
        return nextHbarEquiv;
    }

    public int getNextCentEquiv() {
        return nextCentEquiv;
    }

    public long getCurrExpiry() {
        return currExpiry;
    }

    public long getNextExpiry() {
        return nextExpiry;
    }

    /* --- Helpers --- */

    public void replaceWith(ExchangeRateSet newRates) {
        this.currHbarEquiv = newRates.getCurrentRate().getHbarEquiv();
        this.currCentEquiv = newRates.getCurrentRate().getCentEquiv();
        this.currExpiry = newRates.getCurrentRate().getExpirationTime().getSeconds();

        this.nextHbarEquiv = newRates.getNextRate().getHbarEquiv();
        this.nextCentEquiv = newRates.getNextRate().getCentEquiv();
        this.nextExpiry = newRates.getNextRate().getExpirationTime().getSeconds();

        initialized = true;
    }

    public ExchangeRates copy() {
        return new ExchangeRates(
                currHbarEquiv, currCentEquiv, currExpiry, nextHbarEquiv, nextCentEquiv, nextExpiry);
    }

    public ExchangeRateSet toGrpc() {
        return ExchangeRateSet.newBuilder()
                .setCurrentRate(
                        ExchangeRate.newBuilder()
                                .setHbarEquiv(currHbarEquiv)
                                .setCentEquiv(currCentEquiv)
                                .setExpirationTime(
                                        TimestampSeconds.newBuilder().setSeconds(currExpiry)))
                .setNextRate(
                        ExchangeRate.newBuilder()
                                .setHbarEquiv(nextHbarEquiv)
                                .setCentEquiv(nextCentEquiv)
                                .setExpirationTime(
                                        TimestampSeconds.newBuilder().setSeconds(nextExpiry)))
                .build();
    }

    public static ExchangeRates fromGrpc(ExchangeRateSet grpc) {
        return new ExchangeRates(
                grpc.getCurrentRate().getHbarEquiv(),
                grpc.getCurrentRate().getCentEquiv(),
                grpc.getCurrentRate().getExpirationTime().getSeconds(),
                grpc.getNextRate().getHbarEquiv(),
                grpc.getNextRate().getCentEquiv(),
                grpc.getNextRate().getExpirationTime().getSeconds());
    }
}
