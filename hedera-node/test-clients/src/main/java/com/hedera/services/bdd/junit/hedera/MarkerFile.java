/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.hedera;

/**
 * Enumerates the marker files that may be written by a node.
 */
public enum MarkerFile {
    NOW_FROZEN_MF {
        @Override
        public String fileName() {
            return "now_frozen.mf";
        }
    },
    EXEC_IMMEDIATE_MF {
        @Override
        public String fileName() {
            return "execute_immediate.mf";
        }
    },
    EXEC_TELEMETRY_MF {
        @Override
        public String fileName() {
            return "execute_telemetry.mf";
        }
    },
    FREEZE_SCHEDULED_MF {
        @Override
        public String fileName() {
            return "freeze_scheduled.mf";
        }
    },
    FREEZE_ABORTED_MF {
        @Override
        public String fileName() {
            return "freeze_aborted.mf";
        }
    };

    public abstract String fileName();
}
