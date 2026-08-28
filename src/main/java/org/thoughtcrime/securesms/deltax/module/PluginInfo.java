package org.thoughtcrime.securesms.deltax.module;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class PluginInfo {
  public final Manifest manifest;
  public final File pluginDir;
  public String builtinAssetDir = null;
  public boolean enabled = true;
  public boolean loaded = false;
  public LuaValue globals = null;
  public LuaTable exportedFunctions = null;
  public LuaValue onEnableFunc = null;
  public LuaValue onDisableFunc = null;

  public PluginInfo(Manifest manifest, File pluginDir) {
    this.manifest = manifest;
    this.pluginDir = pluginDir;
  }

  public String getPackageName() {
    return manifest.getPackageName();
  }

  public File getScriptsDir() {
    return new File(pluginDir, "scripts");
  }

  public File getResourcesDir() {
    return new File(pluginDir, "resources");
  }

  /** Returns true when an optional {@code logo.png} icon is bundled with this plugin. */
  public boolean hasLogo() {
    if (pluginDir != null) {
      return new File(pluginDir, "logo.png").exists();
    }
    return builtinAssetDir != null;
  }

  /** Decodes the optional {@code logo.png} icon, or null when absent. */
  public Bitmap getLogoBitmap(Context context) {
    if (pluginDir != null) {
      File file = new File(pluginDir, "logo.png");
      if (file.exists()) return BitmapFactory.decodeFile(file.getAbsolutePath());
    } else if (builtinAssetDir != null) {
      try (InputStream in = context.getAssets().open(builtinAssetDir + "/logo.png")) {
        return BitmapFactory.decodeStream(in);
      } catch (IOException ignored) {
        return null;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return getPackageName();
  }
}
