package org.thoughtcrime.securesms.deltax;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.deltax.module.Manifest;
import org.thoughtcrime.securesms.deltax.module.PluginInfo;
import org.thoughtcrime.securesms.deltax.module.PluginPackager;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Shows a plugin's details (icon, name, author, version, description and README) before it is
 * installed. When launched in install mode the user confirms or cancels each staged plugin; in view
 * mode it is a read-only details screen for an already installed plugin.
 */
public class PluginInstallActivity extends PassphraseRequiredActionBarActivity {

  public static final String EXTRA_DIRS = "deltax.install.dirs";
  public static final String EXTRA_VIEW_DIR = "deltax.install.view_dir";
  public static final String EXTRA_VIEW_MODE = "deltax.install.view_mode";

  private final List<File> dirs = new ArrayList<>();
  private boolean viewMode;
  private int index;
  private DeltaX deltaX;
  private ImageView icon;
  private TextView nameView;
  private TextView metaView;
  private TextView descView;
  private TextView readmeView;
  private Button cancelButton;
  private Button confirmButton;

  @Override
  protected void onPreCreate() {
    dynamicTheme = new DynamicNoActionBarTheme();
    super.onPreCreate();
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    setContentView(R.layout.plugin_install_activity);

    ViewUtil.applyWindowInsets(findViewById(R.id.install_root), true, false, true, false);
    ViewUtil.applyWindowInsetsAsMargin(
        findViewById(R.id.install_button_bar), false, false, false, true);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setDisplayShowTitleEnabled(true);
    }

    icon = findViewById(R.id.install_icon);
    nameView = findViewById(R.id.install_name);
    metaView = findViewById(R.id.install_meta);
    descView = findViewById(R.id.install_desc);
    readmeView = findViewById(R.id.install_readme);
    cancelButton = findViewById(R.id.install_cancel);
    confirmButton = findViewById(R.id.install_confirm);

    deltaX = DeltaX.getInstance(this);
    if (!deltaX.isInitialised()) {
      deltaX.init();
    }

    Intent intent = getIntent();
    viewMode = intent.getBooleanExtra(EXTRA_VIEW_MODE, false);
    if (intent.hasExtra(EXTRA_VIEW_DIR)) {
      dirs.add(new File(intent.getStringExtra(EXTRA_VIEW_DIR)));
    } else {
      ArrayList<String> list = intent.getStringArrayListExtra(EXTRA_DIRS);
      if (list != null) {
        for (String path : list) dirs.add(new File(path));
      }
    }

    if (dirs.isEmpty()) {
      finish();
      return;
    }

    cancelButton.setOnClickListener(v -> onCancel());
    confirmButton.setOnClickListener(v -> onConfirm());

    showCurrent();
  }

  private void showCurrent() {
    if (index >= dirs.size()) {
      finishFlow();
      return;
    }
    File dir = dirs.get(index);
    PluginPackager packager = deltaX.getPluginPackager();
    Manifest manifest = packager.parseManifest(new File(dir, "manifest.json"));
    if (manifest == null) {
      advance();
      return;
    }
    PluginInfo info = new PluginInfo(manifest, dir);
    nameView.setText(manifest.name);
    metaView.setText(
        getString(R.string.deltax_plugin_meta, manifest.author, "v" + manifest.version));
    descView.setText(manifest.description != null ? manifest.description : "");
    String readme = PluginInfo.readReadme(dir);
    readmeView.setText(readme != null ? readme : getString(R.string.deltax_plugin_no_readme));

    icon.setImageDrawable(PluginIcons.getIcon(this, info));

    if (viewMode) {
      if (getSupportActionBar() != null) getSupportActionBar().setTitle(manifest.name);
      confirmButton.setText(android.R.string.ok);
      cancelButton.setVisibility(android.view.View.GONE);
    } else if (getSupportActionBar() != null) {
      getSupportActionBar().setTitle(getString(R.string.deltax_install_title, manifest.name));
    }
  }

  private void onConfirm() {
    if (!viewMode) {
      File dir = dirs.get(index);
      boolean ok = deltaX.getPluginPackager().install(dir);
      deleteRecursive(dir);
      if (!ok) {
        Toast.makeText(this, R.string.deltax_install_failed, Toast.LENGTH_SHORT).show();
      }
    }
    advance();
  }

  private void onCancel() {
    if (!viewMode) {
      deleteRecursive(dirs.get(index));
    }
    advance();
  }

  private void advance() {
    index++;
    if (index >= dirs.size()) {
      finishFlow();
    } else {
      showCurrent();
    }
  }

  private void finishFlow() {
    if (!viewMode) {
      deltaX.reloadPlugins();
      setResult(RESULT_OK);
    }
    finish();
  }

  private void deleteRecursive(File file) {
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) deleteRecursive(child);
    }
    file.delete();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onCancel();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
