package gr.mpekiaris.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.CheckBox;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {

    private static final int BG = 0xFF0E1116;
    private static final int CARD = 0xFF181D25;
    private static final int CARD_2 = 0xFF222833;
    private static final int TEXT = 0xFFE6E8EB;
    private static final int MUTED = 0xFF8B93A1;
    private static final int ACCENT = 0xFFF2A33A;

    private static final int REQ_CAL = 10;
    private static final int REQ_CALL = 11;
    private static final int REQ_PICK = 12;

    private static final Locale EL = new Locale("el", "GR");

    private SharedPreferences prefs;
    private ScrollView scroll;
    private TextView dateView;
    private TextView updateBanner;
    private EditText search;
    private LinearLayout results;
    private LinearLayout calendarBox;
    private LinearLayout favBox;
    private LinearLayout callsBox;
    private LinearLayout todoBox;
    private LinearLayout dock;

    private final List<App> apps = new ArrayList<>();
    private boolean showingAll = false;
    private String pendingCall;

    static class App {
        String label, norm, pkg, cls;
        Drawable icon;
    }

    // ---------- lifecycle ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("dash", MODE_PRIVATE);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDate();
        loadApps();
        renderFav();
        renderCalendar();
        fetchPlatform();
        renderCalls();
        renderTodos();
        renderDock();
        refreshUpdateBanner();
        checkUpdate(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (handleInstallStatus(intent)) return;
        resetHome();
    }

    @Override
    public void onBackPressed() {
        resetHome();
    }

    private void resetHome() {
        search.setText("");
        showingAll = false;
        results.removeAllViews();
        results.setVisibility(View.GONE);
        hideKeyboard();
        search.clearFocus();
        scroll.smoothScrollTo(0, 0);
    }

    // ---------- UI skeleton ----------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(28), dp(16), dp(16));
        scroll.addView(col);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Header: clock + date
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(TEXT);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setOnClickListener(v -> openIntent(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)));
        clock.setOnLongClickListener(v -> { openIntent(new Intent(Settings.ACTION_HOME_SETTINGS)); return true; });
        col.addView(clock);

        dateView = new TextView(this);
        dateView.setTextColor(MUTED);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        dateView.setPadding(0, 0, 0, dp(18));
        col.addView(dateView);
        dateView.setOnLongClickListener(v -> {
            String[] items = {"Έλεγχος για ενημέρωση", "Έκδοση " + myVersionName()};
            new AlertDialog.Builder(this).setItems(items, (d, w) -> { if (w == 0) checkUpdate(true); }).show();
            return true;
        });

        updateBanner = new TextView(this);
        updateBanner.setTextColor(BG);
        updateBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        updateBanner.setTypeface(Typeface.DEFAULT_BOLD);
        updateBanner.setBackground(round(ACCENT, 14));
        updateBanner.setPadding(dp(16), dp(12), dp(16), dp(12));
        updateBanner.setVisibility(View.GONE);
        updateBanner.setOnClickListener(v -> installUpdate());
        col.addView(updateBanner, lp(-1, -2, 0, 0, 0, dp(12)));

        // Search
        search = new EditText(this);
        search.setHint("Αναζήτηση εφαρμογής");
        search.setHintTextColor(MUTED);
        search.setTextColor(TEXT);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setBackground(round(CARD, 14));
        search.setPadding(dp(16), dp(14), dp(16), dp(14));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { showingAll = false; renderResults(); }
        });
        search.setOnEditorActionListener((v, id, ev) -> {
            List<App> m = match(search.getText().toString());
            if (!m.isEmpty()) launch(m.get(0));
            return true;
        });
        col.addView(search, lp(-1, -2, 0, 0, 0, dp(8)));

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setBackground(round(CARD, 14));
        results.setPadding(dp(6), dp(6), dp(6), dp(6));
        results.setVisibility(View.GONE);
        col.addView(results, lp(-1, -2, 0, 0, 0, dp(8)));

        // Sections
        favBox = section(col, "ΕΦΑΡΜΟΓΕΣ", "+ Προσθήκη", v -> pickFavs());
        calendarBox = section(col, "ΣΗΜΕΡΑ", "+ Νέο", v -> showAddEvent());
        ((View) calendarBox.getParent()).setOnLongClickListener(v -> {
            boolean in = !prefs.getString("cookie", "").isEmpty();
            String[] items = in ? new String[]{"Άνοιγμα πλατφόρμας", "Ανανέωση", "Άδεια ημερολογίου κινητού", "Αποσύνδεση"}
                                : new String[]{"Σύνδεση στην πλατφόρμα", "Άδεια ημερολογίου κινητού"};
            new AlertDialog.Builder(this).setItems(items, (d, w) -> {
                String it = items[w];
                if (it.startsWith("Άνοιγμα")) openPlatform();
                else if (it.equals("Ανανέωση")) fetchPlatform();
                else if (it.startsWith("Άδεια")) requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL);
                else if (it.equals("Αποσύνδεση")) platLogout();
                else showLogin();
            }).show();
            return true;
        });
        callsBox = section(col, "ΓΡΗΓΟΡΕΣ ΚΛΗΣΕΙΣ", "+ Επαφή", v -> pickContact());
        todoBox = section(col, "ΕΚΚΡΕΜΟΤΗΤΕΣ", null, null);

        // Dock
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(12), dp(10), dp(12), dp(14));
        dock.setBackgroundColor(0xFF12161C);
        root.addView(dock, new LinearLayout.LayoutParams(-1, -2));

        return root;
    }

    private LinearLayout section(LinearLayout parent, String title, String action, View.OnClickListener onAction) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, 18));
        card.setPadding(dp(16), dp(12), dp(16), dp(14));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(title, 12, ACCENT, true);
        t.setLetterSpacing(0.08f);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (action != null) {
            TextView a = text(action, 14, MUTED, false);
            a.setPadding(dp(10), dp(6), 0, dp(6));
            a.setOnClickListener(onAction);
            head.addView(a);
        }
        card.addView(head, lp(-1, -2, 0, 0, 0, dp(6)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        card.addView(body);
        parent.addView(card, lp(-1, -2, 0, dp(8), 0, dp(4)));
        return body;
    }

    private void updateDate() {
        String d = new SimpleDateFormat("EEEE, d MMMM", EL).format(new Date());
        dateView.setText(d.isEmpty() ? d : Character.toUpperCase(d.charAt(0)) + d.substring(1));
    }

    // ---------- Apps & search ----------

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        apps.clear();
        for (ResolveInfo r : list) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            App a = new App();
            a.label = String.valueOf(r.loadLabel(pm));
            a.norm = norm(a.label);
            a.pkg = r.activityInfo.packageName;
            a.cls = r.activityInfo.name;
            apps.add(a);
        }
        Collections.sort(apps, (x, y) -> x.norm.compareTo(y.norm));
    }

    private static String norm(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(new Locale("el")).replace('ς', 'σ');
    }

    private List<App> match(String q) {
        List<App> starts = new ArrayList<>(), contains = new ArrayList<>();
        String nq = norm(q.trim());
        if (nq.isEmpty()) return starts;
        for (App a : apps) {
            if (a.norm.startsWith(nq)) starts.add(a);
            else if (a.norm.contains(" " + nq) || a.norm.contains(nq)) contains.add(a);
        }
        starts.addAll(contains);
        return starts;
    }

    private void renderResults() {
        results.removeAllViews();
        List<App> list;
        if (showingAll) list = apps;
        else {
            list = match(search.getText().toString());
            if (list.size() > 8) list = list.subList(0, 8);
        }
        String q = search.getText().toString().trim();
        if (list.isEmpty() && !q.isEmpty()) {
            TextView none = text("Καμία εφαρμογή για «" + q + "»", 15, MUTED, false);
            none.setPadding(dp(12), dp(12), dp(12), dp(12));
            results.addView(none);
            results.setVisibility(View.VISIBLE);
            return;
        }
        results.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        PackageManager pm = getPackageManager();
        for (App a : list) {
            iconOf(a);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackground(ripple());
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            row.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
            TextView tv = text(a.label, 16, TEXT, false);
            tv.setPadding(dp(14), 0, 0, 0);
            row.addView(tv);
            row.setOnClickListener(v -> launch(a));
            row.setOnLongClickListener(v -> {
                boolean pinned = favKeys().contains(key(a));
                String[] items = {pinned ? "Είναι ήδη στην αρχική" : "Προσθήκη στην αρχική", "Πληροφορίες εφαρμογής"};
                new AlertDialog.Builder(this).setTitle(a.label).setItems(items, (d, w) -> {
                    if (w == 0) { if (!pinned) addFav(a); }
                    else openIntent(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
                }).show();
                return true;
            });
            results.addView(row);
        }
    }

    private void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(new ComponentName(a.pkg, a.cls));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        openIntent(i);
    }

    // ---------- Εφαρμογές στην αρχική ----------

    private static String key(App a) { return a.pkg + "/" + a.cls; }

    private List<String> favKeys() {
        JSONArray arr = readArr("fav");
        List<String> l = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) l.add(arr.optString(i));
        return l;
    }

    private void saveFav(List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        saveArr("fav", a);
    }

    private App findApp(String k) {
        for (App a : apps) if (key(a).equals(k)) return a;
        // ίδιο πακέτο, άλλη activity (π.χ. μετά από ενημέρωση της εφαρμογής)
        String pkg = k.contains("/") ? k.substring(0, k.indexOf('/')) : k;
        for (App a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private Drawable iconOf(App a) {
        if (a.icon == null) {
            PackageManager pm = getPackageManager();
            try { a.icon = pm.getActivityIcon(new ComponentName(a.pkg, a.cls)); }
            catch (Exception e) { a.icon = pm.getDefaultActivityIcon(); }
        }
        return a.icon;
    }

    private void renderFav() {
        favBox.removeAllViews();
        List<String> keys = favKeys();
        List<App> list = new ArrayList<>();
        for (String k : keys) { App a = findApp(k); if (a != null && !list.contains(a)) list.add(a); }
        if (list.isEmpty()) {
            TextView t = text("Πάτα «+ Προσθήκη» για να βάλεις εφαρμογές εδώ", 15, MUTED, false);
            t.setPadding(0, dp(6), 0, dp(6));
            t.setOnClickListener(v -> pickFavs());
            favBox.addView(t);
            return;
        }
        final int COLS = 4;
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                favBox.addView(row, lp(-1, -2, 0, dp(2), 0, dp(2)));
            }
            App a = list.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(2), dp(8), dp(2), dp(6));
            cell.setBackground(ripple());
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(iconOf(a));
            cell.addView(iv, new LinearLayout.LayoutParams(dp(50), dp(50)));
            TextView tv = text(a.label, 12, TEXT, false);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
            tv.setPadding(0, dp(5), 0, 0);
            cell.addView(tv, new LinearLayout.LayoutParams(-1, -2));
            cell.setOnClickListener(v -> launch(a));
            final int idx = i, total = list.size();
            cell.setOnLongClickListener(v -> { favMenu(a, idx, total); return true; });
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        int rest = list.size() % COLS;
        if (rest != 0 && row != null) for (int i = rest; i < COLS; i++) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
    }

    private void favMenu(App a, int idx, int total) {
        List<String> opts = new ArrayList<>();
        if (idx > 0) opts.add("◀ Μετακίνηση αριστερά");
        if (idx < total - 1) opts.add("Μετακίνηση δεξιά ▶");
        opts.add("Αφαίρεση από την αρχική");
        opts.add("Πληροφορίες εφαρμογής");
        String[] items = opts.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(a.label).setItems(items, (d, w) -> {
            String it = items[w];
            List<String> l = new ArrayList<>();
            for (String k : favKeys()) { App x = findApp(k); if (x != null && !l.contains(key(x))) l.add(key(x)); }
            int pos = l.indexOf(key(a));
            if (it.startsWith("◀") && pos > 0) Collections.swap(l, pos, pos - 1);
            else if (it.startsWith("Μετακίνηση δεξιά") && pos >= 0 && pos < l.size() - 1) Collections.swap(l, pos, pos + 1);
            else if (it.startsWith("Αφαίρεση")) l.remove(key(a));
            else if (it.startsWith("Πληροφορίες")) {
                openIntent(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
                return;
            }
            saveFav(l);
            renderFav();
        }).show();
    }

    private void addFav(App a) {
        List<String> l = favKeys();
        if (l.contains(key(a))) { toast("Υπάρχει ήδη στην αρχική"); return; }
        l.add(key(a));
        saveFav(l);
        renderFav();
        toast(a.label + ": προστέθηκε στην αρχική");
    }

    private void pickFavs() {
        if (apps.isEmpty()) loadApps();
        String[] labels = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        List<String> cur = favKeys();
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label;
            checked[i] = cur.contains(key(apps.get(i)));
        }
        new AlertDialog.Builder(this)
                .setTitle("Εφαρμογές στην αρχική")
                .setMultiChoiceItems(labels, checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("OK", (d, w) -> {
                    // κράτα τη σειρά των υπαρχόντων, νέες στο τέλος
                    List<String> l = new ArrayList<>();
                    for (String k : cur) {
                        for (int i = 0; i < apps.size(); i++) if (checked[i] && key(apps.get(i)).equals(k)) l.add(k);
                    }
                    for (int i = 0; i < apps.size(); i++) if (checked[i] && !l.contains(key(apps.get(i)))) l.add(key(apps.get(i)));
                    saveFav(l);
                    renderFav();
                })
                .setNegativeButton("Άκυρο", null)
                .show();
    }

    // ---------- Calendar: πλατφόρμα ΜΠΕΚΙΑΡΗΣ + ημερολόγιο κινητού ----------

    private static final String BASE = "https://mpekiaris.tail97f291.ts.net";
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private boolean fetching = false;

    private static String todayIso() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static class Resp { int code; String body; }

    private Resp http(String method, String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(prefs.getString("plat_base", BASE) + "/api" + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        String ck = prefs.getString("cookie", "");
        if (!ck.isEmpty()) c.setRequestProperty("Cookie", ck);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        Resp r = new Resp();
        r.code = c.getResponseCode();
        List<String> sc = c.getHeaderFields().get("Set-Cookie");
        if (sc == null) sc = c.getHeaderFields().get("set-cookie");
        if (sc != null) mergeCookies(sc);
        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }
        }
        r.body = sb.toString();
        c.disconnect();
        return r;
    }

    private synchronized void mergeCookies(List<String> setCookies) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String p : prefs.getString("cookie", "").split(";\\s*")) {
            int i = p.indexOf('=');
            if (i > 0) m.put(p.substring(0, i), p.substring(i + 1));
        }
        for (String s : setCookies) {
            String first = s.split(";", 2)[0];
            int i = first.indexOf('=');
            if (i <= 0) continue;
            String k = first.substring(0, i).trim(), v = first.substring(i + 1).trim();
            String low = s.toLowerCase(Locale.US);
            if (v.isEmpty() || low.contains("max-age=0") || low.contains("expires=thu, 01 jan 1970")) m.remove(k);
            else m.put(k, v);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        prefs.edit().putString("cookie", sb.toString()).apply();
    }

    private static String errMsg(Resp r) {
        try { return new JSONObject(r.body).optString("error", "Σφάλμα " + r.code); }
        catch (Exception e) { return "Σφάλμα " + r.code; }
    }

    /** Φέρνει τα σημερινά της πλατφόρμας στο παρασκήνιο και ξαναζωγραφίζει. */
    private void fetchPlatform() {
        if (fetching || prefs.getString("cookie", "").isEmpty()) return;
        fetching = true;
        final String day = todayIso();
        net.execute(() -> {
            String err = null;
            try {
                Resp r = http("GET", "/calendar?from=" + day + "&to=" + day, null);
                if (r.code == 401) {
                    prefs.edit().remove("cookie").remove("plat_cache").apply();
                    err = "Η σύνδεση έληξε";
                } else if (r.code >= 400) {
                    err = errMsg(r);
                } else {
                    JSONArray ev = new JSONObject(r.body).optJSONArray("events");
                    prefs.edit().putString("plat_cache", ev == null ? "[]" : ev.toString())
                            .putString("plat_day", day).putLong("plat_at", System.currentTimeMillis()).apply();
                }
            } catch (Exception e) {
                err = "Χωρίς σύνδεση με την πλατφόρμα";
            }
            final String fe = err;
            runOnUiThread(() -> {
                fetching = false;
                prefs.edit().putString("plat_err", fe == null ? "" : fe).apply();
                if (!isFinishing()) renderCalendar();
            });
        });
    }

    private void renderCalendar() {
        calendarBox.removeAllViews();
        int shown = 0;

        // ---- Πλατφόρμα ----
        if (prefs.getString("cookie", "").isEmpty()) {
            TextView t = text("Σύνδεση στην πλατφόρμα ΜΠΕΚΙΑΡΗΣ", 15, ACCENT, true);
            t.setPadding(0, dp(6), 0, dp(6));
            t.setOnClickListener(v -> showLogin());
            calendarBox.addView(t);
            String err = prefs.getString("plat_err", "");
            if (!err.isEmpty()) calendarBox.addView(text(err, 13, MUTED, false));
        } else {
            JSONArray ev = new JSONArray();
            if (todayIso().equals(prefs.getString("plat_day", ""))) {
                try { ev = new JSONArray(prefs.getString("plat_cache", "[]")); } catch (Exception ignored) {}
            }
            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < ev.length(); i++) { JSONObject o = ev.optJSONObject(i); if (o != null) list.add(o); }
            Collections.sort(list, (a, b) -> Integer.compare(rank(a), rank(b)) != 0 ? Integer.compare(rank(a), rank(b))
                    : a.optString("time", "99").compareTo(b.optString("time", "99")));
            for (JSONObject e : list) { calendarBox.addView(platRow(e)); shown++; }
            String err = prefs.getString("plat_err", "");
            if (!err.isEmpty()) {
                TextView t = text("⚠ " + err + (prefs.getString("cookie", "").isEmpty() ? "" : " · πάτα για ανανέωση"), 13, MUTED, false);
                t.setPadding(0, dp(4), 0, dp(4));
                t.setOnClickListener(v -> fetchPlatform());
                calendarBox.addView(t);
            }
        }

        // ---- Ημερολόγιο κινητού (αν έχει δοθεί άδεια) ----
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            shown += renderPhoneCalendar();
        }

        if (shown == 0 && !prefs.getString("cookie", "").isEmpty()) {
            TextView t = text("Τίποτα για σήμερα", 15, MUTED, false);
            t.setPadding(0, dp(6), 0, dp(6));
            t.setOnClickListener(v -> openPlatform());
            calendarBox.addView(t);
        }
    }

    private static int rank(JSONObject e) {
        switch (e.optString("kind")) {
            case "event": return 0;
            case "sched": return 1;
            case "todo": return 2;
            default: return 3;
        }
    }

    private View platRow(JSONObject e) {
        String kind = e.optString("kind");
        String when, title = e.optString("title"), sub = null;
        int whenColor = ACCENT;
        switch (kind) {
            case "todo":
                when = "Εκκρ.";
                whenColor = 0xFFE5707A;
                String resp = e.optString("resp", "");
                sub = (resp.isEmpty() ? "" : resp) + ("high".equals(e.optString("priority")) ? (resp.isEmpty() ? "" : " · ") + "❗ υψηλή" : "");
                break;
            case "expiry":
                when = "Λήξη";
                whenColor = 0xFFE5707A;
                break;
            case "sched":
                when = "start".equals(e.optString("edge")) ? "▶ Έναρξη" : "⏹ Λήξη";
                whenColor = 0xFF5FB3F0;
                String store = e.optString("store", ""), client = e.optString("client", ""), proj = e.optString("proj", "");
                StringBuilder s = new StringBuilder();
                if (!store.isEmpty()) s.append(store); else if (!client.isEmpty()) s.append(client);
                if (!proj.isEmpty() && !proj.equals(store)) { if (s.length() > 0) s.append(" · "); s.append(proj); }
                sub = s.toString();
                break;
            default:
                String tm = e.optString("time", "");
                when = tm.isEmpty() ? "Σήμερα" : tm;
                if (e.optBoolean("shared")) title = "👥 " + title;
                String note = e.optString("note", "");
                String by = e.optBoolean("shared") ? e.optString("by", "") : "";
                sub = note + (note.isEmpty() || by.isEmpty() ? "" : " · ") + by;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(7), 0, dp(7));
        row.setBackground(ripple());
        TextView tw = text(when, 14, whenColor, true);
        tw.setMinWidth(dp(84));
        row.addView(tw);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(text(title.isEmpty() ? "(χωρίς τίτλο)" : title, 15, TEXT, false));
        if (sub != null && !sub.isEmpty()) txt.addView(text(sub, 13, MUTED, false));
        row.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setOnClickListener(v -> openPlatform());
        if ("event".equals(kind) && e.optBoolean("mine") && e.has("id")) {
            final String id = e.optString("id");
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage("Διαγραφή της καταχώρησης από την πλατφόρμα;")
                        .setPositiveButton("Διαγραφή", (d, w) -> platDelete(id))
                        .setNegativeButton("Άκυρο", null).show();
                return true;
            });
        }
        return row;
    }

    private void openPlatform() {
        openIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(BASE + "/")));
    }

    private void showLogin() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText user = new EditText(this);
        user.setHint("Όνομα χρήστη");
        user.setSingleLine(true);
        user.setText(prefs.getString("plat_user", ""));
        EditText pass = new EditText(this);
        pass.setHint("Κωδικός");
        pass.setSingleLine(true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(user); box.addView(pass);
        new AlertDialog.Builder(this)
                .setTitle("Πλατφόρμα ΜΠΕΚΙΑΡΗΣ")
                .setView(box)
                .setPositiveButton("Σύνδεση", (d, w) -> platLogin(user.getText().toString().trim(), pass.getText().toString()))
                .setNegativeButton("Άκυρο", null)
                .show();
    }

    private void platLogin(String u, String p) {
        if (u.isEmpty() || p.isEmpty()) { toast("Συμπλήρωσε όνομα και κωδικό"); return; }
        net.execute(() -> {
            String err = null;
            try {
                prefs.edit().remove("cookie").apply();
                JSONObject b = new JSONObject().put("username", u).put("password", p).put("remember", true);
                Resp r = http("POST", "/login", b.toString());
                if (r.code >= 400) err = errMsg(r);
                else if (prefs.getString("cookie", "").isEmpty()) err = "Η πλατφόρμα δεν έδωσε συνεδρία";
                else prefs.edit().putString("plat_user", u).putString("plat_err", "").apply();
            } catch (Exception e) {
                err = "Χωρίς σύνδεση με την πλατφόρμα";
            }
            final String fe = err;
            runOnUiThread(() -> {
                if (fe != null) toast(fe); else { toast("Συνδέθηκες"); fetchPlatform(); }
                renderCalendar();
            });
        });
    }

    private void platLogout() {
        net.execute(() -> {
            try { http("POST", "/logout", "{}"); } catch (Exception ignored) {}
            prefs.edit().remove("cookie").remove("plat_cache").putString("plat_err", "").apply();
            runOnUiThread(this::renderCalendar);
        });
    }

    private void showAddEvent() {
        if (prefs.getString("cookie", "").isEmpty()) { showLogin(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText title = new EditText(this);
        title.setHint("π.χ. Ραντεβού ΔΕΔΔΗΕ");
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText time = new EditText(this);
        time.setHint("Ώρα (προαιρετικά, π.χ. 10:30)");
        time.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        CheckBox shared = new CheckBox(this);
        shared.setText("👥 Να το βλέπουν όλοι");
        box.addView(title); box.addView(time); box.addView(shared);
        new AlertDialog.Builder(this)
                .setTitle("Νέα καταχώρηση σήμερα")
                .setView(box)
                .setPositiveButton("Προσθήκη", (d, w) -> {
                    String t = title.getText().toString().trim();
                    String tm = normTime(time.getText().toString().trim());
                    if (t.isEmpty()) return;
                    if (tm == null) { toast("Η ώρα θέλει μορφή 10:30"); return; }
                    platAdd(t, tm, shared.isChecked());
                })
                .setNegativeButton("Άκυρο", null)
                .show();
    }

    /** "9" → "09:00", "930" → "09:30", "9.30"/"9:30" → "09:30", "" → "". null αν δεν βγαίνει ώρα. */
    static String normTime(String s) {
        if (s.isEmpty()) return "";
        String d = s.replaceAll("[^0-9]", " ").trim();
        int h, m = 0;
        try {
            String[] parts = d.split("\\s+");
            if (parts.length >= 2) { h = Integer.parseInt(parts[0]); m = Integer.parseInt(parts[1]); }
            else if (parts[0].length() <= 2) h = Integer.parseInt(parts[0]);
            else { int n = Integer.parseInt(parts[0]); h = n / 100; m = n % 100; }
        } catch (Exception e) { return null; }
        if (h < 0 || h > 23 || m < 0 || m > 59) return null;
        return String.format(Locale.US, "%02d:%02d", h, m);
    }

    private void platAdd(String title, String time, boolean shared) {
        final String day = todayIso();
        net.execute(() -> {
            String err = null;
            try {
                JSONObject b = new JSONObject().put("date", day).put("title", title).put("time", time).put("shared", shared);
                Resp r = http("POST", "/calendar", b.toString());
                if (r.code == 401) { prefs.edit().remove("cookie").apply(); err = "Η σύνδεση έληξε"; }
                else if (r.code >= 400) err = errMsg(r);
            } catch (Exception e) { err = "Χωρίς σύνδεση με την πλατφόρμα"; }
            final String fe = err;
            runOnUiThread(() -> { if (fe != null) toast(fe); fetchPlatform(); renderCalendar(); });
        });
    }

    private void platDelete(String id) {
        net.execute(() -> {
            String err = null;
            try {
                Resp r = http("DELETE", "/calendar/" + Uri.encode(id), null);
                if (r.code >= 400) err = errMsg(r);
            } catch (Exception e) { err = "Χωρίς σύνδεση με την πλατφόρμα"; }
            final String fe = err;
            runOnUiThread(() -> { if (fe != null) toast(fe); fetchPlatform(); });
        });
    }

    /** Ραντεβού του ημερολογίου του κινητού για σήμερα· επιστρέφει πόσα έδειξε. */
    private int renderPhoneCalendar() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        long end = start + 24L * 3600 * 1000;
        String todayKey = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(start));
        SimpleDateFormat utcKey = new SimpleDateFormat("yyyyMMdd", Locale.US);
        utcKey.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", EL);

        Uri.Builder ub = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(ub, start - 24L * 3600 * 1000);
        ContentUris.appendId(ub, end + 24L * 3600 * 1000);
        String[] proj = {
                CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.EVENT_LOCATION};
        int shown = 0;
        long now = System.currentTimeMillis();
        try (Cursor cur = getContentResolver().query(ub.build(), proj, null, null,
                CalendarContract.Instances.ALL_DAY + " DESC, " + CalendarContract.Instances.BEGIN + " ASC")) {
            while (cur != null && cur.moveToNext() && shown < 6) {
                String title = cur.getString(0);
                long b = cur.getLong(1), e = cur.getLong(2);
                boolean allDay = cur.getInt(3) == 1;
                long id = cur.getLong(4);
                String loc = cur.getString(5);
                String when;
                if (allDay) {
                    String bk = utcKey.format(new Date(b)), ek = utcKey.format(new Date(e));
                    if (todayKey.compareTo(bk) < 0 || todayKey.compareTo(ek) >= 0) continue;
                    when = "📱 Όλη μέρα";
                } else {
                    if (e <= start || b >= end) continue;
                    when = "📱 " + hm.format(new Date(Math.max(b, start)));
                }
                boolean past = !allDay && e < now;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(7), 0, dp(7));
                row.setBackground(ripple());
                TextView tw = text(when, 14, past ? MUTED : ACCENT, true);
                tw.setMinWidth(dp(84));
                row.addView(tw);
                LinearLayout txt = new LinearLayout(this);
                txt.setOrientation(LinearLayout.VERTICAL);
                txt.addView(text(TextUtils.isEmpty(title) ? "(χωρίς τίτλο)" : title, 15, past ? MUTED : TEXT, false));
                if (!TextUtils.isEmpty(loc)) txt.addView(text(loc, 13, MUTED, false));
                row.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
                final long fb = b, fe = e;
                row.setOnClickListener(v -> {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id));
                    i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, fb);
                    i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, fe);
                    openIntent(i);
                });
                calendarBox.addView(row);
                shown++;
            }
        } catch (Exception ignored) {}
        return shown;
    }

    // ---------- Quick calls ----------

    private JSONArray readArr(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void saveArr(String key, JSONArray a) {
        prefs.edit().putString(key, a.toString()).apply();
    }

    private void renderCalls() {
        callsBox.removeAllViews();
        JSONArray arr = readArr("calls");
        if (arr.length() == 0) {
            TextView t = text("Πάτα «+ Επαφή» για να προσθέσεις συχνές κλήσεις", 15, MUTED, false);
            t.setPadding(0, dp(6), 0, dp(6));
            callsBox.addView(t);
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                callsBox.addView(row, lp(-1, -2, 0, dp(3), 0, dp(3)));
            }
            String name = o.optString("n"), num = o.optString("p");
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setBackground(round(CARD_2, 14));
            chip.setPadding(dp(10), dp(10), dp(10), dp(10));

            TextView ini = text(initials(name), 14, BG, true);
            ini.setGravity(Gravity.CENTER);
            GradientDrawable circ = new GradientDrawable();
            circ.setShape(GradientDrawable.OVAL);
            circ.setColor(ACCENT);
            ini.setBackground(circ);
            chip.addView(ini, new LinearLayout.LayoutParams(dp(34), dp(34)));

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setPadding(dp(10), 0, 0, 0);
            TextView tn = text(name, 15, TEXT, true);
            tn.setSingleLine(true); tn.setEllipsize(TextUtils.TruncateAt.END);
            TextView tp = text(num, 12, MUTED, false);
            tp.setSingleLine(true);
            txt.addView(tn); txt.addView(tp);
            chip.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));

            final int idx = i;
            chip.setOnClickListener(v -> call(num));
            chip.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(name)
                        .setItems(new String[]{"Μήνυμα", "Αφαίρεση από τις γρήγορες κλήσεις"}, (d, w) -> {
                            if (w == 0) openIntent(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num))));
                            else { JSONArray a = readArr("calls"); a.remove(idx); saveArr("calls", a); renderCalls(); }
                        }).show();
                return true;
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(i % 2 == 0 ? 0 : dp(4), 0, i % 2 == 0 ? dp(4) : 0, 0);
            row.addView(chip, p);
        }
        if (arr.length() % 2 == 1 && row != null) {
            View spacer = new View(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1f);
            p.setMargins(dp(4), 0, 0, 0);
            row.addView(spacer, p);
        }
    }

    private static String initials(String n) {
        String[] parts = n.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    private void pickContact() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_PICK);
        } catch (ActivityNotFoundException e) {
            toast("Δεν βρέθηκε εφαρμογή επαφών");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        try (Cursor c = getContentResolver().query(data.getData(), new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                JSONObject o = new JSONObject();
                o.put("n", c.getString(0));
                o.put("p", c.getString(1));
                JSONArray a = readArr("calls");
                a.put(o);
                saveArr("calls", a);
                renderCalls();
            }
        } catch (Exception e) {
            toast("Δεν προστέθηκε η επαφή");
        }
    }

    private void call(String num) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            openIntent(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num))));
        } else {
            pendingCall = num;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (req == REQ_CAL) renderCalendar();
        if (req == REQ_CALL && pendingCall != null) {
            String n = pendingCall; pendingCall = null;
            openIntent(new Intent(ok ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))));
        }
    }

    // ---------- Todos ----------

    private void renderTodos() {
        todoBox.removeAllViews();
        JSONArray arr = readArr("todos");
        boolean anyDone = false;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                boolean done = o.optBoolean("d");
                if ((pass == 0) == done) continue;
                if (done) anyDone = true;
                final int idx = i;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(6), 0, dp(6));
                row.setBackground(ripple());
                TextView box = text(done ? "✓" : "", 13, BG, true);
                box.setGravity(Gravity.CENTER);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(6));
                if (done) g.setColor(ACCENT); else g.setStroke(dp(2), MUTED);
                box.setBackground(g);
                row.addView(box, new LinearLayout.LayoutParams(dp(22), dp(22)));
                TextView t = text(o.optString("t"), 15, done ? MUTED : TEXT, false);
                t.setPadding(dp(12), 0, 0, 0);
                if (done) t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                row.setOnClickListener(v -> {
                    JSONArray a = readArr("todos");
                    JSONObject x = a.optJSONObject(idx);
                    if (x != null) { try { x.put("d", !x.optBoolean("d")); } catch (Exception ignored) {} }
                    saveArr("todos", a); renderTodos();
                });
                row.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setMessage("Διαγραφή;")
                            .setPositiveButton("Διαγραφή", (d, w) -> {
                                JSONArray a = readArr("todos"); a.remove(idx); saveArr("todos", a); renderTodos();
                            })
                            .setNegativeButton("Άκυρο", null).show();
                    return true;
                });
                todoBox.addView(row);
            }
        }

        EditText add = new EditText(this);
        add.setHint("+ Νέα εκκρεμότητα");
        add.setHintTextColor(MUTED);
        add.setTextColor(TEXT);
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        add.setSingleLine(true);
        add.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        add.setImeOptions(EditorInfo.IME_ACTION_DONE);
        add.setBackground(round(CARD_2, 12));
        add.setPadding(dp(12), dp(10), dp(12), dp(10));
        add.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_DONE || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String s = add.getText().toString().trim();
                if (!s.isEmpty()) {
                    try {
                        JSONArray a = readArr("todos");
                        a.put(new JSONObject().put("t", s).put("d", false));
                        saveArr("todos", a);
                    } catch (Exception ignored) {}
                    renderTodos();
                    View n = todoBox.getChildAt(todoBox.getChildCount() - (hasClear() ? 2 : 1));
                    if (n instanceof EditText) n.requestFocus();
                }
                return true;
            }
            return false;
        });
        todoBox.addView(add, lp(-1, -2, 0, dp(8), 0, 0));

        if (anyDone) {
            TextView clr = text("Καθάρισε τις ολοκληρωμένες", 13, MUTED, false);
            clr.setTag("clear");
            clr.setPadding(0, dp(10), 0, dp(2));
            clr.setOnClickListener(v -> {
                JSONArray a = readArr("todos"), keep = new JSONArray();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null && !o.optBoolean("d")) keep.put(o);
                }
                saveArr("todos", keep); renderTodos();
            });
            todoBox.addView(clr);
        }
    }

    private boolean hasClear() {
        View last = todoBox.getChildAt(todoBox.getChildCount() - 1);
        return last != null && "clear".equals(last.getTag());
    }

    // ---------- Dock ----------

    private void renderDock() {
        dock.removeAllViews();
        addDock("Τηλέφωνο", new Intent(Intent.ACTION_DIAL));
        addDock("Μηνύματα", Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING));
        addDock("Email", Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL));
        addDock("Κάμερα", new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));

        // All apps button
        LinearLayout b = dockCell("Εφαρμογές");
        TextView grid = text("⋮⋮", 20, TEXT, true);
        grid.setGravity(Gravity.CENTER);
        grid.setBackground(round(CARD_2, 14));
        b.addView(grid, 0, new LinearLayout.LayoutParams(dp(46), dp(46)));
        b.setOnClickListener(v -> {
            search.setText("");
            showingAll = true;
            renderResults();
            scroll.post(() -> scroll.smoothScrollTo(0, results.getTop() - dp(70)));
        });
    }

    private void addDock(String label, Intent intent) {
        PackageManager pm = getPackageManager();
        ResolveInfo r = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        Drawable icon = null;
        if (r != null && r.activityInfo != null && !"android".equals(r.activityInfo.packageName)) {
            icon = r.loadIcon(pm);
        }
        LinearLayout cell = dockCell(label);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            cell.addView(iv, 0, new LinearLayout.LayoutParams(dp(46), dp(46)));
        } else {
            TextView t = text(label.substring(0, 1), 20, TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setBackground(round(CARD_2, 14));
            cell.addView(t, 0, new LinearLayout.LayoutParams(dp(46), dp(46)));
        }
        cell.setOnClickListener(v -> openIntent(intent));
    }

    private LinearLayout dockCell(String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setBackground(ripple());
        cell.setPadding(0, dp(4), 0, dp(2));
        TextView t = text(label, 11, MUTED, false);
        t.setSingleLine(true);
        t.setPadding(0, dp(4), 0, 0);
        cell.addView(t);
        dock.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        return cell;
    }

    // ---------- Αυτόματη ενημέρωση από GitHub ----------

    static final String UPDATE_URL =
            "https://raw.githubusercontent.com/nmpekiaris-hue/dashboard-launcher/main/release/version.json";
    static final String ACTION_INSTALL = "gr.mpekiaris.launcher.INSTALL_STATUS";
    private static final long CHECK_EVERY = 3L * 3600 * 1000;
    private boolean checkingUpdate = false;

    private long myVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode(); }
        catch (Exception e) { return 0; }
    }

    private String myVersionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    private File updateFile() { return new File(getCacheDir(), "update.apk"); }

    private void refreshUpdateBanner() {
        long ready = prefs.getLong("upd_code", 0);
        if (ready > myVersion() && updateFile().length() > 0) {
            String notes = prefs.getString("upd_notes", "");
            updateBanner.setText("⬆ Νέα έκδοση " + prefs.getString("upd_name", "") + " · πάτα για εγκατάσταση"
                    + (notes.isEmpty() ? "" : "\n" + notes));
            updateBanner.setVisibility(View.VISIBLE);
        } else {
            updateBanner.setVisibility(View.GONE);
            if (ready != 0 && ready <= myVersion()) {
                updateFile().delete();
                prefs.edit().remove("upd_code").remove("upd_name").remove("upd_notes").apply();
            }
        }
    }

    private void checkUpdate(boolean manual) {
        long now = System.currentTimeMillis();
        if (checkingUpdate) return;
        if (!manual && now - prefs.getLong("upd_checked", 0) < CHECK_EVERY) return;
        checkingUpdate = true;
        prefs.edit().putLong("upd_checked", now).apply();
        final String url = prefs.getString("upd_url", UPDATE_URL);
        net.execute(() -> {
            String msg = null;
            try {
                JSONObject v = new JSONObject(httpGetText(url + "?t=" + now));
                long code = v.getLong("versionCode");
                if (code > myVersion()) {
                    if (prefs.getLong("upd_code", 0) != code || updateFile().length() == 0) {
                        download(v.getString("apk") + "?t=" + now, updateFile());
                    }
                    prefs.edit().putLong("upd_code", code).putString("upd_name", v.optString("versionName"))
                            .putString("upd_notes", v.optString("notes")).apply();
                } else if (manual) {
                    msg = "Έχεις την τελευταία έκδοση (" + myVersionName() + ")";
                }
            } catch (Exception e) {
                if (manual) msg = "Δεν έγινε έλεγχος ενημέρωσης";
            }
            final String fm = msg;
            runOnUiThread(() -> {
                checkingUpdate = false;
                if (fm != null) toast(fm);
                refreshUpdateBanner();
            });
        });
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Cache-Control", "no-cache");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        return c;
    }

    private static String httpGetText(String url) throws Exception {
        HttpURLConnection c = open(url);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static void download(String url, File dest) throws Exception {
        HttpURLConnection c = open(url);
        File tmp = new File(dest.getPath() + ".part");
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (tmp.length() < 1000) { tmp.delete(); throw new Exception("short apk"); }
        if (!tmp.renameTo(dest)) throw new Exception("rename");
    }

    private void installUpdate() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            toast("Επίτρεψε στο Dashboard να εγκαθιστά ενημερώσεις και ξαναπάτα");
            openIntent(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        final File f = updateFile();
        updateBanner.setText("⏳ Ετοιμάζω την εγκατάσταση…");
        net.execute(() -> {
            try {
                PackageInstaller pi = getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                p.setAppPackageName(getPackageName());
                if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                int id = pi.createSession(p);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    try (InputStream in = new FileInputStream(f); OutputStream out = s.openWrite("base.apk", 0, f.length())) {
                        byte[] buf = new byte[16384]; int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        s.fsync(out);
                    }
                    Intent cb = new Intent(this, MainActivity.class).setAction(ACTION_INSTALL);
                    PendingIntent pend = PendingIntent.getActivity(this, 7, cb,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    s.commit(pend.getIntentSender());
                }
            } catch (Exception e) {
                runOnUiThread(() -> { toast("Η ενημέρωση απέτυχε: " + e.getMessage()); refreshUpdateBanner(); });
            }
        });
    }

    /** Επιστρέφει true αν το intent ήταν απάντηση του installer. */
    private boolean handleInstallStatus(Intent intent) {
        if (intent == null || !ACTION_INSTALL.equals(intent.getAction())) return false;
        int st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(confirm); } catch (Exception e) { toast("Δεν άνοιξε η εγκατάσταση"); }
            }
        } else if (st != PackageInstaller.STATUS_SUCCESS) {
            String m = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            toast("Η ενημέρωση δεν ολοκληρώθηκε" + (m == null ? "" : ": " + m));
            refreshUpdateBanner();
        }
        return true;
    }

    // ---------- helpers ----------

    private void openIntent(Intent i) {
        try {
            if (!(i.getFlags() != 0)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("Δεν βρέθηκε εφαρμογή για αυτό");
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Drawable ripple() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return getDrawable(tv.resourceId);
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }
}
