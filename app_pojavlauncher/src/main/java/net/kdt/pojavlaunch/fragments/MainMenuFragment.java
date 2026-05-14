package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
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
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private boolean isQuickActionsListMode = false;

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

        if (quickActionsContainer != null) {
            updateQuickActionsLayout(quickActionsContainer);
        }

        if (quickActionsToggleButton != null && quickActionsContainer != null) {
            quickActionsToggleButton.setOnClickListener(v -> {
                isQuickActionsListMode = !isQuickActionsListMode;
                updateQuickActionsLayout(quickActionsContainer);
            });
        }

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
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
