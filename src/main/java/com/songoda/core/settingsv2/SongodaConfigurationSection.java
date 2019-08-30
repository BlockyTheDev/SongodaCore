package com.songoda.core.settingsv2;

import com.songoda.core.compatibility.LegacyMaterials;
import com.songoda.core.settingsv2.adapters.ConfigDefaultsAdapter;
import com.songoda.core.settingsv2.adapters.ConfigOptionsAdapter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for a specific node
 *
 * @since 2019-08-28
 * @author jascotty2
 */
public class SongodaConfigurationSection extends MemoryConfiguration {

    final String fullPath;
    final SongodaConfigurationSection root;
    final SongodaConfigurationSection parent;
    protected int indentation = 2; // between 2 and 9 (inclusive)
    protected char pathChar = '.';
    final HashMap<String, Comment> configComments;
    final HashMap<String, Comment> defaultComments;
    final LinkedHashMap<String, Object> defaults;
    final LinkedHashMap<String, Object> values;
    /**
     * Internal root state: if any configuration value has changed from file state
     */
    boolean changed = false;
    final boolean isDefault;
    final Object lock = new Object();

    SongodaConfigurationSection() {
        this.root = this;
        this.parent = null;
        isDefault = false;
        fullPath = "";
        configComments = new HashMap();
        defaultComments = new HashMap();
        defaults = new LinkedHashMap();
        values = new LinkedHashMap();
    }

    SongodaConfigurationSection(SongodaConfigurationSection root, SongodaConfigurationSection parent, String path, boolean isDefault) {
        this.root = root;
        this.parent = parent;
        this.fullPath = parent.fullPath + path + root.pathChar;
        this.isDefault = isDefault;
        configComments = defaultComments = null;
        defaults = null;
        values = null;
    }

    public int getIndent() {
        return root.indentation;
    }

    public void setIndent(int indentation) {
        root.indentation = indentation;
    }

    protected void onChange() {
        if (parent != null) {
            root.onChange();
        }
    }

    /**
     * Sets the character used to separate configuration nodes. <br>
     * IMPORTANT: Do not change this after loading or adding ConfigurationSections!
     * 
     * @param pathChar character to use
     */
    public void setPathSeparator(char pathChar) {
        if (!root.values.isEmpty() || !root.defaults.isEmpty())
            throw new RuntimeException("Path change after config initialization");
        root.pathChar = pathChar;
    }

    public char getPathSeparator() {
        return root.pathChar;
    }
    
    @NotNull
    public SongodaConfigurationSection createDefaultSection(@NotNull String path) {
        SongodaConfigurationSection section = new SongodaConfigurationSection(root, this, path, true);
        synchronized (root.lock) {
            root.defaults.put(fullPath + path, section);
        }
        return section;
    }
    
    @NotNull
    public SongodaConfigurationSection createDefaultSection(@NotNull String path, String... comment) {
        SongodaConfigurationSection section = new SongodaConfigurationSection(root, this, path, true);
        synchronized (root.lock) {
            root.defaults.put(fullPath + path, section);
        }
        return section;
    }

    @NotNull
    public SongodaConfigurationSection setComment(@NotNull String path, @Nullable ConfigFormattingRules.CommentStyle commentStyle, String... lines) {
        return setComment(path, commentStyle, lines.length == 0 ? (List) null : Arrays.asList(lines));
    }

    @NotNull
    public SongodaConfigurationSection setComment(@NotNull String path, @Nullable ConfigFormattingRules.CommentStyle commentStyle, @Nullable List<String> lines) {
        synchronized (root.lock) {
            if (isDefault) {
                root.defaultComments.put(fullPath + path, lines != null ? new Comment(commentStyle, lines) : null);
            } else {
                root.configComments.put(fullPath + path, lines != null ? new Comment(commentStyle, lines) : null);
            }
        }
        return this;
    }

    @NotNull
    public SongodaConfigurationSection setDefaultComment(@NotNull String path, String... lines) {
        return setDefaultComment(path, lines.length == 0 ? (List) null : Arrays.asList(lines));
    }

