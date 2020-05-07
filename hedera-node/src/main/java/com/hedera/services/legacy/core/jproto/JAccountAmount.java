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

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JAccountAmount implements FastCopyable {

  private static final Logger log = LogManager.getLogger(JAccountAmount.class);
  private static final long LEGACY_VERSION_1 = 1;
  private static final long CURRENT_VERSION = 2;
  private JAccountID accountID;
  private long amount;

  public JAccountAmount() {
  }

  public JAccountAmount(final JAccountID accountID, final long amount) {
    this.accountID = accountID;
    this.amount = amount;
  }

  public JAccountAmount(final JAccountAmount other) {
    this.accountID = (JAccountID) other.accountID.copy();
    this.amount = other.amount;
  }

  public JAccountID getAccountID() {
    return accountID;
  }

  public void setAccountID(final JAccountID accountID) {
    this.accountID = accountID;
  }

  public long getAmount() {
    return amount;
  }

  public void setAmount(final long amount) {
    this.amount = amount;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JAccountAmount that = (JAccountAmount) o;
    return amount == that.amount &&
        Objects.equals(accountID, that.accountID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accountID, amount);
  }

  /**
   * Custom serialize method. If some field is null then it will set 0 byte for that field otherwise
   * it add length of the byte first and then actual byte of the field.
   */

  private void serialize(final FCDataOutputStream outStream) throws IOException {
    outStream.writeLong(CURRENT_VERSION);
    outStream.writeLong(JObjectType.JAccountAmount.longValue());

    this.accountID.copyTo(outStream);
    this.accountID.copyToExtra(outStream);

    outStream.writeLong(this.amount);

  }

  /**
   * Custom deserialization  of this class. It read first length of the field if it is 0 then sets
   * field to null otherwise it read bytes from DataInputStream of specified length and deserialize
   * those byte for the field.
   *
   * @return deserialize JAccountAmount
   */
  @SuppressWarnings("unchecked")
  public static <T extends FastCopyable> T deserialize(final DataInputStream inStream)
      throws IOException {
    final JAccountAmount accountAmount = new JAccountAmount();

    deserialize(inStream, accountAmount);
    return (T) accountAmount;
  }

  private static void deserialize(final DataInputStream inStream,
      final JAccountAmount accountAmount) throws IOException {
    final long version = inStream.readLong();
    if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
      throw new IllegalStateException("Illegal version was read from the stream");
    }

    final long objectType = inStream.readLong();
    final JObjectType type = JObjectType.valueOf(objectType);
    if (!JObjectType.JAccountAmount.equals(type)) {
      throw new IllegalStateException("Illegal JObjectType was read from the stream");
    }

    accountAmount.accountID = JAccountID.deserialize(inStream);
    accountAmount.amount = inStream.readLong();
  }

  @Override
  public FastCopyable copy() {
    return new JAccountAmount(this);
  }

  @Override
  public void copyTo(final FCDataOutputStream outStream) throws IOException {
    serialize(outStream);
  }

  @Override
  public void copyFrom(final FCDataInputStream inStream) throws IOException {
    //NoOp method
  }

  @Override
  public void copyToExtra(final FCDataOutputStream outStream) throws IOException {
    //NoOp method
  }

  @Override
  public void copyFromExtra(final FCDataInputStream inStream) throws IOException {
    //NoOp method
  }

  @Override
  public void diffCopyTo(final FCDataOutputStream outStream, final FCDataInputStream inStream)
      throws IOException {
    serialize(outStream);
  }

  @Override
  public void diffCopyFrom(final FCDataOutputStream outStream, final FCDataInputStream inStream)
      throws IOException {
    deserialize(inStream, this);
  }

  @Override
  public void delete() {
    //NoOp method
  }
}
