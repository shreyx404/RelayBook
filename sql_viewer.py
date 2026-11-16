import http.server
import json
import subprocess
import urllib.parse

PORT = 8085

def run_query(sql):
    cmd = [
        r"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "-u", "root", "--batch", "--raw", "-e", sql, "relaybook"
    ]
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
        lines = [line for line in res.stdout.splitlines() if not line.startswith("mysql: [Warning]")]
        if not lines:
            return {"columns": [], "rows": [], "error": res.stderr if res.returncode != 0 else None}
        columns = lines[0].split("\t")
        rows = [line.split("\t") for line in lines[1:] if line.strip()]
        return {"columns": columns, "rows": rows, "error": None}
    except Exception as e:
        return {"columns": [], "rows": [], "error": str(e)}

HTML_PAGE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>RelayBook — Live MySQL Database Inspector</title>
<style>
  :root {
    --bg: #0d1117;
    --card: #161b22;
    --border: #30363d;
    --text: #e6edf3;
    --text-muted: #8b949e;
    --accent: #58a6ff;
    --success: #3fb950;
    --purple: #bc8cff;
    --orange: #f0883e;
    --table-header: #21262d;
    --table-stripe: #13171d;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background: var(--bg);
    color: var(--text);
    padding: 1.5rem;
    line-height: 1.5;
  }
  header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid var(--border);
    padding-bottom: 1rem;
    margin-bottom: 1.5rem;
  }
  .title-group h1 {
    font-size: 1.5rem;
    color: var(--accent);
    display: flex;
    align-items: center;
    gap: .5rem;
  }
  .title-group p {
    color: var(--text-muted);
    font-size: .88rem;
    margin-top: .2rem;
  }
  .badge-live {
    display: inline-flex;
    align-items: center;
    gap: .4rem;
    background: rgba(63, 185, 80, 0.15);
    color: var(--success);
    border: 1px solid var(--success);
    padding: .3rem .8rem;
    border-radius: 20px;
    font-size: .8rem;
    font-weight: 600;
  }
  .dot {
    width: 8px;
    height: 8px;
    background: var(--success);
    border-radius: 50%;
    animation: pulse 1.8s infinite;
  }
  @keyframes pulse {
    0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(63, 185, 80, 0.7); }
    70% { transform: scale(1.1); box-shadow: 0 0 0 8px rgba(63, 185, 80, 0); }
    100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(63, 185, 80, 0); }
  }
  .grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 1.5rem;
  }
  .card {
    background: var(--card);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 1.2rem;
    box-shadow: 0 4px 12px rgba(0,0,0,0.3);
  }
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
  }
  .card-title {
    font-size: 1.1rem;
    font-weight: 600;
    color: #fff;
    display: flex;
    align-items: center;
    gap: .5rem;
  }
  .tag {
    background: #21262d;
    color: var(--orange);
    padding: .2rem .5rem;
    border-radius: 4px;
    font-size: .75rem;
    font-family: monospace;
  }
  .query-box {
    display: flex;
    flex-direction: column;
    gap: .75rem;
    margin-bottom: 1.5rem;
  }
  .presets {
    display: flex;
    gap: .5rem;
    flex-wrap: wrap;
  }
  .btn-preset {
    background: #21262d;
    border: 1px solid var(--border);
    color: var(--text-muted);
    padding: .35rem .75rem;
    border-radius: 6px;
    font-size: .8rem;
    cursor: pointer;
    transition: all .2s;
  }
  .btn-preset:hover {
    color: var(--accent);
    border-color: var(--accent);
  }
  .input-row {
    display: flex;
    gap: .5rem;
  }
  textarea {
    flex: 1;
    background: #0d1117;
    border: 1px solid var(--border);
    color: #58a6ff;
    padding: .6rem;
    border-radius: 6px;
    font-family: monospace;
    font-size: .92rem;
    resize: vertical;
    min-height: 55px;
  }
  .btn-run {
    background: #238636;
    border: none;
    color: white;
    padding: 0 1.4rem;
    border-radius: 6px;
    font-weight: 600;
    cursor: pointer;
    transition: background .2s;
  }
  .btn-run:hover {
    background: #2ea043;
  }
  .table-container {
    overflow-x: auto;
    border: 1px solid var(--border);
    border-radius: 6px;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: .85rem;
    text-align: left;
  }
  th {
    background: var(--table-header);
    color: var(--text-muted);
    font-weight: 600;
    padding: .65rem .85rem;
    border-bottom: 1px solid var(--border);
    font-family: monospace;
  }
  td {
    padding: .65rem .85rem;
    border-bottom: 1px solid var(--border);
    color: var(--text);
  }
  tr:nth-child(even) {
    background: var(--table-stripe);
  }
  tr:hover {
    background: rgba(88, 166, 255, 0.08);
  }
  .empty-msg {
    padding: 2rem;
    text-align: center;
    color: var(--text-muted);
    font-style: italic;
  }
  .flash {
    animation: highlight 1.2s ease-out;
  }
  @keyframes highlight {
    from { background-color: rgba(63, 185, 80, 0.25); }
    to { background-color: transparent; }
  }