    @NotNull
    public SongodaConfigurationSection setDefaultComment(@NotNull String path, @Nullable List<String> lines) {
        synchronized (root.lock) {
            root.defaultComments.put(fullPath + path, new Comment(lines));
        }
        return this;
    }

    @NotNull
    public SongodaConfigurationSection setDefaultComment(@NotNull String path, ConfigFormattingRules.CommentStyle commentStyle, String... lines) {
        return setDefaultComment(path, commentStyle, lines.length == 0 ? (List) null : Arrays.asList(lines));
    }

    @NotNull
    public SongodaConfigurationSection setDefaultComment(@NotNull String path, ConfigFormattingRules.CommentStyle commentStyle, @Nullable List<String> lines) {
        synchronized (root.lock) {
            root.defaultComments.put(fullPath + path, new Comment(commentStyle, lines));
        }
        return this;
    }

    @Nullable
    public Comment getComment(@NotNull String path) {
        Comment result = root.configComments.get(fullPath + path);
        if (result == null) {
            result = root.defaultComments.get(fullPath + path);
        }
        return result;
    }

    @Override
    public void addDefault(@NotNull String path, @Nullable Object value) {
        root.defaults.put(fullPath + path, value);
    }

    @Override
    public void addDefaults(@NotNull Map<String, Object> defaults) {
        defaults.entrySet().stream().forEach(m -> root.defaults.put(fullPath + m.getKey(), m.getValue()));
    }

    @Override
    public void setDefaults(Configuration c) {
        if (fullPath.isEmpty()) {
            root.defaults.clear();
        } else {
            root.defaults.keySet().stream()
                    .filter(k -> k.startsWith(fullPath))
                    .forEach(k -> root.defaults.remove(k));
        }
        addDefaults(c);
    }

    @Override
    public ConfigDefaultsAdapter getDefaults() {
        return new ConfigDefaultsAdapter(root, this);
    }

    @Override
    public ConfigDefaultsAdapter getDefaultSection() {
        return new ConfigDefaultsAdapter(root, this);
    }

    @Override
    public ConfigOptionsAdapter options() {
        return new ConfigOptionsAdapter(root);
    }

