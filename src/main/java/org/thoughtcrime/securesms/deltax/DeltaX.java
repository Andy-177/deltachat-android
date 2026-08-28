package org.thoughtcrime.securesms.deltax;

import android.content.Context;
import android.util.Log;
import com.b44t.messenger.DcContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.deltax.module.ConfigManager;
import org.thoughtcrime.securesms.deltax.module.Manifest;
import org.thoughtcrime.securesms.deltax.module.PluginInfo;
import org.thoughtcrime.securesms.deltax.module.PluginLoader;
import org.thoughtcrime.securesms.deltax.module.PluginPackager;

public class DeltaX {

  private static final String TAG = "DeltaX";
  public static final String ENGINE_NAME = "DeltaX";
  public static final String ENGINE_VERSION = "1.0";

  private final Context context;
  private final int accountId;
  private final File extensionDir;
  private final File pluginsDir;

  private final LuaEngine luaEngine;
  private final ConfigManager configManager;
  private final PluginLoader evaluator;
  private final PluginPackager pluginPackager;

  private List<PluginInfo> loadedPlugins = new ArrayList<>();
  private boolean initialised = false;
  private final Map<String, String> builtinAssetDirs = new HashMap<>();

  private static final Map<Integer, DeltaX> instances = new HashMap<>();

  public static synchronized DeltaX getInstance(Context context) {
    return getInstance(context, getSelectedAccountId(context));
  }

  public static synchronized DeltaX getInstance(Context context, int accountId) {
    DeltaX inst = instances.get(accountId);
    if (inst == null) {
      inst = new DeltaX(context, accountId);
      instances.put(accountId, inst);
    }
    return inst;
  }

  private static int getSelectedAccountId(Context context) {
    try {
      DcContext dc = DcHelper.getContext(context);
      if (dc != null) return dc.getAccountId();
    } catch (Exception ignored) {
    }
    return -1;
  }

  public DeltaX(Context context, int accountId) {
    this.context = context.getApplicationContext();
    this.accountId = accountId;
    File accountDir = resolveAccountDir(context);
    this.extensionDir = new File(accountDir, "extension");
    this.pluginsDir = new File(extensionDir, "plugin");

    this.configManager = new ConfigManager(extensionDir);
    this.luaEngine = new LuaEngine(this.context, this);
    this.evaluator = new PluginLoader(this, pluginsDir, configManager, luaEngine);
    this.pluginPackager = new PluginPackager(extensionDir);
  }

  private static File resolveAccountDir(Context context) {
    try {
      DcContext dc = DcHelper.getContext(context);
      if (dc != null) {
        String blobdir = dc.getBlobdir();
        if (blobdir != null && !blobdir.isEmpty()) {
          File dir = new File(blobdir).getParentFile();
          if (dir != null && dir.isDirectory()) return dir;
        }
      }
    } catch (Exception ignored) {
    }
    return new File(context.getFilesDir(), "DeltaX");
  }

  public boolean isInitialised() {
    return initialised;
  }

  public void init() {
    if (initialised) return;
    extensionDir.mkdirs();
    pluginsDir.mkdirs();
    loadedPlugins = evaluator.loadPlugins();
    initialised = true;
    Log.i(
        TAG,
        "DeltaX initialised with " + loadedPlugins.size() + " plugin(s) for account " + accountId);
  }

  public void shutdown() {
    evaluator.shutdown();
  }

  public void reloadPlugins() {
    evaluator.shutdown();
    loadedPlugins = evaluator.loadPlugins();
  }

  public List<String> getLoadedPlugins() {
    List<String> names = new ArrayList<>();
    for (PluginInfo plugin : loadedPlugins) {
      names.add(plugin.getPackageName());
    }
    return names;
  }

  public List<String> getPluginList() {
    return evaluator.getPluginList();
  }

  public PluginInfo getPlugin(String nameOrPkg) {
    return evaluator.getPlugin(nameOrPkg);
  }

  public LuaEngine getLuaEngine() {
    return luaEngine;
  }

  public PluginLoader getPluginLoader() {
    return evaluator;
  }

  public PluginPackager getPluginPackager() {
    return pluginPackager;
  }

  public Context getContext() {
    return context;
  }

  public int getAccountId() {
    return accountId;
  }

  public File getExtensionDir() {
    return extensionDir;
  }

  public File getBaseDir() {
    return extensionDir;
  }

  public File getPluginsDir() {
    return pluginsDir;
  }

  public List<PluginInfo> getInstalledPlugins() {
    return pluginPackager.getInstalledPlugins();
  }

