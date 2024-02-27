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

package com.swirlds.logging.api.internal.format;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * based on https://github.com/apache/tomcat/blob/main/java/org/apache/tomcat/util/http/FastHttpDateFormat.java#L163
 */
public class ConcurrentDateFormat {

    private final String format;
    private final Locale locale;
    private final TimeZone timezone;
    private final Queue<SimpleDateFormat> queue = new ConcurrentLinkedQueue<>();

    private final Map<Long, String> formatCache = new ConcurrentHashMap<>();

    private final int CACHE_SIZE = 60_000;

    public ConcurrentDateFormat(String format, Locale locale, TimeZone timezone) {
        this.format = format;
        this.locale = locale;
        this.timezone = timezone;
        SimpleDateFormat initial = createInstance();
        queue.add(initial);
    }

    public String format(long timestamp) {
        final Long key = Long.valueOf(timestamp);
        String cachedDate = formatCache.get(key);
        if (cachedDate != null) {
            return cachedDate;
        } else {
            if (formatCache.size() > CACHE_SIZE) {
                formatCache.clear();
            }
            SimpleDateFormat sdf = queue.poll();
            if (sdf == null) {
                sdf = createInstance();
            }
            String result = sdf.format(new Date(timestamp));
            formatCache.put(key, result);
            queue.add(sdf);
            return result;
        }
    }

    private SimpleDateFormat createInstance() {
        SimpleDateFormat sdf = new SimpleDateFormat(format, locale);
        sdf.setTimeZone(timezone);
        return sdf;
    }
}