    @NotNull
    @Override
    public Set<String> getKeys(boolean deep) {
        LinkedHashSet<String> result = new LinkedHashSet();
        int pathIndex = fullPath.lastIndexOf(root.pathChar);
        if (deep) {
            result.addAll(root.defaults.keySet().stream()
                    .filter(k -> k.startsWith(fullPath))
                    .map(k -> !k.endsWith(String.valueOf(root.pathChar)) ? k.substring(pathIndex + 1) : k.substring(pathIndex + 1, k.length() - 1))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            result.addAll(root.values.keySet().stream()
                    .filter(k -> k.startsWith(fullPath))
                    .map(k -> !k.endsWith(String.valueOf(root.pathChar)) ? k.substring(pathIndex + 1) : k.substring(pathIndex + 1, k.length() - 1))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        } else {
            result.addAll(root.defaults.keySet().stream()
                    .filter(k -> k.startsWith(fullPath) && k.lastIndexOf(root.pathChar) == pathIndex + 1)
                    .map(k -> !k.endsWith(String.valueOf(root.pathChar)) ? k.substring(pathIndex + 1) : k.substring(pathIndex + 1, k.length() - 1))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            result.addAll(root.values.keySet().stream()
                    .filter(k -> k.startsWith(fullPath) && k.lastIndexOf(root.pathChar) == pathIndex)
                    .map(k -> !k.endsWith(String.valueOf(root.pathChar)) ? k.substring(pathIndex + 1) : k.substring(pathIndex + 1, k.length() - 1))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return result;
    }

    @NotNull
    @Override
    public Map<String, Object> getValues(boolean deep) {
        LinkedHashMap<String, Object> result = new LinkedHashMap();
        int pathIndex = fullPath.lastIndexOf(root.pathChar);
        if (deep) {
            result.putAll((Map<String, Object>) root.defaults.entrySet().stream()
                    .filter(k -> k.getKey().startsWith(fullPath))
                    .collect(Collectors.toMap(
                            e -> !e.getKey().endsWith(String.valueOf(root.pathChar)) ? e.getKey().substring(pathIndex + 1) : e.getKey().substring(pathIndex + 1, e.getKey().length() - 1), 
                            e -> e.getValue(),
                            (v1, v2) -> { throw new IllegalStateException(); }, // never going to be merging keys
                            LinkedHashMap::new)));
            result.putAll((Map<String, Object>) root.values.entrySet().stream()
                    .filter(k -> k.getKey().startsWith(fullPath))
                    .collect(Collectors.toMap(
                            e -> !e.getKey().endsWith(String.valueOf(root.pathChar)) ? e.getKey().substring(pathIndex + 1) : e.getKey().substring(pathIndex + 1, e.getKey().length() - 1), 
                            e -> e.getValue(),
                            (v1, v2) -> { throw new IllegalStateException(); }, // never going to be merging keys
                            LinkedHashMap::new)));
        } else {
           result.putAll((Map<String, Object>) root.defaults.entrySet().stream()
                    .filter(k -> k.getKey().startsWith(fullPath) && k.getKey().lastIndexOf(root.pathChar) == pathIndex)
                    .collect(Collectors.toMap(
                            e -> !e.getKey().endsWith(String.valueOf(root.pathChar)) ? e.getKey().substring(pathIndex + 1) : e.getKey().substring(pathIndex + 1, e.getKey().length() - 1), 
                            e -> e.getValue(),
                            (v1, v2) -> { throw new IllegalStateException(); }, // never going to be merging keys
                            LinkedHashMap::new)));
            result.putAll((Map<String, Object>) root.values.entrySet().stream()
                    .filter(k -> k.getKey().startsWith(fullPath) && k.getKey().lastIndexOf(root.pathChar) == pathIndex)
                    .collect(Collectors.toMap(
                            e -> !e.getKey().endsWith(String.valueOf(root.pathChar)) ? e.getKey().substring(pathIndex + 1) : e.getKey().substring(pathIndex + 1, e.getKey().length() - 1), 
                            e -> e.getValue(),
                            (v1, v2) -> { throw new IllegalStateException(); }, // never going to be merging keys
                            LinkedHashMap::new)));
        }
        return result;
    }

    @Override
    public boolean contains(@NotNull String path) {
        return root.defaults.containsKey(fullPath + path) || root.values.containsKey(fullPath + path);
    }

    @Override
    public boolean contains(@NotNull String path, boolean ignoreDefault) {
        return (!ignoreDefault && root.defaults.containsKey(fullPath + path)) || root.values.containsKey(fullPath + path);
    }

    @Override
    public boolean isSet(@NotNull String path) {
        return root.defaults.get(fullPath + path) != null || root.values.get(fullPath + path) != null;
    }

    @Override
    public String getCurrentPath() {
        return fullPath.isEmpty() ? "" : fullPath.substring(0, fullPath.length() - 1);
    }

    @Override
    public String getName() {
        if (fullPath.isEmpty())
            return "";
        String[] parts = fullPath.split(Pattern.quote(String.valueOf(root.pathChar)));
        return parts[parts.length - 1];
    }

    @Override
    public SongodaConfigurationSection getRoot() {
        return root;
    }

    @Override
    public SongodaConfigurationSection getParent() {
        return parent;
    }

    @Nullable
    @Override
    public Object get(@NotNull String path) {
        Object result = root.values.get(fullPath + path);
        if (result == null) {
            result = root.defaults.get(fullPath + path);
        }
        return result;
    }

    @Nullable
    @Override
    public Object get(@NotNull String path, @Nullable Object def) {
        Object result = root.values.get(fullPath + path);
        return result != null ? result : def;
    }

    @Override
    public void set(@NotNull String path, @Nullable Object value) {
        if (isDefault) {
            root.defaults.put(fullPath + path, value);
        } else {
            synchronized (root.lock) {
                if (value != null) {
                    root.changed |= root.values.put(fullPath + path, value) != value;
                } else {
                    root.changed |= root.values.remove(fullPath + path) != null;
                }
            }
            onChange();
        }
    }

    @NotNull
    public SongodaConfigurationSection set(@NotNull String path, @Nullable Object value, String ... comment) {
        set(path, value);
        return setComment(path, null, comment);
    }

    @NotNull
    public SongodaConfigurationSection set(@NotNull String path, @Nullable Object value, List<String> comment) {
        set(path, value);
        return setComment(path, null, comment);
    }

    @NotNull
    public SongodaConfigurationSection set(@NotNull String path, @Nullable Object value, @Nullable ConfigFormattingRules.CommentStyle commentStyle, String ... comment) {
        set(path, value);
        return setComment(path, commentStyle, comment);
    }

    @NotNull
    public SongodaConfigurationSection set(@NotNull String path, @Nullable Object value, @Nullable ConfigFormattingRules.CommentStyle commentStyle, List<String> comment) {
        set(path, value);
        return setComment(path, commentStyle, comment);
    }

    @NotNull
    public SongodaConfigurationSection setDefault(@NotNull String path, @Nullable Object value) {
        addDefault(path, value);
        return this;
    }

    @NotNull
    public SongodaConfigurationSection setDefault(@NotNull String path, @Nullable Object value, String ... comment) {
        addDefault(path, value);
        return setDefaultComment(path, comment);
    }

    @NotNull
    public SongodaConfigurationSection setDefault(@NotNull String path, @Nullable Object value, List<String> comment) {
        addDefault(path, value);
        return setDefaultComment(path, comment);
    }

    @NotNull
    public SongodaConfigurationSection setDefault(@NotNull String path, @Nullable Object value, ConfigFormattingRules.CommentStyle commentStyle, String ... comment) {
        addDefault(path, value);
        return setDefaultComment(path, commentStyle, comment);
    }

    @NotNull
    public SongodaConfigurationSection setDefault(@NotNull String path, @Nullable Object value, ConfigFormattingRules.CommentStyle commentStyle, List<String> comment) {
        addDefault(path, value);
        return setDefaultComment(path, commentStyle, comment);
    }

    @NotNull
    @Override
    public SongodaConfigurationSection createSection(@NotNull String path) {
        SongodaConfigurationSection section = new SongodaConfigurationSection(root, this, path, false);
        synchronized(root.lock) {
            root.values.put(fullPath + path, section);
        }
        root.changed = true;
        onChange();
        return section;
    }

    @NotNull
    public SongodaConfigurationSection createSection(@NotNull String path, String... comment) {
        return createSection(path, null, comment.length == 0 ? (List) null : Arrays.asList(comment));
    }

    @NotNull
    public SongodaConfigurationSection createSection(@NotNull String path, @Nullable List<String> comment) {
        return createSection(path, null, comment);
    }

    @NotNull
    public SongodaConfigurationSection createSection(@NotNull String path, @Nullable ConfigFormattingRules.CommentStyle commentStyle, String... comment) {
        return createSection(path, commentStyle, comment.length == 0 ? (List) null : Arrays.asList(comment));
    }

    @NotNull
    public SongodaConfigurationSection createSection(@NotNull String path, @Nullable ConfigFormattingRules.CommentStyle commentStyle, @Nullable List<String> comment) {
        SongodaConfigurationSection section = new SongodaConfigurationSection(root, this, path, false);
        synchronized (root.lock) {
            root.values.put(fullPath + path, section);
        }
        setComment(path, commentStyle, comment);
        root.changed = true;
        onChange();
        return section;
    }

    @NotNull
    @Override
    public SongodaConfigurationSection createSection(@NotNull String path, Map<?, ?> map) {
        SongodaConfigurationSection section = new SongodaConfigurationSection(root, this, path, false);
        synchronized (root.lock) {
            root.values.put(fullPath + path, section);
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map) {
                section.createSection(entry.getKey().toString(), (Map) entry.getValue());
                continue;
            }
            section.set(entry.getKey().toString(), entry.getValue());
        }
        root.changed = true;
        onChange();
        return section;
    }

    @Nullable
    @Override
    public String getString(@NotNull String path) {
        Object result = get(path);
        return result != null ? result.toString() : null;
    }

    @Nullable
    @Override
    public String getString(@NotNull String path, @Nullable String def) {
        Object result = get(path);
        return result != null ? result.toString() : def;
    }

    public char getChar(@NotNull String path) {
        Object result = get(path);
        return result != null && !result.toString().isEmpty() ? result.toString().charAt(0) : '\0';
    }

    public char getChar(@NotNull String path, char def) {
        Object result = get(path);
        return result != null && !result.toString().isEmpty() ? result.toString().charAt(0) : def;
    }

    @Override
    public int getInt(@NotNull String path) {
        Object result = get(path);
        return result instanceof Number ? ((Number) result).intValue() : 0;
    }

    @Override
    public int getInt(@NotNull String path, int def) {
        Object result = get(path);
        return result instanceof Number ? ((Number) result).intValue() : def;
    }

    @Override
    public boolean getBoolean(@NotNull String path) {
        Object result = get(path);
        return result instanceof Boolean ? (Boolean) result : false;
    }

    @Override
    public boolean getBoolean(@NotNull String path, boolean def) {
        Object result = get(path);
        return result instanceof Boolean ? (Boolean) result : def;
    }

    @Override
    public double getDouble(@NotNull String path) {
        Object result = get(path);
        return result instanceof Number ? ((Number) result).doubleValue() : 0;
    }

    @Override
    public double getDouble(@NotNull String path, double def) {
        Object result = get(path);
        return result instanceof Number ? ((Number) result).doubleValue() : def;
    }

    @Override
    public long getLong(@NotNull String path) {
        Object result = get(path);
        return result instanceof Number ? ((Number) result).longValue(): 0;
    }

    @Override
    public long getLong(@NotNull String path, long def) {
        Object result = get(path);
        return result instanceof Number ? ((Number) result).longValue() : def;
    }

    @Nullable
    @Override
    public List<?> getList(@NotNull String path) {
        Object result = get(path);
        return result instanceof List ? (List) result : null;
    }

    @Nullable
    @Override
    public List<?> getList(@NotNull String path, @Nullable List<?> def) {
        Object result = get(path);
        return result instanceof List ? (List) result : def;
    }

    @Nullable
    public Material getMaterial(@NotNull String path) {
        String val = getString(path);
        LegacyMaterials mat = val != null ? LegacyMaterials.getMaterial(val) : null;
        return mat != null ? mat.getMaterial() : null;
    }

    @Nullable
    public Material getMaterial(@NotNull String path, @Nullable LegacyMaterials def) {
        String val = getString(path);
        LegacyMaterials mat = val != null ? LegacyMaterials.getMaterial(val) : null;
        return mat != null ? mat.getMaterial() : (def != null ? def.getMaterial() : null);
    }

    @Nullable
    @Override
    public <T> T getObject(@NotNull String path, @NotNull Class<T> clazz) {
        Object result = get(path);
        return result != null && clazz.isInstance(result) ? clazz.cast(result) : null;
    }

    @Nullable
    @Override
    public <T> T getObject(@NotNull String path, @NotNull Class<T> clazz, @Nullable T def) {
        Object result = get(path);
        return result != null && clazz.isInstance(result) ? clazz.cast(result) : def;
    }

    @Override
    public SongodaConfigurationSection getConfigurationSection(@NotNull String path) {
        Object result = get(path);
        return result instanceof SongodaConfigurationSection ? (SongodaConfigurationSection) result : null;
    }

    public SongodaConfigurationSection getOrCreateConfigurationSection(@NotNull String path) {
        Object result = get(path);
        return result instanceof SongodaConfigurationSection ? (SongodaConfigurationSection) result : createSection(path);
    }
}
