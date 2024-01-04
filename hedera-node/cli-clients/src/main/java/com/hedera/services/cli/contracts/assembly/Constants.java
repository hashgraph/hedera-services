/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.contracts.assembly;

import java.util.HexFormat;

public final class Constants {

    public static final int DEFAULT_CODE_OFFSET_COLUMN = 0;
    public static final int DEFAULT_OPCODE_COLUMN = 0;
    public static final int DEFAULT_COMMENT_COLUMN = 6;
    public static final int DEFAULT_LABEL_COLUMN = 6;
    public static final int DEFAULT_MNEMONIC_COLUMN = 10;
    public static final int DEFAULT_OPERAND_COLUMN = 18;
    public static final int DEFAULT_EOL_COMMENT_COLUMN = 22;
    public static final int MAX_EXPECTED_LINE = 80;

    public static final int CODE_OFFSET_COLUMN_WIDTH = 8;
    public static final int OPCODE_COLUMN_WIDTH = 2;

    public static final String FULL_LINE_COMMENT_PREFIX = "# ";
    public static final String EOL_COMMENT_PREFIX = "# ";

    public static final HexFormat UPPERCASE_HEX_FORMATTER = HexFormat.of().withUpperCase();

    private Constants() {}
}
