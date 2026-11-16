package com.relaybook.ui;

import com.relaybook.document.ContactCardDocument;
import com.relaybook.document.CsvDirectoryDocument;
import com.relaybook.document.ImportSummary;
import com.relaybook.document.JsonBackupDocument;
import com.relaybook.document.ReachabilityReportDocument;
import com.relaybook.document.VCardImporter;
import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactFactory;
import com.relaybook.model.ContactMethod;
import com.relaybook.model.ContactType;
import com.relaybook.repository.DataAccessException;
import com.relaybook.service.ContactChangeListener;
import com.relaybook.service.ContactService;
import com.relaybook.service.ReachabilityReport;
import com.relaybook.web.WebServer;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Swing desktop client. All button and selection handlers are explicit event listeners. */
public final class AddressBookFrame extends JFrame implements ContactChangeListener {
    private final ContactService service;
    private final WebServer webServer;
    private final com.relaybook.service.ReminderScheduler reminderScheduler;
    private final ContactTableModel tableModel;
    private final JTable table;
    private final Map<String, JTextField> fields = new HashMap<>();
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"PERSON", "ORGANIZATION", "EMERGENCY"});
    private final JComboBox<String> preferredBox = new JComboBox<>();
    private final JComboBox<String> sortBox = new JComboBox<>(
            new String[]{"NAME", "SCORE", "LAST_VERIFIED", "TYPE"});
    private final DefaultTableModel routesModel = new DefaultTableModel(
            new Object[]{"Channel", "Value", "Last checked", "Label"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return true; // The routes table is the per-route editor.
        }
    };
    private final JTable routesTable = new JTable(routesModel);
    private final JCheckBox shareConsentBox = new JCheckBox("Allow saved routes in exported vCard (consent recorded)");
    private final JTextArea notesArea = new JTextArea(3, 25);
    private final JLabel formTitle = new JLabel("New contact");
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel statsLabel = new JLabel(" ");
    private final JTextArea adviceArea = new JTextArea();
    private String editingId;

    public AddressBookFrame(ContactService service, WebServer webServer,
                            com.relaybook.service.ReminderScheduler reminderScheduler) {
        super("RelayBook — Resilient Address Book");
        this.service = service;
        this.webServer = webServer;
        this.reminderScheduler = reminderScheduler;
        this.tableModel = new ContactTableModel(service);
        this.table = new JTable(tableModel);
        buildUi();
        service.addListener(this); // Observer: web/API changes refresh this table automatically.
        reload(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 700));
        setSize(1280, 820);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                webServer.stop();
                reminderScheduler.stop();
            }
        });
    }

    private void buildUi() {
        setLayout(new BorderLayout());
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem newContactItem = new JMenuItem("New contact");
        newContactItem.addActionListener(event -> clearForm());
        JMenuItem refreshItem = new JMenuItem("Refresh directory");
        refreshItem.addActionListener(event -> reload(editingId));
        JMenuItem browserItem = new JMenuItem("Open browser app");
        browserItem.addActionListener(event -> openWebPage());
        JMenuItem importItem = new JMenuItem("Import vCard…");
        importItem.addActionListener(event -> importVCard());
        JMenuItem exportCsvItem = new JMenuItem("Export CSV directory (privacy-aware)…");
        exportCsvItem.addActionListener(event -> exportCsv(false));
        JMenuItem exportBackupCsvItem = new JMenuItem("Export CSV directory (full local backup)…");
        exportBackupCsvItem.addActionListener(event -> exportCsv(true));
        JMenuItem exportJsonItem = new JMenuItem("Export JSON backup (full)…");
        exportJsonItem.addActionListener(event -> exportJsonBackup());
        JMenuItem exportSummaryItem = new JMenuItem("Export reachability summary…");
        exportSummaryItem.addActionListener(event -> exportReachabilitySummary());
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(event -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        fileMenu.add(newContactItem);
        fileMenu.add(refreshItem);
        fileMenu.add(browserItem);
        fileMenu.addSeparator();
        fileMenu.add(importItem);
        fileMenu.add(exportCsvItem);
        fileMenu.add(exportBackupCsvItem);
        fileMenu.add(exportJsonItem);
        fileMenu.add(exportSummaryItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About RelayBook");
        aboutItem.addActionListener(event -> JOptionPane.showMessageDialog(this,
                "RelayBook · a Java address book with freshness-aware route suggestions.",
                "About RelayBook", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);

        JPanel header = new JPanel(new BorderLayout(12, 4));
        header.setBorder(new EmptyBorder(16, 20, 14, 20));
        JLabel brand = new JLabel("RELAYBOOK  /  Reachability-first contacts");
        brand.setFont(brand.getFont().deriveFont(20f));
        header.add(brand, BorderLayout.WEST);
        JButton openWebButton = new JButton("Open browser app");
        openWebButton.addActionListener(event -> openWebPage());
        header.add(openWebButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildFormPanel(), buildListPanel());
        split.setResizeWeight(0.40);
        split.setDividerLocation(470);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        statusLabel.setBorder(new EmptyBorder(7, 16, 7, 16));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private Component buildFormPanel() {
        JPanel outer = new JPanel(new BorderLayout(8, 8));
        outer.setBorder(new EmptyBorder(12, 16, 12, 12));
        formTitle.setFont(formTitle.getFont().deriveFont(17f));
        outer.add(formTitle, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        int row = 0;
        row = addField(content, row, "Kind", typeBox);
        row = addField(content, row, "Full name / label *", field("fullName"));
        row = addField(content, row, "Organization", field("organization"));
        row = addField(content, row, "Relationship / context", field("relationship"));

        // Per-route editor: each route keeps its own channel, value, and verified date.
        routesTable.setRowHeight(24);
        routesTable.getColumnModel().getColumn(0).setCellEditor(new javax.swing.DefaultCellEditor(
                new JComboBox<>(java.util.Arrays.stream(Channel.values()).map(Channel::name).toArray(String[]::new))));
        JScrollPane routesScroll = new JScrollPane(routesTable);
        routesScroll.setPreferredSize(new Dimension(240, 96));
        JPanel routesPanel = new JPanel(new BorderLayout(4, 4));
        JPanel routeButtons = new JPanel();
        JButton addRouteButton = new JButton("Add route");
        addRouteButton.addActionListener(event -> addRouteRow());
        JButton removeRouteButton = new JButton("Remove selected");
        removeRouteButton.addActionListener(event -> removeSelectedRoute());
        routeButtons.add(addRouteButton);
        routeButtons.add(removeRouteButton);
        routesPanel.add(routesScroll, BorderLayout.CENTER);
        routesPanel.add(routeButtons, BorderLayout.SOUTH);
        JLabel routeHint = new JLabel("Each route keeps its own \"last checked\" date.");
        routeHint.setFont(routeHint.getFont().deriveFont(11f));
        routesPanel.add(routeHint, BorderLayout.NORTH);
        GridBagConstraints routesConstraints = constraints(0, row++);
        routesConstraints.gridwidth = 2;
        routesConstraints.fill = GridBagConstraints.BOTH;
        routesConstraints.weighty = 0.4;
        content.add(routesPanel, routesConstraints);

        row = addField(content, row, "Try first (preferred route)", preferredBox);
        row = addField(content, row, "Best time to reach", field("bestTime"));
        row = addField(content, row, "Time zone", field("timeZone"));
        row = addField(content, row, "Last verified (YYYY-MM-DD)", field("lastVerified"));
        row = addField(content, row, "Private notes", new JScrollPane(notesArea));

        GridBagConstraints consent = constraints(0, row++);
        consent.gridwidth = 2;
        consent.fill = GridBagConstraints.HORIZONTAL;
        content.add(shareConsentBox, consent);

        JPanel buttons = new JPanel();
        JButton saveButton = new JButton("Save contact");
        JButton newButton = new JButton("New / clear");
        JButton deleteButton = new JButton("Delete");
        JButton verifyButton = new JButton("Mark checked today");
        JButton exportButton = new JButton("Export safe vCard");
        saveButton.addActionListener(event -> saveContact());
        newButton.addActionListener(event -> clearForm());
        deleteButton.addActionListener(event -> deleteContact());
        verifyButton.addActionListener(event -> markCheckedToday());
        exportButton.addActionListener(event -> exportVCard());
        buttons.add(saveButton);
        buttons.add(newButton);
        buttons.add(deleteButton);
        buttons.add(verifyButton);
        buttons.add(exportButton);
        GridBagConstraints buttonConstraints = constraints(0, row);
        buttonConstraints.gridwidth = 2;
        buttonConstraints.fill = GridBagConstraints.HORIZONTAL;
        content.add(buttons, buttonConstraints);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        outer.add(scroll, BorderLayout.CENTER);
        JLabel hint = new JLabel("At least one route is required; emergency contacts need two. Dates use ISO format.");
        hint.setBorder(new EmptyBorder(6, 0, 0, 0));
        outer.add(hint, BorderLayout.SOUTH);
        return outer;
    }

    private void addRouteRow() {
        routesModel.addRow(new Object[]{Channel.PHONE.name(), "", "", ""});
        refreshPreferredOptions(null);
    }

    private void removeSelectedRoute() {
        int selected = routesTable.getSelectedRow();
        if (selected >= 0) {
            routesModel.removeRow(selected);
            refreshPreferredOptions(null);
        }
    }

    /** The preferred-route combo always offers exactly the channels currently in the table. */
    private void refreshPreferredOptions(String select) {
        java.util.LinkedHashSet<String> channels = new java.util.LinkedHashSet<>();
        for (int i = 0; i < routesModel.getRowCount(); i++) {
            channels.add(String.valueOf(routesModel.getValueAt(i, 0)));
        }
        preferredBox.setModel(new DefaultComboBoxModel<>(channels.toArray(new String[0])));
        if (select != null && channels.contains(select)) {
            preferredBox.setSelectedItem(select);
        }
    }

    private Component buildListPanel() {
        JPanel outer = new JPanel(new BorderLayout(8, 10));
        outer.setBorder(new EmptyBorder(12, 12, 12, 16));
        JPanel titleRow = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Contact directory");
        title.setFont(title.getFont().deriveFont(17f));
        titleRow.add(title, BorderLayout.WEST);
        JPanel rightControls = new JPanel();
        rightControls.add(new JLabel("Sort:"));
        sortBox.addActionListener(event -> reload(editingId));
        rightControls.add(sortBox);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> reload(editingId));
        rightControls.add(refresh);
        titleRow.add(rightControls, BorderLayout.EAST);
        outer.add(titleRow, BorderLayout.NORTH);
        outer.add(statsLabel, BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.getSelectionModel().addListSelectionListener(this::selectionChanged);
        JScrollPane tableScroll = new JScrollPane(table);
        outer.add(tableScroll, BorderLayout.CENTER);

        adviceArea.setEditable(false);
        adviceArea.setLineWrap(true);
        adviceArea.setWrapStyleWord(true);
        adviceArea.setRows(5);
        adviceArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Reachability insight"),
                new EmptyBorder(6, 8, 6, 8)));
        adviceArea.setText("Select a contact to see a transparent fallback suggestion.\n"
                + "The score measures saved route coverage and data freshness; it is not a guarantee that someone will answer.");
        outer.add(adviceArea, BorderLayout.SOUTH);
        return outer;
    }

    private int addField(JPanel panel, int row, String label, Component component) {
        GridBagConstraints labelConstraints = constraints(0, row);
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        labelConstraints.insets = new Insets(5, 2, 5, 10);
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints valueConstraints = constraints(1, row);
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueConstraints.weightx = 1.0;
        valueConstraints.insets = new Insets(4, 2, 4, 2);
        panel.add(component, valueConstraints);
        return row + 1;
    }

    private GridBagConstraints constraints(int x, int y) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private JTextField field(String key) {
        JTextField textField = new JTextField(22);
        fields.put(key, textField);
        return textField;
    }

    private boolean selectionEventsSuppressed;

    /**
     * Observer callbacks. Service changes can originate from the web-server
     * threads, so every UI update is forwarded to the event dispatch thread
     * with SwingUtilities.invokeLater. The form is not overwritten: only the
     * table refreshes, and the row being edited stays selected.
     */
    @Override
    public void onCreated(Contact contact) {
        scheduleTableRefresh();
    }

    @Override
    public void onUpdated(Contact contact) {
        scheduleTableRefresh();
    }

    @Override
    public void onDeleted(Contact contact) {
        scheduleTableRefresh();
    }

    private void scheduleTableRefresh() {
        SwingUtilities.invokeLater(this::refreshTable);
    }

    private com.relaybook.service.SortOption currentSort() {
        return com.relaybook.service.SortOption.from(String.valueOf(sortBox.getSelectedItem()));
    }

    /** Runs a service/file operation off the event dispatch thread (SwingWorker). */
    private void runOffTheEdt(String busyStatus, ThrowingWork work) {
        statusLabel.setText(busyStatus);
        new javax.swing.SwingWorker<String, Void>() {
            private Exception failure;

            @Override
            protected String doInBackground() {
                try {
                    return work.run();
                } catch (Exception ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                if (failure != null) {
                    showError(failure);
                    return;
                }
                try {
                    statusLabel.setText(get());
                } catch (Exception ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    /** A unit of work that may throw; executed by runOffTheEdt inside a SwingWorker. */
    @FunctionalInterface
    private interface ThrowingWork {
        String run() throws Exception;
    }

    private void updateStatsLabel() {
        com.relaybook.service.DirectoryStats stats = service.stats();
        statsLabel.setText("Total " + stats.getTotal()
                + "  ·  stale " + stats.getStale()
                + "  ·  single-route " + stats.getSingleRoute()
                + "  ·  ready " + stats.getReady()
                + "  ·  emergency contacts missing a second route: " + stats.getEmergencyWithoutTwoRoutes());
    }

    /** Reloads the table model without touching the form or the advice panel. */
    private void refreshTable() {
        new javax.swing.SwingWorker<List<Contact>, Void>() {
            private RuntimeException failure;

            @Override
            protected List<Contact> doInBackground() {
                try {
                    return currentSort().apply(service.listContacts(), service.getAdvisor());
                } catch (RuntimeException ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                if (failure != null) {
                    return; // Background refresh stays silent; the next explicit action reports errors.
                }
                try {
                    List<Contact> contacts = get();
                    tableModel.setContacts(contacts);
                    updateStatsLabel();
                    int selectedRow = table.getSelectedRow();
                    String selectedId = selectedRow >= 0 && selectedRow < contacts.size()
                            ? contacts.get(selectedRow).getId() : editingId;
                    if (selectedId != null) {
                        for (int i = 0; i < contacts.size(); i++) {
                            if (selectedId.equals(contacts.get(i).getId())) {
                                selectionEventsSuppressed = true;
                                table.setRowSelectionInterval(i, i);
                                selectionEventsSuppressed = false;
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Same silence rule as above.
                }
            }
        }.execute();
    }

    private void selectionChanged(ListSelectionEvent event) {
        if (selectionEventsSuppressed) {
            return;
        }
        if (!event.getValueIsAdjusting()) {
            int row = table.getSelectedRow();
            if (row >= 0 && row < tableModel.getRowCount()) {
                Contact contact = tableModel.getContactAt(row);
                fillForm(contact);
                showAdvice(contact);
            }
        }
    }

    private void fillForm(Contact contact) {
        editingId = contact.getId();
        formTitle.setText("Edit contact · " + contact.getFullName());
        typeBox.setSelectedItem(contact.getContactType().name());
        setField("fullName", contact.getFullName());
        setField("organization", contact.getOrganization());
        setField("relationship", contact.getRelationship());
        routesModel.setRowCount(0);
        for (ContactMethod route : contact.getRoutes()) {
            routesModel.addRow(new Object[]{
                    route.getChannel().name(),
                    route.getValue(),
                    route.getLastVerified() == null ? "" : route.getLastVerified().toString(),
                    route.getLabel() == null ? "" : route.getLabel()});
        }
        refreshPreferredOptions(contact.getPreferredChannel() == null ? null : contact.getPreferredChannel().name());
        setField("bestTime", contact.getBestTime());
        setField("timeZone", contact.getTimeZone());
        setField("lastVerified", contact.getLastVerified() == null ? "" : contact.getLastVerified().toString());
        notesArea.setText(contact.getNotes() == null ? "" : contact.getNotes());
        shareConsentBox.setSelected(contact.hasShareConsent());
    }

    private void setField(String key, String value) {
        fields.get(key).setText(value == null ? "" : value);
    }

    private void saveContact() {
        try {
            // Editing the cell in place may not have committed yet; stop it first.
            if (routesTable.isEditing() && routesTable.getCellEditor() != null) {
                routesTable.getCellEditor().stopCellEditing();
            }
            Map<String, String> values = new HashMap<>();
            values.put("type", String.valueOf(typeBox.getSelectedItem()));
            for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getText());
            }
            for (int i = 0; i < routesModel.getRowCount(); i++) {
                values.put("route." + i + ".channel", String.valueOf(routesModel.getValueAt(i, 0)));
                values.put("route." + i + ".value", String.valueOf(routesModel.getValueAt(i, 1)));
                values.put("route." + i + ".verified", String.valueOf(routesModel.getValueAt(i, 2)));
                values.put("route." + i + ".label", String.valueOf(routesModel.getValueAt(i, 3)));
            }
            if (preferredBox.getSelectedItem() != null) {
                values.put("preferredChannel", String.valueOf(preferredBox.getSelectedItem()));
            }
            values.put("notes", notesArea.getText());
            values.put("shareConsent", Boolean.toString(shareConsentBox.isSelected()));
            String id = editingId == null ? UUID.randomUUID().toString() : editingId;
            Contact contact = ContactFactory.fromForm(id, values);
            final boolean creating = editingId == null;
            runOffTheEdt("Saving…", () -> {
                if (creating) {
                    service.create(contact);
                    reload(contact.getId());
                    return "Contact created.";
                }
                service.update(contact);
                reload(contact.getId());
                return "Contact updated.";
            });
        } catch (RuntimeException ex) {
            showError(ex);
        }
    }

    private void deleteContact() {
        if (editingId == null) {
            JOptionPane.showMessageDialog(this, "Select a contact to delete.", "No contact selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Delete this contact? This cannot be undone.",
                "Confirm delete", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        final String idToDelete = editingId;
        runOffTheEdt("Deleting…", () -> {
            service.delete(idToDelete);
            javax.swing.SwingUtilities.invokeLater(this::clearForm);
            reload(null);
            return "Contact deleted.";
        });
    }

    /** Quick action: mark every route of the selected contact as checked today. */
    private void markCheckedToday() {
        if (editingId == null) {
            JOptionPane.showMessageDialog(this, "Select a contact first.", "No contact selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final String idToVerify = editingId;
        runOffTheEdt("Marking routes as checked…", () -> {
            Contact verified = service.markVerified(idToVerify);
            reload(idToVerify);
            javax.swing.SwingUtilities.invokeLater(() -> showAdvice(verified));
            return "All routes marked as checked today.";
        });
    }

    private void exportVCard() {
        if (editingId == null) {
            JOptionPane.showMessageDialog(this, "Select a contact before exporting.", "No contact selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Contact contact = service.requireContact(editingId);
            ContactCardDocument document = new ContactCardDocument(contact);
            int answer = JOptionPane.showConfirmDialog(this,
                    document.getPrivacyLabel() + "\n\nContinue with vCard export?",
                    "Privacy-aware export", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("contact.vcf"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                java.nio.file.Path target = chooser.getSelectedFile().toPath();
                String content = document.export();
                runOffTheEdt("Exporting vCard…", () -> {
                    Files.write(target, content.getBytes(StandardCharsets.UTF_8));
                    return "vCard exported.";
                });
            }
        } catch (RuntimeException ex) {
            showError(ex);
        }
    }

    /** File > Import vCard: choose a .vcf file and show the per-card summary. */
    private void importVCard() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import vCard (.vcf)");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            java.nio.file.Path source = chooser.getSelectedFile().toPath();
            runOffTheEdt("Importing…", () -> {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                ImportSummary summary = new VCardImporter(service).importFromText(text);
                StringBuilder report = new StringBuilder(summary.summaryText());
                if (!summary.getRejected().isEmpty()) {
                    report.append("\n\nRejected cards:");
                    for (String reason : summary.getRejected()) {
                        report.append("\n · ").append(reason);
                    }
                }
                String statusText = summary.summaryText();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    refreshTable();
                    JOptionPane.showMessageDialog(this, report.toString(), "vCard import", JOptionPane.INFORMATION_MESSAGE);
                });
                return statusText;
            });
        } catch (RuntimeException ex) {
            showError(ex);
        }
    }

    private void exportCsv(boolean fullBackup) {
        CsvDirectoryDocument document = new CsvDirectoryDocument(service.listContacts(), fullBackup);
        int answer = JOptionPane.showConfirmDialog(this, document.getPrivacyLabel() + "\n\nContinue?",
                "CSV export", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(fullBackup ? "relaybook-directory-full.csv" : "relaybook-directory.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.nio.file.Path target = chooser.getSelectedFile().toPath();
            new javax.swing.SwingWorker<String, Void>() {
                private Exception failure;

                @Override
                protected String doInBackground() {
                    try {
                        Files.write(target, new CsvDirectoryDocument(service.listContacts(), fullBackup)
                                .export().getBytes(StandardCharsets.UTF_8));
                        return "CSV directory exported.";
                    } catch (IOException | RuntimeException ex) {
                        failure = ex;
                        return null;
                    }
                }

                @Override
                protected void done() {
                    if (failure != null) {
                        showError(failure);
                    } else {
                        try {
                            statusLabel.setText(get());
                        } catch (Exception ex) {
                            showError(ex);
                        }
                    }
                }
            }.execute();
        }
    }

    private void exportJsonBackup() {
        JsonBackupDocument document = new JsonBackupDocument(service.listContacts());
        int answer = JOptionPane.showConfirmDialog(this,
                "The JSON backup contains every field, including private notes and all routes.\n"
                        + "Keep the file in a safe place. Continue?", "Full backup", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("relaybook-backup.json"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.nio.file.Path target = chooser.getSelectedFile().toPath();
            runOffTheEdt("Exporting backup…", () -> {
                Files.write(target, new JsonBackupDocument(service.listContacts())
                        .export().getBytes(StandardCharsets.UTF_8));
                return "JSON backup exported.";
            });
        }
    }

    private void exportReachabilitySummary() {
        List<Contact> contacts = service.listContacts();
        List<Contact> stale = new java.util.ArrayList<>();
        List<Contact> singleRoute = new java.util.ArrayList<>();
        for (Contact contact : contacts) {
            String status = service.analyze(contact).getStatus();
            if ("Needs verification".equals(status)) {
                stale.add(contact);
            } else if ("Single route".equals(status) || "Add a backup route".equals(status)) {
                singleRoute.add(contact);
            }
        }
        ReachabilityReportDocument document = new ReachabilityReportDocument(stale, singleRoute, false);
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("relaybook-reachability.txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.nio.file.Path target = chooser.getSelectedFile().toPath();
            String content = document.export();
            runOffTheEdt("Exporting summary…", () -> {
                Files.write(target, content.getBytes(StandardCharsets.UTF_8));
                return "Reachability summary exported.";
            });
        }
    }

    private void clearForm() {
        editingId = null;
        formTitle.setText("New contact");
        typeBox.setSelectedItem("PERSON");
        for (JTextField textField : fields.values()) {
            textField.setText("");
        }
        routesModel.setRowCount(0);
        addRouteRow(); // Start every new contact with one empty phone row.
        preferredBox.setSelectedItem(Channel.PHONE.name());
        notesArea.setText("");
        shareConsentBox.setSelected(false);
        table.clearSelection();
        adviceArea.setText("Select a contact to see a transparent fallback suggestion.\n"
                + "The score measures saved route coverage and data freshness; it is not a guarantee that someone will answer.");
    }

    private void reload(String selectId) {
        // Repository access happens off the EDT; only the model updates run on it.
        new javax.swing.SwingWorker<List<Contact>, Void>() {
            private RuntimeException failure;

            @Override
            protected List<Contact> doInBackground() {
                try {
                    return currentSort().apply(service.listContacts(), service.getAdvisor());
                } catch (RuntimeException ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                if (failure != null) {
                    showError(failure);
                    return;
                }
                try {
                    List<Contact> contacts = get();
                    tableModel.setContacts(contacts);
                    updateStatsLabel();
                    if (selectId != null) {
                        for (int i = 0; i < contacts.size(); i++) {
                            if (selectId.equals(contacts.get(i).getId())) {
                                table.setRowSelectionInterval(i, i);
                                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                                return;
                            }
                        }
                    }
                } catch (Exception ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    private void showAdvice(Contact contact) {
        ReachabilityReport report = service.analyze(contact);
        String route = report.getSuggestedChannel() == null
                ? "No route saved"
                : report.getSuggestedChannel().getLabel() + " → " + report.getSuggestedValue();
        String verification = report.getDaysSinceVerified() < 0
                ? "Not recorded"
                : report.getDaysSinceVerified() + " days ago";
        String whenToReach = contact.getBestTime() == null ? "Not recorded" : contact.getBestTime();
        if (contact.getTimeZone() != null) {
            whenToReach += " · " + contact.getTimeZone();
        }
        String localTime = report.getLocalTimeText() == null ? "unknown (no valid time zone)" : report.getLocalTimeText();
        String goodTime;
        if (!report.isWindowKnown()) {
            goodTime = "unknown (no parseable window, examples: 'Weekdays 18:00-21:00', 'any time')";
        } else {
            goodTime = report.isGoodTimeToReachNow() ? "yes — inside their window" : "no — outside their window";
        }
        adviceArea.setText(contact.getFullName() + "  ·  " + report.getStatus()
                + "  ·  coverage/freshness score " + report.getScore() + "/100\n"
                + "Next route: " + route + "\n"
                + "When to reach: " + whenToReach + "\n"
                + "Their local time: " + localTime + " · Good time to reach now: " + goodTime + "\n"
                + "Last verified: " + verification + "\n"
                + report.getRationale() + "\n"
                + "This is a reminder aid, not a guarantee of delivery or response.");
    }

    private void openWebPage() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(webServer.getUrl()));
            } else {
                JOptionPane.showMessageDialog(this, "Open this address in a browser:\n" + webServer.getUrl(),
                        "RelayBook web app", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Open this address in a browser:\n" + webServer.getUrl(),
                    "RelayBook web app", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = ex instanceof DataAccessException ? "Database operation failed." : ex.getClass().getSimpleName();
        }
        JOptionPane.showMessageDialog(this, message, "RelayBook", JOptionPane.ERROR_MESSAGE);
        statusLabel.setText("Action failed.");
    }

    private static final class ContactTableModel extends AbstractTableModel {
        private final ContactService service;
        private List<Contact> contacts = java.util.Collections.emptyList();
        private final String[] columns = {"Name", "Kind", "Suggested route", "Verified", "Score"};

        private ContactTableModel(ContactService service) {
            this.service = service;
        }

        private void setContacts(List<Contact> contacts) {
            this.contacts = contacts;
            fireTableDataChanged();
        }

        private Contact getContactAt(int row) {
            return contacts.get(row);
        }

        @Override
        public int getRowCount() { return contacts.size(); }

        @Override
        public int getColumnCount() { return columns.length; }

        @Override
        public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Contact contact = contacts.get(rowIndex);
            ReachabilityReport report = service.analyze(contact);
            switch (columnIndex) {
                case 0: return contact.getFullName();
                case 1: return contact.getTypeLabel();
                case 2: return report.getSuggestedChannel() == null ? "—" : report.getSuggestedChannel().getLabel();
                case 3: return contact.getLastVerified() == null ? "Not recorded" : contact.getLastVerified().toString();
                case 4: return report.getScore() + "/100";
                default: return "";
            }
        }
    }
}
