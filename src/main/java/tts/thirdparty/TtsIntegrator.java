package tts.thirdparty;

import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_REMOVED;
import static android.content.Intent.ACTION_PACKAGE_REPLACED;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA;
import static android.speech.tts.TextToSpeech.Engine.ACTION_TTS_DATA_INSTALLED;
import static android.speech.tts.TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE;
import static android.speech.tts.TextToSpeech.SUCCESS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import tts.smartvoice.SmartVoiceApp;
import tts.smartvoice.api.TtsEngine;

public class TtsIntegrator {

        private static final String URI_SCHEME_PACKAGE = "package";

    private final Context context;
    private final TtsEngine.OnLoadListener onTtsEngineLoadListener;
    private final Set<AndroidTtsEngine> engines;
    private final String selfPackage;
    private final InstallationEventsObserver installationEventsObserver;


    public TtsIntegrator(Context context, TtsEngine.OnLoadListener onTtsEngineLoadListener) {
        this.context = context;
        this.onTtsEngineLoadListener = onTtsEngineLoadListener;
        engines = new HashSet<AndroidTtsEngine>();
        selfPackage = context.getPackageName();
        installationEventsObserver = new InstallationEventsObserver();
        IntentFilter intentFilter = new IntentFilter(ACTION_PACKAGE_ADDED);
        intentFilter.addAction(ACTION_PACKAGE_REPLACED);
        intentFilter.addAction(ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme(URI_SCHEME_PACKAGE);
        ContextCompat.registerReceiver(context, installationEventsObserver, intentFilter, ContextCompat.RECEIVER_EXPORTED);
        ContextCompat.registerReceiver(context, installationEventsObserver, new IntentFilter(ACTION_TTS_DATA_INSTALLED), ContextCompat.RECEIVER_EXPORTED);
    }

    public void loadEngines() {
        for (ServiceInfo service : getAvailableServices(null))
            loadEngine(service);
    }

    public void unloadEngines() {
        for (AndroidTtsEngine engine : engines)
            engine.destroy();
        engines.clear();
    }

    public String getEngineLabelFor(String name) {
        AndroidTtsEngine engine = getEngine(name);
        return (engine != null) ? engine.getLabel() : name;
    }

    public Map<String, Intent> getMenuActions() {
        Map<String, Intent> menuActions = new HashMap<String, Intent>();
        for (AndroidTtsEngine engine : engines) {
            String engineName = engine.getName();
            Intent intent = new Intent(ACTION_INSTALL_TTS_DATA);
            intent.setPackage(engineName);
            if (context.getPackageManager().resolveActivity(intent, MATCH_DEFAULT_ONLY) != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                menuActions.put(engineName, intent);
            }
        }
        return menuActions;
    }

    public void destroy() {
        try {
            context.unregisterReceiver(installationEventsObserver);
            unloadEngines();
        } catch (Exception ex) {
        }
    }


    Context getContext() {
        return context;
    }

    void acceptEngineStartResult(AndroidTtsEngine engine, int status) {
        if (status == SUCCESS)
            onTtsEngineLoadListener.onLoad(engine);
        else engines.remove(engine);
    }


    @Override
    protected void finalize() throws Throwable {
        try {
            destroy();
        }
        finally {
            super.finalize();
        }
    }


    private List<ServiceInfo> getAvailableServices(String packageName) {
        List<ServiceInfo> services = new ArrayList<ServiceInfo>();
        Intent intent = new Intent(INTENT_ACTION_TTS_SERVICE);
        if (packageName != null)
            intent.setPackage(packageName);
        List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentServices(intent, MATCH_DEFAULT_ONLY);
        if (resolveInfos != null) {
            for (ResolveInfo resolveInfo : resolveInfos) {
                ServiceInfo service = resolveInfo.serviceInfo;
                if ((service != null) && !selfPackage.equals(service.packageName))
                    services.add(service);
            }
        }
        return services;
    }

    private void loadEngine(ServiceInfo service) {
        engines.add(new AndroidTtsEngine(this, service));
    }

    private AndroidTtsEngine getEngine(String name) {
        for (AndroidTtsEngine engine : engines)
            if (engine.getName().equals(name))
                return engine;
        return null;
    }


    private class InstallationEventsObserver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                SmartVoiceApp app = (SmartVoiceApp) context.getApplicationContext();
                String action = intent.getAction();
                if (ACTION_TTS_DATA_INSTALLED.equals(action)) {
                    app.refresh();
                } else {
                    String packageName = intent.getData().getSchemeSpecificPart();
                    boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                    if (ACTION_PACKAGE_ADDED.equals(action) && !replacing) {
                        List<ServiceInfo> services = getAvailableServices(packageName);
                        if (!services.isEmpty())
                            loadEngine(services.get(0));
                    } else if ((ACTION_PACKAGE_REPLACED.equals(action) || (ACTION_PACKAGE_REMOVED.equals(action) && !replacing)) && (getEngine(packageName) != null)) {
                        app.refresh();
                    }
                }
            } catch (Exception ex) {
            }
        }

    }

}
