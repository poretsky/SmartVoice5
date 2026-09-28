package tts.smartvoice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

class SystemEventsHandler extends BroadcastReceiver {

    private final TtsService ttsService;

    SystemEventsHandler(TtsService service) {
        super();
        ttsService = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_LOCALE_CHANGED.equalsIgnoreCase(intent.getAction()))
            ttsService.notifyLocaleChange();
    }

}
