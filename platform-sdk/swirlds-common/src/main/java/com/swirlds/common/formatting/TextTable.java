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

package com.swirlds.common.formatting;

import static com.swirlds.common.formatting.HorizontalAlignment.ALIGNED_CENTER;
import static com.swirlds.common.formatting.HorizontalAlignment.ALIGNED_LEFT;
import static com.swirlds.common.formatting.StringFormattingUtils.repeatedChar;
import static com.swirlds.common.formatting.TextEffect.applyEffects;
import static com.swirlds.common.formatting.TextEffect.getPrintableTextLength;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Utility class for formatting and printing an ASCII table.
 * </p>
 *
 * <p>
 * See the TextEffect-Colors.png for a screenshot of all effects rendered by intellij on a macbook.
 * </p>
 */
public class TextTable {

    private static final char PADDING = ' ';
    private static final char CROSS_JUNCTION = '┼';
    private static final char LEFT_JUNCTION = '┠';
    private static final char LEFT_HEADER_JUNCTION = '┣';
    private static final char RIGHT_JUNCTION = '┨';
    private static final char RIGHT_HEADER_JUNCTION = '┫';
    private static final char BOTTOM_JUNCTION = '┷';
    private static final char TOP_JUNCTION = '┯';
    private static final char TOP_LEFT_CORNER = '┏';
    private static final char TOP_RIGHT_CORNER = '┓';
    private static final char BOTTOM_LEFT_CORNER = '┗';
    private static final char BOTTOM_RIGHT_CORNER = '┛';
    private static final char HORIZONTAL_BAR = '─';
    private static final char THICK_HORIZONTAL_BAR = '━';
    private static final char VERTICAL_BAR = '│';
    private static final char THICK_VERTICAL_BAR = '┃';
    private static final char NEWLINE = '\n';

    private String title;
    private final List<List<String>> rows = new ArrayList<>();
    private boolean bordersEnabled = true;
    private int extraPadding = 0;

    /**
     * Describes the position of a cell.
     */
    private record Cell(int row, int column) {}

    private final Map<Integer, List<TextEffect>> rowEffects = new HashMap<>();
    private final Map<Integer, List<TextEffect>> columnEffects = new HashMap<>();
    private final Map<Cell, List<TextEffect>> cellEffects = new HashMap<>();
    private final List<TextEffect> titleEffects = new ArrayList<>();
    private final List<TextEffect> globalCellEffects = new ArrayList<>();
    private final List<TextEffect> borderEffects = new ArrayList<>();

    private final Map<Integer, HorizontalAlignment> rowHorizontalAlignments = new HashMap<>();
    private final Map<Integer, HorizontalAlignment> columnHorizontalAlignments = new HashMap<>();
    private final Map<Cell, HorizontalAlignment> cellHorizontalAlignments = new HashMap<>();
    private HorizontalAlignment titleHorizontalAlignment = ALIGNED_CENTER;
    private HorizontalAlignment globalHorizontalAlignment = ALIGNED_LEFT;

    /**
     * Create a new text table.
     */
    public TextTable() {}

    /**
     * Enable or disable borders.
     *
     * @param bordersEnabled
     * 		whether borders should be enabled
     * @return this object
     */
    public TextTable setBordersEnabled(final boolean bordersEnabled) {
        this.bordersEnabled = bordersEnabled;
        return this;
    }

    /**
     * Enable extra padding for each column.
     *
     * @param extraPadding
     * 		the number of extra spaces to add to each column
     * @return this object
     */
    public TextTable setExtraPadding(final int extraPadding) {
        this.extraPadding = extraPadding;
        return this;
    }

    /**
     * Set the title of the table.
     *
     * @param title
     * 		the title of the table
     * @return this object
     */
    public TextTable setTitle(final String title) {
        this.title = title;
        return this;
    }

    /**
     * Add a row to the table.
     *
     * @param row
     * 		a single row
     * @return this object
     */
    @SuppressWarnings("DuplicatedCode")
    public TextTable addRow(final Object... row) {
        if (row != null) {
            final List<String> rowString = new ArrayList<>();

            for (final Object o : row) {
                rowString.add(o.toString());
            }

            rows.add(rowString);
        }

        return this;
    }

