package info.u_team.oauth_account_manager.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

import info.u_team.oauth_account_manager.OAuthAccountManagerReference;
import net.hycrafthd.minecraft_authenticator.login.AuthenticationFile;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

public class MinecraftAccounts {
	
	private static final Map<UUID, AuthenticationFile> ACCOUNTS = new HashMap<>();
	private static final Map<UUID, GameProfile> LOADED_GAME_PROFILES = new HashMap<>();
	private static final Map<UUID, LoadedAccount> LOADED_ACCOUNTS = new HashMap<>();
	
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	
	private static final Path ACCOUNT_DIRECTORY = Minecraft.getInstance().gameDirectory.toPath().resolve(".oauth-account-manager");
	private static final Path ACCOUNT_FILE = ACCOUNT_DIRECTORY.resolve("accounts.mcoauth");
	
	private static void load() throws IOException {
		ensureDirectoryExists();
		
		if (Files.notExists(ACCOUNT_FILE)) {
			save();
		}
		
		final JsonObject json;
		
		try (final BufferedReader reader = Files.newBufferedReader(ACCOUNT_FILE, StandardCharsets.UTF_8)) {
			json = GSON.fromJson(reader, JsonObject.class);
		}
		
		for (final Entry<String, JsonElement> entry : json.entrySet()) {
			ACCOUNTS.put(UUID.fromString(entry.getKey()), AuthenticationFile.readCompressed(Base64.getDecoder().decode(entry.getValue().getAsString())));
		}
		
		for (final UUID uuid : ACCOUNTS.keySet()) {
			final GameProfile profile = Minecraft.getInstance().getMinecraftSessionService().fillProfileProperties(new GameProfile(uuid, null), false);
			LOADED_GAME_PROFILES.put(uuid, profile);
		}
		
		OAuthAccountManagerReference.LOGGER.info("Loaded {} account" + (ACCOUNTS.size() != 1 ? "s" : "") + " ({})", ACCOUNTS.size(), LOADED_GAME_PROFILES.values().stream().map(GameProfile::getName).collect(Collectors.joining(", ")));
	}
	
	private static void save() throws IOException {
		ensureDirectoryExists();
		
		final JsonObject json = new JsonObject();
		
		for (final Entry<UUID, AuthenticationFile> entry : ACCOUNTS.entrySet()) {
			json.addProperty(entry.getKey().toString(), Base64.getEncoder().encodeToString(entry.getValue().writeCompressed()));
		}
		
		try (final Writer writer = Files.newBufferedWriter(ACCOUNT_FILE, StandardCharsets.UTF_8)) {
			GSON.toJson(json, writer);
		}
	}
	
	public static Future<?> enqueueLoad() {
		return Util.ioPool().submit(() -> {
			try {
				load();
			} catch (final IOException ex) {
				OAuthAccountManagerReference.LOGGER.error("Cannot load minecraft accounts", ex);
			}
		});
	}
	
	public static Future<?> enqueueSave() {
		return Util.ioPool().submit(() -> {
			try {
				save();
			} catch (final IOException ex) {
				OAuthAccountManagerReference.LOGGER.error("Cannot save minecraft accounts", ex);
			}
		});
	}
	
	private static void ensureDirectoryExists() throws IOException {
		Files.createDirectories(ACCOUNT_DIRECTORY);
	}
	
	public static void addAccount(UUID uuid, AuthenticationFile file, GameProfile profile, LoadedAccount account) {
		ACCOUNTS.put(uuid, file);
		LOADED_GAME_PROFILES.put(uuid, profile);
		LOADED_ACCOUNTS.put(uuid, account);
		
		enqueueSave();
	}
	
	public static void removeAccount(UUID uuid) {
		ACCOUNTS.remove(uuid);
		LOADED_GAME_PROFILES.remove(uuid);
		LOADED_ACCOUNTS.remove(uuid);
		
		enqueueSave();
	}
	
	public static void updateAuthenticationFile(UUID uuid, AuthenticationFile file) {
		ACCOUNTS.put(uuid, file);
		
		enqueueSave();
	}
	
	public static Set<UUID> getAccountUUIDs() {
		return ACCOUNTS.keySet();
	}
	
	public static AuthenticationFile getAccount(UUID uuid) {
		return ACCOUNTS.get(uuid);
	}
	
	public static LoadedAccount getLoadedAccount(UUID uuid) {
		return LOADED_ACCOUNTS.get(uuid);
	}
	
	public static GameProfile getGameProfile(UUID uuid) {
		return LOADED_GAME_PROFILES.get(uuid);
	}
	
	public static boolean isLoaded(UUID uuid) {
		return LOADED_ACCOUNTS.containsKey(uuid);
	}
}
