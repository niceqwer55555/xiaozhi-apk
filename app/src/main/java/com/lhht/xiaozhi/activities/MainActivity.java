package com.lhht.xiaozhi.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lhht.xiaozhi.R;
import com.lhht.xiaozhi.adapters.MessageAdapter;
import com.lhht.xiaozhi.models.Message;
import com.lhht.xiaozhi.settings.SettingsManager;
import com.lhht.xiaozhi.views.WaveformView;
import com.lhht.xiaozhi.websocket.WebSocketManager;
import vip.inode.demo.opusaudiodemo.utils.OpusUtils;

import org.json.JSONObject;
import org.json.JSONException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements WebSocketManager.WebSocketListener {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    private static final int PLAY_BUFFER_SIZE = BUFFER_SIZE * 4;  // 播放缓冲区设置为录音缓冲区的4倍
    private static final int OPUS_FRAME_SIZE = 960; // 60ms at 16kHz

    private WebSocketManager webSocketManager;
    private SettingsManager settingsManager;
    private TextView connectionStatus;
    private Button connectButton;
    private ImageButton recordButton;
    private EditText messageInput;
    private Button sendButton;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isRecording = false;
    private ExecutorService executorService;
    private boolean isPlaying = false;
    private byte[] audioBuffer;
    private OpusUtils opusUtils;
    private long encoderHandle;
    private long decoderHandle;
    private short[] decodedBuffer;
    private short[] recordBuffer;
    private TextView callStatusText;
    private WaveformView waveformView;
    private View voiceContainer;
    private ExecutorService audioExecutor;  // 音频处理线程池
    private MessageAdapter messageAdapter;  // 添加消息适配器
    private RecyclerView messagesRecyclerView;  // 添加RecyclerView引用

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("MainActivity", "应用启动");
        setContentView(R.layout.activity_main);

        // 初始化视图
        callStatusText = findViewById(R.id.callStatusText);
        waveformView = findViewById(R.id.waveformView);
        voiceContainer = findViewById(R.id.voiceContainer);
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        
        // 设置RecyclerView
        messageAdapter = new MessageAdapter();
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);
        
        Log.i("MainActivity", "应用启动");

        // 初始化
        settingsManager = new SettingsManager(this);
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.i("MainActivity", "设备ID: " + deviceId);
        webSocketManager = new WebSocketManager(deviceId);
        webSocketManager.setListener(this);
        executorService = Executors.newSingleThreadExecutor();
        audioExecutor = Executors.newSingleThreadExecutor();

        // 初始化音频播放器
        int minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AUDIO_FORMAT
        );
        Log.i("MainActivity", "AudioTrack最小缓冲区: " + minBufferSize + " 字节");
        
        try {
            audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(Math.max(minBufferSize * 8, 32768))  // 增大缓冲区
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build();
            
            int state = audioTrack.getState();
            if (state == AudioTrack.STATE_INITIALIZED) {
                Log.i("MainActivity", "AudioTrack初始化成功");
                audioTrack.play();
            } else {
                Log.e("MainActivity", "AudioTrack初始化失败: " + state);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "创建AudioTrack失败", e);
        }

        // 初始化 Opus 编解码器
        opusUtils = OpusUtils.getInstance();
        encoderHandle = opusUtils.createEncoder(SAMPLE_RATE, 1, 10);
        decoderHandle = opusUtils.createDecoder(SAMPLE_RATE, 1);
        decodedBuffer = new short[OPUS_FRAME_SIZE];
        recordBuffer = new short[OPUS_FRAME_SIZE];

        // 初始化视图
        connectionStatus = findViewById(R.id.connectionStatus);
        connectButton = findViewById(R.id.connectButton);
        recordButton = findViewById(R.id.recordButton);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        ImageButton settingsButton = findViewById(R.id.settingsButton);

        // 设置按钮点击事件
        if (connectButton != null) connectButton.setOnClickListener(v -> toggleConnection());
        if (recordButton != null) recordButton.setOnClickListener(v -> toggleRecording());
        if (sendButton != null) sendButton.setOnClickListener(v -> sendMessage());
        if (settingsButton != null) settingsButton.setOnClickListener(v -> openSettings());

        // 检查并请求权限
        checkPermissions();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void toggleConnection() {
        if (!webSocketManager.isConnected()) {
            String wsUrl = settingsManager.getWsUrl();
            String token = settingsManager.getToken();
            boolean enableToken = settingsManager.isTokenEnabled();
            webSocketManager.connect(wsUrl, token, enableToken);
        } else {
            webSocketManager.disconnect();
        }
    }

    private void toggleRecording() {
        if (!isRecording) {
            startCall();
        } else {
            endCall();
        }
    }

    private void startCall() {
        if (!webSocketManager.isConnected()) {
            Toast.makeText(this, "请先连接服务器", Toast.LENGTH_SHORT).show();
            return;
        }

        // 发送开始通话消息
        try {
            JSONObject startMessage = new JSONObject();
            startMessage.put("type", "listen");
            startMessage.put("state", "start");
            startMessage.put("mode", "auto");  // 使用自动模式
            if (webSocketManager.getSessionId() != null) {
                startMessage.put("session_id", webSocketManager.getSessionId());
            }
            webSocketManager.sendMessage(startMessage.toString());
        } catch (JSONException e) {
            Log.e("XiaoZhi-Voice", "发送开始通话消息失败: " + e.getMessage());
            return;
        }

        if (audioRecord == null) {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
        }

        isRecording = true;
        runOnUiThread(() -> {
            voiceContainer.setVisibility(View.VISIBLE);
            callStatusText.setVisibility(View.VISIBLE);
            callStatusText.setText(R.string.calling);
        });
        
        executorService.execute(() -> {
            audioRecord.startRecording();
            short[] buffer = new short[OPUS_FRAME_SIZE]; // 960 samples
            byte[] encodedBuffer = new byte[1024]; // Opus编码后的缓冲区
            long lastAudioTime = System.currentTimeMillis();
            
            while (isRecording) {
                int samplesRead = audioRecord.read(buffer, 0, OPUS_FRAME_SIZE);
                if (samplesRead > 0) {
                    // 检测音量并更新波形
                    float amplitude = 0;
                    for (int i = 0; i < samplesRead; i++) {
                        amplitude = Math.max(amplitude, Math.abs(buffer[i]) / 32768.0f);
                    }
                    final float finalAmplitude = amplitude;
                    runOnUiThread(() -> waveformView.setAmplitude(finalAmplitude));
                    
                    // 检测静音
                    boolean isSilent = amplitude < 0.02f; // 2%的阈值
                    
                    // 更新最后一次有声音的时间
                    if (!isSilent) {
                        lastAudioTime = System.currentTimeMillis();
                    }
                    
                    // 编码为 Opus
                    int encodedBytes = opusUtils.encode(encoderHandle, buffer, 0, encodedBuffer);
                    if (encodedBytes > 0) {
                        byte[] encodedData = new byte[encodedBytes];
                        System.arraycopy(encodedBuffer, 0, encodedData, 0, encodedBytes);
                        webSocketManager.sendBinaryMessage(encodedData);
                    } else {
                        Log.e("XiaoZhi-Voice", "Opus编码失败: " + encodedBytes);
                    }
                    
                    // 检查静音超时(1秒)
                    if (System.currentTimeMillis() - lastAudioTime > 1000) {
                        // 发送静音帧
                        short[] silenceFrame = new short[OPUS_FRAME_SIZE];
                        int silenceBytes = opusUtils.encode(encoderHandle, silenceFrame, 0, encodedBuffer);
                        if (silenceBytes > 0) {
                            byte[] silenceData = new byte[silenceBytes];
                            System.arraycopy(encodedBuffer, 0, silenceData, 0, silenceBytes);
                            webSocketManager.sendBinaryMessage(silenceData);
                        }
                        runOnUiThread(() -> waveformView.setAmplitude(0));
                    }
                }
            }
        });
    }

    private void endCall() {
        isRecording = false;
        runOnUiThread(() -> {
            voiceContainer.setVisibility(View.GONE);
            callStatusText.setVisibility(View.GONE);
            waveformView.setAmplitude(0);
        });
        
        if (audioRecord != null) {
            audioRecord.stop();
        }

        // 发送停止通话消息
        try {
            JSONObject stopMessage = new JSONObject();
            stopMessage.put("type", "listen");
            stopMessage.put("state", "stop");
            if (webSocketManager.getSessionId() != null) {
                stopMessage.put("session_id", webSocketManager.getSessionId());
            }
            webSocketManager.sendMessage(stopMessage.toString());
        } catch (JSONException e) {
            Log.e("XiaoZhi-Voice", "发送停止通话消息失败: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (!message.isEmpty() && webSocketManager.isConnected()) {
            try {
                JSONObject jsonMessage = new JSONObject();
                jsonMessage.put("type", "listen");
                jsonMessage.put("state", "detect");
                jsonMessage.put("text", message);
                jsonMessage.put("source", "text");
                webSocketManager.sendMessage(jsonMessage.toString());
                messageAdapter.addMessage(new Message(message, false));  // 添加用户消息
                messageInput.setText("");
                messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);  // 滚动到底部
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public void onConnected() {
        addLog("WebSocket", "已连接");
        runOnUiThread(() -> {
            connectionStatus.setText(getString(R.string.connection_status, getString(R.string.status_connected)));
            connectButton.setText(R.string.disconnect);
        });
    }

    @Override
    public void onDisconnected() {
        addLog("WebSocket", "已断开");
        runOnUiThread(() -> {
            connectionStatus.setText(getString(R.string.connection_status, getString(R.string.status_disconnected)));
            connectButton.setText(R.string.connect);
            endCall();
        });
    }

    @Override
    public void onError(String error) {
        addLog("Error", error);
        runOnUiThread(() -> {
            connectionStatus.setText(getString(R.string.connection_status, getString(R.string.status_error)));
            Toast.makeText(this, "错误: " + error, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onMessage(String message) {
        addLog("Message", message);
        try {
            JSONObject jsonMessage = new JSONObject(message);
            String type = jsonMessage.getString("type");
            String state = jsonMessage.optString("state");
            
            if ("tts".equals(type)) {
                if ("start".equals(state) || "sentence_start".equals(state)) {
                    addLog("Audio", "准备播放音频");
                    // 开始播放音频
                    isPlaying = true;
                    audioExecutor.execute(() -> {
                        if (audioTrack != null) {
                            try {
                                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                                    addLog("Audio", "重新初始化AudioTrack");
                                    // 重新初始化AudioTrack
                                    int minBufferSize = AudioTrack.getMinBufferSize(
                                        SAMPLE_RATE,
                                        AudioFormat.CHANNEL_OUT_MONO,
                                        AUDIO_FORMAT
                                    );
                                    audioTrack = new AudioTrack.Builder()
                                        .setAudioAttributes(new AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                            .build())
                                        .setAudioFormat(new AudioFormat.Builder()
                                            .setEncoding(AUDIO_FORMAT)
                                            .setSampleRate(SAMPLE_RATE)
                                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                            .build())
                                        .setBufferSizeInBytes(Math.max(minBufferSize * 8, 32768))
                                        .setTransferMode(AudioTrack.MODE_STREAM)
                                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                                        .build();
                                }
                                
                                if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                                    audioTrack.play();
                                    addLog("Audio", "AudioTrack开始播放");
                                }
                            } catch (Exception e) {
                                addLog("Audio", "AudioTrack初始化/播放失败: " + e.getMessage());
                            }
                        }
                    });
                } else if ("stop".equals(state)) {
                    addLog("Audio", "停止接收新的音频数据");
                    isPlaying = false;  // 只停止接收新数据，不停止播放
                }
            }
            
            // 显示服务器的文本回复
            if (jsonMessage.has("text")) {
                String text = jsonMessage.getString("text");
                runOnUiThread(() -> {
                    messageAdapter.addMessage(new Message(text, true));  // 添加服务器消息
                    messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);  // 滚动到底部
                });
            }
        } catch (JSONException e) {
            addLog("Error", "解析消息失败: " + e.getMessage());
        }
    }

    @Override
    public void onBinaryMessage(byte[] data) {
        Log.i("XiaoZhi-Audio", String.format("收到音频数据: %d 字节, isPlaying=%b", data.length, isPlaying));
        
        if (!isPlaying) {
            Log.i("XiaoZhi-Audio", "当前不在接收状态，忽略音频数据");
            return;
        }

        // 复制数据，避免被修改
        final byte[] audioData = data.clone();
        
        // 在主线程中检查状态
        if (audioTrack == null || audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            Log.e("XiaoZhi-Audio", "错误: AudioTrack未初始化或状态错误");
            return;
        }

        if (decoderHandle == 0) {
            Log.e("XiaoZhi-Audio", "错误: Opus解码器未初始化");
            return;
        }

        // 在音频线程中处理
        audioExecutor.execute(() -> {
            try {
                // 解码 Opus 数据
                int decodedSamples = opusUtils.decode(decoderHandle, audioData, decodedBuffer);
                
                if (decodedSamples < 0) {
                    Log.e("XiaoZhi-Audio", String.format("Opus解码失败: %d", decodedSamples));
                    return;
                }
                
                if (decodedSamples == 0) {
                    return;
                }

                // 将 short[] 转换为 byte[]
                byte[] pcmData = new byte[decodedSamples * 2];
                for (int i = 0; i < decodedSamples; i++) {
                    short sample = decodedBuffer[i];
                    // 使用小端序（同Web端）
                    pcmData[i * 2] = (byte) (sample & 0xff);
                    pcmData[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
                }
                
                // 使用阻塞模式写入，确保数据完整性
                int written = 0;
                int retryCount = 0;
                while (written < pcmData.length && retryCount < 3) {  // 移除 isPlaying 检查，让数据继续写入
                    if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.play();
                        Log.i("XiaoZhi-Audio", "重新开始播放");
                    }
                    
                    int remaining = pcmData.length - written;
                    int result = audioTrack.write(pcmData, written, remaining, AudioTrack.WRITE_BLOCKING);
                    if (result < 0) {
                        Log.e("XiaoZhi-Audio", String.format("写入音频数据失败: %d", result));
                        retryCount++;
                        continue;
                    } else if (result == 0) {
                        break;
                    }
                    written += result;
                }
                
                Log.i("XiaoZhi-Audio", String.format("成功写入 %d/%d 字节", written, pcmData.length));
            } catch (Exception e) {
                Log.e("XiaoZhi-Audio", "播放音频失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void addLog(String tag, String message) {
        Log.i("XiaoZhi-" + tag, message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webSocketManager.disconnect();
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
        if (encoderHandle != 0) {
            opusUtils.destroyEncoder(encoderHandle);
            encoderHandle = 0;
        }
        if (decoderHandle != 0) {
            opusUtils.destroyDecoder(decoderHandle);
            decoderHandle = 0;
        }
        executorService.shutdown();
        audioExecutor.shutdown();
    }

    private void abortCurrentDialog() {
        try {
            JSONObject abortMessage = new JSONObject();
            abortMessage.put("type", "abort");
            if (webSocketManager.getSessionId() != null) {
                abortMessage.put("session_id", webSocketManager.getSessionId());
            }
            webSocketManager.sendMessage(abortMessage.toString());
        } catch (JSONException e) {
            Log.e("XiaoZhi-Voice", "发送中断消息失败: " + e.getMessage());
        }
    }
} 