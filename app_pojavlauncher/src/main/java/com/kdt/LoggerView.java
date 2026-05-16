package com.kdt;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.R;

/**
 * A class able to display logs to the user.
 * It has support for the Logger class
 */
public class LoggerView extends ConstraintLayout {
    private Logger.eventLogListener mLogListener;
    private DefocusableScrollView mScrollView;
    private TextView mLogTextView;
    private boolean mLogListenerRegistered;


    public LoggerView(@NonNull Context context) {
        this(context, null);
    }

    public LoggerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        updateLogListenerRegistration(visibility == VISIBLE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateLogListenerRegistration(getVisibility() == VISIBLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        updateLogListenerRegistration(false);
        super.onDetachedFromWindow();
    }

    /**
     * Inflate the layout, and add component behaviors
     */
    private void init(){
        inflate(getContext(), R.layout.view_logger, this);
        mLogTextView = findViewById(R.id.content_log_view);
        mLogTextView.setTypeface(Typeface.MONOSPACE);
        //TODO clamp the max text so it doesn't go oob
        mLogTextView.setMaxLines(Integer.MAX_VALUE);
        mLogTextView.setEllipsize(null);
        mLogTextView.setVisibility(VISIBLE);

        // Remove the loggerView from the user View
        ImageButton cancelButton = findViewById(R.id.log_view_cancel);
        cancelButton.setOnClickListener(view -> LoggerView.this.setVisibility(GONE));

        // Set the scroll view
        mScrollView = findViewById(R.id.content_log_scroll);
        mScrollView.setKeepFocusing(true);

        // Listen to logs
        mLogListener = text -> post(() -> {
            if (!mLogListenerRegistered) return;
            mLogTextView.append(text + '\n');
            mScrollView.post(() -> mScrollView.fullScroll(FOCUS_DOWN));
        });
        updateLogListenerRegistration(getVisibility() == VISIBLE);
    }

    private void updateLogListenerRegistration(boolean shouldRegister) {
        if (mLogListener == null) return;
        if (shouldRegister == mLogListenerRegistered) return;
        mLogListenerRegistered = shouldRegister;
        if (shouldRegister) {
            Logger.addLogListener(mLogListener);
            post(() -> mScrollView.fullScroll(FOCUS_DOWN));
        } else {
            mLogTextView.setText("");
            Logger.removeLogListener(mLogListener);
        }
    }
}
