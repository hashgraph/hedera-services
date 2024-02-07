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

package com.swirlds.base.sample;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Client {

    public static void main(String[] args) {
        try {

            String endpoint = "http://localhost:8080/swirlds/api/wallets/";
            String id = "0";
            int totalRequests = 1000;
            long durationInMillis = Duration.of(1, ChronoUnit.MINUTES).toMillis();
            Random random = new Random();

            final long millis = Duration.of(1, ChronoUnit.SECONDS).toMillis();
            final int buckets = (int) (durationInMillis / millis);

            try (ScheduledExecutorService executor = Executors.newScheduledThreadPool(buckets); ) {

                for (int i = 0; i <= buckets; i++) {
                    final String name = "Bucket i:" + i;
                    executor.schedule(
                            () -> {
                                System.out.println("Started:" + name);
                                long startTime = System.currentTimeMillis();
                                long elapsedTime = System.currentTimeMillis() - startTime;
                                long remainingTime = millis - elapsedTime;
                                // Start sending requests
                                for (int y = 0; y < totalRequests && remainingTime > 0; y++) {
                                    // Create the URL object with parameters
                                    extracted(endpoint, id);

                                    elapsedTime = System.currentTimeMillis() - startTime;
                                    remainingTime = millis - elapsedTime;
                                    // Calculate the remaining time to distribute the requests
                                    double tPerMs = (double) y / elapsedTime;
                                    double rPerRTime = ((double) (totalRequests) - y) / remainingTime;

                                    long randomDelay =
                                            tPerMs > rPerRTime ? random.nextInt((int) remainingTime / 10) : 0;
                                    System.out.println(name + " request:" + y + " of:" + totalRequests + " elapsedTime:"
                                            + elapsedTime
                                            + " remainingTime:" + remainingTime + " t/ms:" + tPerMs
                                            + " remaining/remaining time:" + rPerRTime
                                            + " randomDelay:"
                                            + randomDelay);
                                    if (randomDelay > 0)
                                    // Sleep for the random delay
                                    {
                                        try {
                                            Thread.sleep(randomDelay);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                }
                            },
                            i,
                            TimeUnit.SECONDS);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void extracted(final String endpoint, final String id) {
        try {
            URL url = new URL(endpoint + id);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();
            // System.out.println("Response Code: " + responseCode +" Response: " + response);

            connection.disconnect();
        } catch (IOException e) {
            System.err.println("Error:" + e.getMessage());
            e.printStackTrace();
        }
    }
}
