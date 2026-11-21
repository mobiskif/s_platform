package ru.richelieu.voice;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Загружает слои векторного портрета из assets/richelieu.txt (см. art/make_art.py). */
final class FaceArt {

    static final class Layer {
        String group;
        Path path;
        Paint fill;   // null — без заливки
        Paint stroke; // null — без обводки
    }

    final List<Layer> layers = new ArrayList<Layer>();

    static FaceArt load(Context ctx) {
        FaceArt art = new FaceArt();
        BufferedReader r = null;
        try {
            InputStream in = ctx.getAssets().open("richelieu.txt");
            r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.length() == 0) continue;
                String[] p = line.split("\\|");
                if (p.length < 6) continue;
                Layer l = new Layer();
                l.group = p[0];
                int alpha = Math.round(Float.parseFloat(p[2]) * 255f);
                if (!p[1].equals("-")) {
                    l.fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                    l.fill.setStyle(Paint.Style.FILL);
                    l.fill.setColor(Color.parseColor(p[1]));
                    l.fill.setAlpha(alpha);
                }
                if (!p[3].equals("-")) {
                    l.stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                    l.stroke.setStyle(Paint.Style.STROKE);
                    l.stroke.setStrokeCap(Paint.Cap.ROUND);
                    l.stroke.setStrokeJoin(Paint.Join.ROUND);
                    l.stroke.setColor(Color.parseColor(p[3]));
                    l.stroke.setStrokeWidth(Float.parseFloat(p[4]));
                    l.stroke.setAlpha(alpha);
                }
                l.path = parsePath(p[5]);
                art.layers.add(l);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ignored) {
                }
            }
        }
        return art;
    }

    /** Путь из абсолютных команд M L C Q Z, разделённых пробелами. */
    static Path parsePath(String d) {
        String[] t = d.trim().split("\\s+");
        Path p = new Path();
        int i = 0;
        while (i < t.length) {
            char c = t[i++].charAt(0);
            switch (c) {
                case 'M':
                    p.moveTo(f(t[i]), f(t[i + 1]));
                    i += 2;
                    break;
                case 'L':
                    p.lineTo(f(t[i]), f(t[i + 1]));
                    i += 2;
                    break;
                case 'Q':
                    p.quadTo(f(t[i]), f(t[i + 1]), f(t[i + 2]), f(t[i + 3]));
                    i += 4;
                    break;
                case 'C':
                    p.cubicTo(f(t[i]), f(t[i + 1]), f(t[i + 2]), f(t[i + 3]), f(t[i + 4]), f(t[i + 5]));
                    i += 6;
                    break;
                case 'Z':
                    p.close();
                    break;
                default:
                    throw new IllegalArgumentException("Неизвестная команда пути: " + c);
            }
        }
        return p;
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }
}
