/*
 * Copy of org.thoughtcrime.securesms.ContactSelectionActivity (the page opened by
 * the main page "add" button). The plugin import FAB opens this page, which looks
 * identical to the original but offers the "Import" row plus the built-in plugins
 * bundled under assets/plugins; the Import row triggers the external plugin import
 * flow while built-in plugins open an install preview page on click.
 */
package org.thoughtcrime.securesms.deltax;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterToolbar;
import org.thoughtcrime.securesms.deltax.module.PluginInfo;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.ViewUtil;

public class DeltaXImportActivity extends PassphraseRequiredActionBarActivity
    implements DeltaXImportListFragment.Listener {

  private ContactFilterToolbar toolbar;
  private DeltaXImportListFragment contactsFragment;
  private DeltaX deltaX;
  private ActivityResultLauncher<Intent> pickerLauncher;

  @Override
  protected void onPreCreate() {
    dynamicTheme = new DynamicNoActionBarTheme();
    super.onPreCreate();
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    setContentView(R.layout.deltax_import_activity);

    initializeToolbar();
    initializeResources();
    initializeSearch();

    deltaX = DeltaX.getInstance(this);
    if (!deltaX.isInitialised()) {
      deltaX.init();
    }

    pickerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK
                  && result.getData() != null
                  && result.getData().getData() != null) {
                installFromUri(result.getData().getData());
              }
            });
  }

  private void initializeToolbar() {
    this.toolbar = ViewUtil.findById(this, R.id.toolbar);
    setSupportActionBar(toolbar);

    assert getSupportActionBar() != null;
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
    getSupportActionBar().setIcon(null);
    getSupportActionBar().setLogo(null);
  }

  private void initializeResources() {
    contactsFragment =
        (DeltaXImportListFragment)
            getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    contactsFragment.setListener(this);
  }

  private void initializeSearch() {
    toolbar.setOnFilterChangedListener(filter -> contactsFragment.setQueryFilter(filter));
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      getOnBackPressedDispatcher().onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onImportClicked() {
    if (!deltaX.isImportPluginEnabled()) {
      Toast.makeText(this, R.string.deltax_import_disabled, Toast.LENGTH_SHORT).show();
      return;
    }
    openFilePicker();
  }

  @Override
  public void onPluginClicked(PluginInfo plugin) {
    DeltaX deltaX = DeltaX.getInstance(this);
    if (!deltaX.isInitialised()) {
      deltaX.init();
    }
    File staged = deltaX.stageBuiltinPlugin(plugin);
    if (staged == null) {
      Toast.makeText(this, R.string.deltax_install_failed, Toast.LENGTH_SHORT).show();
      return;
    }
    ArrayList<String> dirs = new ArrayList<>();
    dirs.add(staged.getAbsolutePath());
    Intent intent = new Intent(this, PluginInstallActivity.class);
    intent.putStringArrayListExtra(PluginInstallActivity.EXTRA_DIRS, dirs);
    intent.putExtra(PluginInstallActivity.EXTRA_VIEW_MODE, false);
    startActivity(intent);
    finish();
  }

  private void openFilePicker() {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("application/zip");
    try {
      pickerLauncher.launch(Intent.createChooser(intent, getString(R.string.deltax_install)));
    } catch (android.content.ActivityNotFoundException e) {
      Toast.makeText(this, R.string.deltax_install_failed, Toast.LENGTH_SHORT).show();
    }
  }

  private void installFromUri(Uri uri) {
    File tmp = new File(getCacheDir(), "deltax_install_" + System.currentTimeMillis() + ".zip");
    try (InputStream in = getContentResolver().openInputStream(uri);
        OutputStream out = new FileOutputStream(tmp)) {
      byte[] buf = new byte[8192];
      int len;
      while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
    } catch (Exception e) {
      if (tmp.exists()) tmp.delete();
      Toast.makeText(this, R.string.deltax_install_failed, Toast.LENGTH_SHORT).show();
      return;
    }
    if (deltaX.isBackupPackage(tmp)) {
      boolean ok = deltaX.restoreBackupFromZip(tmp);
      if (tmp.exists()) tmp.delete();
      if (ok) {
        Toast.makeText(this, R.string.deltax_restore_success, Toast.LENGTH_SHORT).show();
      } else {
        Toast.makeText(this, R.string.deltax_restore_failed, Toast.LENGTH_SHORT).show();
      }
    } else {
      List<File> staged = deltaX.getPluginPackager().extractPluginDirectories(tmp);
      if (tmp.exists()) tmp.delete();
      if (staged.isEmpty()) {
        Toast.makeText(this, R.string.deltax_install_failed, Toast.LENGTH_SHORT).show();
      } else {
        ArrayList<String> dirs = new ArrayList<>();
        for (File dir : staged) dirs.add(dir.getAbsolutePath());
        Intent intent = new Intent(this, PluginInstallActivity.class);
        intent.putStringArrayListExtra(PluginInstallActivity.EXTRA_DIRS, dirs);
        intent.putExtra(PluginInstallActivity.EXTRA_VIEW_MODE, false);
        startActivity(intent);
      }
    }
    finish();
  }
}
