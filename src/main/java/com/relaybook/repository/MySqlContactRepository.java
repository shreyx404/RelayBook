package com.relaybook.repository;

import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactMethod;
import com.relaybook.model.ContactType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC/MySQL implementation of the repository contract; all user data uses prepared statements.
 *
 * <p>Routes are stored in a normalized {@code contact_methods} table (one row per
 * route with its own verification date). Writes of a contact and its routes run
 * in a single transaction. When an older installation still has the flat
 * {@code phone}/{@code email}/{@code alternate_phone} columns, this class copies
 * them into {@code contact_methods} inside a transaction and then removes the
 * legacy columns, so the migration is safe and runs only once.</p>
 */
public final class MySqlContactRepository implements ContactRepository {
    private static final String CONTACT_COLUMNS = "id, contact_type, full_name, organization, "
            + "relationship_label, preferred_channel, best_time, time_zone, notes, last_verified, share_consent";

    private final String url;
    private final String user;
    private final String password;

    public MySqlContactRepository(String url, String user, String password) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("DB_URL must contain a JDBC URL.");
        }
        this.url = url;
        this.user = user == null ? "" : user;
        this.password = password == null ? "" : password;
        ensureSchemaExists();
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void ensureSchemaExists() {
        try (Connection connection = openConnection()) {
            createContactsTable(connection);
            createContactMethodsTable(connection);
            migrateLegacyRouteColumns(connection);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not connect to MySQL or create the contacts tables. "
                    + "Check DB_URL, DB_USER, DB_PASSWORD, the database name, and Connector/J.", ex);
        }
    }

    private void createContactsTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS contacts ("
                + "id VARCHAR(36) PRIMARY KEY,"
                + "contact_type VARCHAR(24) NOT NULL,"
                + "full_name VARCHAR(160) NOT NULL,"
                + "organization VARCHAR(160) NULL,"
                + "relationship_label VARCHAR(100) NULL,"
                + "preferred_channel VARCHAR(30) NULL,"
                + "best_time VARCHAR(160) NULL,"
                + "time_zone VARCHAR(80) NULL,"
                + "notes TEXT NULL,"
                + "last_verified DATE NULL,"
                + "share_consent BOOLEAN NOT NULL DEFAULT FALSE,"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void createContactMethodsTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS contact_methods ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "contact_id VARCHAR(36) NOT NULL,"
                + "channel VARCHAR(30) NOT NULL,"
                + "`value` VARCHAR(254) NOT NULL,"
                + "last_verified DATE NULL,"
                + "label VARCHAR(100) NULL,"
                + "UNIQUE KEY uq_contact_channel (contact_id, channel),"
                + "FOREIGN KEY (contact_id) REFERENCES contacts(id) ON DELETE CASCADE"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    /** One-time, transactional migration from the pre-2.0 flat route columns. */
    private void migrateLegacyRouteColumns(Connection connection) throws SQLException {
        if (!columnExists(connection, "contacts", "phone")
                && !columnExists(connection, "contacts", "email")
                && !columnExists(connection, "contacts", "alternate_phone")) {
            return; // Fresh installation or already migrated.
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long existingMethods = countRows(connection, "contact_methods");
            if (existingMethods == 0) {
                copyLegacyColumn(connection, "PHONE", "phone");
                copyLegacyColumn(connection, "EMAIL", "email");
                copyLegacyColumn(connection, "ALTERNATE_PHONE", "alternate_phone");
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE contacts DROP COLUMN phone, DROP COLUMN email, DROP COLUMN alternate_phone");
            }
            connection.commit();
            System.out.println("RelayBook migrated legacy phone/email columns into contact_methods.");
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static final List<String> MIGRATABLE_COLUMNS = List.of("phone", "email", "alternate_phone");

    private void copyLegacyColumn(Connection connection, String channel, String column) throws SQLException {
        if (!MIGRATABLE_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("Refusing to migrate unexpected column: " + column);
        }
        String sql = "INSERT IGNORE INTO contact_methods (contact_id, channel, `value`, last_verified, label) "
                + "SELECT id, ?, " + column + ", last_verified, NULL FROM contacts "
                + "WHERE " + column + " IS NOT NULL AND " + column + " <> ''";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, channel);
            statement.executeUpdate();
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getLong(1) > 0;
            }
        }
    }

    private long countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() ? rows.getLong(1) : 0;
        }
    }

    @Override
    public List<Contact> findAll() {
        String contactsSql = "SELECT " + CONTACT_COLUMNS + " FROM contacts ORDER BY full_name";
        String methodsSql = "SELECT contact_id, channel, `value`, last_verified, label FROM contact_methods "
                + "ORDER BY contact_id, channel";
        try (Connection connection = openConnection()) {
            Map<String, List<ContactMethod>> methodsByContact = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(methodsSql);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    methodsByContact.computeIfAbsent(rows.getString("contact_id"), ignored -> new ArrayList<>())
                            .add(new ContactMethod(Channel.from(rows.getString("channel")), rows.getString("value"),
                                    toLocalDate(rows.getDate("last_verified")), rows.getString("label")));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(contactsSql);
                 ResultSet rows = statement.executeQuery()) {
                List<Contact> contacts = new ArrayList<>();
                while (rows.next()) {
                    contacts.add(mapRow(rows, methodsByContact.getOrDefault(rows.getString("id"), new ArrayList<>())));
                }
                return contacts;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not read contacts from MySQL.", ex);
        }
    }

    @Override
    public Optional<Contact> findById(String id) {
        String contactsSql = "SELECT " + CONTACT_COLUMNS + " FROM contacts WHERE id = ?";
        String methodsSql = "SELECT channel, `value`, last_verified, label FROM contact_methods "
                + "WHERE contact_id = ? ORDER BY channel";
        try (Connection connection = openConnection();
             PreparedStatement contactStatement = connection.prepareStatement(contactsSql)) {
            contactStatement.setString(1, id);
            try (ResultSet rows = contactStatement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                List<ContactMethod> methods = new ArrayList<>();
                try (PreparedStatement methodStatement = connection.prepareStatement(methodsSql)) {
                    methodStatement.setString(1, id);
                    try (ResultSet methodRows = methodStatement.executeQuery()) {
                        while (methodRows.next()) {
                            methods.add(new ContactMethod(Channel.from(methodRows.getString("channel")),
                                    methodRows.getString("value"),
                                    toLocalDate(methodRows.getDate("last_verified")),
                                    methodRows.getString("label")));
                        }
                    }
                }
                return Optional.of(mapRow(rows, methods));
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not find the requested contact in MySQL.", ex);
        }
    }

    @Override
    public void insert(Contact contact) {
        String sql = "INSERT INTO contacts (" + CONTACT_COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        Connection connection = null;
        try {
            connection = openConnection();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, contact.getId());
                bindContactFields(statement, contact, 2);
                statement.executeUpdate();
            }
            insertRouteRows(connection, contact);
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DataAccessException("Could not create contact in MySQL.", ex);
        } finally {
            closeQuietly(connection);
        }
    }

    @Override
    public void update(Contact contact) {
        String sql = "UPDATE contacts SET contact_type=?, full_name=?, organization=?, relationship_label=?, "
                + "preferred_channel=?, best_time=?, time_zone=?, notes=?, last_verified=?, share_consent=? WHERE id=?";
        Connection connection = null;
        try {
            connection = openConnection();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindContactFields(statement, contact, 1);
                statement.setString(11, contact.getId());
                if (statement.executeUpdate() == 0) {
                    connection.rollback();
                    throw new DataAccessException("Contact no longer exists in MySQL.");
                }
            }
            try (PreparedStatement deleteMethods = connection.prepareStatement(
                    "DELETE FROM contact_methods WHERE contact_id = ?")) {
                deleteMethods.setString(1, contact.getId());
                deleteMethods.executeUpdate();
            }
            insertRouteRows(connection, contact);
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DataAccessException("Could not update contact in MySQL.", ex);
        } finally {
            closeQuietly(connection);
        }
    }

    @Override
    public boolean delete(String id) {
        String sql = "DELETE FROM contacts WHERE id = ?";
        Connection connection = null;
        try {
            connection = openConnection();
            connection.setAutoCommit(false);
            int deleted;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                deleted = statement.executeUpdate();
            }
            connection.commit();
            return deleted > 0;
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DataAccessException("Could not delete contact from MySQL.", ex);
        } finally {
            closeQuietly(connection);
        }
    }

    private void insertRouteRows(Connection connection, Contact contact) throws SQLException {
        String sql = "INSERT INTO contact_methods (contact_id, channel, `value`, last_verified, label) VALUES (?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ContactMethod method : contact.getRoutes()) {
                statement.setString(1, contact.getId());
                statement.setString(2, method.getChannel().name());
                statement.setString(3, method.getValue());
                statement.setDate(4, method.getLastVerified() == null ? null : Date.valueOf(method.getLastVerified()));
                statement.setString(5, method.getLabel());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void bindContactFields(PreparedStatement statement, Contact contact, int startIndex) throws SQLException {
        int i = startIndex;
        statement.setString(i++, contact.getContactType().name());
        statement.setString(i++, contact.getFullName());
        statement.setString(i++, contact.getOrganization());
        statement.setString(i++, contact.getRelationship());
        statement.setString(i++, contact.getPreferredChannel() == null ? null : contact.getPreferredChannel().name());
        statement.setString(i++, contact.getBestTime());
        statement.setString(i++, contact.getTimeZone());
        statement.setString(i++, contact.getNotes());
        statement.setDate(i++, contact.getLastVerified() == null ? null : Date.valueOf(contact.getLastVerified()));
        statement.setBoolean(i++, contact.hasShareConsent());
    }

    private Contact mapRow(ResultSet row, List<ContactMethod> methods) throws SQLException {
        // Routes carry their own dates; the record-level last_verified column is a
        // derived value used for fast sorting and is NOT fed back into the builder,
        // so a route the user left undated stays undated after a database round-trip.
        return Contact.builder(ContactType.from(row.getString("contact_type")))
                .id(row.getString("id"))
                .fullName(row.getString("full_name"))
                .organization(row.getString("organization"))
                .relationship(row.getString("relationship_label"))
                .routes(methods)
                .preferredChannel(Channel.from(row.getString("preferred_channel")))
                .bestTime(row.getString("best_time"))
                .timeZone(row.getString("time_zone"))
                .notes(row.getString("notes"))
                .shareConsent(row.getBoolean("share_consent"))
                .build();
    }

    private static LocalDate toLocalDate(Date sqlDate) {
        return sqlDate == null ? null : sqlDate.toLocalDate();
    }

    private static void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // Original failure is reported to the caller.
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.setAutoCommit(true);
                connection.close();
            } catch (SQLException ignored) {
                // Nothing useful to do while closing.
            }
        }
    }
}
