package com.hedera.services.legacy.core.jproto;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hedera.services.legacy.exception.DeserializationException;
import com.hedera.services.legacy.exception.SerializationException;

/**
 * Custom class represents ExchangeRateSet message.
 * 
 * @author Hua Li Created on 2019-06-11
 */
public class JExchangeRateSet implements FastCopyable {
  private static final Logger log = LogManager.getLogger(JExchangeRateSet.class);
  private static final long LEGACY_VERSION_1 = 1;
  private static final long CURRENT_VERSION = 2;
  private JExchangeRate currentRate;
  private JExchangeRate nextRate;

  public JExchangeRateSet() {}

  public JExchangeRateSet(JExchangeRate currentRate, JExchangeRate nextRate) {
    super();
    this.currentRate = currentRate;
    this.nextRate = nextRate;
  }

  public JExchangeRateSet(final JExchangeRateSet other) {
    this.currentRate = other.currentRate;
    this.nextRate = other.nextRate;
  }

  public static JExchangeRateSet convert(ExchangeRateSet exchangeRateSet) {
    return new JExchangeRateSet(JExchangeRate.convert(exchangeRateSet.getCurrentRate()),
        JExchangeRate.convert(exchangeRateSet.getNextRate()));
  }

  public static ExchangeRateSet convert(JExchangeRateSet exchangeRate) {
    return ExchangeRateSet.newBuilder()
        .setCurrentRate(JExchangeRate.convert(exchangeRate.getCurrentRate()))
        .setNextRate(JExchangeRate.convert(exchangeRate.getNextRate())).build();
  }

  public JExchangeRate getCurrentRate() {
    return currentRate;
  }

  public void setCurrentRate(JExchangeRate currentRate) {
    this.currentRate = currentRate;
  }

  public JExchangeRate getNextRate() {
    return nextRate;
  }

  public void setNextRate(JExchangeRate nextRate) {
    this.nextRate = nextRate;
  }

