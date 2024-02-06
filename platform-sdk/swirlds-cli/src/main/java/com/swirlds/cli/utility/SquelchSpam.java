/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.cli.utility;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SquelchSpam {

    private static final String startOfSpam = "] Bad JNI lookup accessibilityHitTest";
    private static final String endOfSpam = "Exception in thread \"AppKit Thread\" java.lang.NoSuchMethodError: accessibilityHitTest";

    private static boolean inSpam = false;
    private static boolean nextLineIsSpam = false;

    public static boolean lineIsSpam(String line) {

        if (!nextLineIsSpam) {
            inSpam = false;
        }

        if (!inSpam) {
            if (line.contains(startOfSpam)) {
                inSpam = true;
                nextLineIsSpam = true;
            }
        } else {
            if (line.contains(endOfSpam)) {
                nextLineIsSpam = false;
            }
        }

        return inSpam;
    }

    public static void resetSpamStatus(){
        inSpam = false;
        nextLineIsSpam = false;
    }

    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!lineIsSpam(line)) {
                    System.out.println(line);
                    System.out.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("Error during spam squelching: " + e.getMessage());
        }
    }
}