package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import com.b44t.messenger.DcContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.ViewUtil;

/** Manages the permission groups of a chatroom, see the chatroom documentation. */
public class ChatroomPermissionsActivity extends PassphraseRequiredActionBarActivity {

  public static final String CHAT_ID_EXTRA = "chat_id";

  private static final int OWNER_GROUP = 1;
  private static final int EVERYONE_GROUP = 2;

  private static final String[] PERMISSION_KEYS = {
    "add_contact_to_chat",
    "remove_contact_from_chat",
    "set_chat_name",
    "set_chat_profile_image",
    "set_chat_description",
    "set_pinned_message_state",
    "set_chat_ephemeral_timer",
    "manage_permission_group",
    "assign_permission_group"
  };

  private static final int[] PERMISSION_TEXTS = {
    R.string.chatroom_perm_add_contact_to_chat,
    R.string.chatroom_perm_remove_contact_from_chat,
    R.string.chatroom_perm_set_chat_name,
    R.string.chatroom_perm_set_chat_profile_image,
    R.string.chatroom_perm_set_chat_description,
    R.string.chatroom_perm_set_pinned_message_state,
    R.string.chatroom_perm_set_chat_ephemeral_timer,
    R.string.chatroom_perm_manage_permission_group,
    R.string.chatroom_perm_assign_permission_group
  };

  private DcContext dcContext;
  private int chatId;
  private int[] groupIds = new int[0];
  private ListView listView;
  private Toolbar toolbar;

  @Override
  protected void onPreCreate() {
    dynamicTheme = new DynamicNoActionBarTheme();
    super.onPreCreate();
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    dcContext = DcHelper.getContext(this);
    chatId = getIntent().getIntExtra(CHAT_ID_EXTRA, 0);
    setContentView(R.layout.chatroom_permissions_activity);
    this.toolbar = ViewUtil.findById(this, R.id.toolbar);
    setSupportActionBar(this.toolbar);
    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar != null) {
      supportActionBar.setDisplayHomeAsUpEnabled(true);
      supportActionBar.setTitle(R.string.permission_groups);
    }

    listView = ViewUtil.findById(this, R.id.permission_group_list);
    listView.setOnItemClickListener((parent, view, position, id) -> showGroupOptions(position));
    org.thoughtcrime.securesms.components.registration.PulsingFloatingActionButton fab =
        ViewUtil.findById(this, R.id.fab);
    ViewUtil.applyWindowInsetsAsMargin(fab);
    fab.setOnClickListener(v -> showGroupDialog(0));
  }

  @Override
  public void onResume() {
    super.onResume();
    reload();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.clear();
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);
    int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      finish();
      return true;
    }

    return false;
  }

  private void reload() {
    groupIds = dcContext.getPermissionGroupIds(chatId);
    String[] names = new String[groupIds.length];
    for (int i = 0; i < groupIds.length; i++) {
      names[i] = dcContext.getPermissionGroupName(chatId, groupIds[i]);
    }
    listView.setAdapter(new ArrayAdapter<>(this, R.layout.permission_group_list_item, names));
  }

  private void showGroupOptions(int position) {
    if (position < 0 || position >= groupIds.length) {
      return;
    }
    int groupId = groupIds[position];
    String name = dcContext.getPermissionGroupName(chatId, groupId);
    boolean builtin = groupId == OWNER_GROUP || groupId == EVERYONE_GROUP;
    boolean isEveryone = groupId == EVERYONE_GROUP;

    List<String> labels = new ArrayList<>();
    List<Integer> actions = new ArrayList<>();
    labels.add(getString(R.string.edit_permissions));
    actions.add(0);
    if (!isEveryone) {
      labels.add(getString(R.string.manage_members));
      actions.add(1);
    }
    if (!builtin) {
      labels.add(getString(R.string.delete));
      actions.add(2);
    }

    new AlertDialog.Builder(this)
        .setTitle(name)
        .setItems(
            labels.toArray(new String[0]),
            (dialog, which) -> {
              int action = actions.get(which);
              if (action == 0) {
                showGroupDialog(groupId);
              } else if (action == 1) {
                openMembers(groupId);
              } else {
                confirmDelete(groupId, name);
              }
            })
        .show();
  }

  private void showGroupDialog(int groupId) {
    View view = getLayoutInflater().inflate(R.layout.chatroom_permission_group_dialog, null);
    EditText nameView = ViewUtil.findById(view, R.id.permission_group_name);
    LinearLayout permissionList = ViewUtil.findById(view, R.id.permission_list);

    String granted = "";
    if (groupId != 0) {
      nameView.setText(dcContext.getPermissionGroupName(chatId, groupId));
      granted = dcContext.getPermissionGroupPermissions(chatId, groupId);
    }
    List<String> grantedKeys = Arrays.asList(granted.split(","));

    CheckBox[] boxes = new CheckBox[PERMISSION_KEYS.length];
    for (int i = 0; i < PERMISSION_KEYS.length; i++) {
      CheckBox box = new CheckBox(this);
      box.setText(PERMISSION_TEXTS[i]);
      box.setChecked(grantedKeys.contains(PERMISSION_KEYS[i]));
      permissionList.addView(box);
      boxes[i] = box;
    }

    new AlertDialog.Builder(this)
        .setTitle(groupId == 0 ? R.string.new_permission_group : R.string.edit_permissions)
        .setView(view)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(
            R.string.ok,
            (dialog, which) -> saveGroup(groupId, nameView.getText().toString(), boxes))
        .show();
  }

  private void saveGroup(int groupId, String name, CheckBox[] boxes) {
    StringBuilder permissions = new StringBuilder();
    for (int i = 0; i < boxes.length; i++) {
      if (boxes[i].isChecked()) {
        if (permissions.length() > 0) {
          permissions.append(',');
        }
        permissions.append(PERMISSION_KEYS[i]);
      }
    }

    int result;
    if (groupId == 0) {
      result = dcContext.createPermissionGroup(chatId, name, permissions.toString());
    } else {
      result = dcContext.setPermissionGroup(chatId, groupId, name, permissions.toString());
    }
    if (result == 0) {
      Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
    }
    reload();
  }

  private void confirmDelete(int groupId, String name) {
    new AlertDialog.Builder(this)
        .setMessage(getString(R.string.ask_delete_permission_group, name))
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(
            R.string.delete,
            (dialog, which) -> {
              if (dcContext.deletePermissionGroup(chatId, groupId) == 0) {
                Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
              }
              reload();
            })
        .show();
  }

  private void openMembers(int groupId) {
    Intent intent = new Intent(this, PermissionGroupMembersActivity.class);
    intent.putExtra(PermissionGroupMembersActivity.CHAT_ID_EXTRA, chatId);
    intent.putExtra(PermissionGroupMembersActivity.GROUP_ID_EXTRA, groupId);
    startActivity(intent);
  }
}
