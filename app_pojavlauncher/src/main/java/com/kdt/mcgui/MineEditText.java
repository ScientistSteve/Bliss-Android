package com.kdt.mcgui;

import android.content.*;
import android.util.*;

import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

public class MineEditText extends androidx.appcompat.widget.AppCompatEditText {
	public MineEditText(Context ctx) {
		super(ctx);
		init();
	}

	public MineEditText(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		int horizontalPadding = getResources().getDimensionPixelSize(R.dimen._14sdp);
		int verticalPadding = getResources().getDimensionPixelSize(R.dimen._10sdp);
		setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_bliss_input, null));
		setTextColor(getResources().getColor(R.color.copper_text));
		setHintTextColor(getResources().getColor(R.color.copper_text_muted));
		setTypeface(ResourcesCompat.getFont(getContext(), R.font.outfit));
		setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
		setSingleLine(true);
	}
}
