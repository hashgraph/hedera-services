package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtualh.Account;
import com.hedera.services.state.merkle.virtualh.VirtualTreePath;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

import static com.hedera.services.state.merkle.virtualh.persistence.mmap.VirtualMapDataStore.HASH_SIZE_BYTES;

/**
 * Utility app for viewing mem-mapped data files in Swing
 */
public class MemMapDataViewer {

    public static void main(String[] args) throws Exception {
        Path storeDir = MemMapDataStoreTest.STORE_PATH;
        if (Files.exists(storeDir)) {
            List<StoreInfo> stores = new ArrayList<>();
            Optional<Path> leavesDir = Files.list(storeDir).filter(path -> path.toString().endsWith("leaves")).findFirst();
            if (leavesDir.isPresent()) {
                // data store contains VirtualMapData
                Path parentsDir = storeDir.resolve("parents");
                Path pathsDir = storeDir.resolve("paths");
                int leafStoreSlotSize = Account.BYTES + VirtualMapDataStoreTest.KEY_SIZE_BYTES + VirtualTreePath.BYTES + VirtualMapDataStoreTest.DATA_SIZE_BYTES + HASH_SIZE_BYTES;
                int parentStoreSlotSize = Account.BYTES + VirtualTreePath.BYTES + HASH_SIZE_BYTES;
                int pathStoreSlotSize = Account.BYTES + Long.BYTES + VirtualTreePath.BYTES;
                stores.add(new StoreInfo(leavesDir.get(),leafStoreSlotSize));
                stores.add(new StoreInfo(parentsDir,parentStoreSlotSize));
                stores.add(new StoreInfo(pathsDir,pathStoreSlotSize));
            } else {
                // data from MemMapDataStoreTest
                stores.add(new StoreInfo(storeDir,MemMapDataStoreTest.DATA_SIZE));
            }
            // create window
            JFrame frame = new JFrame("Data Files");
            JTabbedPane tabbedPane = new JTabbedPane();
            frame.getContentPane().add(tabbedPane);
            frame.setSize(800, 600);
            // now we have the stores, add them all as tabs
            stores.forEach(storeInfo -> {
                // find all data files
                try {
                    Files.list(storeInfo.storePath)
                            .filter(path -> path.toString().endsWith(".dat"))
                            .forEach(path -> {
                                long[][] data = loadData(path,storeInfo.slotSize);
                                tabbedPane.addTab(path.getFileName().toString(), new JScrollPane(createTable(data)));
                            });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            // show window
            frame.setVisible(true);
        } else {
            throw new Exception("No Data Store exists at ["+storeDir+"]");
        }
    }

    private static class StoreInfo {
        public final Path storePath;
        public final int slotSize;

        public StoreInfo(Path storePath, int slotSize) {
            this.storePath = storePath;
            this.slotSize = slotSize;
        }
    }

    private static long[][] loadData(Path file, int slotSize) {
        List<long[]> data = new ArrayList<>();
        try {
            LongBuffer longBuffer = ByteBuffer.wrap(Files.readAllBytes(file)).asLongBuffer();
            final int slotSizeLongs = 1 + (slotSize/8);
            System.out.println("slotSizeLongs = " + slotSizeLongs);
            while(longBuffer.remaining() >= slotSizeLongs) {
                long[] slot = new long[slotSizeLongs];
                longBuffer.get(slot);
                data.add(slot);
            }
            System.out.println("longBuffer.remaining() = " + longBuffer.remaining());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data.toArray(new long[data.size()][]);
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
                } else if (columnIndex == 1) {
                    return Long.toHexString(data[rowIndex][columnIndex-1]);
                } else {
                    return data[rowIndex][columnIndex-1];
                }
            }

            @Override
            public String getColumnName(int column) {
                if (column == 0) {
                    return "slot";
                } else if (column == 1) {
                    return "header";
                } else {
                    return Integer.toString(column);
                }
            }

            @Override
            public int findColumn(String columnName) {
                if ("slot".equals(columnName)) {
                    return 0;
                } else if ("header".equals(columnName)) {
                    return 1;
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
                        superRenderer.setBackground(new Color(240,240,240));
                        return superRenderer;
                    }
                });
        return table;
    }
}
