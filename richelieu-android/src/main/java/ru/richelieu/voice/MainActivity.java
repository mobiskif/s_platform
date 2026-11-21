package ru.richelieu.voice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Чат: что бы вы ни написали, говорящее фото произнесёт это синтезированным голосом. */
public class MainActivity extends Activity {

    private static final int MAX_CHUNK = 280;
    private static final Pattern FRENCH = Pattern.compile("[éèêëàâçîïôùûœ]|\\b(le|la|les|un|une|des|et|je|vous|nous|est|que|pour|dans|monsieur)\\b",
            Pattern.CASE_INSENSITIVE);

    private AvatarView face;
    private ListView chat;
    private EditText input;
    private TextView sendBtn;
    private final List<String[]> messages = new ArrayList<String[]>(); // {"me"|"him", text}
    private ChatAdapter adapter;

    private TextToSpeech tts;
    private volatile boolean ttsReady;
    private String pendingText;
    private SharedPreferences prefs;
    private float pitch, rate;

    private final Object lock = new Object();
    private final Map<String, String> chunkText = new HashMap<String, String>();
    private int counter;
    private volatile boolean speaking;
    private Locale lastLocale = new Locale("ru", "RU");

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("voice", MODE_PRIVATE);
        pitch = prefs.getFloat("pitch", 0.55f);
        rate = prefs.getFloat("rate", 0.9f);

        face = (AvatarView) findViewById(R.id.face);
        face.setSpeechRate(rate);
        chat = (ListView) findViewById(R.id.chat);
        input = (EditText) findViewById(R.id.input);
        sendBtn = (TextView) findViewById(R.id.btn_send);

