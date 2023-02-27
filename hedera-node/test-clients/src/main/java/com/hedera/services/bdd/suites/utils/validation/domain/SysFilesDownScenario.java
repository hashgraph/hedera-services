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

import java.util.ArrayList;
import java.util.List;

public class SysFilesDownScenario {
    public static final String COMPARE_EVAL_MODE = "compare";
    public static final String SNAPSHOT_EVAL_MODE = "snapshot";

    String evalMode = SNAPSHOT_EVAL_MODE;
    List<Integer> numsToFetch = new ArrayList<>();

    public List<Integer> getNumsToFetch() {
        return numsToFetch;
    }

    public void setNumsToFetch(List<Integer> numsToFetch) {
        this.numsToFetch = numsToFetch;
    }

    public String getEvalMode() {
        return evalMode;
    }

    public void setEvalMode(String evalMode) {
        this.evalMode = evalMode;
    }
}
