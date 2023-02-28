/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.services.bdd.suites.utils.validation.domain;

public class PersistentContract {
    Long num;
    Long bytecode;
    String source;
    Integer luckyNo;

    public Long getNum() {
        return num;
    }

    public void setNum(Long num) {
        this.num = num;
    }

    public Integer getLuckyNo() {
        return luckyNo;
    }

    public void setLuckyNo(Integer luckyNo) {
        this.luckyNo = luckyNo;
    }

    public Long getBytecode() {
        return bytecode;
    }

    public void setBytecode(Long bytecode) {
        this.bytecode = bytecode;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
