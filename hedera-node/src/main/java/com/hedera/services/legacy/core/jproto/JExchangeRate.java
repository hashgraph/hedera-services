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

import com.hedera.services.legacy.exception.DeserializationException;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hedera.services.legacy.exception.SerializationException;

/**
 * Custom class represents ExchangeRate message.
 * 
 * @author Hua Li Created on 2019-06-11
 */
public class JExchangeRate implements FastCopyable {
  private static final Logger log = LogManager.getLogger(JExchangeRate.class);
  private static final long LEGACY_VERSION_1 = 1;
  private static final long CURRENT_VERSION = 2;
  private int hbarEquiv;
  private int centEquiv;
  private long expirationTime;

  public JExchangeRate() {
  }

  public JExchangeRate(int hbarEquiv, int centEquiv, long expirationTime) {
    super();
    this.hbarEquiv = hbarEquiv;
    this.centEquiv = centEquiv;
    this.expirationTime = expirationTime;
  }

  public JExchangeRate(final JExchangeRate other) {
  	super();
  	this.hbarEquiv = other.hbarEquiv;
  	this.centEquiv = other.centEquiv;
  	this.expirationTime = other.expirationTime;
  }

  public static JExchangeRate convert(ExchangeRate exchangeRate) {
    return new JExchangeRate(exchangeRate.getHbarEquiv(), exchangeRate.getCentEquiv(),
        exchangeRate.getExpirationTime().getSeconds());
  }

  public static ExchangeRate convert(JExchangeRate exchangeRate) {
    return ExchangeRate.newBuilder().setHbarEquiv(exchangeRate.getHbarEquiv())
        .setCentEquiv(exchangeRate.getCentEquiv())
        .setExpirationTime(
            TimestampSeconds.newBuilder().setSeconds(exchangeRate.getExpirationTime()).build())
        .build();
  }

  public int getHbarEquiv() {
    return hbarEquiv;
  }

  public void setHbarEquiv(int hbarEquiv) {
    this.hbarEquiv = hbarEquiv;
  }

  public int getCentEquiv() {
    return centEquiv;
  }

  public void setCentEquiv(int centEquiv) {
    this.centEquiv = centEquiv;
  }

  public long getExpirationTime() {
    return expirationTime;
  }

  public void setExpirationTime(long expirationTime) {
    this.expirationTime = expirationTime;
  }

  /**
   * Custom deserialization of this class.
   * 
   * @param bytes serialize byte array
   * @return deserialize JExchangeRate
   */
  public static JExchangeRate deserialize(byte[] bytes) throws DeserializationException {
    JExchangeRate rv = new JExchangeRate();
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
        rv.hbarEquiv = buffer.readInt();
        rv.centEquiv = buffer.readInt();
        rv.expirationTime = buffer.readLong();
      } catch (Exception e) {
        if (log.isDebugEnabled()) log.debug("Error in deserialize of JExchangeRate " + rv.toString(), e);
        throw new DeserializationException(e);
      }
    } catch (IOException e) {
      if (log.isDebugEnabled()) log.debug("Error in deserialize of JExchangeRate " + rv.toString(), e);
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
        buffer.writeInt(this.hbarEquiv);
        buffer.writeInt(this.centEquiv);
        buffer.writeLong(this.expirationTime);
        buffer.flush();
        byteArrayOutputStream.flush();
        rv = byteArrayOutputStream.toByteArray();
      } catch (Exception e) {
        if (log.isDebugEnabled()) log.debug("Error in serialization of JExchangeRate " + this.toString(), e);
        throw new SerializationException(e);
      }
    } catch (IOException e) {
      if (log.isDebugEnabled()) log.debug("Error in serialization of JExchangeRate :: " + this.toString(), e);
      throw new SerializationException(e);
    }
    return rv;
  }

  @Override
  public FastCopyable copy() {
  	return new JExchangeRate(this);
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
  public void diffCopyFrom(final FCDataOutputStream outStream, final FCDataInputStream inStream) throws IOException {
  	deserialize(inStream, this);
  }

  @Override
  public void diffCopyTo(final FCDataOutputStream outStream, final FCDataInputStream inStream) throws IOException {
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
      JExchangeRate that = (JExchangeRate) o;
      return hbarEquiv == that.hbarEquiv &&
          centEquiv == that.centEquiv &&
          expirationTime == that.expirationTime;
  }

  @Override
  public int hashCode() {
      return Objects.hash(hbarEquiv, centEquiv, expirationTime);
  }

  @Override
  public String toString() {
  	return "JExchangeRate{" +
  			"hbarEquiv=" + hbarEquiv +
            ", centEquiv=" + centEquiv +
            ", expirationTime=" + expirationTime +
  			'}';
  }

  @SuppressWarnings("unchecked")
  public static <T extends FastCopyable> T deserialize(final DataInputStream inStream) throws IOException {
  	final JExchangeRate jExchangeRate = new JExchangeRate();
  
  	deserialize(inStream, jExchangeRate);
  	return (T) jExchangeRate;
  }

  private static void deserialize(final DataInputStream inStream, final JExchangeRate jExchangeRate) throws IOException {
  	long version = inStream.readLong();
  	if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
  		throw new IllegalStateException("Illegal version was read from the stream");
  	}
  
  	long objectType = inStream.readLong();
  	JObjectType type = JObjectType.valueOf(objectType);
  	if (!JObjectType.JExchangeRate.equals(type)) {
  		throw new IllegalStateException("Illegal JObjectType was read from the stream");
  	}
  
  	jExchangeRate.hbarEquiv = inStream.readInt();
  	jExchangeRate.centEquiv = inStream.readInt();
  	jExchangeRate.expirationTime = inStream.readLong();
  }

  private void serialize(final DataOutputStream outStream) throws IOException {
  	outStream.writeLong(CURRENT_VERSION);
  	outStream.writeLong(JObjectType.JExchangeRate.longValue());
  	outStream.writeInt(this.hbarEquiv);
  	outStream.writeInt(this.centEquiv);
  	outStream.writeLong(this.expirationTime);
  }

}
