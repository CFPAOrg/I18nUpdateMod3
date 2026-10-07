package i18nupdatemod.core;

import i18nupdatemod.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Defers NeoForge resource-pack activation until the real client resource-pack
 * repository has been constructed.
 *
 * <p>This class deliberately has no Minecraft linkage.  The transformer passes
 * the names it discovered from the actual Options and Pack classes, and all
 * calls into those objects are reflective.  A failed binding is reported and
 * ignored rather than preventing the client from starting.</p>
 */
public final class RuntimePackActivation {
    private static final Object STATE_LOCK = new Object();
    private static final Map<Object, SelectionCapture> CAPTURES =
            new WeakHashMap<>();

    private static volatile boolean enabled;
    private static PendingActivation pending;

    /* The generated file name is stable across game versions. */
    private static final String I18N_FILE_PREFIX = "Minecraft-Mod-Language-Modpack-Converted-";
    private static final String FILE_ID_PREFIX = "file/";
    private static final String HIDDEN_METHOD = "isHidden";

    private RuntimePackActivation() {
    }

    /**
     * Enables the runtime hook for a NeoForge client.  Calling this more than
     * once is harmless and does not discard an activation already prepared by
     * another early entry point.
     */
    public static void enable() {
        synchronized (STATE_LOCK) {
            enabled = true;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Records a successfully generated pack for the next Options selection
     * pass.  This method intentionally never opens options.txt.
     *
     * @return false when runtime activation is disabled or the generated file
     * is not an available, non-empty regular file
     */
    public static boolean prepare(Path gameDirectory, Path completedPack) {
        if (!enabled) {
            return false;
        }
        if (completedPack == null) {
            reportFailure("completed pack path is null", null);
            return false;
        }

        final String fileName;
        try {
            Path normalized = completedPack.toAbsolutePath().normalize();
            fileName = normalized.getFileName() == null
                    ? ""
                    : normalized.getFileName().toString();
            if (fileName.isEmpty() || !Files.isRegularFile(normalized)
                    || !Files.isReadable(normalized) || Files.size(normalized) <= 0L) {
                reportFailure("completed pack is unavailable: " + normalized, null);
                return false;
            }
        } catch (Throwable failure) {
            reportFailure("unable to inspect completed pack: " + completedPack, failure);
            return false;
        }

        /* gameDirectory is part of the public lifecycle contract.  The file
         * itself is authoritative; callers may use a custom resource-pack
         * directory below the game directory, so do not require a particular
         * parent here. */
        String targetId = FILE_ID_PREFIX + fileName;
        synchronized (STATE_LOCK) {
            if (!enabled) {
                return false;
            }
            pending = new PendingActivation(targetId);
        }
        return true;
    }

    /**
     * Runs at the head of Options.loadSelectedResourcePacks.  It changes only
     * the selected resource-pack list and leaves persistence to afterSelection.
     */
    public static void beforeSelection(Object options, String selectedFieldName) {
        PendingActivation activation;
        synchronized (STATE_LOCK) {
            if (!enabled || options == null || pending == null) {
                return;
            }
            /* Consume before doing reflection.  The capture below is the
             * hand-off that lets afterSelection run even after this token is
             * consumed, including when a reload re-enters this method. */
            activation = pending;
            pending = null;
        }

        SelectionCapture capture = new SelectionCapture(activation.targetId);
        try {
            Object selectedObject = readField(options, selectedFieldName);
            if (!(selectedObject instanceof List<?>)) {
                throw new IllegalStateException("selected field is not a List");
            }

            @SuppressWarnings("unchecked")
            List<Object> selected = (List<Object>) selectedObject;
            capture.selectedReference = new WeakReference<>(selected);
            capture.original = new ArrayList<>(selected);

            List<Object> desired = desiredSelection(capture.original, activation.targetId);
            capture.firstRegistration = !containsI18nPack(capture.original);
            capture.changed = !capture.original.equals(desired);
            if (capture.changed) {
                replaceList(selected, desired);
            }
            capture.beforeSucceeded = true;
        } catch (Throwable failure) {
            capture.beforeSucceeded = false;
            reportFailure("unable to prepare in-memory resource-pack selection", failure);
        }

        synchronized (STATE_LOCK) {
            if (enabled) {
                CAPTURES.put(options, capture);
            }
        }
    }

    /**
     * Runs immediately before loadSelectedResourcePacks returns.  The method
     * receives names discovered by the transformer, so no Minecraft mappings
     * are linked from this class.
     *
     * <p>For a first registration, the generated pack is moved to the end of
     * the repository's visible selection and the repository is updated.  For
     * a filename migration, only the options list is synchronized with the
     * repository's existing order.  Persistence updates only the
     * {@code resourcePacks} line in options.txt: NeoForge reloads keybindings
     * after startup mod loading, so serializing the full Options object here
     * would erase bindings that have not registered yet.</p>
     */
    public static void afterSelection(Object options,
                                      Object repository,
                                      String selectedPacksMethodName,
                                      String packIdMethodName,
                                      String fixedPositionMethodName,
                                      String setSelectedMethodName) {
        SelectionCapture capture;
        synchronized (STATE_LOCK) {
            capture = options == null ? null : CAPTURES.remove(options);
        }
        if (capture == null) {
            return;
        }
        if (!capture.beforeSucceeded || !capture.changed) {
            return;
        }
        if (repository == null) {
            restoreSelection(capture, "resource-pack repository is null");
            return;
        }

        try {
            List<PackEntry> entries = readSelectedPacks(
                    repository, selectedPacksMethodName, packIdMethodName, fixedPositionMethodName);
            if (capture.firstRegistration) {
                activateFirstSelection(capture, options, repository, entries,
                        packIdMethodName, fixedPositionMethodName,
                        setSelectedMethodName, selectedPacksMethodName);
            } else {
                activateMigration(capture, options, entries);
            }
        } catch (Throwable failure) {
            restoreSelection(capture, "runtime selection failed", failure);
        }
    }

    private static void activateFirstSelection(SelectionCapture capture,
                                               Object options,
                                               Object repository,
                                               List<PackEntry> entries,
                                               String packIdMethodName,
                                               String fixedPositionMethodName,
                                               String setSelectedMethodName,
                                               String selectedPacksMethodName) throws Exception {
        PackEntry target = findVisibleTarget(entries, capture.targetId);
        if (target == null) {
            restoreSelection(capture, "generated pack was not selected by the repository");
            return;
        }

        List<String> reordered = new ArrayList<String>();
        for (PackEntry entry : entries) {
            /* PackRepository.setSelected accepts selected root IDs, not Pack
             * instances. Hidden children are re-expanded by the repository;
             * fixed roots remain in the repository's existing relative order. */
            if (entry.hidden || entry.id == null
                    || capture.targetId.equals(entry.id)) {
                continue;
            }
            reordered.add(entry.id);
        }
        reordered.add(target.id);

        invokeWithCollection(repository, setSelectedMethodName, reordered);

        List<PackEntry> finalEntries = readSelectedPacks(
                repository, selectedPacksMethodName, packIdMethodName, fixedPositionMethodName);
        if (findVisibleTarget(finalEntries, capture.targetId) == null) {
            restoreSelection(capture, "generated pack disappeared after repository selection");
            return;
        }

        List<String> visibleIds = visibleIds(finalEntries);
        if (!containsString(visibleIds, capture.targetId)) {
            restoreSelection(capture, "generated pack is not a visible selected root");
            return;
        }
        replaceList(selectedList(capture), visibleIds);
        GameConfig.updateResourcePacks(findOptionsFile(options), visibleIds);
    }

    private static void activateMigration(SelectionCapture capture,
                                          Object options,
                                          List<PackEntry> entries) throws Exception {
        if (findVisibleTarget(entries, capture.targetId) == null) {
            restoreSelection(capture, "generated pack was not selected during filename migration");
            return;
        }

        /* Match vanilla Options.updateResourcePacks: only visible, movable
         * packs are represented in resourcePacks.  Repository order remains
         * untouched, preserving manual interleaving. */
        List<String> visibleIds = visibleIds(entries);
        if (!containsString(visibleIds, capture.targetId)) {
            restoreSelection(capture, "generated pack is not a visible migrated root");
            return;
        }
        replaceList(selectedList(capture), visibleIds);
        GameConfig.updateResourcePacks(findOptionsFile(options), visibleIds);
    }

    private static Path findOptionsFile(Object options) throws Exception {
        if (options == null) {
            throw new IllegalArgumentException("options is null");
        }
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> type = options.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                fields.add(field);
            }
        }

        /* Mapped and obfuscated variants may use different member names.  The
         * known name is only an ordering hint; the value-type fallback below
         * keeps this binding independent of Minecraft mappings. */
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())
                    || !isOptionsPathField(field)
                    || !"optionsFile".equalsIgnoreCase(field.getName())) {
                continue;
            }
            Path candidate = readOptionsField(options, field, true);
            if (candidate != null) {
                return candidate;
            }
        }
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())
                    || !isOptionsPathField(field)) {
                continue;
            }
            Path candidate = readOptionsField(options, field, false);
            if (candidate != null) {
                return candidate;
            }
        }
        throw new IllegalStateException("unable to locate options file");
    }

    private static boolean isOptionsPathField(Field field) {
        Class<?> type = field.getType();
        return File.class.isAssignableFrom(type) || Path.class.isAssignableFrom(type);
    }

    private static Path optionsPath(Object value, boolean preferred) {
        Path candidate;
        if (value instanceof File) {
            candidate = ((File) value).toPath();
        } else if (value instanceof Path) {
            candidate = (Path) value;
        } else {
            return null;
        }
        Path fileName = candidate.getFileName();
        if (preferred || (fileName != null && "options.txt".equalsIgnoreCase(fileName.toString()))) {
            return candidate;
        }
        return null;
    }

    private static Path readOptionsField(Object options, Field field, boolean preferred) {
        try {
            makeAccessible(field);
            return optionsPath(field.get(options), preferred);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<PackEntry> readSelectedPacks(Object repository,
                                                     String selectedPacksMethodName,
                                                     String packIdMethodName,
                                                     String fixedPositionMethodName)
            throws Exception {
        Object selected = invokeNoArg(repository, selectedPacksMethodName);
        if (!(selected instanceof Iterable<?>)) {
            throw new IllegalStateException("selected packs method did not return an Iterable");
        }

        List<PackEntry> entries = new ArrayList<PackEntry>();
        Iterator<?> iterator = ((Iterable<?>) selected).iterator();
        while (iterator.hasNext()) {
            Object pack = iterator.next();
            if (pack == null) {
                entries.add(new PackEntry(null, null, false, false));
                continue;
            }
            String id = readId(pack, packIdMethodName);
            boolean hidden = readBoolean(pack, HIDDEN_METHOD, false);
            boolean fixed = readBoolean(pack, fixedPositionMethodName, false);
            entries.add(new PackEntry(pack, id, hidden, fixed));
        }
        return entries;
    }

    private static PackEntry findVisibleTarget(List<PackEntry> entries, String targetId) {
        for (PackEntry entry : entries) {
            if (targetId.equals(entry.id) && !entry.hidden && !entry.fixed) {
                return entry;
            }
        }
        return null;
    }

    private static List<String> visibleIds(List<PackEntry> entries) {
        List<String> ids = new ArrayList<String>();
        for (PackEntry entry : entries) {
            if (entry.hidden || entry.fixed || entry.id == null) {
                continue;
            }
            ids.add(entry.id);
        }
        return ids;
    }

    private static List<Object> desiredSelection(List<Object> original, String targetId) {
        boolean hasExact = false;
        int firstFamily = -1;
        for (int i = 0; i < original.size(); i++) {
            Object value = original.get(i);
            if (targetId.equals(value)) {
                hasExact = true;
            }
            if (isI18nPackId(value)) {
                if (firstFamily < 0) {
                    firstFamily = i;
                }
            }
        }

        if (hasExact) {
            List<Object> desired = new ArrayList<Object>(original.size());
            boolean retained = false;
            for (Object value : original) {
                if (!isI18nPackId(value)) {
                    desired.add(value);
                } else if (targetId.equals(value) && !retained) {
                    desired.add(value);
                    retained = true;
                }
            }
            return desired;
        }

        if (firstFamily >= 0) {
            List<Object> desired = new ArrayList<Object>(original.size());
            boolean replaced = false;
            for (Object value : original) {
                if (!isI18nPackId(value)) {
                    desired.add(value);
                } else if (!replaced) {
                    desired.add(targetId);
                    replaced = true;
                }
            }
            return desired;
        }

        List<Object> desired = new ArrayList<Object>(original.size() + 1);
        desired.addAll(original);
        desired.add(targetId);
        return desired;
    }

    private static boolean containsI18nPack(List<Object> selected) {
        for (Object value : selected) {
            if (isI18nPackId(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isI18nPackId(Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        String id = (String) value;
        return id.startsWith(FILE_ID_PREFIX + I18N_FILE_PREFIX);
    }

    private static String readId(Object pack, String methodName) throws Exception {
        Object id = invokeNoArg(pack, methodName);
        return id == null ? null : String.valueOf(id);
    }

    private static boolean readBoolean(Object target, String methodName, boolean defaultValue) {
        if (target == null || methodName == null || methodName.isEmpty()) {
            return defaultValue;
        }
        try {
            Object result = invokeNoArg(target, methodName);
            return result instanceof Boolean ? ((Boolean) result).booleanValue() : defaultValue;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private static void replaceList(List<?> target, List<?> replacement) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> mutable = (List<Object>) target;
        List<Object> old = new ArrayList<Object>(mutable);
        try {
            mutable.clear();
            mutable.addAll(replacement);
        } catch (Throwable failure) {
            try {
                mutable.clear();
                mutable.addAll(old);
            } catch (Throwable ignored) {
                // The original failure is more useful to the caller.
            }
            if (failure instanceof Exception) {
                throw (Exception) failure;
            }
            throw new Exception(failure);
        }
    }

    private static List<Object> selectedList(SelectionCapture capture) throws Exception {
        List<Object> selected = capture.selectedReference == null
                ? null : capture.selectedReference.get();
        if (selected == null) {
            throw new IllegalStateException("selected resource-pack list is no longer available");
        }
        return selected;
    }


    private static void restoreSelection(SelectionCapture capture, String reason) {
        restoreSelection(capture, reason, null);
    }

    private static void restoreSelection(SelectionCapture capture, String reason, Throwable failure) {
        List<Object> selected = capture.selectedReference == null
                ? null : capture.selectedReference.get();
        if (selected != null && capture.original != null) {
            try {
                replaceList(selected, capture.original);
            } catch (Throwable restoreFailure) {
                reportFailure(reason + "; unable to restore selected list", restoreFailure);
                return;
            }
        }
        reportFailure(reason, failure);
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        if (target == null || fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("selected field name is empty");
        }
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            throw new NoSuchFieldException(fieldName);
        }
        makeAccessible(field);
        return field.get(target);
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        if (target == null || methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("method name is empty");
        }
        Method method = findMethod(target.getClass(), methodName, 0, null);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "." + methodName + "()");
        }
        makeAccessible(method);
        try {
            return method.invoke(target);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw failure;
        }
    }

    private static void invokeWithCollection(Object target, String methodName, List<String> value) throws Exception {
        if (target == null || methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("set-selected method name is empty");
        }
        Method method = findMethod(target.getClass(), methodName, 1, value);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "." + methodName + "(Collection)");
        }
        makeAccessible(method);
        try {
            method.invoke(target, value);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw failure;
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through inherited fields.
            }
        }
        try {
            return type.getField(name);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount, Object argument) {
        Method[] publicMethods = type.getMethods();
        Method candidate = chooseMethod(publicMethods, name, parameterCount, argument);
        if (candidate != null) {
            return candidate;
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            candidate = chooseMethod(current.getDeclaredMethods(), name, parameterCount, argument);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static Method chooseMethod(Method[] methods, String name, int parameterCount, Object argument) {
        for (Method method : methods) {
            if (!method.getName().equals(name)
                    || method.getParameterTypes().length != parameterCount
                    || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (parameterCount == 0) {
                return method;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            if (argument == null || parameter.isAssignableFrom(argument.getClass())) {
                return method;
            }
        }
        return null;
    }

    private static void makeAccessible(java.lang.reflect.AccessibleObject object) {
        try {
            object.setAccessible(true);
        } catch (RuntimeException ignored) {
            // Public methods/fields may still be invocable without it.
        }
    }

    private static boolean containsString(List<String> values, String target) {
        return values.contains(target);
    }

    private static void reportFailure(String message, Throwable failure) {
        try {
            if (failure == null) {
                Log.warning("Runtime pack activation failed: %s", message);
            } else {
                String detail = failure.getClass().getSimpleName();
                if (failure.getMessage() != null && failure.getMessage().length() > 0) {
                    detail += ": " + failure.getMessage();
                }
                Log.warning("Runtime pack activation failed: %s (%s)", message, detail);
            }
        } catch (Throwable ignored) {
            // Logging must never make a loader mismatch fatal.
        }
    }

    private static final class PendingActivation {
        final String targetId;

        PendingActivation(String targetId) {
            this.targetId = targetId;
        }
    }

    private static final class SelectionCapture {
        final String targetId;
        WeakReference<List<Object>> selectedReference;
        List<Object> original;
        boolean firstRegistration;
        boolean changed;
        boolean beforeSucceeded;

        SelectionCapture(String targetId) {
            this.targetId = targetId;
        }
    }

    private static final class PackEntry {
        final Object pack;
        final String id;
        final boolean hidden;
        final boolean fixed;

        PackEntry(Object pack, String id, boolean hidden, boolean fixed) {
            this.pack = pack;
            this.id = id;
            this.hidden = hidden;
            this.fixed = fixed;
        }
    }
}
