package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.ai.AiAssistantConfig;
import net.kdt.pojavlaunch.ai.AiChatFragment;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainMenuFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private boolean isQuickActionsListMode = false;
    private boolean isServersGridMode = false;
    private LinearLayout mServersContainer;
    private TextView mServersEmptyText;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mServerExecutor = Executors.newCachedThreadPool();
    private Map<ServerListManager.ServerEntry, MinecraftServerPinger.PingResult> serverPingState = new HashMap<>();
    private ServerListManager.ServerList mServerList;
    private File mLoadedProfileDirectory;
    private volatile int mServersLoadGeneration = 0;
    private volatile boolean mServerRefreshInFlight = false;
    private final Map<ServerListManager.ServerEntry, ServerStatusViews> mServerStatusViews = new HashMap<>();
    private final Runnable mServerRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshServerStatuses();
            mMainHandler.postDelayed(this, SERVER_REFRESH_INTERVAL_MS);
        }
    };

    private static final int QUICK_ACTION_ITEM_HEIGHT_DP = 72;
    private static final int QUICK_ACTION_ICON_BUTTON_DP = 60;
    private static final int QUICK_ACTION_ICON_SIZE_DP = 32;
    private static final int QUICK_ACTION_ITEM_VERTICAL_PADDING_DP = 6;
    private static final int QUICK_ACTION_LABEL_MARGIN_START_DP = 16;
    private static final int SERVER_EDIT_BUTTON_DP = 40;
    private static final int SERVER_REFRESH_INTERVAL_MS = 300;
    private static final int DIALOG_CORNER_RADIUS_DP = 12;
    private static final String STATE_SERVERS_GRID_MODE = "serversGridMode";

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
        ImageButton aiAssistantFab = view.findViewById(R.id.ai_assistant_fab);

        if (savedInstanceState != null) {
            isServersGridMode = savedInstanceState.getBoolean(STATE_SERVERS_GRID_MODE, isServersGridMode);
        }

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

        if (aiAssistantFab != null) aiAssistantFab.setOnClickListener(v -> openAiAssistantOrPrompt());

        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        mInstallJarButton.setOnLongClickListener(v -> {
            runInstallerWithConfirmation(true);
            return true;
        });
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

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
        applyPressAnimation(aiAssistantFab);
    }

    private void openAiAssistantOrPrompt() {
        if (!AiAssistantConfig.hasApiKey(requireContext())) {
            Snackbar.make(requireView(), "Set your API key in Settings → Miscellaneous", Snackbar.LENGTH_LONG).show();
            return;
        }
        Tools.swapFragment(requireActivity(), AiChatFragment.class, AiChatFragment.TAG, null);
    }

    private void updateQuickActionsLayout(LinearLayout quickActionsContainer) {
        quickActionsContainer.setOrientation(isQuickActionsListMode ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        quickActionsContainer.setPadding(0, 0, 0, 0);
        for (int i = 0; i < quickActionsContainer.getChildCount(); i++) {
            View child = quickActionsContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                ((LinearLayout) child).setGravity(isQuickActionsListMode ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            }
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
        loadServersForCurrentProfile(true);
        startServerRefreshCycle();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SERVERS_GRID_MODE, isServersGridMode);
    }

    @Override
    public void onStart() {
        super.onStart();
        LauncherPreferences.DEFAULT_PREF.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        LauncherPreferences.DEFAULT_PREF.unregisterOnSharedPreferenceChangeListener(this);
        stopServerRefreshCycle();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        stopServerRefreshCycle();
        mServerExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (LauncherPreferences.PREF_KEY_CURRENT_PROFILE.equals(key)) loadServersForCurrentProfile();
    }

    private void loadServersForCurrentProfile() {
        loadServersForCurrentProfile(false);
    }

    private void loadServersForCurrentProfile(boolean forceReload) {
        File profileDirectory = getCurrentProfileDirectory();
        if (!forceReload && profileDirectory.equals(mLoadedProfileDirectory) && mServerList != null) return;
        mLoadedProfileDirectory = profileDirectory;
        int generation = ++mServersLoadGeneration;
        if (mServersEmptyText != null) {
            mServersEmptyText.setVisibility(View.VISIBLE);
            mServersEmptyText.setText(R.string.main_servers_loading);
        }
        if (mServersContainer != null) mServersContainer.removeAllViews();
        mServerStatusViews.clear();
        serverPingState = new HashMap<>();
        mServerExecutor.execute(() -> {
            ServerListManager.ServerList loaded = ServerListManager.load(profileDirectory);
            mMainHandler.post(() -> {
                if (!isAdded() || generation != mServersLoadGeneration) return;
                mServerList = loaded;
                renderServers();
                startServerRefreshCycle();
            });
        });
    }

    private void startServerRefreshCycle() {
        stopServerRefreshCycle();
        mMainHandler.post(mServerRefreshRunnable);
    }

    private void stopServerRefreshCycle() {
        mMainHandler.removeCallbacks(mServerRefreshRunnable);
    }

    private void refreshServerStatuses() {
        if (!isAdded() || mServerRefreshInFlight || mServerList == null || mServerList.servers.isEmpty()) return;
        int generation = mServersLoadGeneration;
        List<ServerListManager.ServerEntry> snapshot = new ArrayList<>(mServerList.servers);
        if (snapshot.isEmpty()) return;
        mServerRefreshInFlight = true;
        AtomicInteger pendingPings = new AtomicInteger(snapshot.size());
        for (ServerListManager.ServerEntry server : snapshot) {
            mServerExecutor.execute(() -> {
                try {
                    if (server == null || generation != mServersLoadGeneration || Thread.currentThread().isInterrupted()) return;
                    MinecraftServerPinger.PingResult ping = MinecraftServerPinger.ping(server.address);
                    mMainHandler.post(() -> {
                        if (!isAdded() || generation != mServersLoadGeneration) return;
                        if (ping != null && ping.favicon != null) server.icon = ping.favicon;
                        serverPingState.put(server, ping);
                        updateServerStatusView(server);
                    });
                } finally {
                    if (pendingPings.decrementAndGet() == 0) mServerRefreshInFlight = false;
                }
            });
        }
    }

    private void renderServers() {
        if (mServersContainer == null || mServersEmptyText == null) return;
        mServersContainer.removeAllViews();
        mServerStatusViews.clear();
        List<ServerListManager.ServerEntry> servers = mServerList == null ? new ArrayList<>() : mServerList.servers;
        if (servers.isEmpty()) {
            mServersEmptyText.setVisibility(View.VISIBLE);
            mServersEmptyText.setText(R.string.main_servers_empty);
            return;
        }
        mServersEmptyText.setVisibility(View.GONE);
        mServersContainer.setOrientation(LinearLayout.VERTICAL);
        if (isServersGridMode) renderServerGrid(servers);
        else for (ServerListManager.ServerEntry server : servers) {
            if (server != null) mServersContainer.addView(createServerListItem(server));
        }
    }

    private void renderServerGrid(List<ServerListManager.ServerEntry> servers) {
        LinearLayout row = null;
        for (int i = 0; i < servers.size(); i++) {
            ServerListManager.ServerEntry server = servers.get(i);
            if (server == null) continue;
            if (row == null || row.getChildCount() == 4) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.START);
                mServersContainer.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(QUICK_ACTION_ITEM_HEIGHT_DP)));
            }
            FrameLayout cell = new FrameLayout(requireContext());
            cell.setPadding(0, dp(QUICK_ACTION_ITEM_VERTICAL_PADDING_DP), 0, dp(QUICK_ACTION_ITEM_VERTICAL_PADDING_DP));
            cell.addView(createServerIconButton(server), new FrameLayout.LayoutParams(dp(QUICK_ACTION_ICON_BUTTON_DP), dp(QUICK_ACTION_ICON_BUTTON_DP), Gravity.CENTER));
            final ServerListManager.ServerEntry selectedServer = server;
            cell.setOnClickListener(v -> showServerDialog(selectedServer));
            cell.setOnLongClickListener(v -> {
                showDeleteServerDialog(selectedServer);
                return true;
            });
            row.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private View createServerListItem(ServerListManager.ServerEntry server) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinimumHeight(dp(QUICK_ACTION_ITEM_HEIGHT_DP));
        item.setPadding(0, dp(QUICK_ACTION_ITEM_VERTICAL_PADDING_DP), 0, dp(QUICK_ACTION_ITEM_VERTICAL_PADDING_DP));

        item.addView(createServerIconButton(server), new LinearLayout.LayoutParams(dp(QUICK_ACTION_ICON_BUTTON_DP), dp(QUICK_ACTION_ICON_BUTTON_DP)));

        LinearLayout textColumn = new LinearLayout(requireContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(dp(QUICK_ACTION_LABEL_MARGIN_START_DP), 0, dp(8), 0);
        TextView name = new TextView(requireContext());
        String serverName = server == null ? "" : server.name;
        String serverAddress = server == null ? "" : server.address;
        name.setText(serverName == null || serverName.isEmpty() ? serverAddress : serverName);
        name.setTextColor(0xffffffff);
        name.setTextSize(16);
        TextView details = new TextView(requireContext());
        MinecraftServerPinger.PingResult ping = server == null ? null : serverPingState.get(server);
        details.setText(getServerDetails(ping));
        details.setTextColor(getServerDetailsColor(ping));
        details.setTextSize(13);
        textColumn.addView(name);
        textColumn.addView(details);
        ServerStatusViews statusViews = mServerStatusViews.get(server);
        if (statusViews == null) mServerStatusViews.put(server, new ServerStatusViews(details, null));
        else statusViews.details = details;
        item.addView(textColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton edit = new ImageButton(requireContext());
        edit.setImageResource(R.drawable.ic_edit_profile);
        edit.setColorFilter(getColorCompat(R.color.secondary_text));
        edit.setBackgroundColor(Color.TRANSPARENT);
        edit.setPadding(dp(8), dp(8), dp(8), dp(8));
        edit.setScaleType(ImageView.ScaleType.CENTER);
        edit.setContentDescription(getString(R.string.main_servers_edit));
        edit.setOnClickListener(v -> showServerDialog(server));
        item.addView(edit, new LinearLayout.LayoutParams(dp(SERVER_EDIT_BUTTON_DP), dp(SERVER_EDIT_BUTTON_DP)));
        item.setOnClickListener(v -> showServerDialog(server));
        item.setOnLongClickListener(v -> {
            showDeleteServerDialog(server);
            return true;
        });
        return item;
    }

    private String getServerDetails(MinecraftServerPinger.PingResult ping) {
        if (ping == null) return getString(R.string.main_servers_loading);
        if (!ping.online) return getString(R.string.main_servers_offline);
        String players = ping.playersOnline >= 0 && ping.playersMax >= 0 ? ping.playersOnline + "/" + ping.playersMax : getString(R.string.main_servers_players_unknown);
        return getString(R.string.main_servers_online) + " • " + players + " • " + ping.latencyMs + " ms";
    }

    private int getServerDetailsColor(MinecraftServerPinger.PingResult ping) {
        if (ping == null) return 0xffbdbdbd;
        return ping.online ? 0xff8ee88e : 0xffff8a80;
    }

    private void updateServerStatusViews() {
        for (Map.Entry<ServerListManager.ServerEntry, ServerStatusViews> entry : mServerStatusViews.entrySet()) {
            ServerListManager.ServerEntry server = entry.getKey();
            ServerStatusViews views = entry.getValue();
            MinecraftServerPinger.PingResult ping = serverPingState.get(server);
            if (views.details != null) {
                views.details.setText(getServerDetails(ping));
                views.details.setTextColor(getServerDetailsColor(ping));
            }
            if (views.icon != null && ping != null && ping.favicon != null) {
                Bitmap bitmap = decodeFavicon(ping.favicon);
                if (bitmap != null) views.icon.setImageBitmap(bitmap);
            }
        }
    }

    private void updateServerStatusView(@NonNull ServerListManager.ServerEntry server) {
        ServerStatusViews views = mServerStatusViews.get(server);
        if (views == null) return;
        MinecraftServerPinger.PingResult ping = serverPingState.get(server);
        if (views.details != null) {
            views.details.setText(getServerDetails(ping));
            views.details.setTextColor(getServerDetailsColor(ping));
        }
        if (views.icon != null && ping != null && ping.favicon != null) {
            Bitmap bitmap = decodeFavicon(ping.favicon);
            if (bitmap != null) views.icon.setImageBitmap(bitmap);
        }
    }

    private FrameLayout createServerIconButton(ServerListManager.ServerEntry server) {
        FrameLayout iconButton = new FrameLayout(requireContext());
        iconButton.setBackgroundResource(R.drawable.bg_quick_action_icon_custom);
        iconButton.setClipToOutline(true);
        iconButton.setContentDescription(server == null ? null : server.name);

        ImageView icon = new ImageView(requireContext());
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = decodeFavicon(server == null ? null : server.icon);
        if (bitmap != null) icon.setImageBitmap(bitmap);
        else icon.setImageResource(android.R.drawable.sym_def_app_icon);
        icon.setContentDescription(server == null ? null : server.name);
        iconButton.addView(icon, new FrameLayout.LayoutParams(dp(QUICK_ACTION_ICON_SIZE_DP), dp(QUICK_ACTION_ICON_SIZE_DP), Gravity.CENTER));
        ServerStatusViews existingViews = mServerStatusViews.get(server);
        if (existingViews == null) mServerStatusViews.put(server, new ServerStatusViews(null, icon));
        else existingViews.icon = icon;
        return iconButton;
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
        int dialogPadding = dp(20);
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dialogPadding, dialogPadding, dialogPadding, dialogPadding);
        content.setBackground(createRoundedDrawable(0xff252525, DIALOG_CORNER_RADIUS_DP));

        TextView title = new TextView(requireContext());
        title.setText(existingServer == null ? R.string.main_servers_add : R.string.main_servers_edit);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.START);
        content.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText nameField = createServerDialogField(getString(R.string.main_servers_name_hint));
        nameField.setSingleLine(true);
        nameField.setInputType(InputType.TYPE_CLASS_TEXT);
        EditText addressField = createServerDialogField(getString(R.string.main_servers_address_hint));
        addressField.setSingleLine(true);
        addressField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (existingServer != null) {
            nameField.setText(existingServer.name);
            addressField.setText(existingServer.address);
        }
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        nameParams.setMargins(0, dp(18), 0, dp(12));
        content.addView(nameField, nameParams);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        addressParams.setMargins(0, 0, 0, dp(18));
        content.addView(addressField, addressParams);

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancelButton = createDialogActionButton(android.R.string.cancel, getColorCompat(R.color.secondary_text));
        TextView saveButton = createDialogActionButton(R.string.global_save, getColorCompat(R.color.minebutton_color));
        buttonRow.addView(cancelButton);
        buttonRow.addView(saveButton);
        content.addView(buttonRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(content)
                .create();
        cancelButton.setOnClickListener(v -> dialog.cancel());
        saveButton.setOnClickListener(v -> saveServerFromDialog(dialog, existingServer, nameField, addressField));
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        });
        dialog.show();
    }

    private EditText createServerDialogField(String hint) {
        EditText field = new EditText(requireContext());
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.WHITE);
        field.setTextSize(16);
        field.setHint(hint);
        field.setPadding(dp(14), 0, dp(14), 0);
        field.setSelectAllOnFocus(false);
        field.setBackground(createDialogFieldBackground(false));
        field.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(createDialogFieldBackground(hasFocus)));
        return field;
    }

    private TextView createDialogActionButton(int textRes, @ColorInt int textColor) {
        TextView button = new TextView(requireContext());
        button.setText(getString(textRes));
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private GradientDrawable createDialogFieldBackground(boolean focused) {
        GradientDrawable drawable = createRoundedDrawable(0xff2f2f2f, 8);
        drawable.setStroke(dp(1), focused ? getColorCompat(R.color.minebutton_color) : 0xff6a6a6a);
        return drawable;
    }

    private GradientDrawable createRoundedDrawable(@ColorInt int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private @ColorInt int getColorCompat(int colorRes) {
        return getResources().getColor(colorRes);
    }

    private void showDeleteServerDialog(ServerListManager.ServerEntry server) {
        if (!isAdded() || server == null) return;
        int dialogPadding = dp(20);
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dialogPadding, dialogPadding, dialogPadding, dialogPadding);
        content.setBackground(createRoundedDrawable(0xff252525, DIALOG_CORNER_RADIUS_DP));

        TextView title = new TextView(requireContext());
        title.setText("Delete server?");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.START);
        content.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(requireContext());
        String serverName = server.name == null || server.name.trim().isEmpty() ? server.address : server.name;
        body.setText("Are you sure you want to remove " + serverName + "?");
        body.setTextColor(0xffd6d6d6);
        body.setTextSize(15);
        body.setPadding(0, dp(14), 0, dp(18));
        content.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancelButton = createDialogActionButton(android.R.string.cancel, getColorCompat(R.color.secondary_text));
        TextView deleteButton = createDialogActionButton(R.string.global_delete, 0xffff5252);
        buttonRow.addView(cancelButton);
        buttonRow.addView(deleteButton);
        content.addView(buttonRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(content)
                .create();
        cancelButton.setOnClickListener(v -> dialog.cancel());
        deleteButton.setOnClickListener(v -> deleteServer(dialog, server));
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        });
        dialog.show();
    }

    private void deleteServer(Dialog dialog, ServerListManager.ServerEntry server) {
        if (server == null) return;
        File profileDirectory = getCurrentProfileDirectory();
        String targetName = server.name;
        String targetAddress = server.address;
        if (mServerList != null) {
            mServerList.servers.remove(server);
            serverPingState.remove(server);
            renderServers();
        }
        mServerExecutor.execute(() -> {
            boolean saved = false;
            try {
                ServerListManager.ServerList list = ServerListManager.load(profileDirectory);
                for (int i = list.servers.size() - 1; i >= 0; i--) {
                    ServerListManager.ServerEntry candidate = list.servers.get(i);
                    if (serverMatches(candidate, targetName, targetAddress)) {
                        list.servers.remove(i);
                        break;
                    }
                }
                saved = ServerListManager.save(list);
            } catch (RuntimeException ignored) { }
            boolean finalSaved = saved;
            mMainHandler.post(() -> {
                if (!isAdded()) return;
                dialog.dismiss();
                if (!finalSaved) Toast.makeText(requireContext(), R.string.main_servers_save_failed, Toast.LENGTH_LONG).show();
                mLoadedProfileDirectory = null;
                loadServersForCurrentProfile(true);
            });
        });
    }

    private boolean serverMatches(ServerListManager.ServerEntry candidate, String name, String address) {
        if (candidate == null) return false;
        boolean sameAddress = (address == null && candidate.address == null) || (address != null && address.equals(candidate.address));
        boolean sameName = (name == null && candidate.name == null) || (name != null && name.equals(candidate.name));
        return sameAddress && sameName;
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
            boolean saved;
            try {
                ServerListManager.ServerList list = ServerListManager.load(profileDirectory);
                int index = existingServer == null || mServerList == null ? -1 : mServerList.servers.indexOf(existingServer);
                ServerListManager.ServerEntry target;
                if (index >= 0 && index < list.servers.size()) target = list.servers.get(index);
                else {
                    target = new ServerListManager.ServerEntry(finalName, finalAddress);
                    list.servers.add(target);
                }
                target.name = finalName;
                target.address = finalAddress;
                saved = ServerListManager.save(list);
            } catch (RuntimeException e) {
                saved = false;
            }
            boolean finalSaved = saved;
            mMainHandler.post(() -> {
                if (!isAdded()) return;
                if (finalSaved) {
                    dialog.dismiss();
                    mLoadedProfileDirectory = null;
                    loadServersForCurrentProfile(true);
                    Toast.makeText(requireContext(), R.string.main_servers_saved, Toast.LENGTH_SHORT).show();
                } else Toast.makeText(requireContext(), R.string.main_servers_save_failed, Toast.LENGTH_LONG).show();
            });
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class ServerStatusViews {
        TextView details;
        ImageView icon;

        ServerStatusViews(TextView details, ImageView icon) {
            this.details = details;
            this.icon = icon;
        }
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
