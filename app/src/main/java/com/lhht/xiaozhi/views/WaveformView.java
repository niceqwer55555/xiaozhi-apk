package com.lhht.xiaozhi.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;
import com.lhht.xiaozhi.R;

public class WaveformView extends View {
    private Paint wavePaint;
    private Path wavePath;
    private float amplitude = 0f; // 波形振幅
    private float phase = 0f; // 波形相位
    private static final float FREQUENCY = 1.2f; // 波形频率
    private static final float VELOCITY = 0.2f; // 波形移动速度

    public WaveformView(Context context) {
        super(context);
        init();
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        wavePaint = new Paint();
        wavePaint.setColor(ContextCompat.getColor(getContext(), R.color.wave_color));
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(4f);
        wavePaint.setAntiAlias(true);
        
        wavePath = new Path();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float width = getWidth();
        float height = getHeight();
        float centerY = height / 2;
        
        wavePath.reset();
        wavePath.moveTo(0, centerY);
        
        // 绘制波形
        for (float x = 0; x < width; x++) {
            float y = centerY + amplitude * 
                (float) Math.sin(2 * Math.PI * (x / width) * FREQUENCY + phase);
            wavePath.lineTo(x, y);
        }
        
        canvas.drawPath(wavePath, wavePaint);
        
        // 更新相位，产生移动效果
        phase += VELOCITY;
        if (phase > 2 * Math.PI) {
            phase = 0;
        }
        
        // 继续动画
        invalidate();
    }

    public void setAmplitude(float amplitude) {
        this.amplitude = amplitude * getHeight() / 4; // 根据视图高度调整振幅
        invalidate();
    }
} 