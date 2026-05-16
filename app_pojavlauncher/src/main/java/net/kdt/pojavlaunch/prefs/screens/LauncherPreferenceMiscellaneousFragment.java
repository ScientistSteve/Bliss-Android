package net.kdt.pojavlaunch.prefs.screens;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.ai.AiAssistantConfig;
import net.kdt.pojavlaunch.utils.GLInfoUtils;

public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {
    private static final int DIALOG_CORNER_RADIUS_DP = 12;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_misc);
        Preference apiKeyPreference = requirePreference("aiAssistantApiKey");
        apiKeyPreference.setOnPreferenceClickListener(preference -> {
            showAiApiKeyDialog();
            return true;
        });
        Preference driverPreference = requirePreference("zinkPreferSystemDriver");
        PackageManager packageManager = driverPreference.getContext().getPackageManager();
        boolean supportsTurnip = Tools.checkVulkanSupport(packageManager) && GLInfoUtils.getGlInfo().isAdreno();
        driverPreference.setVisible(supportsTurnip);
    }

    private void showAiApiKeyDialog() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(14));
        content.setBackground(createRoundedDrawable(0xff242424, DIALOG_CORNER_RADIUS_DP));

        TextView title = new TextView(requireContext());
        title.setText(R.string.ai_assistant_api_key_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Spinner providerSpinner = new Spinner(requireContext());
        String[] providers = {AiAssistantConfig.PROVIDER_OPENAI, AiAssistantConfig.PROVIDER_GEMINI, AiAssistantConfig.PROVIDER_GROQ};
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, providers);
        providerSpinner.setAdapter(providerAdapter);
        String savedProvider = AiAssistantConfig.getProvider(requireContext());
        for (int i = 0; i < providers.length; i++) if (providers[i].equals(savedProvider)) providerSpinner.setSelection(i);
        providerSpinner.setBackground(createDialogFieldBackground(false));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        spinnerParams.setMargins(0, dp(18), 0, dp(12));
        content.addView(providerSpinner, spinnerParams);
        providerSpinner.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(createDialogFieldBackground(hasFocus)));
        TextView modelLabel = new TextView(requireContext());
        modelLabel.setText("Model");
        modelLabel.setTextColor(Color.WHITE);
        modelLabel.setTextSize(14);
        modelLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams modelLabelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        modelLabelParams.setMargins(0, 0, 0, dp(6));
        content.addView(modelLabel, modelLabelParams);

        Spinner modelSpinner = new Spinner(requireContext());
        modelSpinner.setBackground(createDialogFieldBackground(false));
        LinearLayout.LayoutParams modelSpinnerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        modelSpinnerParams.setMargins(0, 0, 0, dp(12));
        content.addView(modelSpinner, modelSpinnerParams);
        modelSpinner.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(createDialogFieldBackground(hasFocus)));
        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) ((TextView) view).setTextColor(Color.WHITE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        String savedModel = AiAssistantConfig.getModel(requireContext());
        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) ((TextView) view).setTextColor(Color.WHITE);
                String provider = parent.getItemAtPosition(position) == null ? AiAssistantConfig.PROVIDER_OPENAI : parent.getItemAtPosition(position).toString();
                String[] models = AiAssistantConfig.getModelsForProvider(provider);
                ArrayAdapter<String> modelAdapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, models);
                modelSpinner.setAdapter(modelAdapter);
                String selectedModel = AiAssistantConfig.normalizeModel(provider, savedModel);
                for (int i = 0; i < models.length; i++) {
                    if (models[i].equals(selectedModel)) {
                        modelSpinner.setSelection(i);
                        break;
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        String initialProvider = providerSpinner.getSelectedItem() == null ? savedProvider : providerSpinner.getSelectedItem().toString();
        String[] initialModels = AiAssistantConfig.getModelsForProvider(initialProvider);
        modelSpinner.setAdapter(new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, initialModels));
        String initialModel = AiAssistantConfig.normalizeModel(initialProvider, savedModel);
        for (int i = 0; i < initialModels.length; i++) {
            if (initialModels[i].equals(initialModel)) {
                modelSpinner.setSelection(i);
                break;
            }
        }

        FrameLayout keyContainer = new FrameLayout(requireContext());
        keyContainer.setBackground(createDialogFieldBackground(false));
        EditText keyField = new EditText(requireContext());
        keyField.setText(AiAssistantConfig.getApiKey(requireContext()));
        keyField.setHint(R.string.ai_assistant_api_key_hint);
        keyField.setSingleLine(true);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setTextColor(Color.WHITE);
        keyField.setHintTextColor(0xffbdbdbd);
        keyField.setBackgroundColor(Color.TRANSPARENT);
        keyField.setPadding(dp(14), 0, dp(52), 0);
        keyContainer.addView(keyField, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(52)));
        ImageButton eyeButton = new ImageButton(requireContext());
        eyeButton.setImageResource(R.drawable.ic_eye);
        eyeButton.setBackgroundColor(Color.TRANSPARENT);
        eyeButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        FrameLayout.LayoutParams eyeParams = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.END | Gravity.CENTER_VERTICAL);
        keyContainer.addView(eyeButton, eyeParams);
        keyField.setOnFocusChangeListener((v, hasFocus) -> keyContainer.setBackground(createDialogFieldBackground(hasFocus)));
        eyeButton.setOnClickListener(v -> {
            int selection = keyField.getSelectionStart();
            boolean hidden = (keyField.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
            keyField.setInputType(InputType.TYPE_CLASS_TEXT | (hidden ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            keyField.setSelection(Math.max(0, selection));
        });
        content.addView(keyContainer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancelButton = createDialogActionButton(android.R.string.cancel, getResources().getColor(R.color.secondary_text));
        TextView saveButton = createDialogActionButton(R.string.global_save, getResources().getColor(R.color.minebutton_color));
        buttonRow.addView(cancelButton);
        buttonRow.addView(saveButton);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, dp(12), 0, 0);
        content.addView(buttonRow, buttonParams);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(content).create();
        cancelButton.setOnClickListener(v -> dialog.cancel());
        saveButton.setOnClickListener(v -> {
            String provider = providerSpinner.getSelectedItem() == null ? AiAssistantConfig.PROVIDER_OPENAI : providerSpinner.getSelectedItem().toString();
            String model = modelSpinner.getSelectedItem() == null ? AiAssistantConfig.getDefaultModel(provider) : modelSpinner.getSelectedItem().toString();
            String apiKey = keyField.getText() == null ? "" : keyField.getText().toString().trim();
            AiAssistantConfig.save(requireContext(), provider, apiKey, model);
            dialog.dismiss();
        });
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        });
        dialog.show();
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
        drawable.setStroke(dp(1), focused ? getResources().getColor(R.color.minebutton_color) : 0xff6a6a6a);
        return drawable;
    }

    private GradientDrawable createRoundedDrawable(@ColorInt int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
