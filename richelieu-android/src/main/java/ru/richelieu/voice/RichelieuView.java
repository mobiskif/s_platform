package ru.richelieu.voice;

import android.content.Context;
import android.graphics.Canvas;
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

    private final FaceArt art;
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

        for (FaceArt.Layer l : art.layers) {
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
