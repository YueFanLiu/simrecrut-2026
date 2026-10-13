package fr.isep.simrecrut.workspace;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.jna.platform.win32.Crypt32Util;
import jakarta.annotation.PreDestroy;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@org.springframework.context.annotation.Profile("resume-workspace")
public class ResumeService {
  final Path data;
  final ObjectMapper json = Facts.JSON;
  final Set<String> processing = ConcurrentHashMap.newKeySet();
  final Map<String, Future<?>> tasks = new ConcurrentHashMap<>();
  final ExecutorService executor = Executors.newFixedThreadPool(2);
  static final Set<String> VISUAL = Set.of(".pdf", ".png", ".jpg", ".jpeg", ".webp");
  static final String SCHEMA = "simrecrut-professional-v1", PROMPT = "local-rules-v2";

  Clock clock = Clock.systemUTC();
  String now() {
    return clock.instant().toString();
  }

  @Autowired
  public ResumeService() throws Exception {
    this(Path.of(System.getenv().getOrDefault("SIMRECRUT_DATA_DIR", "local-data")));
  }

  ResumeService(Path directory) throws Exception {
    data = directory.toAbsolutePath().normalize();
    Files.createDirectories(data.resolve("originals"));
    try (Connection c = db();
        Statement s = c.createStatement()) {
      s.execute(
          "CREATE TABLE IF NOT EXISTS resumes(id TEXT PRIMARY KEY,filename TEXT NOT NULL,sha256"
              + " TEXT UNIQUE NOT NULL,extension TEXT NOT NULL,bytes INTEGER NOT NULL,pages"
              + " INTEGER,original_text TEXT NOT NULL,redacted_text TEXT NOT NULL,status TEXT NOT"
              + " NULL,draft TEXT,confirmed INTEGER NOT NULL DEFAULT 0,revision INTEGER NOT NULL"
              + " DEFAULT 1,cloud_revision INTEGER,created_at TEXT NOT NULL,updated_at TEXT NOT"
              + " NULL,error TEXT,model_name TEXT NOT NULL DEFAULT 'manual',reference_month TEXT)");
      boolean hasPolicy = false;
      try (ResultSet columns = s.executeQuery("PRAGMA table_info(resumes)")) {
        while (columns.next()) if (columns.getString("name").equals("temporary_original")) hasPolicy = true;
      }
      if (!hasPolicy) s.execute("ALTER TABLE resumes ADD COLUMN temporary_original INTEGER NOT NULL DEFAULT 0");
      Set<String> names = new HashSet<>();
      try (ResultSet columns = s.executeQuery("PRAGMA table_info(resumes)")) {
        while (columns.next()) names.add(columns.getString("name"));
      }
      for (String column : List.of("expires_at", "confirmed_at", "deletion_reason"))
        if (!names.contains(column)) s.execute("ALTER TABLE resumes ADD COLUMN " + column + " TEXT");
      if (!names.contains("publication_attempted")) s.execute("ALTER TABLE resumes ADD COLUMN publication_attempted INTEGER NOT NULL DEFAULT 0");
      s.execute("CREATE TABLE IF NOT EXISTS deletion_receipts(id TEXT PRIMARY KEY,reason TEXT NOT NULL,deleted_at TEXT NOT NULL)");
      s.execute("CREATE TABLE IF NOT EXISTS repeat_operations(operation_key TEXT PRIMARY KEY,action TEXT NOT NULL,target TEXT NOT NULL,request_hash TEXT NOT NULL,expires_at TEXT NOT NULL,response TEXT)");
      s.execute("CREATE INDEX IF NOT EXISTS repeat_operations_expiry ON repeat_operations(expires_at)");
      s.execute("CREATE INDEX IF NOT EXISTS repeat_operations_target ON repeat_operations(target)");
      // Preserve research originals and migrate temporary records from their original timestamps.
      s.executeUpdate("UPDATE resumes SET expires_at=strftime('%Y-%m-%dT%H:%M:%fZ',CASE WHEN confirmed=1 THEN updated_at ELSE created_at END,CASE WHEN confirmed=1 THEN '+30 days' ELSE '+1 day' END) WHERE temporary_original=1 AND expires_at IS NULL");
      s.executeUpdate("UPDATE resumes SET status='UPLOADED' WHERE status='IMPORTED'");
      s.executeUpdate("UPDATE resumes SET status='EXTRACTING' WHERE status='PROCESSING'");
      s.executeUpdate(
          "UPDATE resumes SET status='FAILED',error='The previous process was interrupted. Try"
              + " cleaning again.' WHERE status='EXTRACTING'");
    }
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }

