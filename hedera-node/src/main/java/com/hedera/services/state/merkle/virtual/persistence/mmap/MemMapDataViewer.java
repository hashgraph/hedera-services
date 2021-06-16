package com.hedera.services.state.merkle.virtual.persistence.mmap;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class MemMapDataViewer {

    public static JFrame frame = null;
    public static JTabbedPane tabbedPane;
    public static Set<String> fileNames = new HashSet<>();

    public static synchronized void showData(String fileName, long[][] data) {
        System.out.println("MemMapDataViewer.showData");

        fileNames.add(fileName);
        System.out.println("frame = " + frame);
        if (frame == null) {
            frame = new JFrame("Data Files");
            tabbedPane = new JTabbedPane();
            frame.getContentPane().add(tabbedPane);
            frame.setSize(800, 600);
            frame.setVisible(true);
        }
        System.out.println("    frame = " + frame);

        int tabIndex = tabbedPane.indexOfTab(fileName);
        if (tabIndex >= 0) tabbedPane.removeTabAt(tabIndex);
        tabbedPane.addTab(fileName, new JScrollPane(createTable(data)));
    }

    private static JTable createTable(long[][] data) {
        TableModel tm = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return data.length;
            }

            @Override
            public int getColumnCount() {
                return 1+data[0].length;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                if (columnIndex == 0) {
                    return rowIndex;
                } else {
                    return data[rowIndex][columnIndex-1];
                }
            }

            @Override
            public String getColumnName(int column) {
                if (column == 0) {
                    return "slot";
                } else {
                    return Integer.toString(column);
                }
            }

            @Override
            public int findColumn(String columnName) {
                if ("slot".equals(columnName)) {
                    return 0;
                } else {
                    return Integer.parseInt(columnName);
                }
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return Long.class;
            }
        };
        JTable table = new JTable(tm);
        table.getColumn("slot").setCellRenderer(
                new DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable table,
                                                                   Object value, boolean isSelected, boolean hasFocus,
                                                                   int row, int column) {
                        Component superRenderer = super.getTableCellRendererComponent(table,
                                value, isSelected, hasFocus, row, column);
                        superRenderer.setFont(superRenderer.getFont().deriveFont(Font.BOLD));
                        superRenderer.setBackground(new Color(250,250,250));
                        return superRenderer;
                    }
                });
        return table;
    }
}
