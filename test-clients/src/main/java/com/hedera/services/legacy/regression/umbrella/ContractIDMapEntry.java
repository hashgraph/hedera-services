package com.hedera.services.legacy.regression.umbrella;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hederahashgraph.api.proto.java.ContractID;

/**
 * This class represents the key and value for the contractIDMap in SmartContractServiceTest
 *
 * @author pal Created on 2019-04-16
 */
public class ContractIDMapEntry {
  private ContractID id;
  private String fileName;
  public ContractIDMapEntry(ContractID _id, String _fileName) {
    id = _id;
    fileName = _fileName;
  }

  public ContractID getId() {
    return id;
  }

  public String getFileName() {
    return fileName;
  }
}