  /**
   * Custom deserialization of this class.
   * 
   * @param bytes serialize byte array
   * @return deserialize JExchangeRate
   */
  public static JExchangeRateSet deserialize(byte[] bytes) throws DeserializationException {
    JExchangeRateSet rv = new JExchangeRateSet();
    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes)) {
      try (DataInputStream buffer = new DataInputStream(byteArrayInputStream)) {

        long version = buffer.readLong();
        if (version != CURRENT_VERSION) {
          throw new IllegalStateException("Illegal version was read from the stream");
        }
        long objectType = buffer.readLong();
        JObjectType type = JObjectType.valueOf(objectType);
        if (objectType < 0 || type == null) {
          throw new IllegalStateException("Illegal JObjectType was read from the stream");
        }

        byte[] tBytes = new byte[buffer.readInt()];
        buffer.read(tBytes);
        rv.currentRate = JExchangeRate.deserialize(tBytes);

        tBytes = new byte[buffer.readInt()];
        buffer.read(tBytes);
        rv.nextRate = JExchangeRate.deserialize(tBytes);
      } catch (Exception e) {
        if (log.isDebugEnabled()) log.debug("Error in deserialize of JExchangeRateSet " + rv.toString(), e);
        throw new DeserializationException(e);
      }
    } catch (IOException e) {
      if (log.isDebugEnabled()) log.debug("Error in deserialize of JExchangeRateSet " + rv.toString(), e);
      throw new DeserializationException(e);
    }

    return rv;
  }

  /**
   * Custom serialize method.
   *
   * @return serialized byte array of this class
   */
  public byte[] serialize() throws SerializationException {
    byte[] rv;
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
      try (DataOutputStream buffer = new DataOutputStream(byteArrayOutputStream)) {

        buffer.writeLong(CURRENT_VERSION);
        buffer.writeLong(JObjectType.JExchangeRate.longValue());
        byte[] bytes = this.currentRate.serialize();
        buffer.writeInt(bytes.length);
        buffer.write(bytes);

        bytes = this.nextRate.serialize();
        buffer.writeInt(bytes.length);
        buffer.write(bytes);

        buffer.flush();
        byteArrayOutputStream.flush();
        rv = byteArrayOutputStream.toByteArray();
      } catch (Exception e) {
        if (log.isDebugEnabled()) log.debug("Error in serialization of JExchangeRateSet " + this.toString(), e);
        throw new SerializationException(e);
      }
    } catch (IOException e) {
      if (log.isDebugEnabled()) log.debug("Error in serialization of JExchangeRateSet :: " + this.toString(), e);
      throw new SerializationException(e);
    }
    return rv;
  }

  @Override
  public FastCopyable copy() {
    return new JExchangeRateSet(this);
  }

  @Override
  public void copyFrom(final FCDataInputStream inStream) throws IOException {

  }

  @Override
  public void copyFromExtra(final FCDataInputStream inStream) throws IOException {

  }

  @Override
  public void copyTo(final FCDataOutputStream outStream) throws IOException {
    serialize(outStream);
  }

  @Override
  public void copyToExtra(final FCDataOutputStream outStream) throws IOException {

  }

  @Override
  public void delete() {

  }

  @Override
  public void diffCopyFrom(final FCDataOutputStream outStream, final FCDataInputStream inStream)
      throws IOException {
    deserialize(inStream, this);
  }

  @Override
  public void diffCopyTo(final FCDataOutputStream outStream, final FCDataInputStream inStream)
      throws IOException {
    serialize(outStream);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JExchangeRateSet that = (JExchangeRateSet) o;
    return Objects.equals(currentRate, that.currentRate) && Objects.equals(nextRate, that.nextRate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(currentRate, nextRate);
  }

  @Override
  public String toString() {
    return "JExchangeRateSet{" + "currentRate=" + currentRate + ", nextRate=" + nextRate + '}';
  }

  @SuppressWarnings("unchecked")
  public static <T extends FastCopyable> T deserialize(final DataInputStream inStream)
      throws IOException {
    final JExchangeRateSet jExchangeRateSet = new JExchangeRateSet();

    deserialize((FCDataInputStream) inStream, jExchangeRateSet);
    return (T) jExchangeRateSet;
  }

  /**
   * Custom deserialization of this class. It read first length of the field if it is 0 then sets
   * field to null otherwise it read bytes from DataInputStream of specified length and deserialize
   * those byte for the field.
   */

  private static void deserialize(final FCDataInputStream inStream,
      final JExchangeRateSet jExchangeRateSet) throws IOException {

    long version = inStream.readLong();
    if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
      throw new IllegalStateException("Illegal version was read from the stream");
    }

    long objectType = inStream.readLong();
    JObjectType type = JObjectType.valueOf(objectType);
    if (!JObjectType.JExchangeRateSet.equals(type)) {
      throw new IllegalStateException("Illegal JObjectType was read from the stream");
    }

    final boolean currentRatePresent = inStream.readBoolean();
    if (currentRatePresent) {
      jExchangeRateSet.currentRate = JExchangeRate.deserialize(inStream);
    }

    final boolean nextRatePresent = inStream.readBoolean();
    if (nextRatePresent) {
      jExchangeRateSet.nextRate = JExchangeRate.deserialize(inStream);
    }
  }

  /**
   * Custom serialize method. If some field is null then it will set 0 byte for that field otherwise
   * it add length of the byte first and then actual byte of the field.
   *
   * @return serialized byte array of this class
   */
  private void serialize(final FCDataOutputStream outStream) throws IOException {

    outStream.writeLong(CURRENT_VERSION);
    outStream.writeLong(JObjectType.JExchangeRateSet.longValue());

    if (this.currentRate != null) {
      outStream.writeBoolean(true);
      this.currentRate.copyTo(outStream);
      this.currentRate.copyToExtra(outStream);
    } else {
      outStream.writeBoolean(false);
    }

    if (this.nextRate != null) {
      outStream.writeBoolean(true);
      this.nextRate.copyTo(outStream);
      this.nextRate.copyToExtra(outStream);

    } else {
      outStream.writeBoolean(false);
    }
  }
}