  /**
   * Lists the built-in plugins bundled under {@code assets/plugins/}. Each subdirectory is expected
   * to contain a {@code manifest.json} (name, version, author, main) and the referenced script
   * files. These are offered in the plugin import page and installed on demand.
   */
  public List<PluginInfo> getBuiltinPlugins() {
    List<PluginInfo> result = new ArrayList<>();
    builtinAssetDirs.clear();
    try {
      String[] dirs = context.getAssets().list("plugins");
      if (dirs == null) return result;
      ObjectMapper mapper = new ObjectMapper();
      for (String dir : dirs) {
        if (dir.startsWith(".")) continue;
        String manifestAsset = "plugins/" + dir + "/manifest.json";
        try (InputStream in = context.getAssets().open(manifestAsset)) {
          JsonNode root = mapper.readTree(in);
          String name = nodeText(root, "name");
          String version = nodeText(root, "version");
          String main = nodeText(root, "main");
          String author = nodeText(root, "author");
          if (name == null || version == null || main == null || author == null) continue;
          Manifest manifest = new Manifest();
          manifest.name = name;
          manifest.version = version;
          manifest.main = main;
          manifest.author = author;
          manifest.description = nodeText(root, "description");
          PluginInfo info = new PluginInfo(manifest, null);
          result.add(info);
          builtinAssetDirs.put(info.getPackageName(), "plugins/" + dir);
        } catch (Exception ignored) {
        }
      }
    } catch (Exception ignored) {
    }
    return result;
  }

  /** Installs a built-in plugin into the account (see {@link #getBuiltinPlugins()}). */
  public boolean installBuiltinPlugin(PluginInfo info) {
    String assetDir = builtinAssetDirs.get(info.getPackageName());
    if (assetDir == null) return false;
    File tmp = new File(context.getCacheDir(), "deltax_builtin_" + System.currentTimeMillis());
    tmp.mkdirs();
    try {
      copyAssetDir(assetDir, tmp);
    } catch (IOException e) {
      Log.w(TAG, "Failed to stage built-in plugin: " + e.getMessage());
      deleteRecursive(tmp);
      return false;
    }
    boolean ok = pluginPackager.install(tmp);
    deleteRecursive(tmp);
    if (ok) reloadPlugins();
    return ok;
  }

  private void copyAssetDir(String assetPath, File dest) throws IOException {
    String[] entries = context.getAssets().list(assetPath);
    if (entries == null) return;
    dest.mkdirs();
    for (String entry : entries) {
      String full = assetPath + "/" + entry;
      String[] sub = context.getAssets().list(full);
      if (sub != null && sub.length > 0) {
        copyAssetDir(full, new File(dest, entry));
      } else {
        try (InputStream in = context.getAssets().open(full)) {
          File out = new File(dest, entry);
          out.getParentFile().mkdirs();
          java.nio.file.Files.copy(
              in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private void deleteRecursive(File dir) {
    File[] files = dir.listFiles();
    if (files != null) {
      for (File f : files) {
        if (f.isDirectory()) deleteRecursive(f);
        else f.delete();
      }
    }
    dir.delete();
  }

  private static String nodeText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && !value.isNull() ? value.asText() : null;
  }

  public int installPluginFromZip(File zip) {
    int n = pluginPackager.installFromZip(zip);
    reloadPlugins();
    return n;
  }

  public boolean uninstallPlugin(String packageName) {
    boolean ok = pluginPackager.uninstall(packageName);
    reloadPlugins();
    return ok;
  }

  public boolean isBackupPackage(File zip) {
    return PluginPackager.isBackupZip(zip);
  }

  /** Restores a backup package into this account's extension directory. */
  public boolean restoreBackupFromZip(File zip) {
    boolean ok = pluginPackager.restoreBackup(zip);
    pluginPackager.reload();
    reloadPlugins();
    return ok;
  }

  public void setPluginEnabled(String packageName, boolean enabled) {
    if (enabled) {
      evaluator.enablePlugin(packageName);
    } else {
      evaluator.disablePlugin(packageName);
    }
    reloadPlugins();
  }

  public boolean isPluginDisabled(String packageName) {
    return evaluator.isDisabled(packageName);
  }

  /** Returns true when the plugin (by package name) registered an interactive page via onOpen. */
  public boolean hasInteractivePage(String packageName) {
    PluginInfo plugin = evaluator.getPlugin(packageName);
    if (plugin == null || plugin.globals == null) return false;
    LuaValue onOpen = plugin.globals.get("onOpen");
    return onOpen.isfunction();
  }
}