</style>
</head>
<body>

<header>
  <div class="title-group">
    <h1>🗄️ RelayBook — Live MySQL Database Inspector</h1>
    <p>Connected to <code>mysql://127.0.0.1:3306/relaybook</code> (InnoDB 3NF Normalized Schema)</p>
  </div>
  <div class="badge-live">
    <div class="dot"></div>
    <span>Live Auto-Sync (Every 2s)</span>
  </div>
</header>

<div class="card query-box">
  <div class="card-title">⚡ Interactive SQL Query Console</div>
  <div class="presets">
    <button class="btn-preset" onclick="setQuery('SELECT id, contact_type, full_name, preferred_channel, updated_at FROM contacts;')">Contacts Summary</button>
    <button class="btn-preset" onclick="setQuery('SELECT contact_id, channel, value, last_verified, label FROM contact_methods;')">Child Routes</button>
    <button class="btn-preset" onclick="setQuery('SELECT c.full_name, c.contact_type, m.channel, m.value, m.last_verified FROM contacts c JOIN contact_methods m ON c.id=m.contact_id ORDER BY c.full_name;')">3NF Relational JOIN</button>
    <button class="btn-preset" onclick="setQuery('SELECT contact_type, COUNT(*) as count FROM contacts GROUP BY contact_type;')">Group by Type</button>
    <button class="btn-preset" onclick="setQuery('DESCRIBE contacts;')">DESCRIBE contacts</button>
    <button class="btn-preset" onclick="setQuery('DESCRIBE contact_methods;')">DESCRIBE contact_methods</button>
  </div>
  <div class="input-row">
    <textarea id="sqlInput">SELECT c.full_name, c.contact_type, m.channel, m.value, m.last_verified FROM contacts c JOIN contact_methods m ON c.id=m.contact_id ORDER BY c.full_name;</textarea>
    <button class="btn-run" onclick="executeCustomQuery()">Run Query</button>
  </div>
  <div id="queryResultContainer" class="table-container" style="display:none; margin-top:.5rem;">
    <table id="queryTable"><thead></thead><tbody></tbody></table>
  </div>
</div>

<div class="grid">
  <!-- Contacts Table -->
  <div class="card">
    <div class="card-header">
      <div class="card-title">
        <span>Table: <code>contacts</code></span>
        <span class="tag" id="contactsCount">0 rows</span>
      </div>
    </div>
    <div class="table-container">
      <table id="contactsTable">
        <thead>
          <tr>
            <th>id</th>
            <th>contact_type</th>
            <th>full_name</th>
            <th>preferred_channel</th>
            <th>notes</th>
            <th>updated_at</th>
          </tr>
        </thead>
        <tbody></tbody>
      </table>
    </div>
  </div>

  <!-- Contact Methods Table -->
  <div class="card">
    <div class="card-header">
      <div class="card-title">
        <span>Table: <code>contact_methods</code> (Foreign Key: <code>contact_id &rarr; contacts.id</code>)</span>
        <span class="tag" id="methodsCount">0 rows</span>
      </div>
    </div>
    <div class="table-container">
      <table id="methodsTable">
        <thead>
          <tr>
            <th>contact_id</th>
            <th>channel</th>
            <th>value</th>
            <th>last_verified</th>
            <th>label</th>
          </tr>
        </thead>
        <tbody></tbody>
      </table>
    </div>
  </div>
</div>

<script>
let lastContactsHash = "";

function setQuery(sql) {
  document.getElementById('sqlInput').value = sql;
  executeCustomQuery();
}

