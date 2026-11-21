package ru.richelieu.voice;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;

/**
 * «Говорящее фото»: показывает снимок (assets/face.jpg) и оживляет лицо — нижняя часть лица опускается
 * в такт словам (сетка деформации), между губами появляется полость рта, моргание, лёгкое покачивание.
 * Ориентиры лица — в assets/avatar.properties (см. art/make_photo.py).
 */
public class AvatarView extends View {

    private static final int BG = 0xFF101820;

    // --- фото и ориентиры (в пикселях растра)
    private Bitmap bmp;
    private float mouthX, mouthY, cornerL, cornerR, eyeLx, eyeLy, eyeRx, eyeRy, eyeW, eyeH;
    private float faceX, faceY, jaw, chinY;
    private int lidColor;

    // --- сетка деформации
    private float[] mesh, meshHx, meshW;
    private int cols, rows;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random rnd = new Random();

    // --- состояние анимации
    private boolean speaking;
    private float mouth, energy = 0.7f, phase, blink, bob, rate = 1f;
    private long pauseFrom, pauseUntil, blinkStart, nextBlink, lastFrame;

    public AvatarView(Context c) {
        this(c, null);
    }

    public AvatarView(Context c, AttributeSet a) {
        super(c, a);
        load(c);
        lashPaint.setStyle(Paint.Style.STROKE);
        lashPaint.setStrokeCap(Paint.Cap.ROUND);
        lashPaint.setStrokeWidth(2f);
        lashPaint.setColor(0xFF3A2A24);
        nextBlink = now() + 1500;
    }

