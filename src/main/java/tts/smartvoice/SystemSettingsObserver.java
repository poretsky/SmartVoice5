package tts.smartvoice;

import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceManager;

class SystemSettingsObserver extends ContentObserver {

    private final SmartVoiceApp app;

    SystemSettingsObserver(SmartVoiceApp app) {
        super(new Handler(Looper.myLooper()));
        this.app = app;
    }

    @Override
    public void onChange(boolean selfChange) {
        onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if (!app.isDebugEnabled()) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(app);
            String prefKey = app.getString(R.string.log_text_key);
            boolean prefState = app.getResources().getBoolean(R.bool.log_text_state);
            if (preferences.getBoolean(prefKey, prefState)) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(prefKey, prefState);
                editor.apply();
            }
        }
    }

    @Override
    public boolean deliverSelfNotifications() {
        return true;
    }

}
