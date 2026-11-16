# RelayBook — Presentation & Project Report Visual Asset Catalog

This catalog documents the complete collection of high-resolution screenshots generated for your PowerPoint presentation and Project Report. All images are stored locally in the [`screenshots/`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots) folder of your project repository.

---

## 1. Authentic MySQL Database Pictures

### A. MySQL Workbench 8.0 Query & Result Grid
* **File:** [`10_mysql_workbench_query_and_results.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/10_mysql_workbench_query_and_results.png)
* **Recommended Slide/Section:** *Database Architecture / MySQL Workbench Interface / Relational Queries*
* **Caption for Report:** *Figure 1: MySQL Workbench 8.0 CE session showing active `relaybook` schema tree, SQL JOIN query editor, normalized multi-route Result Grid, and Action Output execution log.*

![MySQL Workbench](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/10_mysql_workbench_query_and_results.png)

---

### B. MySQL Terminal: Tables & Schema Definition (`DESCRIBE`)
* **File:** [`11_mysql_terminal_tables_and_describe.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/11_mysql_terminal_tables_and_describe.png)
* **Recommended Slide/Section:** *Database Schema / CLI Inspection / Data Types & Constraints*
* **Caption for Report:** *Figure 2: MySQL 8.0 command-line client verifying database selection (`USE relaybook;`), `SHOW TABLES;`, and structural schema inspection of `contacts` and `contact_methods` tables.*

![MySQL Terminal Describe](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/11_mysql_terminal_tables_and_describe.png)

---

### C. MySQL Terminal: Actual Stored Records & Relational JOIN
* **File:** [`12_mysql_terminal_select_records.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/12_mysql_terminal_select_records.png)
* **Recommended Slide/Section:** *Data Persistence / Stored Records / Route Freshness Verification*
* **Caption for Report:** *Figure 3: Command-line query execution displaying actual stored records across `contacts` and normalized `contact_methods`, including individual verification dates and route freshness calculations.*

![MySQL Terminal Select](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/12_mysql_terminal_select_records.png)

---

### D. MySQL Transactional Persistence (ACID Insert)
* **File:** [`13_mysql_transactional_insert_crud.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/13_mysql_transactional_insert_crud.png)
* **Recommended Slide/Section:** *JDBC Persistence Implementation / ACID Transactions / CRUD Operations*
* **Caption for Report:** *Figure 4: Demonstration of atomic transactional insertion (`START TRANSACTION ... COMMIT`) ensuring synchronized writes across parent contacts and child routes with zero orphaned records.*

![MySQL Transactional Insert](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/13_mysql_transactional_insert_crud.png)

---

### E. phpMyAdmin Database Inspection View
* **File:** [`14_phpmyadmin_database_inspection.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/14_phpmyadmin_database_inspection.png)
* **Recommended Slide/Section:** *Database Administration / Storage Engine / Foreign Key Constraints*
* **Caption for Report:** *Figure 5: phpMyAdmin database overview showing table sizes, InnoDB engine status, utf8mb4 collation, and foreign key constraint enforcement (`fk_methods_contact` with `ON DELETE CASCADE`).*

![phpMyAdmin Database View](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/14_phpmyadmin_database_inspection.png)

---

### F. Relational Storage Architecture Overview
* **File:** [`06_mysql_database_tables_and_storage.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/06_mysql_database_tables_and_storage.png)
* **Recommended Slide/Section:** *Database Normalization / 3NF Relational Storage*
* **Caption for Report:** *Figure 6: High-level overview of the relational storage architecture contrasting parent contacts and child routes.*

![Relational Storage Overview](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/06_mysql_database_tables_and_storage.png)

---

### G. Entity-Relationship (ER) Schema Diagram
* **File:** [`07_sql_er_schema_diagram.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/07_sql_er_schema_diagram.png)
* **Recommended Slide/Section:** *Database Design / ER Modeling*
* **Caption for Report:** *Figure 7: Entity-Relationship diagram illustrating 3NF normalization, 1:N cardinality, primary/foreign keys, and cascading delete constraints.*

![Entity-Relationship Schema Diagram](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/07_sql_er_schema_diagram.png)

---

## 2. Web & Desktop Interface Screens

### A. Web Directory Dashboard (`/`)
* **File:** [`01_web_contacts_dashboard.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/01_web_contacts_dashboard.png)
* **Caption for Report:** *Figure 8: RelayBook Web Directory Dashboard with search, filtering, directory counters, and reachability scores.*

![Web Directory Dashboard](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/01_web_contacts_dashboard.png)

---

### B. Contact Reachability Detail View (`/person/demo-mira-sen`)
* **File:** [`02_web_person_detail_mira_sen.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/02_web_person_detail_mira_sen.png)
* **Caption for Report:** *Figure 9: Individual contact inspection view showing 100/100 Reachability score, recommended channel, and per-route verification dates.*

![Contact Reachability Detail](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/02_web_person_detail_mira_sen.png)

---

### C. Emergency Contact Inspection (`/person/demo-dev-rao`)
* **File:** [`03_web_emergency_contact_dev_rao.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/03_web_emergency_contact_dev_rao.png)
* **Caption for Report:** *Figure 10: Emergency contact detail screen illustrating strict 90-day verification policy and fallback recommendation.*

![Emergency Contact Detail](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/03_web_emergency_contact_dev_rao.png)

---

### D. Reachability & Check-Ins Dashboard (`/check-ins`)
* **File:** [`04_web_checkins_dashboard.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/04_web_checkins_dashboard.png)
* **Caption for Report:** *Figure 11: Check-ins management screen highlighting stale contacts and verification priorities.*

![Check-ins Dashboard](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/04_web_checkins_dashboard.png)

---

### E. Java Swing Desktop Client (`AddressBookFrame`)
* **File:** [`05_swing_desktop_gui.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/05_swing_desktop_gui.png)
* **Caption for Report:** *Figure 12: Java Swing desktop client featuring split-pane navigation, contact selection JTable, dynamic route sub-editor, and live advisor rationale.*

![Swing Desktop Client](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/05_swing_desktop_gui.png)

---

## 3. Architecture & Verification

### A. Reachability Algorithm Flowchart
* **File:** [`09_reachability_algorithm_flowchart.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/09_reachability_algorithm_flowchart.png)
* **Caption for Report:** *Figure 13: Strategy Pattern scoring pipeline contrasting DefaultRoutingPolicy and StrictRoutingPolicy.*

![Reachability Algorithm](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/09_reachability_algorithm_flowchart.png)

---

### B. Automated Test Suite Execution
* **File:** [`08_test_suite_verification_pass.png`](file:///c:/Users/shrey/Downloads/RelayBook/screenshots/08_test_suite_verification_pass.png)
* **Caption for Report:** *Figure 14: Terminal execution verification demonstrating 91/91 JUnit 5 unit tests passing, smoke checks, and 25/25 API acceptance checks passing.*

![Test Suite Execution](file:///C:/Users/shrey/.gemini/antigravity/brain/0d253f78-ff30-4f22-90e0-361c39280360/screenshots/08_test_suite_verification_pass.png)
