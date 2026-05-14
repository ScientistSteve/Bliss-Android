package com.kdt.mcgui;

import android.content.*;
import android.util.*;

import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

public class MineButton extends androidx.appcompat.widget.AppCompatButton {
	
	public MineButton(Context ctx) {
		this(ctx, null);
	}
	
	public MineButton(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		setTypeface(ResourcesCompat.getFont(getContext(), R.font.outfit));
		setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_copper_button, null));
		setTextColor(getResources().getColor(R.color.copper_text));
		setLetterSpacing(0.02f);
		setAllCaps(false);
		setMinHeight(getResources().getDimensionPixelSize(R.dimen._48sdp));
		setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._14ssp));
	}

}
