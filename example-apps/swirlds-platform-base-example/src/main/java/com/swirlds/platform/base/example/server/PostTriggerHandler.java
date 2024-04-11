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

package com.swirlds.platform.base.example.server;

import com.sun.net.httpserver.HttpExchange;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import com.swirlds.platform.base.example.store.persistence.exception.EntityNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PostTriggerHandler<T> implements HttpHandlerDefinition {

    private static final Logger logger = Loggers.getLogger(PostTriggerHandler.class);

    private final Class<T> bodyType;

    private final Consumer<T> postHandler;

    private final String path;

    public PostTriggerHandler(final String path, Class<T> bodyType, Consumer<T> postHandler) {
        this.path = path;
        this.bodyType = bodyType;
        this.postHandler = postHandler;
    }

    public Class<T> getBodyType() {
        return bodyType;
    }

    private void post(T body) {
        postHandler.accept(body);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final String requestMethod = exchange.getRequestMethod();
        long start = System.nanoTime();

        final List<String> urlParts =
                DataTransferUtils.urlToList(exchange.getRequestURI().toString());
        final String id = urlParts.getLast();
        Object result = null;
        int statusCode;
        try {
            if ("POST".equals(requestMethod)) {
                this.post(DataTransferUtils.deserializeJsonFromExchange(exchange, getBodyType()));
                statusCode = 201;
            } else {
                throw new UnsupportedOperationException("Operation not supported for " + requestMethod);
            }
        } catch (UnsupportedOperationException e) {
            statusCode = 405;
            result = Map.of("error", "Operation not supported for " + requestMethod);
        } catch (IllegalArgumentException e) {
            statusCode = 400;
            result = Map.of("error", e.getMessage());
        } catch (EntityNotFoundException e) {
            statusCode = 404;
            result = Map.of("error", e.getEntityType() + " with code " + e.getId() + " not found");
        } catch (RuntimeException e) {
            statusCode = 500;
            result = Map.of("error", "Internal error");
        }

        final String response = DataTransferUtils.serializeToJson(result);
        long duration = System.nanoTime() - start;
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();

        logger.trace(
                "Received Request to:{}{} took:{}ns answered status:{} body:{}",
                requestMethod,
                exchange.getRequestURI(),
                duration,
                statusCode,
                response);
    }

    @Override
    public String path() {
        return path;
    }
}
