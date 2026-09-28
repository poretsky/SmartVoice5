package tts.smartvoice.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.SeekBarPreference;

public class Slider extends SeekBarPreference {

    public Slider(Context context, AttributeSet attributes, int defStyleAttr, int defStyleRes) {
        super(context, attributes, defStyleAttr, defStyleRes);
    }

    public Slider(Context context, AttributeSet attributes, int defStyleAttr) {
        super(context, attributes, defStyleAttr);
    }

    public Slider(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    public Slider(Context context) {
        super(context);
    }

    @Override
    protected int getPersistedInt(int defaultReturnValue) {
        return Math.round(getPersistedFloat((float) defaultReturnValue));
    }

    @Override
    protected boolean persistInt(int value) {
        return persistFloat((float) value);
    }

}