    /**
     * Add to the current row.
     *
     * @param elements
     * 		the elements to add to the current row
     * @return this object
     */
    public TextTable addToRow(final Object... elements) {
        final List<String> row;
        if (rows.isEmpty()) {
            row = new ArrayList<>();
            rows.add(row);
        } else {
            row = rows.get(rows.size() - 1);
        }

        for (final Object o : elements) {
            row.add(o.toString());
        }

        return this;
    }

    /**
     * <p>
     * Add text effects to a row. Has no effect if there is no data in the specified row when the table is rendered.
     * </p>
     *
     * <p>
     * Effects are applied in the following order:
     * </p>
     * <ol>
     * <li> global </li>
     * <li> row </li>
     * <li> column </li>
     * <li> cell</li>
     * </ol>
     *
     * @param row
     * 		the target row
     * @param effects
     * 		zero or more effects to add for the row
     * @return this object
     */
    public TextTable addRowEffects(final int row, final TextEffect... effects) {
        if (effects == null) {
            return this;
        }

        final List<TextEffect> effectList = rowEffects.computeIfAbsent(row, k -> new ArrayList<>());
        effectList.addAll(Arrays.asList(effects));

        return this;
    }

    /**
     * <p>
     * Add text effects to a column. Has no effect if there is no data in the specified column when the table is
     * rendered.
     * </p>
     *
     * <p>
     * Effects are applied in the following order:
     * </p>
     * <ol>
     * <li> global </li>
     * <li> row </li>
     * <li> column </li>
     * <li> cell </li>
     * </ol>
     *
     * @param column
     * 		the target column
     * @param effects
     * 		zero or more effects to add for the column
     * @return this object
     */
    public TextTable addColumnEffects(final int column, final TextEffect... effects) {
        if (effects == null) {
            return this;
        }

        final List<TextEffect> effectList = columnEffects.computeIfAbsent(column, k -> new ArrayList<>());
        effectList.addAll(Arrays.asList(effects));

        return this;
    }

    /**
     * <p>
     * Add text effects to a cell. Has no effect if there is no data in the specified cell when the table is rendered.
     * </p>
     *
     * <p>
     * Effects are applied in the following order:
     * </p>
     * <ol>
     * <li> global </li>
     * <li> row </li>
     * <li> column </li>
     * <li> cell </li>
     * </ol>
     *
     * @param row
     * 		the target row
     * @param column
     * 		the target column
     * @param effects
     * 		zero or more effects to add for the cell
     * @return this object
     */
    public TextTable addCellEffects(final int row, final int column, final TextEffect... effects) {
        if (effects == null) {
            return this;
        }

        final Cell cell = new Cell(row, column);

        final List<TextEffect> effectList = cellEffects.computeIfAbsent(cell, k -> new ArrayList<>());
        effectList.addAll(Arrays.asList(effects));

        return this;
    }

    /**
     * Add text effects to the title.
     *
     * @param effects
     * 		zero or more effects to add to the title
     * @return this object
     */
    public TextTable addTitleEffects(final TextEffect... effects) {
        if (effects == null) {
            return this;
        }
        titleEffects.addAll(Arrays.asList(effects));
        return this;
    }

    /**
     * <p>
     * Add text effects to all cells. Does not affect the header, title, or border.
     * </p>
     *
     * <p>
     * Effects are applied in the following order:
     * </p>
     * <ol>
     * <li> global </li>
     * <li> row </li>
     * <li> column </li>
     * <li> cell </li>
     * </ol>
     *
     * @param effects
     * 		zero or more effects to add to all cells
     * @return this object
     */
    public TextTable addGlobalCellEffects(final TextEffect... effects) {
        if (effects == null) {
            return this;
        }
        globalCellEffects.addAll(Arrays.asList(effects));
        return this;
    }

    /**
     * Add text effects to cell borders.
     *
     * @param effects
     * 		zero or more effects to add to borders
     * @return this object
     */
    public TextTable addBorderEffects(final TextEffect... effects) {
        if (effects == null) {
            return this;
        }
        borderEffects.addAll(Arrays.asList(effects));
        return this;
    }