  Connection db() throws SQLException {
    Connection connection = DriverManager.getConnection("jdbc:sqlite:" + data.resolve("index.sqlite"));
    try (Statement statement = connection.createStatement()) { statement.execute("PRAGMA secure_delete=ON"); }
    return connection;
  }

  List<ObjectNode> query(String sql, Object... params) throws SQLException {
    try (Connection c = db();
        PreparedStatement s = c.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) s.setObject(i + 1, params[i]);
      try (ResultSet r = s.executeQuery()) {
        List<ObjectNode> rows = new ArrayList<>();
        while (r.next()) {
          ObjectNode n = json.createObjectNode();
          for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
            n.set(r.getMetaData().getColumnName(i), json.valueToTree(r.getObject(i)));
          rows.add(n);
        }
        return rows;
      }
    }
  }

  void execute(String sql, Object... params) throws SQLException {
    try (Connection c = db();
        PreparedStatement s = c.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) s.setObject(i + 1, params[i]);
      s.executeUpdate();
    }
  }

  List<ObjectNode> list() throws SQLException {
    List<ObjectNode> rows = query(
        "SELECT"
            + " id,filename,extension,bytes,pages,status,confirmed,revision,cloud_revision,created_at,updated_at,error,temporary_original,expires_at,confirmed_at"
            + " FROM resumes WHERE status<>'PURGING' AND (expires_at IS NULL OR expires_at>?) ORDER BY created_at DESC", now());
    for (ObjectNode item : rows) item.put("originalAvailable", originalAvailable(item));
    return rows;
  }

  ObjectNode storedRow(String id) throws SQLException {
    Facts.require(id.matches("[a-f0-9]{32}"), "Invalid resume ID.");
    List<ObjectNode> rows = query("SELECT * FROM resumes WHERE id=?", id);
    Facts.require(!rows.isEmpty(), "Local resume not found.");
    return rows.get(0);
  }

  ObjectNode row(String id) throws SQLException {
    ObjectNode record = storedRow(id);
    Facts.require(!record.path("status").asText().equals("PURGING") && !expired(record), "This resume has expired or is pending deletion.");
    return record;
  }

  boolean expired(ObjectNode record) {
    String deadline = record.path("expires_at").asText("");
    return !deadline.isBlank() && !clock.instant().isBefore(Instant.parse(deadline));
  }

  ObjectNode detail(String id) throws Exception {
    ObjectNode r = row(id);
    r.put("originalAvailable", originalAvailable(r));
    r.remove("original_text");
    r.set("draft", json.readTree(r.path("draft").asText()));
    return r;
  }

  synchronized void patch(String id, Map<String, Object> fields) throws SQLException {
    Map<String, Object> p = new LinkedHashMap<>(fields);
    p.put("updated_at", now());
    String clauses = String.join(",", p.keySet().stream().map(k -> k + "=?").toList());
    List<Object> args = new ArrayList<>(p.values());
    args.add(id);
    execute("UPDATE resumes SET " + clauses + " WHERE id=?", args.toArray());
  }

  static Map<String, Object> fields(Object... pairs) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], pairs[i + 1]);
    return out;
  }

  Map<String, String> settings() throws Exception {
    Path p = data.resolve("settings.dpapi");
    if (!Files.exists(p)) return new HashMap<>();
    byte[] clear = Crypt32Util.cryptUnprotectData(Files.readAllBytes(p));
    try {
      return json.readValue(
          clear, json.getTypeFactory().constructMapType(HashMap.class, String.class, String.class));
    } finally {
      Arrays.fill(clear, (byte) 0);
    }
  }

  ObjectNode publicSettings() throws Exception {
    return publicSettings(settings());
  }

  ObjectNode publicSettings(Map<String, String> cfg) {
    ObjectNode out = json.createObjectNode();
    for (String k : List.of("host", "port", "user", "database", "caPem"))
      out.put(k, cfg.getOrDefault(k, ""));
    out.put("hasPassword", !cfg.getOrDefault("password", "").isBlank());
    out.put("localFolder", data.resolve("originals").toString());
    out.put("schemaVersion", SCHEMA);
    return out;
  }

  synchronized ObjectNode saveSettings(JsonNode body) throws Exception {
    Set<String> allowed =
        Set.of("host", "port", "user", "password", "database", "caPem");
    Facts.keys(body, allowed);
    Map<String, String> cfg = settings();
    body.fields()
        .forEachRemaining(
            e -> {
              String value = e.getValue().asText().trim();
              if (!e.getKey().equals("password") || !value.isBlank())
                cfg.put(e.getKey(), value);
            });
    cfg.putIfAbsent("port", "25060");
    cfg.putIfAbsent("database", "defaultdb");
    Facts.require(
        cfg.getOrDefault("host", "").isEmpty() || cfg.get("host").matches("[A-Za-z0-9.-]+"),
        "Enter a hostname without a URL or path.");
    int port = Integer.parseInt(cfg.get("port"));
    Facts.require(port >= 1 && port <= 65535, "Invalid database port.");
    Facts.require(cfg.get("database").matches("[A-Za-z0-9_-]{1,64}"), "Invalid database name.");
    if (!cfg.getOrDefault("caPem", "").isBlank()) certificates(cfg.get("caPem"));
    byte[] clear = json.writeValueAsBytes(cfg);
    try {
      Files.write(data.resolve("settings.tmp"), Crypt32Util.cryptProtectData(clear));
      Files.move(
          data.resolve("settings.tmp"),
          data.resolve("settings.dpapi"),
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Arrays.fill(clear, (byte) 0);
    }
    return publicSettings(cfg);
  }

  static Collection<? extends Certificate> certificates(String pem) throws Exception {
    return CertificateFactory.getInstance("X.509")
        .generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
  }

  Connection cloud() throws Exception {
    Map<String, String> cfg = settings();
    for (String k : List.of("host", "port", "user", "password", "database", "caPem"))
      Facts.require(
          !cfg.getOrDefault(k, "").isBlank(),
          "Configure all Aiven connection fields and the CA certificate first.");
    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, null);
    int i = 0;
    for (Certificate cert : certificates(cfg.get("caPem")))
      ks.setCertificateEntry("ca-" + (i++), cert);
    Facts.require(i > 0, "No CA certificate found.");
    Path trust = Files.createTempFile(data, "aiven-trust-", ".jks");
    String pass = UUID.randomUUID().toString();
    try {
      try (OutputStream out = Files.newOutputStream(trust)) {
        ks.store(out, pass.toCharArray());
      }
      Properties p = new Properties();
      p.setProperty("user", cfg.get("user"));
      p.setProperty("password", cfg.get("password"));
      p.setProperty("sslMode", "VERIFY_IDENTITY");
      p.setProperty("trustCertificateKeyStoreUrl", trust.toUri().toString());
      p.setProperty("trustCertificateKeyStorePassword", pass);
      p.setProperty("trustCertificateKeyStoreType", "JKS");
      p.setProperty("fallbackToSystemTrustStore", "false");
      p.setProperty("connectTimeout", "10000");
      p.setProperty("socketTimeout", "20000");
      p.setProperty("connectionTimeZone", "UTC");
      return DriverManager.getConnection(
          "jdbc:mysql://" + cfg.get("host") + ":" + cfg.get("port") + "/" + cfg.get("database"), p);
    } finally {
      Files.deleteIfExists(trust);
    }
  }

  Object testCloud() throws Exception {
    try (Connection c = cloud();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT VERSION(),DATABASE()")) {
      r.next();
      return Map.of("connected", true, "version", r.getString(1), "database", r.getString(2));
    }
  }

  static String redact(String text) {
    String out =
        text.replaceAll("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}", "[EMAIL]")
            .replaceAll("https?://\\S+|www\\.\\S+", "[LINK]")
            .replaceAll(
                "(?im)^(?:\u59d3\u540d|name|nom|\u7535\u8bdd|\u8054\u7cfb\u65b9\u5f0f|\u5730\u5740|address|\u51fa\u751f\u65e5\u671f|date of"
                    + " birth|\u6027\u522b|gender|\u6c11\u65cf|ethnicity)\\s*[:：].*$",
                "[IDENTIFIER REMOVED]");
    Matcher m = Pattern.compile("(?<!\\w)(?:\\+?\\d[\\d ().-]{7,}\\d)(?!\\w)").matcher(out);
    return m.replaceAll(
        match ->
            Pattern.compile("\\d{4}-(?:0[1-9]|1[0-2])").matcher(match.group()).find()
                    || match.group().replaceAll("\\D", "").length() < 9
                ? Matcher.quoteReplacement(match.group())
                : "[PHONE]");
  }

  synchronized Object importFile(JsonNode body) throws Exception {
    String name = body.path("filename").asText("resume.pdf").replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    if (name.length() > 255) name = name.substring(0, 255);
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    byte[] bytes = Base64.getDecoder().decode(body.path("content").asText());
    Facts.require(
        bytes.length > 0 && bytes.length <= 10 * 1024 * 1024,
        "File size must be between 1 byte and 10 MB.");
    String text = "";
    Integer pages = null;
    if (ext.equals(".pdf")) {
      try (PDDocument pdf = Loader.loadPDF(bytes)) {
        Facts.require(!pdf.isEncrypted(), "Encrypted PDFs are not supported.");
        pages = pdf.getNumberOfPages();
        Facts.require(pages >= 1 && pages <= 20, "PDFs must contain 1–20 pages.");
      } catch (java.io.IOException error) {
        // Invalid input is a file error, not a temporary service failure.
        throw new IllegalArgumentException("The PDF is invalid or password-protected. Choose a readable, unencrypted PDF.");
      }
    } else if (VISUAL.contains(ext)) {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      Facts.require(image != null, "Unsupported or invalid image. Use PNG or JPEG.");
      pages = 1;
    } else if (ext.equals(".docx")) {
      try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
        StringBuilder b = new StringBuilder();
        doc.getParagraphs().forEach(p -> b.append(p.getText()).append('\n'));
        doc.getTables()
            .forEach(
                t ->
                    t.getRows()
                        .forEach(
                            r ->
                                r.getTableCells()
                                    .forEach(c -> b.append(c.getText()).append(" | "))));
        text = b.toString();
      }
    } else if (ext.equals(".txt")) {
      try {
        text =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString();
      } catch (CharacterCodingException e) {
        text = new String(bytes, Charset.forName("GB18030"));
      }
      text = text.replaceFirst("^\\uFEFF", "");
    } else throw new IllegalArgumentException("Supported formats: PDF, images, DOCX, and TXT.");
    Facts.require(VISUAL.contains(ext) || !text.isBlank(), "The file contains no readable text.");
    Facts.require(text.length() <= 60000, "Resume text exceeds 60,000 characters.");
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    List<ObjectNode> exists = query("SELECT id FROM resumes WHERE sha256=?", hash);
    if (!exists.isEmpty()) {
      row(exists.get(0).path("id").asText());
      return Map.of("id", exists.get(0).get("id").asText(), "duplicate", true);
    }
    String id = UUID.randomUUID().toString().replace("-", "");
    Path file = data.resolve("originals").resolve(id + ext);
    Files.write(file, bytes);
    try {
      execute(
          "INSERT INTO"
              + " resumes(id,filename,sha256,extension,bytes,pages,original_text,redacted_text,status,draft,created_at,updated_at)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
          id,
          name,
          hash,
          ext,
          bytes.length,
          pages,
          text,
          redact(text),
          "UPLOADED",
          Facts.empty().toString(),
          now(),
          now());
      patch(id, fields("temporary_original", 1, "expires_at", clock.instant().plus(Duration.ofHours(24)).toString()));
    } catch (Exception e) {
      Files.deleteIfExists(file);
      throw e;
    }
    return Map.of("id", id, "duplicate", false);
  }

  List<byte[]> images(ObjectNode r, Integer only) throws Exception {
    Path file =
        data.resolve("originals").resolve(r.get("id").asText() + r.get("extension").asText());
    List<byte[]> out = new ArrayList<>();
    if (r.get("extension").asText().equals(".pdf")) {
      try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
        PDFRenderer renderer = new PDFRenderer(pdf);
        for (int i = 0; i < pdf.getNumberOfPages(); i++)
          if (only == null || only == i + 1) {
            BufferedImage image = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
            out.add(jpeg(image));
          }
      }
    } else {
      BufferedImage src = ImageIO.read(file.toFile());
      Facts.require(src != null, "Invalid image.");
      double scale = Math.min(1.0, 2200.0 / Math.max(src.getWidth(), src.getHeight()));
      BufferedImage dest =
          new BufferedImage(
              (int) (src.getWidth() * scale),
              (int) (src.getHeight() * scale),
              BufferedImage.TYPE_INT_RGB);
      Graphics2D g = dest.createGraphics();
      g.setColor(java.awt.Color.WHITE);
      g.fillRect(0, 0, dest.getWidth(), dest.getHeight());
      g.drawImage(src, 0, 0, dest.getWidth(), dest.getHeight(), null);
      g.dispose();
      out.add(jpeg(dest));
    }
    Facts.require(
        out.stream().mapToLong(b -> b.length).sum() <= 32L * 1024 * 1024,
        "Rendered images are too large. Split the PDF.");
    return out;
  }

  static byte[] jpeg(BufferedImage img) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    ImageIO.write(img, "jpeg", b);
    return b.toByteArray();
  }

  byte[] page(String id, int number) throws Exception {
    ObjectNode r = row(id);
    Facts.require(
        VISUAL.contains(r.path("extension").asText())
            && number >= 1
            && number <= r.path("pages").asInt(1),
        "Page not found.");
    return images(r, number).get(0);
  }

  synchronized Object save(String id, JsonNode body) throws Exception {
    ObjectNode r = row(id);
    Facts.require(
        !processing.contains(id), "The resume is being processed. Wait until it finishes.");
    ObjectNode draft = Facts.validate(body.get("draft"));
    FactProvenance.markCorrections(draft, json.readTree(r.path("draft").asText()));
    if (body.has("expectedRevision")) Facts.require(body.path("expectedRevision").asInt(-1) == r.path("revision").asInt(), "The resume changed. Reload before saving.");
    boolean confirm = body.path("confirmed").asBoolean();
    Facts.require(!confirm || Set.of("REVIEW_REQUIRED", "CONFIRMED").contains(r.path("status").asText()), "Save a review draft before confirmation.");
    if (r.path("confirmed").asInt() == 1) {
      Facts.require(confirm && draft.equals(json.readTree(r.path("draft").asText())), "Confirmed facts are immutable. Import a new resume to make changes.");
      return Map.of("saved", true);
    }
    Facts.require(
        !confirm || Facts.hasFacts(draft),
        "An empty resume cannot be confirmed. Add a professional fact.");
    patch(
        id,
        fields(
            "draft",
            draft.toString(),
            "confirmed",
            confirm ? 1 : 0,
            "revision",
            r.path("revision").asInt() + 1,
            "status",
            confirm ? "CONFIRMED" : "REVIEW_REQUIRED",
            "error",
            null,
            "confirmed_at", confirm ? now() : null,
            "expires_at", confirm ? clock.instant().plus(Duration.ofDays(30)).toString() : r.path("expires_at").asText(null),
            "reference_month",
            confirm ? YearMonth.now(ZoneOffset.UTC).toString() : null));
    return Map.of("saved", true);
  }

  synchronized Object clean(String id, JsonNode body) throws Exception {
    ObjectNode row=row(id);
    Facts.require(Set.of("UPLOADED", "REVIEW_REQUIRED", "FAILED").contains(row.path("status").asText()), "Extraction cannot start in the current state.");
    Facts.require(row.path("confirmed").asInt() == 0, "Confirmed facts are immutable.");
    Facts.require(originalAvailable(row), "The temporary original was removed after cloud confirmation.");
    Facts.require(!processing.contains(id),"The resume is being processed.");
    patch(id,fields("status","EXTRACTING","confirmed",0,"revision",row.path("revision").asInt()+1,"error",null,"model_name","local-rules-v2"));
    processing.add(id);
    FutureTask<Void> task = new FutureTask<>(()->{
      try {
        ObjectNode draft = new fr.isep.simrecrut.integration.pdf.LocalResumeCleaner().clean(
            row.path("filename").asText(), Files.readAllBytes(originalPath(row)));
        draft.remove(List.of("schemaVersion", "cleanerVersion", "status"));
        draft = Facts.validate(draft);
        String text = row.path("redacted_text").asText();
        synchronized (this) {
          row(id); // Recheck retention before an asynchronous task writes its result.
          patch(id,fields("redacted_text",redact(text),"draft",draft.toString(),"status","REVIEW_REQUIRED","confirmed",0,"error",null));
        }
      } catch(Exception e) {
        try { synchronized (this) { row(id); patch(id,fields("status","FAILED","error",e instanceof IllegalArgumentException?e.getMessage():"Local parsing or OCR failed. Check the local OCR installation or use manual entry.")); } } catch(Exception ignored){}
      } finally{processing.remove(id); tasks.remove(id);}
    }, null);
    tasks.put(id, task);
    executor.execute(task);
    return Map.of("processing",true);
  }

  static final String DDL =
      "CREATE TABLE IF NOT EXISTS sim_cv_cleaned(resume_id CHAR(32) CHARACTER SET ascii NOT"
          + " NULL,revision INT NOT NULL,source_sha256 CHAR(64) CHARACTER SET ascii NOT"
          + " NULL,schema_version VARCHAR(64) NOT NULL,prompt_version VARCHAR(64) NOT"
          + " NULL,model_name VARCHAR(100) NOT NULL,professional_json JSON NOT NULL,warnings_json"
          + " JSON NOT NULL,assessment_reference_month CHAR(7) NOT NULL,confirmed_at DATETIME(3)"
          + " NOT NULL,uploaded_at DATETIME(3) NOT NULL,PRIMARY KEY(resume_id,revision))"
          + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

  synchronized Object upload(String id) throws Exception {
    ObjectNode r = row(id);
    Facts.require(
        !processing.contains(id) && r.path("confirmed").asInt() == 1,
        "Save and confirm the structured data first.");
    ObjectNode d = Facts.validate(json.readTree(r.path("draft").asText()));
    // Persist intent before the remote commit so crash recovery can remove an orphaned publication.
    patch(id, fields("publication_attempted", 1));
    try (Connection c = cloud()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        s.execute(DDL);
      }
      Facts.require(!expired(r), "This resume expired before publication could finish.");
      try (PreparedStatement s =
          c.prepareStatement(
              "INSERT INTO"
                  + " sim_cv_cleaned(resume_id,revision,source_sha256,schema_version,prompt_version,model_name,professional_json,warnings_json,assessment_reference_month,confirmed_at,uploaded_at)"
                  + " VALUES(?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
                  + " resume_id=VALUES(resume_id)")) {
        Object[] args = {
          id,
          r.path("revision").asInt(),
          r.path("sha256").asText(),
          SCHEMA,
          PROMPT,
          r.path("model_name").asText(),
          d.get("professional").toString(),
          d.get("warnings").toString(),
          r.path("reference_month").asText(),
          Timestamp.from(Instant.parse(r.path("confirmed_at").asText(r.path("updated_at").asText()))),
          Timestamp.from(Instant.now())
        };
        for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
        s.executeUpdate();
      }
      if (expired(r)) { c.rollback(); throw new IllegalArgumentException("This resume expired before publication could finish."); }
      c.commit();
    }
    patch(id, fields("cloud_revision", r.path("revision").asInt()));
    boolean removed = cleanupOriginal(id);
    return Map.of("uploaded", true, "revision", r.path("revision").asInt(),
        "table", "sim_cv_cleaned", "originalRemoved", removed);

  }
  Path originalPath(ObjectNode row) {
    return data.resolve("originals").resolve(row.path("id").asText() + row.path("extension").asText());
  }

  boolean originalAvailable(ObjectNode row) {
    return Files.isRegularFile(originalPath(row));
  }

  synchronized boolean cleanupOriginal(String id) throws Exception {
    ObjectNode row = row(id);
    // Existing library records predate the temporary-storage policy and are preserved.
    if (row.path("temporary_original").asInt() != 1) return false;
    Facts.require(row.path("cloud_revision").asInt(-1) == row.path("revision").asInt(),
        "Upload the confirmed version successfully before deleting its original.");
    try {
      Files.deleteIfExists(originalPath(row));
      patch(id, fields("original_text", "", "redacted_text", "", "error", null));
      return true;
    } catch (IOException ex) {
      patch(id, fields("error", "Cloud upload succeeded. Temporary-file cleanup pending; retry cleanup."));
      return false;
    }
  }

  /** Block access before deleting original files and every published revision. */
  synchronized Object delete(String id) throws Exception {
    storedRow(id);
    patch(id, fields("status", "PURGING", "deletion_reason", "USER_REQUEST"));
    cancel(id);
    return purge(id);
  }

  synchronized Object purge(String id) throws Exception {
    ObjectNode record = storedRow(id);
    if (processing.contains(id)) return Map.of("deleted", false, "pending", true);
    try {
      if (record.hasNonNull("cloud_revision") || record.path("publication_attempted").asInt() == 1) {
        try (Connection connection = cloud(); PreparedStatement statement = connection.prepareStatement("DELETE FROM sim_cv_cleaned WHERE resume_id=?")) {
          statement.setString(1, id); statement.executeUpdate();
        } catch (SQLException missingTable) {
          if (missingTable.getErrorCode() != 1146) throw missingTable;
        }
      }
      Files.deleteIfExists(originalPath(record));
      try (Connection connection = db()) {
        connection.setAutoCommit(false);
        try (PreparedStatement receipt = connection.prepareStatement("INSERT OR IGNORE INTO deletion_receipts(id,reason,deleted_at) VALUES(?,?,?)");
             PreparedStatement deletion = connection.prepareStatement("DELETE FROM resumes WHERE id=?");
             PreparedStatement operations = connection.prepareStatement("DELETE FROM repeat_operations WHERE target=? AND action<>'delete'")) {
          receipt.setString(1, id); receipt.setString(2, record.path("deletion_reason").asText("EXPIRED")); receipt.setString(3, now()); receipt.executeUpdate();
          operations.setString(1, id); operations.executeUpdate();
          deletion.setString(1, id); deletion.executeUpdate(); connection.commit();
        }
      }
      return Map.of("deleted", true, "pending", false);
    } catch (Exception error) {
      patch(id, fields("error", "Deletion pending. Access is blocked; cleanup will retry."));
      return Map.of("deleted", false, "pending", true);
    }
  }

  synchronized void sweep() throws Exception {
    execute("DELETE FROM repeat_operations WHERE expires_at<=?", now());
    for (ObjectNode record : query("SELECT * FROM resumes WHERE temporary_original=1 OR status='PURGING'")) {
      String id = record.path("id").asText();
      if (expired(record) || record.path("status").asText().equals("PURGING")) {
        if (!record.path("status").asText().equals("PURGING")) patch(id, fields("status", "PURGING", "deletion_reason", "EXPIRED"));
        cancel(id);
        purge(id);
      } else if (record.path("confirmed").asInt() == 1 && record.path("cloud_revision").asInt(-1) == record.path("revision").asInt()) cleanupOriginal(id);
    }
  }

  private void cancel(String id) {
    Future<?> task = tasks.remove(id);
    if (task != null) task.cancel(true);
    processing.remove(id);
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void sweepLocalWorkspace() throws Exception { sweep(); }
}
