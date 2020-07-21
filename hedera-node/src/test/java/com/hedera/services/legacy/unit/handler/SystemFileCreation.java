package com.hedera.services.legacy.unit.handler;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.legacy.unit.FCStorageWrapper;
import com.hedera.services.legacy.service.GlobalFlag;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.builder.RequestBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SystemFileCreation {
  private static final Logger log = LogManager.getLogger(SystemFileCreation.class);
  private FCStorageWrapper storageWrapper;

  public SystemFileCreation(FCStorageWrapper storageWrapper) {
    this.storageWrapper = storageWrapper;
  }

  public ExchangeRateSet readExchangeRate(String fileDataPath) {
    byte[] bytes;
    bytes = storageWrapper.fileRead(fileDataPath);
    ExchangeRateSet exchangeRate = getDefaultExchangeRateSet();
    if (bytes.length > 0) {
      try {
        exchangeRate = ExchangeRateSet.parseFrom(bytes);
      } catch (InvalidProtocolBufferException e) {
        log.error("Error in parsing exchange rate file..setting default values.");
      }
    }
    GlobalFlag globalFlag = GlobalFlag.getInstance();
    globalFlag.setExchangeRateSet(exchangeRate);
    return exchangeRate;
  }

  private ExchangeRateSet getDefaultExchangeRateSet() {
    long expiryTime = PropertiesLoader.getExpiryTime();
    int currentHbarEquivalent = PropertiesLoader.getCurrentHbarEquivalent();
    int currentCentEquivalent = PropertiesLoader.getCurrentCentEquivalent();
    return RequestBuilder
        .getExchangeRateSetBuilder(currentHbarEquivalent, currentCentEquivalent, expiryTime,
            currentHbarEquivalent, 15, expiryTime);
  }
}
