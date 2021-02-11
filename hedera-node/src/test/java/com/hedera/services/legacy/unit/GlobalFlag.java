package com.hedera.services.legacy.unit;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GlobalFlag {
  private static final Logger log = LogManager.getLogger(GlobalFlag.class);

  private ExchangeRateSet exchangeRateSet;
  private static volatile GlobalFlag instance;

  private GlobalFlag() {
    this.exchangeRateSet = ExchangeRateSet.getDefaultInstance();
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

  public ExchangeRateSet getExchangeRateSet() {
    return exchangeRateSet;
  }

  public void setExchangeRateSet(ExchangeRateSet exchangeRateSet) {
    log.info("Exchange rates are now :: {}", exchangeRateSet);
    this.exchangeRateSet = exchangeRateSet;
  }
}
