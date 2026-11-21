package ru.richelieu.voice;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

    /** Параметры анимируемого фото-лица (строка photo в richelieu.txt); null — рисованное лицо без фото. */
    static final class Photo {
        Bitmap bmp;
        float bx, by, u;            // положение (ед. холста) и размер пикселя растра в единицах холста
        float mouthY, mouthX, cornerL, cornerR;
        float eyeLx, eyeLy, eyeRx, eyeRy, eyeW, eyeH;
        int skin, lid;
        int layerIndex;             // фото рисуется перед слоем с этим индексом
    }

    Photo photo;

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
                if (p[0].equals("photo")) {
                    art.photo = parsePhoto(ctx, p[5], art.layers.size());
                    continue;
                }
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

    private static Photo parsePhoto(Context ctx, String spec, int index) {
        Photo ph = new Photo();
        ph.layerIndex = index;
        for (String kv : spec.trim().split("\\s+")) {
            String[] a = kv.split("=");
            String k = a[0], v = a[1];
            if (k.equals("skin")) ph.skin = Color.parseColor(v);
            else if (k.equals("lid")) ph.lid = Color.parseColor(v);
            else if (k.equals("eyeL")) { String[] c = v.split(","); ph.eyeLx = f(c[0]); ph.eyeLy = f(c[1]); }
            else if (k.equals("eyeR")) { String[] c = v.split(","); ph.eyeRx = f(c[0]); ph.eyeRy = f(c[1]); }
            else if (k.equals("bx")) ph.bx = f(v);
            else if (k.equals("by")) ph.by = f(v);
            else if (k.equals("u")) ph.u = f(v);
            else if (k.equals("mouthY")) ph.mouthY = f(v);
            else if (k.equals("mouthX")) ph.mouthX = f(v);
            else if (k.equals("cornerL")) ph.cornerL = f(v);
            else if (k.equals("cornerR")) ph.cornerR = f(v);
            else if (k.equals("eyeRx")) ph.eyeW = f(v);
            else if (k.equals("eyeRy")) ph.eyeH = f(v);
        }
        InputStream in = null;
        try {
            in = ctx.getAssets().open("face.png");
            ph.bmp = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            return null; // нет фото — рисуем без него
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
        return ph;
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
