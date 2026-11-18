package com.ringout.api.deployment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/** Fingerprint applied migrations, including checksums and repeatables, rather than only their version. */
@Service
@RequiredArgsConstructor
public class FlywaySchemaState {
    private final JdbcTemplate jdbc;
    public record Snapshot(String version, String fingerprint) {}
    public Snapshot current() {
        try {
            var rows = jdbc.query("SELECT version, type, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank",
                    (rs, index) -> new String[] {rs.getString("version"), rs.getString("type"),
                        rs.getString("script"), rs.getString("checksum"), rs.getBoolean("success") ? "1" : "0"});
            String version = null;
            var history = new StringBuilder();
            for (var row : rows) {
                if (!"1".equals(row[4])) return new Snapshot(null, null);
                if (row[0] != null) version = row[0];
                for (String value : row) {
                    String text = value == null ? "" : value;
                    history.append(text.length()).append(':').append(text);
                }
                history.append('\n');
            }
            if (version == null) return new Snapshot(null, null);
            var hash = MessageDigest.getInstance("SHA-256").digest(history.toString().getBytes(StandardCharsets.UTF_8));
            return new Snapshot(version, HexFormat.of().formatHex(hash));
        } catch (DataAccessException e) {
            return new Snapshot(null, null);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
