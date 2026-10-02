package com.birdware.config;

import com.birdware.BirdWare;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Crash-safe JSON file IO.
 * <ul>
 *     <li>Writes go to a temp file that is atomically moved over the target, so a crash mid-write never leaves a
 *     truncated config.</li>
 *     <li>Unreadable/corrupted files are renamed to {@code name.corrupt-<timestamp>.json} (so the user can recover
 *     them) and {@code null} is returned, letting callers fall back to defaults.</li>
 *     <li>{@link #writeAsync} serialises on the caller thread (consistent snapshot) and performs disk IO on a single
 *     background thread, keeping the render thread free.</li>
 * </ul>
 */
public final class JsonFiles {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "BirdWare-IO");
		thread.setDaemon(true);
		return thread;
	});

	private JsonFiles() {
	}

	/** Reads a JSON file. Returns null if missing or corrupted (corrupted files are backed up). */
	public static JsonElement read(Path path) {
		if (!Files.isRegularFile(path)) return null;
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement element = JsonParser.parseReader(reader);
			if (element == null || element.isJsonNull()) throw new IOException("empty document");
			return element;
		} catch (Exception e) {
			BirdWare.LOGGER.error("Config file {} is corrupted; backing it up and using defaults", path, e);
			backupCorrupt(path);
			return null;
		}
	}

	private static void backupCorrupt(Path path) {
		try {
			String name = path.getFileName().toString();
			String base = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
			Path backup = path.resolveSibling(base + ".corrupt-" + LocalDateTime.now().format(STAMP) + ".json");
			Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			BirdWare.LOGGER.error("Could not back up corrupted file {}", path, e);
		}
	}

	/** Synchronously writes JSON atomically. Returns false on failure (already logged). */
	public static boolean write(Path path, JsonElement element) {
		return writeString(path, GSON.toJson(element));
	}

	/** Serialises now, writes in the background. */
	public static void writeAsync(Path path, JsonElement element) {
		String json = GSON.toJson(element);
		IO.execute(() -> writeString(path, json));
	}

	private static synchronized boolean writeString(Path path, String json) {
		try {
			Path parent = path.getParent();
			if (parent != null) Files.createDirectories(parent);
			Path temp = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				writer.write(json);
			}
			try {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException e) {
			BirdWare.LOGGER.error("Failed to write {}", path, e);
			return false;
		}
	}

	/** Waits for pending background writes (called on shutdown). */
	public static void flush() {
		try {
			IO.submit(() -> {
			}).get(5, TimeUnit.SECONDS);
		} catch (Exception e) {
			BirdWare.LOGGER.warn("Timed out waiting for pending config writes", e);
		}
	}
}