    private void load(Context c) {
        InputStream in = null;
        try {
            Properties p = new Properties();
            in = c.getAssets().open("avatar.properties");
            p.load(in);
            in.close();
            in = c.getAssets().open("face.jpg");
            bmp = BitmapFactory.decodeStream(in);
            mouthX = f(p, "mouthX");
            mouthY = f(p, "mouthY");
            cornerL = f(p, "cornerL");
            cornerR = f(p, "cornerR");
            eyeLx = f(p, "eyeLx");
            eyeLy = f(p, "eyeLy");
            eyeRx = f(p, "eyeRx");
            eyeRy = f(p, "eyeRy");
            eyeW = f(p, "eyeW");
            eyeH = f(p, "eyeH");
            faceX = f(p, "faceX");
            faceY = f(p, "faceY");
            jaw = f(p, "jaw");
            chinY = f(p, "chinY");
            lidColor = Color.parseColor(p.getProperty("lid"));
            float sigma = f(p, "sigma"), decay = f(p, "decay");
            int step = (int) f(p, "step");

            cols = Math.max(1, bmp.getWidth() / step);
            rows = Math.max(1, bmp.getHeight() / step);
            mesh = new float[(cols + 1) * (rows + 1) * 2];
            meshHx = new float[cols + 1];
            meshW = new float[rows + 1];
            for (int i = 0; i <= cols; i++) {
                float dx = (i * (float) bmp.getWidth() / cols - mouthX) / sigma;
                meshHx[i] = (float) Math.exp(-dx * dx);                 // затухание к краям лица
            }
            for (int r = 0; r <= rows; r++) {
                float y = r * (float) bmp.getHeight() / rows;
                // 0 выше губ -> 1 ниже губ -> снова 0 ниже подбородка (куртка не «гнётся»)
                meshW[r] = smooth((y - (mouthY - 6f)) / 12f) * (1f - smooth((y - chinY) / decay));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static float f(Properties p, String k) {
        return Float.parseFloat(p.getProperty(k));
    }

    private static float smooth(float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        return t * t * (3f - 2f * t);
    }

    private static long now() {
        return android.os.SystemClock.uptimeMillis();
    }

    // ------------------------------------------------------------------ управление

    public void setSpeaking(boolean s) {
        speaking = s;
        if (s) pauseUntil = 0;
        invalidate();
    }

    public void setSpeechRate(float r) {
        rate = r < 0.3f ? 0.3f : r;
    }

    /** Вызывается, когда синтезатор начал произносить слово. */
    public void onWord(String word) {
        if (word == null || word.length() == 0) return;
        int vowels = 0, letters = 0;
        for (int i = 0; i < word.length(); i++) {
            char ch = Character.toLowerCase(word.charAt(i));
            if (Character.isLetter(ch)) {
                letters++;
                if ("аеёиоуыэюяaeiouyàâéèêëîïôùûœ".indexOf(ch) >= 0) vowels++;
            }
        }
        energy = 0.55f + 0.45f * Math.min(1f, vowels / 3f);
        char last = word.charAt(word.length() - 1);
        if (".,;:!?…".indexOf(last) >= 0) {
            pauseFrom = now() + (long) (Math.max(2, letters) * 75 / rate);
            pauseUntil = pauseFrom + 280;
        }
        invalidate();
    }

    // ------------------------------------------------------------------ рисование

    @Override
    protected void onDraw(Canvas canvas) {
        long t = now();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (t - lastFrame) / 1000f);
        lastFrame = t;
        update(t, dt);

        canvas.drawColor(BG);
        float vw = getWidth(), vh = getHeight();
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        // фото заполняет окно; лицо выравниваем по центру окна (чуть выше середины), края не показываем
        float s = Math.max(vw / bw, vh / bh) * 1.06f;
        float ox = clamp(vw * 0.5f - faceX * s, vw - bw * s, 0f);
        float oy = clamp(vh * 0.44f - faceY * s, vh - bh * s, 0f);

        canvas.save();
        canvas.translate(0f, bob * vh * 0.004f);                // покачивание при речи
        canvas.rotate(bob * 0.5f, vw * 0.5f, vh * 0.8f);
        canvas.translate(ox, oy);
        canvas.scale(s, s);
        drawFace(canvas);
        canvas.restore();

        postInvalidateOnAnimation(); // непрерывная анимация: моргание идёт и в покое
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void drawFace(Canvas c) {
        float w = bmp.getWidth(), h = bmp.getHeight();
        float d = jaw * mouth;

        if (d < 0.4f) {
            c.drawBitmap(bmp, 0f, 0f, bmpPaint);
        } else {
            int k = 0;
            for (int r = 0; r <= rows; r++) {
                float y = r * h / rows;
                for (int col = 0; col <= cols; col++) {
                    mesh[k++] = col * w / cols;
                    mesh[k++] = y + d * meshW[r] * meshHx[col];
                }
            }
            c.drawBitmapMesh(bmp, cols, rows, mesh, 0, null, 0, bmpPaint);

            // полость рта между разошедшимися губами
            float ym = mouthY, mx = mouthX;
            float bot = ym + d + 5f;
            float cy2 = ym + (bot - ym) * 1.33f;
            path.reset();
            path.moveTo(cornerL + 3f, ym);
            path.quadTo(mx, ym - 4f, cornerR - 3f, ym);
            path.cubicTo(cornerR - 10f, cy2, cornerL + 10f, cy2, cornerL + 3f, ym);
            path.close();
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(0xFF3A0C10);
            c.drawPath(path, fillPaint);
            fillPaint.setColor(0xD9D9CDB5);   // верхние зубы
            c.drawRoundRect(mx - 15f, ym - 2f, mx + 48f, ym + 1f + Math.min(d * 0.2f, 5f), 3f, 3f, fillPaint);
            fillPaint.setColor(0xE6A94A4A);   // язык
            c.drawOval(mx - 30f, ym + d * 0.72f - d * 0.24f, mx + 30f, ym + d * 0.72f + d * 0.24f, fillPaint);
        }

        if (blink > 0.02f) {
            drawLid(c, eyeLx, eyeLy);
            drawLid(c, eyeRx, eyeRy);
        }
    }

    private void drawLid(Canvas c, float ex, float ey) {
        float hh = eyeH * blink;
        int solid = (lidColor & 0x00FFFFFF) | 0xF2000000;
        int clear = lidColor & 0x00FFFFFF;
        lidPaint.setStyle(Paint.Style.FILL);
        lidPaint.setShader(new RadialGradient(0f, 0f, 1f, new int[]{solid, solid, clear},
                new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        c.save();
        c.translate(ex, ey - eyeH + hh);
        c.scale(eyeW * 1.12f, Math.max(0.01f, hh * 1.12f));
        c.drawCircle(0f, 0f, 1f, lidPaint);
        c.restore();
        if (blink > 0.5f) {
            float y = ey - eyeH + 2f * hh;
            path.reset();
            path.moveTo(ex - eyeW * 0.85f, y);
            path.quadTo(ex, y + 3f, ex + eyeW * 0.85f, y);
            c.drawPath(path, lashPaint);
        }
    }

    private void update(long t, float dt) {
        float target = 0f;
        if (speaking) {
            phase += dt * 2f * (float) Math.PI * 2.4f * rate;
            boolean paused = t >= pauseFrom && t < pauseUntil;
            if (!paused) {
                target = energy * (0.22f + 0.78f * Math.abs((float) Math.sin(phase)));
            }
        }
        mouth += (target - mouth) * Math.min(1f, dt * 20f);

        float bobTarget = speaking ? (float) Math.sin(phase * 0.5f) * (0.4f + 0.6f * energy) : 0f;
        bob += (bobTarget - bob) * Math.min(1f, dt * 6f);

        if (blinkStart == 0 && t >= nextBlink) blinkStart = t;
        if (blinkStart != 0) {
            float p = (t - blinkStart) / 170f;
            if (p >= 1f) {
                blink = 0f;
                blinkStart = 0;
                nextBlink = t + 2200 + rnd.nextInt(3200);
            } else {
                blink = p < 0.5f ? p * 2f : (1f - p) * 2f;
            }
        }
    }
}
