package com.hedera.services.legacy.service;

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
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import com.swirlds.common.PlatformStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GlobalFlag {
  private static final Logger log = LogManager.getLogger(GlobalFlag.class);

  private PlatformStatus platformStatus;
  private ExchangeRateSet exchangeRateSet;
  private static volatile GlobalFlag instance;

  private CryptoFeeBuilder crFeeBuilder;
  private FileFeeBuilder fileFeeBuilder;
  private SmartContractFeeBuilder smFeeBuilder;

  private GlobalFlag() {
    this.exchangeRateSet = ExchangeRateSet.getDefaultInstance();
    this.crFeeBuilder = new CryptoFeeBuilder();
    this.fileFeeBuilder = new FileFeeBuilder();
    this.smFeeBuilder = new SmartContractFeeBuilder();
  }

  public static GlobalFlag getInstance() {
    if (instance == null) {
      synchronized (GlobalFlag.class) {
        if (instance == null) {
          instance = new GlobalFlag();
        }
      }
    }
    return instance;
  }

  public PlatformStatus getPlatformStatus() {
    return platformStatus;
  }

  public void setPlatformStatus(PlatformStatus platformStatus) {
    this.platformStatus = platformStatus;
  }

  public ExchangeRateSet getExchangeRateSet() {
    return exchangeRateSet;
  }

  public void setExchangeRateSet(ExchangeRateSet exchangeRateSet) {
    log.info("Exchange rates are now :: {}", exchangeRateSet);
    this.exchangeRateSet = exchangeRateSet;
  }

  public CryptoFeeBuilder getCrFeeBuilder() {
    return crFeeBuilder;
  }

  public FileFeeBuilder getFileFeeBuilder() {
    return fileFeeBuilder;
  }

  public SmartContractFeeBuilder getSmFeeBuilder() {
    return smFeeBuilder;
  }

  public static void setInstance(GlobalFlag instance) {
    GlobalFlag.instance = instance;
  }
}
