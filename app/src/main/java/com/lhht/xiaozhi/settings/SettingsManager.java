package com.lhht.xiaozhi.settings;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREF_NAME = "xiaozhi_settings";
    private static final String KEY_WS_URL = "ws_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ENABLE_TOKEN = "enable_token";
    
    private final SharedPreferences preferences;
    
    public SettingsManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public void saveSettings(String wsUrl, String token, boolean enableToken) {
        preferences.edit()
                .putString(KEY_WS_URL, wsUrl)
                .putString(KEY_TOKEN, token)
                .putBoolean(KEY_ENABLE_TOKEN, enableToken)
                .apply();
    }
    
    public String getWsUrl() {
        return preferences.getString(KEY_WS_URL, "wss://api.tenclass.net/xiaozhi/v1/");
    }
    
    public String getToken() {
        return preferences.getString(KEY_TOKEN, "test-token");
    }
    
    public boolean isTokenEnabled() {
        return preferences.getBoolean(KEY_ENABLE_TOKEN, true);
    }
} 