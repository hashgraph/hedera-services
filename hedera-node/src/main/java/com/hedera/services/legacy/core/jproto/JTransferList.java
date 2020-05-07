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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Akshay
 * @Date : 1/9/2019
 */
public class JTransferList implements FastCopyable {

  private static final Logger log = LogManager.getLogger(JTransferList.class);
  private static final long LEGACY_VERSION_1 = 1;
  private static final long CURRENT_VERSION = 2;
  private List<JAccountAmount> jAccountAmountsList;

  public JTransferList() {
    jAccountAmountsList = new LinkedList<>();
  }

  public JTransferList(final List<AccountAmount> accountAmounts) {
    this.jAccountAmountsList = convert(accountAmounts);
  }

  public JTransferList(final JTransferList other) {
    this.jAccountAmountsList = new LinkedList<>(other.jAccountAmountsList);
  }

  public List<JAccountAmount> convert(final List<AccountAmount> accountAmounts) {
    return accountAmounts
        .stream()
        .filter(a -> a.getAccountID() != null)
        .map(a -> new JAccountAmount(JAccountID.convert(a.getAccountID()), a.getAmount()))
        .collect(Collectors.toList());
  }

  public List<JAccountAmount> getjAccountAmountsList() {
    return jAccountAmountsList;
  }


  public static AccountAmount convert(final JAccountAmount jAccountAmount) {
    AccountAmount.Builder builder = AccountAmount.newBuilder();
    if (jAccountAmount.getAccountID() != null) {
      AccountID accountID = RequestBuilder
          .getAccountIdBuild(jAccountAmount.getAccountID().getAccountNum(),
              jAccountAmount.getAccountID().getRealmNum(),
              jAccountAmount.getAccountID().getShardNum());
      builder.setAccountID(accountID);
    }
    return builder.setAmount(jAccountAmount.getAmount()).build();
  }

  /**
   * Custom serialize method. If some field is null then it will set 0 byte for that field otherwise
   * it add length of the byte first and then actual byte of the field.
   */
  private void serialize(final FCDataOutputStream outStream) throws IOException {

    outStream.writeLong(CURRENT_VERSION);
    outStream.writeLong(JObjectType.JTransferList.longValue());
    if (CollectionUtils.isNotEmpty(jAccountAmountsList)) {
      outStream.writeInt(jAccountAmountsList.size());
      jAccountAmountsList.forEach(a -> {
        try {
          a.copyTo(outStream);
          a.copyToExtra(outStream);
        } catch (IOException e) {
          log.error("Error in serialization of JTransferList :: " + this.toString(), e);
        }
      });
    } else {
      outStream.writeInt(0); // size of the list is zero here
    }

  }

  /**
   * Custom deserialization  of this class. It read first length of the field if it is 0 then sets
   * field to null otherwise it read bytes from DataInputStream of specified length and deserialize
   * those byte for the field.
   *
   * @return deserialize JTransferList
   */

  @SuppressWarnings("unchecked")
  public static <T extends FastCopyable> T deserialize(final DataInputStream inStream)
      throws IOException {
    final JTransferList transferList = new JTransferList();

    deserialize(inStream, transferList);
    return (T) transferList;
  }

  private static void deserialize(final DataInputStream inStream, final JTransferList list)
      throws IOException {
    final long version = inStream.readLong();
    if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
      throw new IllegalStateException("Illegal version was read from the stream");
    }

    final long objectType = inStream.readLong();
    final JObjectType type = JObjectType.valueOf(objectType);
    if (!JObjectType.JTransferList.equals(type)) {
      throw new IllegalStateException("Illegal JObjectType was read from the stream");
    }

    int listSize = inStream.readInt();
    for (int i = 0; i < listSize; i++) {
      list.jAccountAmountsList.add(JAccountAmount.deserialize(inStream));
    }
  }

  @Override
  public FastCopyable copy() {
    return new JTransferList(this);
  }

  @Override
  public void copyTo(final FCDataOutputStream outStream) throws IOException {
    serialize(outStream);
  }

  @Override
  public void copyFrom(final FCDataInputStream inStream) throws IOException {

  }

  @Override
  public void copyToExtra(final FCDataOutputStream outStream) throws IOException {

  }

  @Override
  public void copyFromExtra(final FCDataInputStream inStream) throws IOException {

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

  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    JTransferList that = (JTransferList) o;
    return Objects.equals(jAccountAmountsList, that.jAccountAmountsList);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jAccountAmountsList);
  }

}