    /**
     * <p>
     * Set the horizontal alignment for a row.
     * </p>
     *
     * <p>
     * Alignments are applied in the following order:
     * </p>
     * <ol>
     * <li> global </li>
     * <li> row </li>
     * <li> column </li>
     * <li> cell </li>
     * </ol>
     *
     * @param row
     * 		the row index
     * @param alignment
     * 		the alignment of the row
     * @return this object
     */
    public TextTable setRowHorizontalAlignment(final int row, final HorizontalAlignment alignment) {
        throwArgNull(alignment, "alignment");
        rowHorizontalAlignments.put(row, alignment);
        return this;
    }

    /**
     * <p>
     * Set the horizontal alignment for a column.
     * </p>
     *
     * <p>
     * Alignments are applied in the following order:
     * </p>
     * <ol>
     * <li> global </li>
     * <li> row </li>
     * <li> column </li>
     * <li> cell </li>
     * </ol>
     *
     * @param column
     * 		the column index
     * @param alignment
     * 		the alignment of the column
     * @return this object
     */
    public TextTable setColumnHorizontalAlignment(final int column, final HorizontalAlignment alignment) {
        throwArgNull(alignment, "alignment");
        columnHorizontalAlignments.put(column, alignment);
        return this;
    }

    /**
     * <p>
     * Set the horizontal alignment for a cell.
     * </p>
     *
     * <p>
     * Alignments are applied in the following order:
     * </p>
     * <ol>
     * <li> global </li>
     * <li> row </li>
     * <li> column </li>
     * <li> cell </li>
     * </ol>
     *
     * @param row
     * 		the row index
     * @param column
     * 		the column index
     * @param alignment
     * 		the alignment of the cell
     * @return this object
     */
    public TextTable setCellHorizontalAlignment(final int row, final int column, final HorizontalAlignment alignment) {
        throwArgNull(alignment, "alignment");
        cellHorizontalAlignments.put(new Cell(row, column), alignment);
        return this;
    }

    /**
     * Set the horizontal alignment for the title. If not specified then the global alignment is used.
     *
     * @param alignment
     * 		the alignment of the title
     * @return this object
     */
    public TextTable setTitleHorizontalAlignment(final HorizontalAlignment alignment) {
        throwArgNull(alignment, "alignment");
        titleHorizontalAlignment = alignment;
        return this;
    }

    /**
     * <p>
     * Set the default horizontal alignment for the entire table
     * </p>
     *
     * <p>
     * Alignments are applied in the following order:
     * </p>
     * <ol>
     * <li> global </li>
     * <li> row </li>
     * <li> column </li>
     * <li> cell </li>
     * </ol>
     *
     * @param alignment
     * 		the alignment for the entire table
     * @return this object
     */
    public TextTable setGlobalHorizontalAlignment(final HorizontalAlignment alignment) {
        throwArgNull(alignment, "alignment");
        globalHorizontalAlignment = alignment;
        return this;
    }

    /**
     * Format cell data.
     */
    private String formatCellData(final int row, final int column, final String cellData) {
        final List<TextEffect> effects = new ArrayList<>(globalCellEffects);
        if (rowEffects.containsKey(row)) {
            effects.addAll(rowEffects.get(row));
        }
        if (columnEffects.containsKey(column)) {
            effects.addAll(columnEffects.get(column));
        }
        final Cell cell = new Cell(row, column);
        if (cellEffects.containsKey(cell)) {
            effects.addAll(cellEffects.get(cell));
        }

        if (effects.isEmpty()) {
            return cellData;
        } else {
            return TextEffect.applyEffects(cellData, effects);
        }
    }

    /**
     * Format a cell with left/right alignment.
     */
    private void alignCellData(
            final StringBuilder sb, final int row, final int column, final String cellData, final int desiredWidth) {

        final HorizontalAlignment alignment;

        final Cell cell = new Cell(row, column);

        if (cellHorizontalAlignments.containsKey(cell)) {
            alignment = cellHorizontalAlignments.get(cell);
        } else if (columnHorizontalAlignments.containsKey(column)) {
            alignment = columnHorizontalAlignments.get(column);
        } else if (rowHorizontalAlignments.containsKey(row)) {
            alignment = rowHorizontalAlignments.get(row);
        } else {
            alignment = globalHorizontalAlignment;
        }

        alignment.pad(sb, cellData, desiredWidth);
    }

