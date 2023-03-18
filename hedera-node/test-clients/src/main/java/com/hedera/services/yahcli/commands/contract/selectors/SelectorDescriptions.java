/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.yahcli.commands.contract.selectors;

import static java.net.HttpURLConnection.HTTP_OK;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SelectorDescriptions {

    // See https://www.4byte.directory/docs/ for API to get a method signature from selector
    // (N.B.: I couldn't get the "retrieving signatures" endpoint `/api/v1/signatures/:id/`
    // to work.)

    // TODO: Consider trying to fetch the signature from https://github.com/ethereum-lists/4bytes
    // in case 4byte.directory is down (which it can be).  Reason this wouldn't be the primary
    // source is that it isn't kept up to date.

    static final String SERVICE_URL = "http://www.4byte.directory";

    @NonNull
    static String retrieveSignatureEndpoint(long sig) {
        return "%s/api/v1/signatures/?hex_signature=0x%08X".formatted(SERVICE_URL, sig);
    }

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    static final boolean DEBUGGING_HTTP_REQUEST = false;

    @JsonSerialize
    static record FourByteSignature(@JsonProperty("text_signature") String textSignature) {}

    @JsonSerialize
    static record FourBytesSignatureListing(int count, List<FourByteSignature> results) {}

    static record SignatureListingJsonBodyHandler(@NonNull ObjectMapper om)
            implements HttpResponse.BodyHandler<Supplier<FourBytesSignatureListing>> {

        @Override
        public HttpResponse.BodySubscriber<Supplier<FourBytesSignatureListing>> apply(
                final HttpResponse.ResponseInfo responseInfo) {
            return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofInputStream(), in -> () -> {
                try (final var stream = in) {
                    var bytes = in.readAllBytes();
                    if (DEBUGGING_HTTP_REQUEST)
                        System.out.printf("SignatureListingJsonBodyHandler: have '%s'%n", new String(bytes));
                    return om.readValue(bytes, FourBytesSignatureListing.class);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }
    }

    public record MethodSignatureInfo(long selector, String methodName, String signature) {}

    @Nullable
    static MethodSignatureInfo identifySelector(
            long selector,
            final @NonNull HttpClient client,
            final @NonNull HttpRequest.Builder requestTemplate,
            final @NonNull SignatureListingJsonBodyHandler jsonAdapter,
            final @NonNull Map<Long, Exception> errorAccumulator) {

        Function<Object, MethodSignatureInfo> wrapError = (@NonNull Object msg) -> {
            Exception ex = msg instanceof Exception e ? e : new RuntimeException(msg.toString());
            errorAccumulator.put(selector, ex);
            return null;
        };

        @NonNull
        final var request = requestTemplate
                .uri(URI.create(retrieveSignatureEndpoint(selector)))
                .build();

        if (DEBUGGING_HTTP_REQUEST) System.out.printf("identifySelector: request: '%s'%n", request.toString());

        HttpResponse<Supplier<SelectorDescriptions.FourBytesSignatureListing>> response;
        try {
            response = client.send(request, jsonAdapter);
        } catch (InterruptedException ex) {
            // I don't know why `send` declares it can throw `InterruptedException` with _no_
            // documentation on _when_ that occurs.  It isn't the normal HTTP timeout because that
            // has a bespoke exception.  And supposedly _ignoring_ this exception has dire
            // consequences.  Strict reading of all the warnings means that I have no idea how to
            // use a `send()` in a Java Stream.  So ... I'm ignoring it.
            Thread.currentThread().interrupt();
            return wrapError.apply(ex);
        } catch (UncheckedIOException | IOException /* includes HttpTimeoutException */ ex) {
            return wrapError.apply(ex);
        }

        if (response.statusCode() != HTTP_OK)
            return wrapError.apply(
                    "web service returned status other than HTTP_OK: %d".formatted(response.statusCode()));

        final var signatures = response.body().get();
        if (signatures.count != 1) return wrapError.apply("selector unknown");

        final var signature = signatures.results.get(0);
        final var name = signature.textSignature.split("[(]", 2)[0];
        return new MethodSignatureInfo(selector, name, signature.textSignature);
    }

    public record SelectorIdentificationResults(
            @NonNull Map<Long, MethodSignatureInfo> identifications, @NonNull Map<Long, Exception> errors) {

        public boolean hasMappings() {
            return !identifications.isEmpty();
        }

        public boolean hasAllMappings() {
            return errors.isEmpty();
        }
    }

    @NonNull
    public SelectorIdentificationResults identifySelectors(@NonNull Collection<Long> selectors) {
        final var om = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        final var jsonAdapter = new SignatureListingJsonBodyHandler(om);

        final var client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        final var requestTemplate = HttpRequest.newBuilder()
                .GET()
                .setHeader("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT);
        final var errorAccumulator = new HashMap<Long, Exception>();
        final var result = selectors.stream()
                .flatMap(selector -> Stream.ofNullable(
                        identifySelector(selector, client, requestTemplate.copy(), jsonAdapter, errorAccumulator)))
                .collect(Collectors.toMap(MethodSignatureInfo::selector, i -> i));

        return new SelectorIdentificationResults(result, errorAccumulator);
    }
}