        messages.add(new String[]{"him", getString(R.string.greeting)});
        adapter = new ChatAdapter();
        chat.setAdapter(adapter);
        chat.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                speak(messages.get(pos)[1]);
            }
        });

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (text.length() == 0) {
                    if (speaking) stopSpeaking();
                    return;
                }
                input.setText("");
                messages.add(new String[]{"me", text});
                adapter.notifyDataSetChanged();
                chat.setSelection(messages.size() - 1);
                speak(text);
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    Toast.makeText(MainActivity.this, R.string.tts_fail, Toast.LENGTH_LONG).show();
                    return;
                }
                tts.setOnUtteranceProgressListener(listener);
                ttsReady = true;
                if (pendingText != null) {
                    String t = pendingText;
                    pendingText = null;
                    speak(t);
                }
            }
        });
    }

    // ------------------------------------------------------------------ речь

    private final UtteranceProgressListener listener = new UtteranceProgressListener() {
        @Override
        public void onStart(String id) {
            setFaceSpeaking(true);
        }

        @Override
        public void onDone(String id) {
            finished(id);
        }

        @Override
        public void onError(String id) {
            finished(id);
        }

        @Override
        public void onStop(String id, boolean interrupted) {
            finished(id);
        }

        // Android 8+ (API 26): вызывается на каждое произносимое слово.
        public void onRangeStart(String id, int start, int end, int frame) {
            final String text;
            synchronized (lock) {
                text = chunkText.get(id);
            }
            if (text == null || start < 0 || end > text.length() || start >= end) return;
            final String word = text.substring(start, end);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    face.onWord(word);
                }
            });
        }
    };

    private void finished(String id) {
        boolean none;
        synchronized (lock) {
            chunkText.remove(id);
            none = chunkText.isEmpty();
        }
        if (none) setFaceSpeaking(false);
    }

    private void setFaceSpeaking(final boolean s) {
        speaking = s;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                face.setSpeaking(s);
                sendBtn.setText(s ? "■" : "➤");
            }
        });
    }

    private void speak(String text) {
        if (!ttsReady) {
            pendingText = text;
            return;
        }
        Locale loc = detectLocale(text);
        int r = tts.setLanguage(loc);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, R.string.lang_missing, Toast.LENGTH_LONG).show();
        }
        applySavedVoice(loc);
        tts.setPitch(pitch);
        tts.setSpeechRate(rate);
        face.setSpeechRate(rate);
        lastLocale = loc;

        List<String> chunks = split(text);
        List<String> ids = new ArrayList<String>();
        synchronized (lock) {
            chunkText.clear();
            for (String c : chunks) {
                String id = "u" + (++counter);
                ids.add(id);
                chunkText.put(id, c);
            }
        }
        for (int i = 0; i < chunks.size(); i++) {
            tts.speak(chunks.get(i), i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, ids.get(i));
        }
    }

    /** Установленные голоса для языка, по имени (стабильный порядок для переключения). */
    private List<Voice> voicesFor(Locale loc) {
        List<Voice> list = new ArrayList<Voice>();
        try {
            Set<Voice> all = tts.getVoices();
            if (all != null) {
                for (Voice v : all) {
                    if (v.getLocale().getLanguage().equals(loc.getLanguage())) list.add(v);
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(list, new Comparator<Voice>() {
            @Override
            public int compare(Voice a, Voice b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return list;
    }

    private void applySavedVoice(Locale loc) {
        String name = prefs.getString("voice_" + loc.getLanguage(), null);
        if (name == null) return;
        for (Voice v : voicesFor(loc)) {
            if (v.getName().equals(name)) {
                tts.setVoice(v);
                return;
            }
        }
    }

    private String currentVoiceName(Locale loc) {
        String name = prefs.getString("voice_" + loc.getLanguage(), null);
        return name != null ? name : getString(R.string.voice_default);
    }

    private void stopSpeaking() {
        synchronized (lock) {
            chunkText.clear();
        }
        if (tts != null) tts.stop();
        setFaceSpeaking(false);
    }

    private static Locale detectLocale(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock bl = Character.UnicodeBlock.of(text.charAt(i));
            if (bl == Character.UnicodeBlock.CYRILLIC) return new Locale("ru", "RU");
        }
        if (FRENCH.matcher(text).find()) return Locale.FRANCE;
        return Locale.US;
    }

    /** Делит текст на куски по предложениям (движки ограничивают длину; так же точнее синхронизация рта). */
    private static List<String> split(String text) {
        List<String> out = new ArrayList<String>();
        String[] sentences = text.split("(?<=[.!?…])\\s+|\\n+");
        StringBuilder cur = new StringBuilder();
        for (String s : sentences) {
            while (s.length() > MAX_CHUNK) {
                int cut = s.lastIndexOf(' ', MAX_CHUNK);
                if (cut <= 0) cut = MAX_CHUNK;
                flush(out, cur);
                out.add(s.substring(0, cut));
                s = s.substring(cut).trim();
            }
            if (cur.length() + s.length() + 1 > MAX_CHUNK) flush(out, cur);
            if (cur.length() > 0) cur.append(' ');
            cur.append(s);
        }
        flush(out, cur);
        if (out.isEmpty()) out.add(text);
        return out;
    }

    private static void flush(List<String> out, StringBuilder cur) {
        if (cur.length() > 0) {
            out.add(cur.toString());
            cur.setLength(0);
        }
    }

    // ------------------------------------------------------------------ настройки голоса

    private void showSettings() {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * d);
        box.setPadding(pad, pad / 2, pad, 0);

        box.addView(label(getString(R.string.pitch)));
        final SeekBar pitchBar = new SeekBar(this);
        pitchBar.setMax(100); // 0.3 .. 1.3
        pitchBar.setProgress(Math.round((pitch - 0.3f) * 100f));
        box.addView(pitchBar);

        box.addView(label(getString(R.string.rate)));
        final SeekBar rateBar = new SeekBar(this);
        rateBar.setMax(100); // 0.5 .. 1.5
        rateBar.setProgress(Math.round((rate - 0.5f) * 100f));
        box.addView(rateBar);

        final TextView voiceBtn = label(getString(R.string.voice_label, currentVoiceName(lastLocale)));
        voiceBtn.setTextColor(0xFF8EC5FF);
        voiceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!ttsReady) return;
                List<Voice> list = voicesFor(lastLocale);
                if (list.isEmpty()) {
                    Toast.makeText(MainActivity.this, R.string.lang_missing, Toast.LENGTH_LONG).show();
                    return;
                }
                String cur = prefs.getString("voice_" + lastLocale.getLanguage(), null);
                int idx = -1;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getName().equals(cur)) idx = i;
                }
                Voice next = list.get((idx + 1) % list.size());
                prefs.edit().putString("voice_" + lastLocale.getLanguage(), next.getName()).apply();
                voiceBtn.setText(getString(R.string.voice_label, next.getName()));
                tts.setVoice(next);
                tts.setPitch(0.3f + pitchBar.getProgress() / 100f);
                tts.setSpeechRate(0.5f + rateBar.getProgress() / 100f);
                tts.speak(getString(R.string.sample), TextToSpeech.QUEUE_FLUSH, null, "sample");
            }
        });
        box.addView(voiceBtn);

        TextView sys = label(getString(R.string.tts_settings));
        sys.setTextColor(0xFF8EC5FF);
        sys.setPadding(0, pad / 2, 0, pad / 2);
        sys.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                } catch (Exception e) {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                }
            }
        });
        box.addView(sys);

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.settings)
                .setView(box)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dlg, int which) {
                        pitch = 0.3f + pitchBar.getProgress() / 100f;
                        rate = 0.5f + rateBar.getProgress() / 100f;
                        prefs.edit().putFloat("pitch", pitch).putFloat("rate", rate).apply();
                        speak(getString(R.string.sample));
                    }
                })
                .show();
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        return t;
    }

    // ------------------------------------------------------------------ чат

    private class ChatAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return messages.size();
        }

        @Override
        public Object getItem(int i) {
            return messages.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            String[] m = messages.get(i);
            boolean mine = m[0].equals("me");
            float d = getResources().getDisplayMetrics().density;

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(mine ? Gravity.END : Gravity.START);
            row.setPadding(0, (int) (3 * d), 0, (int) (3 * d));

            TextView tv = new TextView(MainActivity.this);
            tv.setText(m[1]);
            tv.setTextSize(16);
            tv.setTextColor(0xFFEAF2FA);
            tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
            tv.setPadding((int) (14 * d), (int) (9 * d), (int) (14 * d), (int) (9 * d));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(mine ? 0xFF2F6FB3 : 0xFF2A3846);
            bg.setCornerRadius(18 * d);
            if (!mine) bg.setStroke((int) d, 0x668EC5FF);
            tv.setBackground(bg);
            row.addView(tv);
            return row;
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