async function executeCustomQuery() {
  const sql = document.getElementById('sqlInput').value.trim();
  if (!sql) return;
  try {
    const res = await fetch('/api/query', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({sql})
    });
    const data = await res.json();
    const container = document.getElementById('queryResultContainer');
    const table = document.getElementById('queryTable');
    container.style.display = 'block';
    
    if (data.error) {
      table.innerHTML = `<tr><td style="color:#f85149; padding:1rem;">Error: ${data.error}</td></tr>`;
      return;
    }
    
    let html = '<thead><tr>' + data.columns.map(c => `<th>${c}</th>`).join('') + '</tr></thead><tbody>';
    data.rows.forEach(r => {
      html += '<tr>' + r.map(v => `<td>${v === 'NULL' ? '<span style="color:#6e7681">NULL</span>' : v}</td>`).join('') + '</tr>';
    });
    html += '</tbody>';
    table.innerHTML = html;
  } catch(e) {
    console.error(e);
  }
}

async function syncTables() {
  try {
    // 1. Fetch contacts
    const resC = await fetch('/api/query', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({sql: 'SELECT id, contact_type, full_name, preferred_channel, notes, updated_at FROM contacts ORDER BY full_name;'})
    });
    const dataC = await resC.json();
    document.getElementById('contactsCount').textContent = `${dataC.rows.length} rows`;
    
    const hash = JSON.stringify(dataC.rows);
    const tbodyC = document.querySelector('#contactsTable tbody');
    if (hash !== lastContactsHash) {
      if (lastContactsHash !== "") tbodyC.classList.add('flash');
      lastContactsHash = hash;
      let html = '';
      dataC.rows.forEach(r => {
        html += `<tr>
          <td><code>${r[0]}</code></td>
          <td><span style="color:#58a6ff; font-weight:600;">${r[1]}</span></td>
          <td style="font-weight:600; color:#fff;">${r[2]}</td>
          <td>${r[3] || '<span style="color:#6e7681">-</span>'}</td>
          <td style="color:#8b949e; max-width:200px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${r[4] || ''}</td>
          <td style="font-family:monospace; font-size:.8rem; color:#8b949e;">${r[5]}</td>
        </tr>`;
      });
      tbodyC.innerHTML = html || '<tr><td colspan="6" class="empty-msg">No contacts found</td></tr>';
      setTimeout(() => tbodyC.classList.remove('flash'), 1200);
    }

    // 2. Fetch contact methods
    const resM = await fetch('/api/query', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({sql: 'SELECT contact_id, channel, value, last_verified, label FROM contact_methods ORDER BY contact_id;'})
    });
    const dataM = await resM.json();
    document.getElementById('methodsCount').textContent = `${dataM.rows.length} routes`;
    const tbodyM = document.querySelector('#methodsTable tbody');
    let htmlM = '';
    dataM.rows.forEach(r => {
      htmlM += `<tr>
        <td><code>${r[0]}</code></td>
        <td><span style="color:#bc8cff; font-weight:600;">${r[1]}</span></td>
        <td style="font-family:monospace; color:#3fb950;">${r[2]}</td>
        <td>${r[3] === 'NULL' ? '<span style="color:#6e7681">Never verified</span>' : r[3]}</td>
        <td>${r[4] === 'NULL' ? '<span style="color:#6e7681">-</span>' : r[4]}</td>
      </tr>`;
    });
    tbodyM.innerHTML = htmlM || '<tr><td colspan="5" class="empty-msg">No routes found</td></tr>';
  } catch(e) {
    console.error(e);
  }
}

// Initial sync and loop
syncTables();
setInterval(syncTables, 2000);
executeCustomQuery();
</script>
</body>
</html>
"""

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass  # Quiet logs

    def do_GET(self):
        if self.path in ("/", "/index.html", "/sql"):
            content = HTML_PAGE.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", len(content))
            self.end_headers()
            self.wfile.write(content)
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/api/query":
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length).decode("utf-8")
            try:
                data = json.loads(body)
                sql = data.get("sql", "")
                result = run_query(sql)
            except Exception as e:
                result = {"columns": [], "rows": [], "error": str(e)}
            resp = json.dumps(result).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", len(resp))
            self.end_headers()
            self.wfile.write(resp)

def run():
    server = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"Live MySQL Database Inspector running on http://127.0.0.1:{PORT}", flush=True)
    server.serve_forever()

if __name__ == '__main__':
    run()