    /**
     * Format a border character(s) and write it to a string builder.
     */
    private void writeBorder(final StringBuilder sb, final String border) {
        if (borderEffects.isEmpty()) {
            sb.append(border);
        } else {
            applyEffects(sb, border, borderEffects);
        }
    }

    /**
     * Format a border character and write it to a string builder.
     */
    private void writeBorder(final StringBuilder sb, final char border) {
        writeBorder(sb, String.valueOf(border));
    }

    /**
     * Expand planned column widths to fit a given row. After all rows have been processed this way,
     * the column widths list will contain the proper width for each column.
     *
     * @param row
     * 		the row that needs to be fitted into the table
     */
    private static void expandColumnWidthsForRow(final List<Integer> columnWidths, final List<String> row) {
        for (int column = 0; column < row.size(); column++) {

            final int columnWidth = getPrintableTextLength(row.get(column));

            if (columnWidths.size() <= column) {
                columnWidths.add(columnWidth);
            } else {
                columnWidths.set(column, Math.max(columnWidths.get(column), columnWidth));
            }
        }
    }

    /**
     * Compute the width for each column.
     *
     * @return a list of widths indexed by column
     */
    private List<Integer> computeColumnWidths() {
        final List<Integer> columnWidths = new ArrayList<>();

        for (final List<String> row : rows) {
            expandColumnWidthsForRow(columnWidths, row);
        }

        return columnWidths;
    }

    /**
     * Generate the top of the table.
     */
    private void generateTopLine(final StringBuilder sb, final List<Integer> columnWidths, final int columnWidthSum) {

        if (!bordersEnabled) {
            return;
        }

        writeBorder(sb, TOP_LEFT_CORNER);

        if (title != null) {
            writeBorder(sb, repeatedChar(THICK_HORIZONTAL_BAR, columnWidthSum + columnWidths.size() * 3 - 1));
        } else {

            for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
                writeBorder(sb, repeatedChar(THICK_HORIZONTAL_BAR, columnWidths.get(columnIndex) + 2));
                if (columnIndex + 1 < columnWidths.size()) {
                    writeBorder(sb, TOP_JUNCTION);
                }
            }
        }

