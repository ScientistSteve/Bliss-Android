package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.minecraft.MinecraftServerPinger;
import net.kdt.pojavlaunch.minecraft.ServerListManager;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainMenuFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private boolean isQuickActionsListMode = false;
    private boolean isServersGridMode = false;
    private LinearLayout mServersContainer;
    private TextView mServersEmptyText;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mServerExecutor = Executors.newSingleThreadExecutor();
    private java.util.Map<ServerListManager.ServerEntry, MinecraftServerPinger.PingResult> serverPingState = new java.util.WeakHashMap<>();
    private ServerListManager.ServerList mServerList;
    private File mLoadedProfileDirectory;
    private volatile int mServersLoadGeneration = 0;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View mCustomControlButton = view.findViewById(R.id.custom_control_button);
        View mInstallJarButton = view.findViewById(R.id.install_jar_button);
        View mShareLogsButton = view.findViewById(R.id.share_logs_button);
        View mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        View mPlayButton = view.findViewById(R.id.play_button);
        LinearLayout quickActionsContainer = view.findViewById(R.id.quick_actions_container);
        ImageButton quickActionsToggleButton = view.findViewById(R.id.quick_actions_toggle_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        mServersContainer = view.findViewById(R.id.servers_container);
        mServersEmptyText = view.findViewById(R.id.servers_empty_text);
        ImageButton serversAddButton = view.findViewById(R.id.servers_add_button);
        ImageButton serversToggleButton = view.findViewById(R.id.servers_toggle_button);

        if (quickActionsContainer != null) {
            updateQuickActionsLayout(quickActionsContainer);
        }

        if (quickActionsToggleButton != null && quickActionsContainer != null) {
            quickActionsToggleButton.setOnClickListener(v -> {
                isQuickActionsListMode = !isQuickActionsListMode;
                updateQuickActionsLayout(quickActionsContainer);
            });
        }

        if (serversAddButton != null) serversAddButton.setOnClickListener(v -> showServerDialog(null));
        if (serversToggleButton != null) {
            serversToggleButton.setOnClickListener(v -> {
                isServersGridMode = !isServersGridMode;
                renderServers();
            });
        }
        loadServersForCurrentProfile();

        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        mInstallJarButton.setOnLongClickListener(v -> {
            runInstallerWithConfirmation(true);
            return true;
        });
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> {
            if (Tools.hasMods("sodium") && !(LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false))) {
                AlertDialog sodiumWarningDialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.sodium_warning_title)
                        .setMessage(R.string.sodium_warning_message)
                        .setNeutralButton(R.string.delete_sodium, (d,w)-> {
                            Tools.deleteSodiumMods();
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                        })
                        .create();
                sodiumWarningDialog.show();
            } else ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);


        });

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> openPath(v.getContext(), getCurrentProfileDirectory(), false));

        applyPressAnimation(mCustomControlButton);
        applyPressAnimation(mInstallJarButton);
        applyPressAnimation(mShareLogsButton);
        applyPressAnimation(mOpenDirectoryButton);
        applyPressAnimation(mEditProfileButton);
        applyPressAnimation(mPlayButton);
        applyPressAnimation(quickActionsToggleButton);
        applyPressAnimation(serversAddButton);
        applyPressAnimation(serversToggleButton);
    }

    private void updateQuickActionsLayout(LinearLayout quickActionsContainer) {
        quickActionsContainer.setOrientation(isQuickActionsListMode ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        for (int i = 0; i < quickActionsContainer.getChildCount(); i++) {
            View child = quickActionsContainer.getChildAt(i);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
            params.width = isQuickActionsListMode ? LinearLayout.LayoutParams.MATCH_PARENT : 0;
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            params.weight = isQuickActionsListMode ? 0f : 1f;
            child.setLayoutParams(params);

            TextView label = child.findViewById(R.id.quick_action_label);
            if (label != null) {
                label.setVisibility(isQuickActionsListMode ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void applyPressAnimation(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            }
            return false;
        });
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        mVersionSpinner.reloadProfiles();
        loadServersForCurrentProfile();
    }

    @Override
    public void onStart() {
        super.onStart();
        LauncherPreferences.DEFAULT_PREF.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        LauncherPreferences.DEFAULT_PREF.unregisterOnSharedPreferenceChangeListener(this);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        mServerExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (LauncherPreferences.PREF_KEY_CURRENT_PROFILE.equals(key)) loadServersForCurrentProfile();
    }

    private void loadServersForCurrentProfile() {
        File profileDirectory = getCurrentProfileDirectory();
        if (profileDirectory.equals(mLoadedProfileDirectory) && mServerList != null) return;
        mLoadedProfileDirectory = profileDirectory;
        int generation = ++mServersLoadGeneration;
        if (mServersEmptyText != null) {
            mServersEmptyText.setVisibility(View.VISIBLE);
            mServersEmptyText.setText(R.string.main_servers_loading);
        }
        if (mServersContainer != null) mServersContainer.removeAllViews();
        mServerExecutor.execute(() -> {
            ServerListManager.ServerList loaded = ServerListManager.load(profileDirectory);
            java.util.Map<ServerListManager.ServerEntry, MinecraftServerPinger.PingResult> pingState = new java.util.WeakHashMap<>();
            for (ServerListManager.ServerEntry server : loaded.servers) {
                if (generation != mServersLoadGeneration) return;
                MinecraftServerPinger.PingResult ping = MinecraftServerPinger.ping(server.address);
                if (ping.favicon != null) server.icon = ping.favicon;
                pingState.put(server, ping);
            }
            mMainHandler.post(() -> {
                if (!isAdded() || generation != mServersLoadGeneration) return;
                serverPingState = pingState;
                mServerList = loaded;
                renderServers();
            });
        });
    }

    private void renderServers() {
        if (mServersContainer == null || mServersEmptyText == null) return;
        mServersContainer.removeAllViews();
        List<ServerListManager.ServerEntry> servers = mServerList == null ? new ArrayList<>() : mServerList.servers;
        if (servers.isEmpty()) {
            mServersEmptyText.setVisibility(View.VISIBLE);
            mServersEmptyText.setText(R.string.main_servers_empty);
            return;
        }
        mServersEmptyText.setVisibility(View.GONE);
        mServersContainer.setOrientation(LinearLayout.VERTICAL);
        if (isServersGridMode) renderServerGrid(servers);
        else for (ServerListManager.ServerEntry server : servers) mServersContainer.addView(createServerListItem(server));
    }

    private void renderServerGrid(List<ServerListManager.ServerEntry> servers) {
        LinearLayout row = null;
        for (int i = 0; i < servers.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                mServersContainer.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            FrameLayout cell = new FrameLayout(requireContext());
            cell.setPadding(0, 8, 0, 8);
            ImageView icon = createServerIcon(servers.get(i), 32);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(32), dp(32));
            iconParams.gravity = android.view.Gravity.CENTER;
            cell.addView(icon, iconParams);
            final ServerListManager.ServerEntry server = servers.get(i);
            cell.setOnClickListener(v -> showServerDialog(server));
            row.addView(cell, new LinearLayout.LayoutParams(0, dp(60), 1f));
        }
    }

    private View createServerListItem(ServerListManager.ServerEntry server) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        item.setPadding(0, 10, 0, 10);

        item.addView(createServerIcon(server, 40), new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout textColumn = new LinearLayout(requireContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(dp(12), 0, dp(8), 0);
        TextView name = new TextView(requireContext());
        name.setText(server.name == null || server.name.isEmpty() ? server.address : server.name);
        name.setTextColor(0xffffffff);
        name.setTextSize(16);
        TextView details = new TextView(requireContext());
        MinecraftServerPinger.PingResult ping = serverPingState.get(server);
        details.setText(getServerDetails(ping));
        details.setTextColor(ping != null && ping.online ? 0xff8ee88e : 0xffff8a80);
        details.setTextSize(13);
        textColumn.addView(name);
        textColumn.addView(details);
        item.addView(textColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView edit = new TextView(requireContext());
        edit.setText(R.string.main_servers_edit);
        edit.setTextColor(0xffffffff);
        edit.setPadding(dp(12), dp(8), dp(12), dp(8));
        edit.setBackgroundResource(android.R.drawable.btn_default);
        edit.setOnClickListener(v -> showServerDialog(server));
        item.addView(edit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return item;
    }

    private String getServerDetails(MinecraftServerPinger.PingResult ping) {
        if (ping == null) return getString(R.string.main_servers_loading);
        if (!ping.online) return getString(R.string.main_servers_offline) + " • " + getString(R.string.main_servers_ping_unknown);
        String players = ping.playersOnline >= 0 && ping.playersMax >= 0 ? ping.playersOnline + "/" + ping.playersMax : getString(R.string.main_servers_players_unknown);
        return getString(R.string.main_servers_online) + " • " + players + " • " + ping.latencyMs + " ms";
    }

    private ImageView createServerIcon(ServerListManager.ServerEntry server, int sizeDp) {
        ImageView icon = new ImageView(requireContext());
        icon.setMaxWidth(dp(sizeDp));
        icon.setMaxHeight(dp(sizeDp));
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackgroundColor(0xff2d2d2d);
        Bitmap bitmap = decodeFavicon(server.icon);
        if (bitmap != null) icon.setImageBitmap(bitmap);
        else icon.setImageResource(android.R.drawable.sym_def_app_icon);
        icon.setContentDescription(server.name);
        return icon;
    }

    private Bitmap decodeFavicon(String favicon) {
        if (favicon == null) return null;
        try {
            int comma = favicon.indexOf(',');
            String encoded = comma >= 0 ? favicon.substring(comma + 1) : favicon;
            byte[] data = Base64.decode(encoded, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void showServerDialog(ServerListManager.ServerEntry existingServer) {
        if (!isAdded()) return;
        LinearLayout fields = new LinearLayout(requireContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), dp(8), dp(20), 0);
        EditText nameField = new EditText(requireContext());
        nameField.setHint(R.string.main_servers_name_hint);
        nameField.setSingleLine(true);
        nameField.setInputType(InputType.TYPE_CLASS_TEXT);
        EditText addressField = new EditText(requireContext());
        addressField.setHint(R.string.main_servers_address_hint);
        addressField.setSingleLine(true);
        addressField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (existingServer != null) {
            nameField.setText(existingServer.name);
            addressField.setText(existingServer.address);
        }
        fields.addView(nameField);
        fields.addView(addressField);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(existingServer == null ? R.string.main_servers_add : R.string.main_servers_edit)
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> saveServerFromDialog(dialog, existingServer, nameField, addressField)));
        dialog.show();
    }

    private void saveServerFromDialog(Dialog dialog, ServerListManager.ServerEntry existingServer, EditText nameField, EditText addressField) {
        String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
        String address = addressField.getText() == null ? "" : addressField.getText().toString().trim();
        if (name.isEmpty()) name = address;
        if (address.isEmpty()) {
            addressField.setError(getString(R.string.main_servers_address_hint));
            return;
        }
        final String finalName = name;
        final String finalAddress = address;
        File profileDirectory = getCurrentProfileDirectory();
        mServerExecutor.execute(() -> {
            ServerListManager.ServerList list = ServerListManager.load(profileDirectory);
            MinecraftServerPinger.PingResult ping = MinecraftServerPinger.ping(finalAddress);
            if (existingServer == null && !ping.online) {
                mMainHandler.post(() -> { if (isAdded()) Toast.makeText(requireContext(), R.string.main_servers_add_failed, Toast.LENGTH_LONG).show(); });
                return;
            }
            int index = existingServer == null || mServerList == null ? -1 : mServerList.servers.indexOf(existingServer);
            ServerListManager.ServerEntry target;
            if (index >= 0 && index < list.servers.size()) target = list.servers.get(index);
            else {
                target = new ServerListManager.ServerEntry(finalName, finalAddress);
                list.servers.add(target);
            }
            target.name = finalName;
            target.address = finalAddress;
            if (ping.favicon != null) target.icon = ping.favicon;
            boolean saved = ServerListManager.save(list);
            mMainHandler.post(() -> {
                if (!isAdded()) return;
                if (saved) {
                    dialog.dismiss();
                    mLoadedProfileDirectory = null;
                    loadServersForCurrentProfile();
                    Toast.makeText(requireContext(), R.string.main_servers_saved, Toast.LENGTH_SHORT).show();
                } else Toast.makeText(requireContext(), R.string.main_servers_save_failed, Toast.LENGTH_LONG).show();
            });
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
