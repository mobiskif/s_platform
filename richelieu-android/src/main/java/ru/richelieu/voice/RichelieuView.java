package ru.richelieu.voice;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Анимированный портрет кардинала. Рот двигается в такт речи (по словам, которые
 * сообщает синтезатор), плюс моргание, подъём бровей на «!» и «?» и лёгкое покачивание головы.
 */
public class RichelieuView extends View {

    private static final float W = 200f, H = 240f;
    private static final float MOUTH_LINE_Y = 136f;
    private static final float EYE_TOP_Y = 94f;
    private static final float JAW_DROP = 7f;
    private static final int BG = 0xFF240910;

    private static final float JAW_PX = 40f;   // макс. раскрытие челюсти, пикселей растра фото
    private static final int MESH_STEP = 6;     // шаг сетки деформации, пикселей растра

    private final FaceArt art;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint cavPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cav = new Path();
    private final Path lash = new Path();
    private float[] mesh, meshHx, meshW;
    private int meshCols, meshRows;
    private final Random rnd = new Random();

    private boolean speaking;
    private float mouth, energy = 0.7f, phase;
    private long pauseFrom, pauseUntil;           // «закрытый рот» между фразами, мс
    private float blink, brow, browTarget, bob;
    private long blinkStart, nextBlink, browOffAt, lastFrame;
    private float rate = 1f;

    public RichelieuView(Context c) {
        this(c, null);
    }

    public RichelieuView(Context c, AttributeSet a) {
        super(c, a);
        art = FaceArt.load(c);
        lashPaint.setStyle(Paint.Style.STROKE);
        lashPaint.setStrokeCap(Paint.Cap.ROUND);
        lashPaint.setStrokeWidth(2f);
        lashPaint.setColor(0xFF3A2A24);
        lidPaint.setStyle(Paint.Style.FILL);
        if (art.photo != null) initMesh(art.photo);
        nextBlink = now() + 1500;
    }

    private static long now() {
        return android.os.SystemClock.uptimeMillis();
    }

    public void setSpeaking(boolean s) {
        speaking = s;
        if (s) {
            pauseUntil = 0;
        }
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
            long t = now();
            pauseFrom = t + (long) (Math.max(2, letters) * 75 / rate);
            pauseUntil = pauseFrom + 280;
            if (last == '!' || last == '?') {
                browTarget = 1f;
                browOffAt = t + 900;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long t = now();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (t - lastFrame) / 1000f);
        lastFrame = t;
        update(t, dt);

        canvas.drawColor(BG);
        float s = Math.min(getWidth() / W, getHeight() / H);
        canvas.save();
        canvas.translate((getWidth() - W * s) / 2f, (getHeight() - H * s) / 2f);
        canvas.scale(s, s);
        // покачивание головы при речи
        canvas.translate(0f, bob * 1.2f);
        canvas.rotate(bob * 0.6f, 100f, 170f);

        for (int li = 0; li <= art.layers.size(); li++) {
            if (art.photo != null && li == art.photo.layerIndex) drawPhoto(canvas, art.photo);
            if (li == art.layers.size()) break;
            FaceArt.Layer l = art.layers.get(li);
            canvas.save();
            if (l.group.equals("brows")) {
                canvas.translate(0f, -3.2f * brow - 0.8f * (speaking ? energy : 0f));
            } else if (l.group.equals("lids")) {
                if (blink < 0.02f) {
                    canvas.restore();
                    continue;
                }
                canvas.scale(1f, blink, 100f, EYE_TOP_Y);
            } else if (l.group.equals("mouth")) {
                if (mouth < 0.03f) {
                    canvas.restore();
                    continue;
                }
                canvas.scale(1f, mouth * 0.95f, 100f, MOUTH_LINE_Y);
            } else if (l.group.equals("jaw")) {
                canvas.translate(0f, JAW_DROP * mouth);
            }
            if (l.fill != null) canvas.drawPath(l.path, l.fill);
            if (l.stroke != null) canvas.drawPath(l.path, l.stroke);
            canvas.restore();
        }
        canvas.restore();

        postInvalidateOnAnimation(); // непрерывная анимация: моргание идёт и в покое
    }

    // ------------------------------------------------------------------ фото-лицо

    private static float smooth(float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        return t * t * (3f - 2f * t);
    }

