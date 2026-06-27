package com.devicespooflab.hooks;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.devicespooflab.hooks.ui.AndroidVersionTable;
import com.devicespooflab.hooks.ui.IdentifierItem;
import com.devicespooflab.hooks.ui.IdentifierRegistry;
import com.devicespooflab.hooks.utils.ConfigManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MainActivity extends AppCompatActivity {

    private static final long PERSIST_DEBOUNCE_MS = 80L;

    private File configFile;
    private final Map<String, IdentifierItem> items = new LinkedHashMap<>();
    private final Map<String, View> rowViews = new LinkedHashMap<>();
    private MaterialSwitch selectAllSwitch;
    private CompoundButton.OnCheckedChangeListener selectAllListener;

    private TextView androidVersionValue;
    private MaterialButton androidVersionMinus;
    private MaterialButton androidVersionPlus;
    private int androidVersionIdx;

    private static final String TIMEZONE_PROPERTY = "persist.sys.timezone";
    private TextView timezoneValue;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService persistExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "spoof-persist");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final Runnable persistRunnable = () -> persistExecutor.execute(this::doPersist);

    // Fired by XposedServiceBridge once the writable IXposedService binder is
    // bound (or replayed from cache). Hop onto persistExecutor so the binder
    // commit never runs on the bind callback's thread (which may be the main
    // thread when the binder was already cached at onCreate time).
    private final Runnable onServiceReady = () -> {
        try {
            persistExecutor.execute(this::publishIfWritable);
        } catch (RejectedExecutionException ignored) {
            // Activity is tearing down; the next launch republishes on bind.
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configFile = new File(getFilesDir(), "device_profile.conf");
        ensureConfigFile();
        ConfigManager.reload();
        ConfigManager.loadScopeList(getFilesDir());
        populateMissingValues();
        schedulePersist();

        setContentView(R.layout.activity_main);

        for (IdentifierRegistry.Definition d : IdentifierRegistry.all()) {
            String value = ConfigManager.getIdentifierValue(d.id);
            boolean enabled = ConfigManager.isIdentifierEnabled(d.id);
            items.put(d.id, new IdentifierItem(d.id, d.displayName, value, enabled));
        }

        wireAndroidVersionStepper();
        populateTimezoneIfMissing();
        wireTimezonePicker();
        wireScopePicker();
        wireRandomizeAll();
        buildSections();

        selectAllSwitch = findViewById(R.id.select_all_switch);
        selectAllListener = (btn, checked) -> {
            boolean changed = false;
            for (IdentifierItem item : items.values()) {
                if (item.enabled != checked) {
                    item.enabled = checked;
                    ConfigManager.setIdentifierEnabled(item.id, checked);
                    changed = true;
                }
            }
            if (changed) {
                schedulePersist();
                syncRowSwitches();
            }
        };
        silentSetSelectAll(allEnabled());

        // Register for the writable IXposedService binder. Vector delivers it to
        // our manifest-declared XposedProvider when this UID starts, regardless
        // of LSPosed hook scope, so the UI can publish edits straight to
        // RemotePreferences without scoping the module to its own process.
        XposedServiceBridge.init(this, onServiceReady);
    }

    @Override
    protected void onDestroy() {
        debounceHandler.removeCallbacks(persistRunnable);
        persistExecutor.execute(this::doPersist);
        persistExecutor.shutdown();
        super.onDestroy();
    }

    private void wireRandomizeAll() {
        MaterialButton btn = findViewById(R.id.btn_randomize_all);
        btn.setOnClickListener(v -> randomizeAll());
    }

    private void randomizeAll() {
        for (IdentifierRegistry.Definition d : IdentifierRegistry.all()) {
            IdentifierItem item = items.get(d.id);
            if (item == null) continue;
            String newVal = d.generator.generate();
            item.currentValue = newVal;
            ConfigManager.setIdentifierValue(item.id, newVal);
            View row = rowViews.get(item.id);
            if (row == null) continue;
            TextView value = row.findViewById(R.id.identifier_value);
            View invalidRow = row.findViewById(R.id.invalid_row);
            if (value != null) value.setText(newVal);
            if (invalidRow != null) invalidRow.setVisibility(d.isValid(newVal) ? View.GONE : View.VISIBLE);
        }
        schedulePersist();
    }

    private void wireAndroidVersionStepper() {
        androidVersionValue = findViewById(R.id.android_version_value);
        androidVersionMinus = findViewById(R.id.android_version_minus);
        androidVersionPlus = findViewById(R.id.android_version_plus);

        androidVersionIdx = AndroidVersionTable.currentIndex();
        renderAndroidVersion();

        androidVersionMinus.setOnClickListener(v -> stepAndroidVersion(-1));
        androidVersionPlus.setOnClickListener(v -> stepAndroidVersion(+1));
    }

    private void stepAndroidVersion(int delta) {
        int next = androidVersionIdx + delta;
        if (next < 0 || next >= AndroidVersionTable.size()) return;
        androidVersionIdx = next;
        AndroidVersionTable.Entry entry = AndroidVersionTable.get(next);
        ConfigManager.setProperties(AndroidVersionTable.buildUpdates(entry));
        renderAndroidVersion();
        schedulePersist();
    }

    private void renderAndroidVersion() {
        if (androidVersionValue == null) return;
        AndroidVersionTable.Entry entry = AndroidVersionTable.get(androidVersionIdx);
        androidVersionValue.setText(entry.displayName);
        androidVersionMinus.setEnabled(androidVersionIdx > 0);
        androidVersionPlus.setEnabled(androidVersionIdx < AndroidVersionTable.size() - 1);
    }

    private static final String TIMEZONE_SEEDED_FLAG = "_tz_seeded";
    private static final String LEGACY_DEFAULT_TIMEZONE = "America/Los_Angeles";

    private void populateTimezoneIfMissing() {
        String current = ConfigManager.getRawProperty(TIMEZONE_PROPERTY);
        String seeded = ConfigManager.getRawProperty(TIMEZONE_SEEDED_FLAG);
        boolean isEmpty = current == null || current.isEmpty();
        boolean isLegacyDefaultUnseeded = !"1".equals(seeded)
                && LEGACY_DEFAULT_TIMEZONE.equals(current);
        if (isEmpty || isLegacyDefaultUnseeded) {
            Map<String, String> updates = new HashMap<>();
            updates.put(TIMEZONE_PROPERTY, TimeZone.getDefault().getID());
            updates.put(TIMEZONE_SEEDED_FLAG, "1");
            ConfigManager.setProperties(updates);
            schedulePersist();
        }
    }

    private void wireTimezonePicker() {
        View card = findViewById(R.id.timezone_card);
        timezoneValue = findViewById(R.id.timezone_value);
        renderTimezoneValue();
        card.setOnClickListener(v -> showTimezonePicker());
    }

    private void renderTimezoneValue() {
        if (timezoneValue == null) return;
        String tz = ConfigManager.getRawProperty(TIMEZONE_PROPERTY);
        timezoneValue.setText(tz == null || tz.isEmpty() ? "—" : tz);
    }

    private TextView scopeSummary;

    private void wireScopePicker() {
        View card = findViewById(R.id.scope_card);
        scopeSummary = findViewById(R.id.scope_summary);
        renderScopeSummary();
        card.setOnClickListener(v -> showScopeDialog());
    }

    private void renderScopeSummary() {
        if (scopeSummary == null) return;
        int count = ConfigManager.getScopePackages().size();
        scopeSummary.setText(count == 0
                ? getString(R.string.scope_summary_empty)
                : getString(R.string.scope_summary_count, count));
    }

    private void showScopeDialog() {
        EditText input = new EditText(this);
        input.setTypeface(Typeface.MONOSPACE);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setMinLines(6);
        input.setHint(R.string.scope_dialog_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        StringBuilder sb = new StringBuilder();
        for (String pkg : ConfigManager.getScopePackages()) {
            sb.append(pkg).append('\n');
        }
        input.setText(sb.toString());

        FrameLayout container = new FrameLayout(this);
        int padH = (int) dpToPx(20);
        int padV = (int) dpToPx(8);
        container.setPadding(padH, padV, padH, padV);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.scope_dialog_title)
                .setView(container)
                .setPositiveButton(R.string.dialog_save, (d, w) -> {
                    String[] lines = input.getText().toString().split("\\n");
                    java.util.List<String> pkgs = new ArrayList<>();
                    for (String line : lines) pkgs.add(line);
                    ConfigManager.setScopePackages(pkgs);
                    schedulePersist();
                    renderScopeSummary();
                    Toast.makeText(this, R.string.scope_saved_restart_hint, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showTimezonePicker() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_timezone_picker, null);
        EditText search = dialogView.findViewById(R.id.timezone_search);
        ListView list = dialogView.findViewById(R.id.timezone_list);

        final List<String> all = new ArrayList<>(Arrays.asList(TimeZone.getAvailableIDs()));
        Collections.sort(all, String.CASE_INSENSITIVE_ORDER);

        // ArrayAdapter's default filter is startsWith; override with contains.
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_single_choice, new ArrayList<>(all)) {
            private final Filter substringFilter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    if (constraint == null || constraint.length() == 0) {
                        results.values = new ArrayList<>(all);
                        results.count = all.size();
                        return results;
                    }
                    String needle = constraint.toString().toLowerCase(Locale.ROOT);
                    List<String> matches = new ArrayList<>();
                    for (String tz : all) {
                        if (tz.toLowerCase(Locale.ROOT).contains(needle)) {
                            matches.add(tz);
                        }
                    }
                    results.values = matches;
                    results.count = matches.size();
                    return results;
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    clear();
                    if (results.values instanceof List) {
                        addAll((List<String>) results.values);
                    }
                    notifyDataSetChanged();
                }
            };

            @Override
            public Filter getFilter() {
                return substringFilter;
            }
        };
        list.setAdapter(adapter);

        String currentTz = ConfigManager.getRawProperty(TIMEZONE_PROPERTY);
        if (currentTz != null && !currentTz.isEmpty()) {
            int idx = all.indexOf(currentTz);
            if (idx >= 0) {
                list.setItemChecked(idx, true);
                list.setSelection(idx);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.timezone_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.getFilter().filter(s);
            }
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            String chosen = adapter.getItem(position);
            if (chosen != null) {
                Map<String, String> updates = new HashMap<>();
                updates.put(TIMEZONE_PROPERTY, chosen);
                ConfigManager.setProperties(updates);
                schedulePersist();
                renderTimezoneValue();
            }
            dialog.dismiss();
        });

        dialog.show();
        // Force MATCH_PARENT so the IME doesn't collapse the list to zero height.
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void buildSections() {
        LinearLayout container = findViewById(R.id.section_container);
        LayoutInflater inflater = LayoutInflater.from(this);

        String currentCategory = null;
        LinearLayout currentCardBody = null;

        for (IdentifierRegistry.Definition d : IdentifierRegistry.all()) {
            IdentifierItem item = items.get(d.id);
            if (item == null) continue;

            if (!d.category.equals(currentCategory)) {
                currentCategory = d.category;

                TextView header = (TextView) inflater.inflate(
                        R.layout.section_header, container, false);
                header.setText(d.category.toUpperCase(Locale.ROOT));
                container.addView(header);

                MaterialCardView card = new MaterialCardView(this);
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                card.setLayoutParams(cardLp);
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface));
                card.setRadius(dpToPx(16));
                card.setCardElevation(0f);
                card.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
                card.setStrokeWidth((int) dpToPx(1));

                currentCardBody = new LinearLayout(this);
                currentCardBody.setOrientation(LinearLayout.VERTICAL);
                card.addView(currentCardBody, new MaterialCardView.LayoutParams(
                        MaterialCardView.LayoutParams.MATCH_PARENT,
                        MaterialCardView.LayoutParams.WRAP_CONTENT));

                container.addView(card);
            } else if (currentCardBody != null) {
                currentCardBody.addView(buildDivider());
            }

            if (currentCardBody == null) continue;
            View row = inflater.inflate(R.layout.item_identifier, currentCardBody, false);
            bindRow(row, item, d);
            currentCardBody.addView(row);
            rowViews.put(d.id, row);
        }
    }

    private View buildDivider() {
        View divider = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, (int) dpToPx(1)));
        lp.setMarginStart((int) dpToPx(20));
        lp.setMarginEnd((int) dpToPx(20));
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
        return divider;
    }

    private void bindRow(View row, IdentifierItem item, IdentifierRegistry.Definition def) {
        TextView name = row.findViewById(R.id.identifier_name);
        TextView value = row.findViewById(R.id.identifier_value);
        View invalidRow = row.findViewById(R.id.invalid_row);
        MaterialSwitch sw = row.findViewById(R.id.identifier_switch);
        MaterialButton copy = row.findViewById(R.id.identifier_copy);
        MaterialButton edit = row.findViewById(R.id.identifier_edit);
        MaterialButton randomize = row.findViewById(R.id.identifier_randomize);

        name.setText(item.displayName);
        value.setText(item.currentValue == null ? "" : item.currentValue);
        invalidRow.setVisibility(def.isValid(item.currentValue) ? View.GONE : View.VISIBLE);

        sw.setOnCheckedChangeListener(null);
        sw.setChecked(item.enabled);
        sw.setOnCheckedChangeListener((btn, checked) -> {
            if (item.enabled == checked) return;
            item.enabled = checked;
            ConfigManager.setIdentifierEnabled(item.id, checked);
            schedulePersist();
            refreshSelectAllState();
        });

        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(item.displayName,
                        item.currentValue == null ? "" : item.currentValue));
                Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_SHORT).show();
            }
        });

        edit.setOnClickListener(v -> showEditDialog(item, def));

        randomize.setOnClickListener(v -> updateValue(item, def, def.generator.generate()));
    }

    private void showEditDialog(IdentifierItem item, IdentifierRegistry.Definition def) {
        EditText input = new EditText(this);
        input.setText(item.currentValue);
        input.setTypeface(Typeface.MONOSPACE);
        if (item.currentValue != null) input.setSelection(item.currentValue.length());

        FrameLayout container = new FrameLayout(this);
        int padH = (int) dpToPx(20);
        int padV = (int) dpToPx(8);
        container.setPadding(padH, padV, padH, padV);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.edit_dialog_title, item.displayName))
                .setView(container)
                .setPositiveButton(R.string.dialog_save, (d, w) ->
                        updateValue(item, def, input.getText().toString()))
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void updateValue(IdentifierItem item, IdentifierRegistry.Definition def, String newVal) {
        item.currentValue = newVal;
        ConfigManager.setIdentifierValue(item.id, newVal);
        schedulePersist();

        View row = rowViews.get(item.id);
        if (row == null) return;
        TextView value = row.findViewById(R.id.identifier_value);
        View invalidRow = row.findViewById(R.id.invalid_row);
        value.setText(newVal == null ? "" : newVal);
        invalidRow.setVisibility(def.isValid(newVal) ? View.GONE : View.VISIBLE);
    }

    private void syncRowSwitches() {
        for (IdentifierItem item : items.values()) {
            View row = rowViews.get(item.id);
            if (row == null) continue;
            MaterialSwitch sw = row.findViewById(R.id.identifier_switch);
            if (sw != null && sw.isChecked() != item.enabled) {
                sw.setChecked(item.enabled);
            }
        }
    }

    private boolean allEnabled() {
        for (IdentifierItem item : items.values()) if (!item.enabled) return false;
        return true;
    }

    private void refreshSelectAllState() {
        silentSetSelectAll(allEnabled());
    }

    private void silentSetSelectAll(boolean checked) {
        if (selectAllSwitch == null) return;
        selectAllSwitch.setOnCheckedChangeListener(null);
        selectAllSwitch.setChecked(checked);
        selectAllSwitch.setOnCheckedChangeListener(selectAllListener);
    }

    private void schedulePersist() {
        debounceHandler.removeCallbacks(persistRunnable);
        debounceHandler.postDelayed(persistRunnable, PERSIST_DEBOUNCE_MS);
    }

    // Writes the live config to disk + a world-readable SharedPreferences (the
    // bootstrap seed target apps read via XSharedPreferences), then pushes it to
    // RemotePreferences over the writable IXposedService binder. Runs on
    // persistExecutor, so the binder commit stays off the main thread.
    private void doPersist() {
        try {
            ConfigManager.saveConfig(configFile);
        } catch (IOException e) {
            runOnUiThread(() -> Toast.makeText(this,
                    getString(R.string.save_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show());
        }
        try {
            ConfigManager.saveScopeList(getFilesDir());
        } catch (IOException e) {
            runOnUiThread(() -> Toast.makeText(this,
                    getString(R.string.save_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show());
        }
        SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, String> e : ConfigManager.getRawProperties().entrySet()) {
            editor.putString(e.getKey(), e.getValue());
        }
        editor.apply();
        makePrefsWorldReadable();
        publishIfWritable();
    }

    // Publishes the in-memory config to RemotePreferences when the writable
    // binder is bound; a no-op otherwise. Always invoked on persistExecutor.
    private void publishIfWritable() {
        if (XposedServiceBridge.isServiceWritable()) {
            ConfigManager.publishToRemotePreferences();
        }
    }

    private void makePrefsWorldReadable() {
        try {
            File dataDir = new File(getApplicationInfo().dataDir);
            File prefsDir = new File(dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, "config.xml");
            if (prefsFile.exists()) prefsFile.setReadable(true, false);
            if (prefsDir.exists()) {
                prefsDir.setExecutable(true, false);
                prefsDir.setReadable(true, false);
            }
            dataDir.setExecutable(true, false);
        } catch (Throwable ignored) {
        }
    }

    private void populateMissingValues() {
        boolean dirty = false;
        for (IdentifierRegistry.Definition d : IdentifierRegistry.all()) {
            String value = ConfigManager.getIdentifierValue(d.id);
            if (value == null || value.isEmpty()) {
                ConfigManager.setIdentifierValue(d.id, d.generator.generate());
                dirty = true;
            }
        }
        if (dirty) {
            try {
                ConfigManager.saveConfig(configFile);
            } catch (IOException ignored) {
            }
        }
    }

    private void ensureConfigFile() {
        if (configFile.exists()) return;
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            fos.write(ConfigManager.getDefaultConfigText().getBytes());
            fos.flush();
        } catch (IOException ignored) {
        }
    }

    private float dpToPx(int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }
}
