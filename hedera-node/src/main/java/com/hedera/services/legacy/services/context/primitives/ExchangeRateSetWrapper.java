package com.hedera.services.legacy.services.context.primitives;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;

import java.io.IOException;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * This class is for storing ExchangeRateSet in HGCAppState. It is used for checking whether an
 * update to the ExchangeRate File exceeds the limit Exchange_Rate_Allowed_Percentage.
 */
public class ExchangeRateSetWrapper implements FastCopyable {
  private static final long VERSION = 1L;
  private static final long OBJECT_ID = 10000121L;

  private int currentHbarEquiv;
  private int currentCentEquiv;
  private long currentExpirationTime;

  private int nextHbarEquiv;
  private int nextCentEquiv;
  private long nextExpirationTime;

  private boolean initialized = false;

  public ExchangeRateSetWrapper() {
  }

  public ExchangeRateSetWrapper(
          int currentHbarEquiv, int currentCentEquiv, long currentExpirationTime,
          int nextHbarEquiv, int nextCentEquiv, long nextExpirationTime
  ) {
    this.currentHbarEquiv = currentHbarEquiv;
    this.currentCentEquiv = currentCentEquiv;
    this.currentExpirationTime = currentExpirationTime;

    this.nextHbarEquiv = nextHbarEquiv;
    this.nextCentEquiv = nextCentEquiv;
    this.nextExpirationTime = nextExpirationTime;

    initialized = true;
  }

  public boolean isInitialized() {
    return initialized;
  }

  public int getCurrentHbarEquiv() {
    return currentHbarEquiv;
  }

  public int getCurrentCentEquiv() {
    return currentCentEquiv;
  }

  public int getNextHbarEquiv() {
    return nextHbarEquiv;
  }

  public int getNextCentEquiv() {
    return nextCentEquiv;
  }

  public void update(final ExchangeRateSet exchangeRateSet) {
    this.currentHbarEquiv = exchangeRateSet.getCurrentRate().getHbarEquiv();
    this.currentCentEquiv = exchangeRateSet.getCurrentRate().getCentEquiv();
    this.currentExpirationTime = exchangeRateSet.getCurrentRate().getExpirationTime().getSeconds();

    this.nextHbarEquiv = exchangeRateSet.getNextRate().getHbarEquiv();
    this.nextCentEquiv = exchangeRateSet.getNextRate().getCentEquiv();
    this.nextExpirationTime = exchangeRateSet.getNextRate().getExpirationTime().getSeconds();

    initialized = true;
  }

  @Override
  public synchronized ExchangeRateSetWrapper copy() {
    return new ExchangeRateSetWrapper(currentHbarEquiv, currentCentEquiv, currentExpirationTime,
        nextHbarEquiv,
        nextCentEquiv, nextExpirationTime);
  }

  @Override
  public synchronized void copyTo(FCDataOutputStream fcDataOutputStream) throws IOException {
    fcDataOutputStream.writeLong(VERSION);
    fcDataOutputStream.writeLong(OBJECT_ID);
    fcDataOutputStream.writeInt(currentHbarEquiv);
    fcDataOutputStream.writeInt(currentCentEquiv);
    fcDataOutputStream.writeLong(currentExpirationTime);
    fcDataOutputStream.writeInt(nextHbarEquiv);
    fcDataOutputStream.writeInt(nextCentEquiv);
    fcDataOutputStream.writeLong(nextExpirationTime);
  }

  @Override
  public synchronized void copyFrom(FCDataInputStream fcDataInputStream) throws IOException {
    //version number
    fcDataInputStream.readLong();
    long objectId = fcDataInputStream.readLong();
    if (objectId != OBJECT_ID) {
      throw new IOException(
          "Read Invalid ObjectID while calling ExchangeRateSetWrapper.copyFrom()");
    }
    currentHbarEquiv = fcDataInputStream.readInt();
    currentCentEquiv = fcDataInputStream.readInt();
    currentExpirationTime = fcDataInputStream.readLong();
    nextHbarEquiv = fcDataInputStream.readInt();
    nextCentEquiv = fcDataInputStream.readInt();
    nextExpirationTime = fcDataInputStream.readLong();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.JSON_STYLE).append("currentHbarEquiv", currentHbarEquiv)
        .append("currentCentEquiv", currentCentEquiv)
        .append("currentExpirationTime", currentExpirationTime)
        .append("nextHbarEquiv", nextHbarEquiv).append("nextCentEquiv", nextCentEquiv)
        .append("nextExpirationTime", nextExpirationTime).toString();
  }

  @Override
  public void copyToExtra(FCDataOutputStream fcDataOutputStream) throws IOException {
    //NoOp method
  }

  @Override
  public void copyFromExtra(FCDataInputStream fcDataInputStream) throws IOException {
    //NoOp method
  }

  @Override
  public void diffCopyTo(FCDataOutputStream outStream, FCDataInputStream inStream) throws IOException {
    //NoOp method
  }

  @Override
  public void diffCopyFrom(FCDataOutputStream outStream, FCDataInputStream inStream) throws IOException {
    //NoOp method
  }

  @Override
  public void delete() {
    //NoOp method
  }
}
