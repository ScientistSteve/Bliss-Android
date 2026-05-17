package net.kdt.pojavlaunch.ai;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.R;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AiChatFragment extends Fragment {
    public static final String TAG = "AiChatFragment";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AiChatAdapter adapter;
    private RecyclerView recyclerView;
    private EditText input;
    private AiChatViewModel viewModel;
    private int dotsFrame = 0;
    private final Runnable typingAnimator = new Runnable() {
        @Override
        public void run() {
            if (viewModel.typingIndex >= 0 && viewModel.typingIndex < viewModel.messages.size()) {
                String[] frames = {"•  •  •", "••  •", "•••"};
                viewModel.messages.get(viewModel.typingIndex).text = frames[dotsFrame % frames.length];
                dotsFrame++;
                adapter.notifyItemChanged(viewModel.typingIndex);
                mainHandler.postDelayed(this, 420);
            }
        }
    };

    public AiChatFragment() {
        super(R.layout.fragment_ai_chat);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(AiChatViewModel.class);
        recyclerView = view.findViewById(R.id.ai_chat_recycler);
        input = view.findViewById(R.id.ai_chat_input);
        ImageButton send = view.findViewById(R.id.ai_chat_send_button);
        ImageButton back = view.findViewById(R.id.ai_chat_back_button);
        ImageButton clear = view.findViewById(R.id.ai_chat_clear_button);

        back.setBackground(new GradientCircleStrokeDrawable(dp(2)));
        back.setOnClickListener(v -> requireActivity().onBackPressed());
        clear.setBackground(new GradientCircleStrokeDrawable(dp(2)));
        clear.setColorFilter(Color.WHITE);
        clear.setOnClickListener(v -> clearChat());

        adapter = new AiChatAdapter(requireContext(), viewModel.messages, this::beginEditingMessage);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        if (viewModel.typingIndex != RecyclerView.NO_POSITION) mainHandler.post(typingAnimator);

        send.setOnClickListener(v -> sendCurrentInput());
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterSend = actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP && !event.isShiftPressed());
            if (enterSend) {
                sendCurrentInput();
                return true;
            }
            return false;
        });

        View inputBar = view.findViewById(R.id.ai_chat_input_bar);
        int originalBottomPadding = inputBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(inputBar, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), originalBottomPadding + bottomInset);
            return insets;
        });
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacks(typingAnimator);
        super.onDestroyView();
    }

    private void clearChat() {
        mainHandler.removeCallbacks(typingAnimator);
        viewModel.messages.clear();
        viewModel.typingIndex = RecyclerView.NO_POSITION;
        viewModel.editingUserIndex = RecyclerView.NO_POSITION;
        viewModel.requestGeneration++;
        adapter.notifyDataSetChanged();
    }

    private void beginEditingMessage(int position, AiChatAdapter.Message message) {
        if (position < 0 || position >= viewModel.messages.size()) return;
        viewModel.editingUserIndex = position;
        input.setText(message.text);
        input.setSelection(input.length());
        input.requestFocus();
    }

    private void sendCurrentInput() {
        String prompt = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(prompt) || viewModel.typingIndex != RecyclerView.NO_POSITION) return;
        input.setText("");
        int userIndex;
        if (viewModel.editingUserIndex != RecyclerView.NO_POSITION && viewModel.editingUserIndex < viewModel.messages.size()) {
            userIndex = viewModel.editingUserIndex;
            viewModel.messages.get(userIndex).text = prompt;
            adapter.notifyItemChanged(userIndex);
            if (userIndex + 1 < viewModel.messages.size() && viewModel.messages.get(userIndex + 1).role != AiChatAdapter.ROLE_USER) {
                viewModel.messages.remove(userIndex + 1);
                adapter.notifyItemRemoved(userIndex + 1);
            }
            viewModel.editingUserIndex = RecyclerView.NO_POSITION;
        } else {
            userIndex = viewModel.messages.size();
            viewModel.messages.add(new AiChatAdapter.Message(AiChatAdapter.ROLE_USER, prompt));
            adapter.notifyItemInserted(userIndex);
        }
        viewModel.requestGeneration++;
        int requestGeneration = viewModel.requestGeneration;
        viewModel.typingIndex = userIndex + 1;
        viewModel.messages.add(viewModel.typingIndex, new AiChatAdapter.Message(AiChatAdapter.ROLE_TYPING, "•  •  •"));
        adapter.notifyItemInserted(viewModel.typingIndex);
        scrollToBottom();
        mainHandler.post(typingAnimator);
        List<AiChatAdapter.Message> snapshot = conversationSnapshot();
        String provider = AiAssistantConfig.getProvider(requireContext());
        String key = AiAssistantConfig.getApiKey(requireContext()).trim();
        String model = AiAssistantConfig.getModel(requireContext());
        int responseIndex = viewModel.typingIndex;
        viewModel.networkExecutor.execute(() -> fetchAiResponse(provider, key, model, snapshot, responseIndex, requestGeneration));
    }

    private List<AiChatAdapter.Message> conversationSnapshot() {
        List<AiChatAdapter.Message> snapshot = new ArrayList<>();
        for (AiChatAdapter.Message message : viewModel.messages) {
            if (message.role == AiChatAdapter.ROLE_USER || message.role == AiChatAdapter.ROLE_AI) {
                snapshot.add(new AiChatAdapter.Message(message.role, message.text));
            }
        }
        return snapshot;
    }

    private void fetchAiResponse(String provider, String key, String model, List<AiChatAdapter.Message> history, int responseIndex, int requestGeneration) {
        try {
            if (key.isEmpty()) throw new Exception("Set your API key in Settings → Miscellaneous");
            String response = AiAssistantConfig.PROVIDER_GEMINI.equals(provider)
                    ? callGemini(key, model, history)
                    : callOpenAiCompatible(provider, key, model, history);
            mainHandler.post(() -> replaceTyping(responseIndex, requestGeneration, new AiChatAdapter.Message(AiChatAdapter.ROLE_AI, response)));
        } catch (Exception e) {
            String reason = e.getMessage() == null ? "Unable to reach AI provider." : e.getMessage();
            mainHandler.post(() -> replaceTyping(responseIndex, requestGeneration, new AiChatAdapter.Message(AiChatAdapter.ROLE_ERROR, reason)));
        }
    }

    private void replaceTyping(int index, int requestGeneration, AiChatAdapter.Message replacement) {
        if (requestGeneration != viewModel.requestGeneration) return;
        mainHandler.removeCallbacks(typingAnimator);
        if (index >= 0 && index < viewModel.messages.size() && viewModel.messages.get(index).role == AiChatAdapter.ROLE_TYPING) {
            viewModel.messages.set(index, replacement);
            adapter.notifyItemChanged(index);
        } else if (viewModel.typingIndex == index) {
            viewModel.messages.add(replacement);
            adapter.notifyItemInserted(viewModel.messages.size() - 1);
        }
        viewModel.typingIndex = RecyclerView.NO_POSITION;
        scrollToBottom();
    }

    private String callOpenAiCompatible(String provider, String key, String model, List<AiChatAdapter.Message> history) throws Exception {
        boolean groq = AiAssistantConfig.PROVIDER_GROQ.equals(provider);
        String endpoint = groq ? "https://api.groq.com/openai/v1/chat/completions" : "https://api.openai.com/v1/chat/completions";
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        JsonArray messagesJson = new JsonArray();
        for (AiChatAdapter.Message message : history) {
            JsonObject item = new JsonObject();
            item.addProperty("role", message.role == AiChatAdapter.ROLE_USER ? "user" : "assistant");
            item.addProperty("content", message.text);
            messagesJson.add(item);
        }
        root.add("messages", messagesJson);
        JsonObject response = postJson(endpoint, root, key, false);
        JsonArray choices = response.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) throw new Exception("No AI response was returned.");
        return choices.get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString().trim();
    }

    private String callGemini(String key, String model, List<AiChatAdapter.Message> history) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;
        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        for (AiChatAdapter.Message message : history) {
            JsonObject content = new JsonObject();
            content.addProperty("role", message.role == AiChatAdapter.ROLE_USER ? "user" : "model");
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", message.text);
            parts.add(part);
            content.add("parts", parts);
            contents.add(content);
        }
        root.add("contents", contents);
        JsonObject response = postJson(endpoint, root, null, true);
        JsonArray candidates = response.getAsJsonArray("candidates");
        if (candidates == null || candidates.size() == 0) throw new Exception("No AI response was returned.");
        JsonArray parts = candidates.get(0).getAsJsonObject().getAsJsonObject("content").getAsJsonArray("parts");
        if (parts == null || parts.size() == 0) throw new Exception("No AI response was returned.");
        return parts.get(0).getAsJsonObject().get("text").getAsString().trim();
    }

    private JsonObject postJson(String endpoint, JsonObject body, @Nullable String bearerKey, boolean gemini) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        if (bearerKey != null) connection.setRequestProperty("Authorization", "Bearer " + bearerKey);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(body.toString());
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String raw = readAll(stream);
        JsonObject json = raw.isEmpty() ? new JsonObject() : JsonParser.parseString(raw).getAsJsonObject();
        if (code < 200 || code >= 300) throw new Exception(extractError(json, code, gemini));
        return json;
    }

    private String extractError(JsonObject json, int code, boolean gemini) {
        try {
            JsonElement error = json.get("error");
            if (error != null && error.isJsonObject()) {
                JsonObject object = error.getAsJsonObject();
                if (object.has("message")) return object.get("message").getAsString();
            }
        } catch (Exception ignored) { }
        if (code == 401 || code == 403) return "Invalid or unauthorized API key.";
        if (code == 429) return "Rate limit reached. Please try again later.";
        return (gemini ? "Gemini" : "AI provider") + " request failed with HTTP " + code + ".";
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private void scrollToBottom() {
        recyclerView.post(() -> {
            if (adapter.getItemCount() > 0) recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class GradientCircleStrokeDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int strokeWidth;

        GradientCircleStrokeDrawable(int strokeWidth) {
            this.strokeWidth = strokeWidth;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            bounds.inset(strokeWidth / 2f, strokeWidth / 2f);
            paint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    Color.parseColor("#FF6B00"), Color.parseColor("#FFB300"), Shader.TileMode.CLAMP));
            canvas.drawOval(bounds, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
