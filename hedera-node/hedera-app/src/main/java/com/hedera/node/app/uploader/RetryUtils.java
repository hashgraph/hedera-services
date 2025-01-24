/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.uploader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RetryUtils {
    private static final Logger logger = LogManager.getLogger(RetryUtils.class);

    public static <T> T withRetry(RetryUtils.SupplierWithException<T> task, int retries) {
        int attempt = 0;
        while (attempt < retries) {
            try {
                return task.get();
            } catch (Exception e) {
                attempt++;
                if (attempt == retries) {
                    // Return failure message after retries
                    return (T) ("Failed after " + retries + " attempts");
                }
            }
        }
        return null; // This line should never be reached if retries are exhausted
    }

    private static long calculateBackoff(int attempt) {
        // Exponential backoff with jitter: 2^n * 100ms + random(50ms)
        return (long) (Math.pow(2, attempt) * 100 + Math.random() * 50);
    }

    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
