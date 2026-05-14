package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private TextView mAccountNameView;
    private TextView mAccountTypeView;
    private TextView mVersionTypeChip;
    private ImageView mAccountAvatarView;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View mAccountCard = view.findViewById(R.id.account_card);
        mAccountNameView = view.findViewById(R.id.home_account_name);
        mAccountTypeView = view.findViewById(R.id.home_account_type);
        mAccountAvatarView = view.findViewById(R.id.home_account_avatar);
        mVersionTypeChip = view.findViewById(R.id.version_type_chip);

        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mAccountCard.setOnClickListener(v -> requireActivity().findViewById(R.id.account_spinner).performClick());
        mVersionSpinner.setOnClickListener(v -> {
            mcVersionSpinner.class.cast(v).performVersionListClick();
            updateVersionChip();
        });

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
        updateAccountCard();
        updateVersionChip();
    }

    private void updateAccountCard() {
        com.kdt.mcgui.mcAccountSpinner accountSpinner = requireActivity().findViewById(R.id.account_spinner);
        MinecraftAccount account = accountSpinner.getSelectedAccount();
        if(account == null) {
            mAccountNameView.setText(R.string.main_add_account);
            mAccountTypeView.setText("Offline");
            mAccountAvatarView.setImageResource(R.mipmap.ic_launcher_round);
            return;
        }
        mAccountNameView.setText(account.username);
        if(account.isMicrosoft) mAccountTypeView.setText("Microsoft");
        else if(account.isElyBy) mAccountTypeView.setText("Ely.by");
        else mAccountTypeView.setText("Offline");
        Bitmap skinFace = account.getSkinFace();
        if(skinFace != null) mAccountAvatarView.setImageBitmap(skinFace);
        else mAccountAvatarView.setImageResource(R.mipmap.ic_launcher_round);
    }

    private void updateVersionChip() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        LauncherProfiles.load();
        MinecraftProfile profileObject = currentProfile == null ? null : LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        String versionName = profileObject == null ? null : profileObject.lastVersionId;
        mVersionTypeChip.setText(resolveVersionType(versionName));
    }

    private String resolveVersionType(String versionName) {
        if(versionName == null) return "Vanilla";
        String lowerVersion = versionName.toLowerCase();
        if(lowerVersion.contains("forge")) return "Forge";
        if(lowerVersion.contains("fabric")) return "Fabric";
        return "Vanilla";
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
