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

package com.swirlds.base.sample.internal;

import com.google.common.base.Preconditions;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.swirlds.base.sample.metrics.ApplicationMetrics;
import com.swirlds.base.sample.persistence.exception.EntityNotFoundException;
import com.swirlds.base.sample.service.CrudService;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdapterHandler<T> implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(AdapterHandler.class.getName());
    private @NonNull final Context context;
    private @NonNull final CrudService<T> delegatedService;
    private @NonNull final String path;
    private @NonNull final CountPerSecond tps;

    protected AdapterHandler(final @NonNull Context context, CrudService<T> delegatedService, final String path) {
        this.context = Objects.requireNonNull(context);
        this.path = Objects.requireNonNull(path);
        this.delegatedService = Objects.requireNonNull(delegatedService);
        this.tps = new CountPerSecond(context.metrics(), ApplicationMetrics.REQUESTS_PER_SECOND);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final String requestMethod = exchange.getRequestMethod();
        long start = System.nanoTime();

        final List<String> urlParts =
                DataTransferUtils.urlToList(exchange.getRequestURI().toString());
        final String id = urlParts.getLast();
        final boolean isId = !id.equalsIgnoreCase(getControllerName());
        Object result = null;
        int statusCode;
        try {
            switch (requestMethod) {
                case "GET":
                    result = (isId && !id.contains("?"))
                            ? retrieve(id)
                            : retrieveAll(DataTransferUtils.getUrlParams(exchange));
                    statusCode = 200;
                    break;
                case "POST":
                    result = this.create(
                            DataTransferUtils.deserializeJsonFromExchange(exchange, delegatedService.getResultType()));
                    statusCode = 201;
                    break;
                case "PUT":
                    if (!isId) {
                        throw new IllegalArgumentException("Identification must be sent in url");
                    }
                    result = this.update(
                            id,
                            DataTransferUtils.deserializeJsonFromExchange(exchange, delegatedService.getResultType()));
                    statusCode = 200;
                    break;
                case "DELETE":
                    if (!isId) {
                        throw new IllegalArgumentException("Identification must be sent in url");
                    }
                    this.delete(id);
                    statusCode = 202;
                    break;
                case "OPTIONS": {
                    handleOptionsRequest(exchange);
                    return;
                }

                default:
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
        context.metrics().getOrCreate(ApplicationMetrics.REQUEST_TOTAL).increment();
        context.metrics().getOrCreate(ApplicationMetrics.REQUEST_AVG_TIME).update(duration);
        tps.count();
        if (statusCode >= 300) {
            context.metrics().getOrCreate(ApplicationMetrics.ERROR_TOTAL).increment();
        }
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

    protected void delete(final @NonNull String key) {
        this.delegatedService.delete(key);
    }

    protected @NonNull T update(final @NonNull String key, final @Nullable T body) {
        Preconditions.checkArgument(Objects.nonNull(body), "Body is required");
        return this.delegatedService.update(key, body);
    }

    protected @NonNull T create(final @Nullable T body) {
        Preconditions.checkArgument(Objects.nonNull(body), "Body is required");

        return this.delegatedService.create(body);
    }

    protected @NonNull T retrieve(final @NonNull String key) {
        return this.delegatedService.retrieve(key);
    }

    protected @NonNull List<T> retrieveAll(@NonNull Map<String, String> params) {
        return this.delegatedService.retrieveAll(params);
    }

    private @NonNull String getControllerName() {
        return DataTransferUtils.urlToList(this.path).getLast();
    }

    @Override
    public String toString() {
        return this.path;
    }

    public void registerInto(@NonNull final HttpServer server) {

        HttpContext context = server.createContext(path);
        context.setHandler(this);
    }

    private static void handleOptionsRequest(@NonNull final HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "3600");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "content-type, authorization");
        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
    }
}
