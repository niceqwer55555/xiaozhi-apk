package com.lhht.xiaozhi.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.lhht.xiaozhi.R;
import com.lhht.xiaozhi.settings.SettingsManager;

public class SettingsActivity extends AppCompatActivity {
    private SettingsManager settingsManager;
    private EditText wsUrlInput;
    private EditText tokenInput;
    private Switch enableTokenSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsManager = new SettingsManager(this);
        
        wsUrlInput = findViewById(R.id.wsUrlInput);
        tokenInput = findViewById(R.id.tokenInput);
        enableTokenSwitch = findViewById(R.id.enableTokenSwitch);
        Button saveButton = findViewById(R.id.saveButton);

        // 加载当前设置
        wsUrlInput.setText(settingsManager.getWsUrl());
        tokenInput.setText(settingsManager.getToken());
        enableTokenSwitch.setChecked(settingsManager.isTokenEnabled());

        // 根据Token开关状态更新Token输入框状态
        updateTokenInputState();
        enableTokenSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updateTokenInputState());

        // 保存设置
        saveButton.setOnClickListener(v -> {
            String wsUrl = wsUrlInput.getText().toString();
            String token = tokenInput.getText().toString();
            boolean enableToken = enableTokenSwitch.isChecked();

            settingsManager.saveSettings(wsUrl, token, enableToken);
            finish();
        });
    }

    private void updateTokenInputState() {
        tokenInput.setEnabled(enableTokenSwitch.isChecked());
    }
} 