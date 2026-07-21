package com.cheeseocean.im.common.core.store.rocksdb;

import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RocksDbSupport implements Closeable {

    private static final String DEFAULT_ROOT = System.getProperty("java.io.tmpdir") + "/cheeseim-rocksdb";

    static {
        RocksDB.loadLibrary();
    }

    private static final Map<String, SharedDb> DBS = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final Path baseDirectory;
    private final SharedDb sharedDb;

    public RocksDbSupport() {
        this(Path.of(DEFAULT_ROOT), ObjectMapperFactory.createDefaultMapper());
    }

    public RocksDbSupport(Path baseDirectory, ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        try {
            this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory");
            Files.createDirectories(this.baseDirectory);
            this.sharedDb = DBS.computeIfAbsent(this.baseDirectory.toAbsolutePath().toString(), ignored -> openDb(this.baseDirectory));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize RocksDB at " + baseDirectory, e);
        }
    }

    public synchronized void put(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        StoredEntry entry = new StoredEntry(
                objectToJson(value),
                ttl == null ? null : System.currentTimeMillis() + ttl.toMillis()
        );
        write(key, entry);
    }

    public synchronized <T> T get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        StoredEntry entry = read(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            delete(key);
            return null;
        }
        try {
            return objectMapper.readValue(entry.payloadJson(), type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize RocksDB value for key " + key, e);
        }
    }

    public synchronized void delete(String key) {
        withDb(db -> {
            try {
                db.delete(key.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, "Failed to delete RocksDB key " + key);
    }

    public synchronized void addToSet(String key, String member, Duration ttl) {
        Objects.requireNonNull(member, "member");
        SetValue setValue = get(key, SetValue.class);
        LinkedHashSet<String> members = setValue == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(setValue.members());
        members.add(member);
        put(key, new SetValue(members), ttl);
    }

    public synchronized Set<String> members(String key) {
        SetValue setValue = get(key, SetValue.class);
        if (setValue == null || setValue.members() == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(setValue.members());
    }

    @Override
    public void close() throws IOException {
        // Shared DB handles stay process-scoped for embedded stores.
    }

    private void write(String key, StoredEntry entry) {
        withDb(db -> {
            try {
                db.put(key.getBytes(StandardCharsets.UTF_8), objectMapper.writeValueAsBytes(entry));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, "Failed to write RocksDB key " + key);
    }

    private StoredEntry read(String key) {
        return withDb(db -> {
            try {
                byte[] bytes = db.get(key.getBytes(StandardCharsets.UTF_8));
                if (bytes == null) {
                    return null;
                }
                return objectMapper.readValue(bytes, StoredEntry.class);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, "Failed to read RocksDB key " + key);
    }

    private String objectToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize RocksDB value", e);
        }
    }

    public record SetValue(Set<String> members) {
    }

    public record StoredEntry(String payloadJson, Long expiresAtEpochMillis) {
        public boolean isExpired() {
            return expiresAtEpochMillis != null && expiresAtEpochMillis <= System.currentTimeMillis();
        }
    }

    private void withDb(ThrowingConsumer action, String errorMessage) {
        try {
            synchronized (sharedDb) {
                action.accept(sharedDb.db);
            }
        } catch (Exception e) {
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private <T> T withDb(ThrowingFunction<T> action, String errorMessage) {
        try {
            synchronized (sharedDb) {
                return action.apply(sharedDb.db);
            }
        } catch (Exception e) {
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private static SharedDb openDb(Path baseDirectory) {
        try {
            Options options = new Options().setCreateIfMissing(true);
            RocksDB db = RocksDB.open(options, baseDirectory.toString());
            return new SharedDb(db, options);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize RocksDB at " + baseDirectory, e);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer {
        void accept(RocksDB db) throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingFunction<T> {
        T apply(RocksDB db) throws Exception;
    }

    private record SharedDb(RocksDB db, Options options) {
    }
}
