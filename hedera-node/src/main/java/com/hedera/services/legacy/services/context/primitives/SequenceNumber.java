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

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Sequence number is use for creating crypto, smart contract accounts and file ID
 *
 * @author Akshay
 * @Date : 11/16/2018
 */
public class SequenceNumber implements FastCopyable {

  private volatile long sequenceNum;

  public SequenceNumber(long sequenceNum) {
    this.sequenceNum = sequenceNum;
  }

  public SequenceNumber() {
  }

  public synchronized long getNextSequenceNum() {
    return sequenceNum++;
  }

  public long getCurrentSequenceNum() {
    return sequenceNum;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SequenceNumber that = (SequenceNumber) o;
    return sequenceNum == that.sequenceNum;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sequenceNum);
  }

  @Override
  public synchronized SequenceNumber copy() {
    return new SequenceNumber(this.sequenceNum);
  }

  @Override
  public synchronized void copyTo(FCDataOutputStream fcDataOutputStream) throws IOException {
    fcDataOutputStream.writeLong(sequenceNum);
  }

  @Override
  public synchronized void copyFrom(FCDataInputStream fcDataInputStream) throws IOException {
    this.sequenceNum = fcDataInputStream.readLong();
  }

  @Override
  public void copyFromExtra(FCDataInputStream arg0) throws IOException {
    //NoOp method
  }

  @Override
  public void copyToExtra(FCDataOutputStream arg0) throws IOException {
    //NoOp method
  }

  @Override
  public void delete() {
    //NoOp method
  }
}