        writeBorder(sb, TOP_RIGHT_CORNER);
        sb.append(NEWLINE);
    }

    /**
     * Generate the line containing the title.
     */
    private void generateTitleLine(final StringBuilder sb, final int columnWidthSum, final int columnCount) {
        if (title == null) {
            return;
        }

        if (bordersEnabled) {
            writeBorder(sb, THICK_VERTICAL_BAR);
            sb.append(PADDING);
        }

        final String formattedTitle = applyEffects(title, titleEffects);

        final int titleWidth;
        if (bordersEnabled) {
            titleWidth = columnWidthSum + columnCount * 3 - 3;
        } else {
            titleWidth = columnWidthSum + columnCount * 3 - 1;
        }

        titleHorizontalAlignment.pad(sb, formattedTitle, titleWidth);

        if (bordersEnabled) {
            sb.append(PADDING);
            writeBorder(sb, THICK_VERTICAL_BAR);
            sb.append(NEWLINE);
        }
    }

    /**
     * Generate the line between the title and the headers.
     */
    private void generateLineBelowTitle(final StringBuilder sb, final List<Integer> columnWidths) {
        if (title == null || !bordersEnabled) {
            return;
        }

        writeBorder(sb, LEFT_HEADER_JUNCTION);
        for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
            writeBorder(sb, repeatedChar(THICK_HORIZONTAL_BAR, columnWidths.get(columnIndex) + 2));
            if (columnIndex + 1 < columnWidths.size()) {
                writeBorder(sb, TOP_JUNCTION);
            }
        }
        writeBorder(sb, RIGHT_HEADER_JUNCTION);
        sb.append(NEWLINE);
    }

    /**
     * Generate a row containing column data.
     */
    private void generateDataRow(final StringBuilder sb, final int row, final List<Integer> columnWidths) {

        final List<String> rowData = rows.get(row);

        if (bordersEnabled) {
            writeBorder(sb, THICK_VERTICAL_BAR);
        }

        for (int column = 0; column < columnWidths.size(); column++) {

            if (bordersEnabled) {
                sb.append(PADDING);
            }

            final String cellData = column < rowData.size() ? rowData.get(column) : "";
            final String formattedCellData = formatCellData(row, column, cellData);
            alignCellData(sb, row, column, formattedCellData, columnWidths.get(column));

            sb.append(PADDING);
            if (extraPadding > 0) {
                sb.append(repeatedChar(PADDING, extraPadding));
            }
            if (bordersEnabled && column + 1 < columnWidths.size()) {
                writeBorder(sb, VERTICAL_BAR);
            }
        }

        if (bordersEnabled) {
            writeBorder(sb, THICK_VERTICAL_BAR);
        }
        sb.append(NEWLINE);
    }

    /**
     * Generate the line below a row containing data.
     */
    private void generateLineBelowDataRow(final StringBuilder sb, final List<Integer> columnWidths) {
        if (!bordersEnabled) {
            return;
        }

        writeBorder(sb, LEFT_JUNCTION);

        for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
            writeBorder(sb, repeatedChar(HORIZONTAL_BAR, columnWidths.get(columnIndex) + 2));
            if (columnIndex + 1 < columnWidths.size()) {
                writeBorder(sb, CROSS_JUNCTION);
            }
        }
        writeBorder(sb, RIGHT_JUNCTION);
        sb.append(NEWLINE);
    }

    /**
     * Generate the rows in the table.
     */
    private void generateRows(final StringBuilder sb, final List<Integer> columnWidths) {
        for (int row = 0; row < rows.size(); row++) {
            generateDataRow(sb, row, columnWidths);

            // Line below row
            if (row + 1 < rows.size()) {
                generateLineBelowDataRow(sb, columnWidths);
            }
        }
    }

    /**
     * Generate the last line in the table.
     */
    private void generateBottomLine(final StringBuilder sb, final List<Integer> columnWidths) {
        if (!bordersEnabled) {
            return;
        }

        writeBorder(sb, BOTTOM_LEFT_CORNER);

        for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
            writeBorder(sb, repeatedChar(THICK_HORIZONTAL_BAR, columnWidths.get(columnIndex) + 2));
            if (columnIndex + 1 < columnWidths.size()) {
                writeBorder(sb, BOTTOM_JUNCTION);
            }
        }

        writeBorder(sb, BOTTOM_RIGHT_CORNER);
    }

    /**
     * If the title is really long then expand the last column to fill the space.
     */
    private void expandLastColumnIfNeeded(final List<Integer> columnWidths) {
        if (title == null) {
            return;
        }

        int columnWidthSum = 0;
        for (final int columnWidth : columnWidths) {
            columnWidthSum += columnWidth;
        }

        final int titleLength = getPrintableTextLength(title);
        final int minimumWidth = titleLength + columnWidths.size() * 3 - 3;
        if (columnWidthSum < minimumWidth) {
            // Title is too wide, expand a column to balance it out

            final int expansion = minimumWidth - columnWidthSum;
            final int lastIndex = columnWidths.size() - 1;
            columnWidths.set(lastIndex, columnWidths.get(lastIndex) + expansion);
        }
    }

    /**
     * Render this table to a string builder.
     *
     * @param sb
     * 		the string builder to add to
     */
    public void render(final StringBuilder sb) {
        final List<Integer> columnWidths = computeColumnWidths();
        expandLastColumnIfNeeded(columnWidths);

        int columnWidthSum = 0;
        for (final int columnWidth : columnWidths) {
            columnWidthSum += columnWidth;
        }

        generateTopLine(sb, columnWidths, columnWidthSum);
        generateTitleLine(sb, columnWidthSum, columnWidths.size());
        generateLineBelowTitle(sb, columnWidths);
        generateRows(sb, columnWidths);
        generateBottomLine(sb, columnWidths);
    }

    /**
     * Render this table to a string.
     *
     * @return the rendered table
     */
    public String render() {
        final StringBuilder sb = new StringBuilder();
        render(sb);
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return render();
    }
}
