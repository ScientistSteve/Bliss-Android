package net.kdt.pojavlaunch.ai;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;

import java.util.List;

import io.noties.markwon.Markwon;

public class AiChatAdapter extends RecyclerView.Adapter<AiChatAdapter.MessageHolder> {
    public static final int ROLE_USER = 0;
    public static final int ROLE_AI = 1;
    public static final int ROLE_TYPING = 2;
    public static final int ROLE_ERROR = 3;

    public interface EditListener { void onEditRequested(int position, Message message); }

    private final List<Message> messages;
    private final Markwon markwon;
    private final EditListener editListener;

    public AiChatAdapter(@NonNull Context context, @NonNull List<Message> messages, @NonNull EditListener editListener) {
        this.messages = messages;
        this.markwon = Markwon.create(context);
        this.editListener = editListener;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).role;
    }

    @NonNull
    @Override
    public MessageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(context, 6), 0, dp(context, 6));
        row.setGravity(viewType == ROLE_USER ? Gravity.END : Gravity.START | Gravity.BOTTOM);
        RecyclerView.LayoutParams rowParams = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowParams);

        if (viewType != ROLE_USER) {
            ImageView avatar = new ImageView(context);
            avatar.setImageResource(R.drawable.ic_ai_robot);
            avatar.setBackgroundResource(R.drawable.bg_ai_fab);
            avatar.setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7));
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(context, 34), dp(context, 34));
            avatarParams.setMargins(0, 0, dp(context, 8), 0);
            row.addView(avatar, avatarParams);
        }

        TextView bubble = new TextView(context);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(15);
        bubble.setLineSpacing(dp(context, 2), 1.0f);
        bubble.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        bubble.setTextIsSelectable(false);
        int maxWidth = (int) (parent.getResources().getDisplayMetrics().widthPixels * 0.78f);
        bubble.setMaxWidth(maxWidth);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(bubble, bubbleParams);
        return new MessageHolder(row, bubble);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageHolder holder, int position) {
        Message message = messages.get(position);
        holder.bubble.setBackground(createBubbleBackground(holder.bubble.getContext(), message.role));
        holder.bubble.setTypeface(Typeface.DEFAULT, message.role == ROLE_TYPING ? Typeface.BOLD : Typeface.NORMAL);
        if (message.role == ROLE_AI) markwon.setMarkdown(holder.bubble, message.text);
        else holder.bubble.setText(message.text);
        if (message.role == ROLE_USER) {
            holder.itemView.setOnLongClickListener(v -> {
                showEditPopup(v, position, message);
                return true;
            });
        } else holder.itemView.setOnLongClickListener(null);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private void showEditPopup(View anchor, int position, Message message) {
        Context context = anchor.getContext();
        TextView edit = new TextView(context);
        edit.setText(R.string.global_edit);
        edit.setTextColor(Color.WHITE);
        edit.setTextSize(15);
        edit.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        edit.setBackground(createRounded(0xff2a2a2a, dp(context, 8)));
        PopupWindow popupWindow = new PopupWindow(edit, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(createRounded(0xff2a2a2a, dp(context, 8)));
        edit.setOnClickListener(v -> {
            popupWindow.dismiss();
            editListener.onEditRequested(position, message);
        });
        popupWindow.showAsDropDown(anchor, 0, -anchor.getHeight());
    }

    private static GradientDrawable createBubbleBackground(Context context, int role) {
        int color;
        if (role == ROLE_USER) color = context.getResources().getColor(R.color.minebutton_color);
        else if (role == ROLE_ERROR) color = 0xff5c2323;
        else color = 0xff2a2a2a;
        return createRounded(color, dp(context, 18));
    }

    private static GradientDrawable createRounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static class MessageHolder extends RecyclerView.ViewHolder {
        final TextView bubble;
        MessageHolder(@NonNull View itemView, @NonNull TextView bubble) {
            super(itemView);
            this.bubble = bubble;
        }
    }

    public static class Message {
        public int role;
        public String text;
        public boolean error;

        public Message(int role, @NonNull String text) {
            this.role = role;
            this.text = text;
            this.error = role == ROLE_ERROR;
        }
    }
}
