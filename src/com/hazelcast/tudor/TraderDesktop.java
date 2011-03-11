package com.hazelcast.tudor;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.MessageListener;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TraderDesktop {
    final HazelcastClient hazelcastClient;

    public static void main(String args[]) {
        String hostname = (args != null && args.length > 0) ? args[0] : "localhost";
        TraderDesktop td = new TraderDesktop(hostname);
        td.init();
    }

    public TraderDesktop(String hostname) {
        hazelcastClient = HazelcastClient.newHazelcastClient("dev", "dev-pass", hostname);
    }

    void init() {
        JFrame frame = new JFrame("Trader Desktop");
        frame.addWindowListener(new WindowAdapter() {

            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        // This is an empty content area in the frame
        final JLabel label = new JLabel("PortfolioManagerId");
        final JTextField txtId = new JTextField("18");
        final JButton btOpen = new JButton("Open");
        btOpen.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openPMWindow(txtId.getText().trim());
            }
        });
        frame.setLayout(new FlowLayout());
        frame.add(label);
        frame.add(txtId);
        frame.add(btOpen);
        frame.pack();
        frame.setVisible(true);
    }

    void openPMWindow(String id) {
        JFrame frame = new PMWindow("PM " + id, Integer.parseInt(id));
        frame.addWindowListener(new WindowAdapter() {

            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        frame.pack();
        frame.setVisible(true);
    }

    class PMWindow extends JFrame {
        final PMTableModel tableModel = new PMTableModel();
        final JTable table = new JTable(tableModel);
        final ITopic topic;
        final java.util.List<Integer> lsInstrumentIds = new ArrayList<Integer>();
        final ConcurrentMap<Integer, PositionView> positions = new ConcurrentHashMap<Integer, PositionView>(100);
        final JLabel lblCount = new JLabel("0");

        PMWindow(String title, int pmId) throws HeadlessException {
            super(title);
            init();
            topic = hazelcastClient.getTopic("pm_" + pmId);
            topic.addMessageListener(new MessageListener<PositionView>() {
                int count = 0;

                public void onMessage(PositionView pv) {
                    lblCount.setText(String.valueOf(count++));
                    Integer instrumentId = pv.getInstrumentId();
                    if (positions.put(instrumentId, pv) == null) {
                        lsInstrumentIds.add(instrumentId);
                        int row = lsInstrumentIds.indexOf(instrumentId);
                        tableModel.fireTableRowsInserted(row, row);
                    } else {
                        int row = lsInstrumentIds.indexOf(instrumentId);
                        table.addRowSelectionInterval(row, row);
                        tableModel.fireTableRowsUpdated(row, row);
                        table.removeRowSelectionInterval(row, row);
                    }
                }
            });
        }

        void init() {
            table.setPreferredScrollableViewportSize(new Dimension(300, 400));
            table.setFillsViewportHeight(true);
            JScrollPane scrollPane = new JScrollPane(table);
            setLayout(new BorderLayout());
            add(lblCount, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);
        }

        class PMTableModel extends AbstractTableModel {
            private String[] columnNames = {"#", "Instrument", "Quantity", "CurrentPrice", "P&L"};

            public int getColumnCount() {
                return columnNames.length;
            }

            public int getRowCount() {
                return lsInstrumentIds.size();
            }

            public String getColumnName(int col) {
                return columnNames[col];
            }

            public Object getValueAt(int row, int col) {
                PositionView pw = positions.get(lsInstrumentIds.get(row));
                Instrument instrument = LookupDatabase.getInstrumentById(pw.getInstrumentId());
                if (col == 0)
                    return row + 1;
                else if (col == 1)
                    return instrument.symbol;
                else if (col == 2)
                    return pw.getQuantity();
                else if (col == 3)
                    return pw.getLastPrice();
                else if (col == 4)
                    return pw.getProfitOrLoss();
                return "ERROR";
            }

            public Class getColumnClass(int c) {
                return String.class;
            }

            public boolean isCellEditable(int row, int col) {
                return false;
            }
        }
    }
}
