package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.mouse.CustomCursorTexture;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class LauncherPreferenceControlFragment extends LauncherPreferenceFragment {
    private boolean mGyroAvailable = false;
    private Preference mCustomCursorPreference;
    private final ActivityResultLauncher<String> mCustomCursorPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::saveCustomCursorTexture);

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        // Get values
        int longPressTrigger = LauncherPreferences.PREF_LONGPRESS_TRIGGER;
        int prefButtonSize = (int) LauncherPreferences.PREF_BUTTONSIZE;
        int mouseScale = (int) (LauncherPreferences.PREF_MOUSESCALE * 100);
        int gyroSampleRate = LauncherPreferences.PREF_GYRO_SAMPLE_RATE;
        int touchControllerVibrateLength = LauncherPreferences.PREF_TOUCHCONTROLLER_VIBRATE_LENGTH;
        float mouseSpeed = LauncherPreferences.PREF_MOUSESPEED;
        float gyroSpeed = LauncherPreferences.PREF_GYRO_SENSITIVITY;
        float joystickDeadzone = LauncherPreferences.PREF_DEADZONE_SCALE;


        //Triggers a write for some reason which resets the value
        addPreferencesFromResource(R.xml.pref_control);

        CustomSeekBarPreference seek2 = requirePreference("timeLongPressTrigger",
                CustomSeekBarPreference.class);
        seek2.setValue(longPressTrigger);
        seek2.setSuffix(" ms");

        CustomSeekBarPreference seek3 = requirePreference("buttonscale",
                CustomSeekBarPreference.class);
        seek3.setValue(prefButtonSize);
        seek3.setSuffix(" %");

        CustomSeekBarPreference seek4 = requirePreference("mousescale",
                CustomSeekBarPreference.class);
        seek4.setValue(mouseScale);
        seek4.setSuffix(" %");

        CustomSeekBarPreference seek6 = requirePreference("mousespeed",
                CustomSeekBarPreference.class);
        seek6.setValue((int) (mouseSpeed * 100f));
        seek6.setSuffix(" %");

        CustomSeekBarPreference deadzoneSeek = requirePreference("gamepad_deadzone_scale",
                CustomSeekBarPreference.class);
        deadzoneSeek.setValue((int) (joystickDeadzone * 100f));
        deadzoneSeek.setSuffix(" %");


        Context context = getContext();
        if (context != null) {
            mGyroAvailable = Tools.deviceSupportsGyro(context);
        }
        PreferenceCategory gyroCategory = requirePreference("gyroCategory",
                PreferenceCategory.class);
        gyroCategory.setVisible(mGyroAvailable);

        CustomSeekBarPreference gyroSensitivitySeek = requirePreference("gyroSensitivity",
                CustomSeekBarPreference.class);
        gyroSensitivitySeek.setValue((int) (gyroSpeed * 100f));
        gyroSensitivitySeek.setSuffix(" %");

        CustomSeekBarPreference gyroSampleRateSeek = requirePreference("gyroSampleRate",
                CustomSeekBarPreference.class);
        gyroSampleRateSeek.setValue(gyroSampleRate);
        gyroSampleRateSeek.setSuffix(" ms");

        CustomSeekBarPreference touchControllerVibrateLengthSeek = requirePreference(
                "touchControllerVibrateLength",
                CustomSeekBarPreference.class);
        touchControllerVibrateLengthSeek.setValue(touchControllerVibrateLength);
        touchControllerVibrateLengthSeek.setSuffix(" ms");

        setupCustomCursorPreference();
        computeVisibility();
    }


    private void setupCustomCursorPreference() {
        mCustomCursorPreference = requirePreference(CustomCursorTexture.PREF_KEY);
        updateCustomCursorSummary();
        mCustomCursorPreference.setOnPreferenceClickListener(preference -> {
            mCustomCursorPicker.launch("image/*");
            return true;
        });
    }

    private void saveCustomCursorTexture(Uri uri) {
        if (uri == null || getContext() == null) return;
        try {
            CustomCursorTexture.saveCursorTexture(requireContext(), uri);
            updateCustomCursorSummary();
            Toast.makeText(requireContext(), R.string.preference_custom_virtual_cursor_texture_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Tools.showErrorRemote(e);
        }
    }

    private void updateCustomCursorSummary() {
        if (mCustomCursorPreference == null || getContext() == null) return;
        mCustomCursorPreference.setSummary(CustomCursorTexture.hasCustomCursorTexture(requireContext())
                ? R.string.preference_custom_virtual_cursor_texture_selected
                : R.string.preference_custom_virtual_cursor_texture_description);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility() {
        requirePreference("timeLongPressTrigger").setVisible(!LauncherPreferences.PREF_DISABLE_GESTURES);
        requirePreference("gyroSensitivity").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroSampleRate").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroInvertX").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroInvertY").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
        requirePreference("gyroSmoothing").setVisible(LauncherPreferences.PREF_ENABLE_GYRO);
    }

}