    private void initMesh(FaceArt.Photo ph) {
        int w = ph.bmp.getWidth(), h = ph.bmp.getHeight();
        meshCols = Math.max(1, w / MESH_STEP);
        meshRows = Math.max(1, h / MESH_STEP);
        mesh = new float[(meshCols + 1) * (meshRows + 1) * 2];
        meshHx = new float[meshCols + 1];
        meshW = new float[meshRows + 1];
        for (int c = 0; c <= meshCols; c++) {
            float dx = (c * (float) w / meshCols - ph.mouthX) / 190f;
            meshHx[c] = (float) Math.exp(-dx * dx);          // затухание к краям лица
        }
        for (int r = 0; r <= meshRows; r++) {
            float y = r * (float) h / meshRows;
            meshW[r] = smooth((y - (ph.mouthY - 6f)) / 12f);  // 0 — выше губ, 1 — ниже
        }
    }

    /** Лицо: нижняя часть (ниже линии губ) опускается, между губами — полость рта; веки; моргание. */
    private void drawPhoto(Canvas c, FaceArt.Photo ph) {
        Bitmap bmp = ph.bmp;
        float w = bmp.getWidth(), h = bmp.getHeight();
        float d = JAW_PX * mouth;
        c.save();
        c.translate(ph.bx, ph.by);
        c.scale(ph.u, ph.u);

        if (d < 0.4f) {
            c.drawBitmap(bmp, 0f, 0f, bmpPaint);
        } else {
            int k = 0;
            for (int r = 0; r <= meshRows; r++) {
                float y = r * h / meshRows;
                for (int col = 0; col <= meshCols; col++) {
                    mesh[k++] = col * w / meshCols;
                    mesh[k++] = y + d * meshW[r] * meshHx[col];
                }
            }
            c.drawBitmapMesh(bmp, meshCols, meshRows, mesh, 0, null, 0, bmpPaint);

            float ym = ph.mouthY, mx = ph.mouthX;
            float bot = ym + d + 6f;
            float cy2 = ym + (bot - ym) * 1.33f;
            cav.reset();
            cav.moveTo(ph.cornerL + 4f, ym);
            cav.quadTo(mx, ym - 5f, ph.cornerR - 4f, ym);
            cav.cubicTo(ph.cornerR - 12f, cy2, ph.cornerL + 12f, cy2, ph.cornerL + 4f, ym);
            cav.close();
            cavPaint.setStyle(Paint.Style.FILL);
            cavPaint.setColor(0xFF3A0C10);
            c.drawPath(cav, cavPaint);
            cavPaint.setColor(0xD9D9CDB5);   // верхние зубы
            c.drawRoundRect(mx - 18f, ym - 2f, mx + 58f, ym + 1f + Math.min(d * 0.2f, 6f), 3f, 3f, cavPaint);
            cavPaint.setColor(0xE6A94A4A);   // язык
            c.drawOval(mx - 36f, ym + d * 0.72f - d * 0.24f, mx + 36f, ym + d * 0.72f + d * 0.24f, cavPaint);
        }

        if (blink > 0.02f) {
            drawLid(c, ph, ph.eyeLx, ph.eyeLy);
            drawLid(c, ph, ph.eyeRx, ph.eyeRy);
        }
        c.restore();
    }

    private void drawLid(Canvas c, FaceArt.Photo ph, float ex, float ey) {
        float rx = ph.eyeW, ry = ph.eyeH, hh = ry * blink;
        int solid = (ph.lid & 0x00FFFFFF) | 0xF2000000;
        int clear = ph.lid & 0x00FFFFFF;
        lidPaint.setShader(new RadialGradient(0f, 0f, 1f, new int[]{solid, solid, clear},
                new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        c.save();
        c.translate(ex, ey - ry + hh);
        c.scale(rx * 1.12f, Math.max(0.01f, hh * 1.12f));
        c.drawCircle(0f, 0f, 1f, lidPaint);
        c.restore();
        if (blink > 0.5f) {
            float y = ey - ry + 2f * hh;
            lash.reset();
            lash.moveTo(ex - rx * 0.85f, y);
            lash.quadTo(ex, y + 3f, ex + rx * 0.85f, y);
            c.drawPath(lash, lashPaint);
        }
    }

    private void update(long t, float dt) {
        // --- рот
        float target = 0f;
        if (speaking) {
            phase += dt * 2f * (float) Math.PI * 2.4f * rate;
            boolean paused = t >= pauseFrom && t < pauseUntil;
            if (!paused) {
                target = energy * (0.22f + 0.78f * Math.abs((float) Math.sin(phase)));
            }
        }
        mouth += (target - mouth) * Math.min(1f, dt * 20f);

        // --- брови
        if (browTarget > 0f && t > browOffAt) browTarget = 0f;
        brow += (browTarget - brow) * Math.min(1f, dt * 10f);

        // --- голова
        float bobTarget = speaking ? (float) Math.sin(phase * 0.5f) * (0.4f + 0.6f * energy) : 0f;
        bob += (bobTarget - bob) * Math.min(1f, dt * 6f);

        // --- моргание
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
