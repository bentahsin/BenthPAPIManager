package com.bentahsin.benthpapimanager;

import com.bentahsin.benthpapimanager.annotations.*;
import com.bentahsin.benthpapimanager.middleware.PlaceholderMiddleware;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BenthPAPIManager {

    private final JavaPlugin plugin;
    private final List<PlaceholderExpansion> registeredExpansions = new ArrayList<>();
    private final Map<Class<?>, Object> injectables = new HashMap<>();
    private String globalErrorText = "§cError§r";
    private boolean debugMode = false;

    private BenthPAPIManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * BenthPAPIManager için bir builder başlatır.
     * @param plugin Ana plugin sınıfınız.
     * @return Yeni bir BenthPAPIManager örneği.
     */
    public static BenthPAPIManager create(JavaPlugin plugin) {
        return new BenthPAPIManager(plugin);
    }

    /**
     * Placeholder sınıflarına enjekte edilebilecek bir nesne kaydeder.
     * @param type Nesnenin sınıf tipi (örn: DatabaseManager.class).
     * @param instance Nesnenin kendisi.
     * @return Zincirleme kullanım için kendisini döndürür.
     */
    public BenthPAPIManager withInjectable(Class<?> type, Object instance) {
        this.injectables.put(type, instance);
        return this;
    }

    /**
     * Tüm placeholder'lar için varsayılan bir hata metni belirler.
     * Anotasyonda belirtilen 'onError' bu değeri ezer.
     * @param errorText Varsayılan hata metni.
     * @return Zincirleme kullanım için kendisini döndürür.
     */
    public BenthPAPIManager withDefaultErrorText(String errorText) {
        this.globalErrorText = errorText;
        return this;
    }

    /**
     * Kütüphane için debug modunu etkinleştirir.
     * Etkinleştirildiğinde, konsola daha ayrıntılı hata ayıklama mesajları basılır.
     * @return Zincirleme kullanım için kendisini döndürür.
     */
    public BenthPAPIManager withDebugMode() {
        this.debugMode = true;
        return this;
    }

    /**
     * Belirtilen placeholder sınıflarını kaydeder.
     * Bu metot, yapılandırma zincirinin son adımı olmalıdır.
     * @param placeholderClasses Kaydedilecek placeholder sınıfları.
     * @return Yapılandırmanın devam etmesi için kendisini döndürür.
     */
    public BenthPAPIManager register(Class<?>... placeholderClasses) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().warning("PlaceholderAPI bulunamadı, BenthPAPIManager placeholder'ları kaydedemedi.");
            return this;
        }

        if (placeholderClasses == null || placeholderClasses.length == 0) {
            plugin.getLogger().info("Kaydedilecek placeholder sınıfı belirtilmedi.");
            return this;
        }

        Map<String, List<Class<?>>> groupedClasses = new HashMap<>();
        for (Class<?> clazz : placeholderClasses) {
            if (!clazz.isAnnotationPresent(Placeholder.class)) {
                plugin.getLogger().warning("'" + clazz.getName() + "' sınıfı @Placeholder anotasyonuna sahip olmadığı için atlandı.");
                continue;
            }
            Placeholder placeholderInfo = clazz.getAnnotation(Placeholder.class);
            groupedClasses.computeIfAbsent(placeholderInfo.identifier(), k -> new ArrayList<>()).add(clazz);
        }

        if (groupedClasses.isEmpty()) {
            plugin.getLogger().info("Kaydedilecek geçerli placeholder bulunamadı.");
            return this;
        }

        for (Map.Entry<String, List<Class<?>>> entry : groupedClasses.entrySet()) {
            String identifier = entry.getKey();
            List<Class<?>> classesInGroup = entry.getValue();

            try {
                PlaceholderExpansion expansion = createGroupedExpansion(classesInGroup);
                if (expansion != null && expansion.register()) {
                    this.registeredExpansions.add(expansion);
                    plugin.getLogger().info("'" + identifier + "' placeholder'ları (" + classesInGroup.size() + " sınıf birleştirildi) başarıyla kaydedildi.");
                } else {
                    plugin.getLogger().warning("'" + identifier + "' placeholder'ları kaydedilemedi.");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "'" + identifier + "' placeholder grubu işlenirken bir hata oluştu:", e);
            }
        }
        return this;
    }

    /**
     * Kayıtlı tüm placeholder'ların listesini ve açıklamalarını bir dosyaya yazar.
     * @param fileName Oluşturulacak dosyanın adı (örn: "placeholders.txt"). Plugin klasörüne kaydedilir.
     */
    @SuppressWarnings("unused")
    public void generateDocs(String fileName) {
        // Ensure the plugin data folder exists before writing the file
        plugin.getDataFolder().mkdirs();
        File file = new File(plugin.getDataFolder(), fileName);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("=== " + plugin.getName() + " Placeholder Listesi ===");
            writer.println("Oluşturulma Tarihi: " + java.time.LocalDateTime.now());
            writer.println("==================================================\n");

            for (PlaceholderExpansion expansion : registeredExpansions) {
                if (expansion instanceof DynamicExpansion) {
                    DynamicExpansion dyn = (DynamicExpansion) expansion;
                    writer.println("GRUP: %" + dyn.getIdentifier() + "_...%");
                    writer.println("Yazar: " + dyn.getAuthor() + " | Versiyon: " + dyn.getVersion());
                    writer.println("--------------------------------------------------");

                    for (Map.Entry<String, PlaceholderMethod> entry : dyn.standardMethods.entrySet()) {
                        PlaceholderMethod pm = entry.getValue();
                        String fullPapi = "%" + dyn.getIdentifier() + "_" + entry.getKey() + "%";

                        writer.println("• " + fullPapi);

                        if (!pm.annotation.description().isEmpty()) {
                            writer.println("  Açıklama: " + pm.annotation.description());
                        }
                        if (!pm.annotation.example().isEmpty()) {
                            writer.println("  Örnek: " + pm.annotation.example());
                        }
                        if (pm.permissionInfo != null) {
                            writer.println("  Gerekli Yetki: " + pm.permissionInfo.value());
                        }
                        if (pm.cacheInfo != null) {
                            writer.println("  Önbellek: " + pm.cacheInfo.duration() + " " + pm.cacheInfo.unit().toString().toLowerCase());
                        }
                        writer.println();
                    }

                    if (!dyn.relationalMethods.isEmpty()) {
                        writer.println("  [İlişkisel Placeholderlar]");
                        for (Map.Entry<String, PlaceholderMethod> entry : dyn.relationalMethods.entrySet()) {
                            PlaceholderMethod pm = entry.getValue();
                            String fullPapi = "%rel_" + dyn.getIdentifier() + "_" + entry.getKey() + "%";
                            writer.println("• " + fullPapi);
                            if (!pm.relAnnotation.description().isEmpty()) {
                                writer.println("  Açıklama: " + pm.relAnnotation.description());
                            }
                            writer.println();
                        }
                    }
                    writer.println("==================================================\n");
                }
            }
            plugin.getLogger().info("Placeholder dokümantasyonu oluşturuldu: " + file.getPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Dokümantasyon oluşturulurken hata meydana geldi.", e);
        }
    }

    public void unregisterAll() {
        if (!registeredExpansions.isEmpty()) {
            plugin.getLogger().info(registeredExpansions.size() + " adet placeholder grubu kaldırılıyor...");
            for (PlaceholderExpansion expansion : registeredExpansions) {
                try {
                    expansion.unregister();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "'" + expansion.getIdentifier() + "' placeholder'ı kaldırılırken bir hata oluştu.", e);
                }
            }
            registeredExpansions.clear();
            plugin.getLogger().info("Tüm placeholder'lar başarıyla kaldırıldı.");
        }
    }

    private PlaceholderExpansion createGroupedExpansion(List<Class<?>> classes) throws Exception {
        if (classes.isEmpty()) return null;

        final Map<String, PlaceholderMethod> standardMethods = new HashMap<>();
        final Map<String, PlaceholderMethod> relationalMethods = new HashMap<>();

        for (Class<?> clazz : classes) {
            Object instance;
            try {
                instance = clazz.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException e) {
                plugin.getLogger().severe("HATA: '" + clazz.getName() + "' sınıfının boş (parametresiz) bir constructor'ı yok!");
                plugin.getLogger().severe("Lütfen 'public " + clazz.getSimpleName() + "() {}' ekleyin.");
                continue;
            }

            handleInjections(clazz, instance);

            for (Method method : clazz.getMethods()) {
                if (method.isAnnotationPresent(PlaceholderIdentifier.class)) {
                    String identifier = method.getAnnotation(PlaceholderIdentifier.class).identifier().toLowerCase();
                    standardMethods.put(identifier, new PlaceholderMethod(method, instance));
                }
                if (method.isAnnotationPresent(RelationalPlaceholder.class)) {
                    String id = method.getAnnotation(RelationalPlaceholder.class).identifier().toLowerCase();
                    relationalMethods.put(id, new PlaceholderMethod(method, instance));
                }
            }
        }

        Placeholder placeholderInfo = classes.get(0).getAnnotation(Placeholder.class);
        return new DynamicExpansion(plugin, placeholderInfo, standardMethods, relationalMethods, this.globalErrorText, this.debugMode);
    }

    private void handleInjections(Class<?> clazz, Object instance) throws IllegalAccessException {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                field.setAccessible(true);
                Class<?> fieldType = field.getType();

                if (injectables.containsKey(fieldType)) {
                    field.set(instance, injectables.get(fieldType));
                }
                else if (JavaPlugin.class.isAssignableFrom(fieldType)) {
                    field.set(instance, this.plugin);
                }
                else {
                    plugin.getLogger().warning("Enjeksiyon hatası: '" + clazz.getSimpleName() + "' sınıfındaki '" +
                            field.getName() + "' alanı için '" + fieldType.getSimpleName() +
                            "' tipinde kaydedilmiş bir nesne bulunamadı.");
                }
            }
        }
    }

    private static final class PlaceholderMethod {
        final Method method;
        final Object instance;
        final PlaceholderIdentifier annotation;
        final RelationalPlaceholder relAnnotation;
        final Cache cacheInfo;
        final Middleware middlewareInfo;
        final RequirePermission permissionInfo;

        PlaceholderMethod(Method method, Object instance) {
            this.method = method;
            this.instance = instance;
            this.annotation = method.getAnnotation(PlaceholderIdentifier.class);
            this.relAnnotation = method.getAnnotation(RelationalPlaceholder.class);
            this.cacheInfo = method.getAnnotation(Cache.class);
            this.middlewareInfo = method.getAnnotation(Middleware.class);
            this.permissionInfo = method.getAnnotation(RequirePermission.class);
        }
    }

    private static class DynamicExpansion extends PlaceholderExpansion implements Relational {
        private final JavaPlugin plugin;
        private final Placeholder placeholderInfo;
        final Map<String, PlaceholderMethod> standardMethods;
        final Map<String, PlaceholderMethod> relationalMethods;
        private final String defaultErrorText;
        private final boolean debug;

        private final Map<Class<?>, PlaceholderMiddleware> middlewareInstances = new ConcurrentHashMap<>();
        private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

        DynamicExpansion(JavaPlugin plugin, Placeholder info, Map<String, PlaceholderMethod> standardMethods, Map<String, PlaceholderMethod> relationalMethods, String defaultErrorText, boolean debug) {
            this.plugin = plugin;
            this.placeholderInfo = info;
            this.standardMethods = standardMethods;
            this.relationalMethods = relationalMethods;
            this.defaultErrorText = defaultErrorText;
            this.debug = debug;
        }

        @Override public @NotNull String getIdentifier() { return placeholderInfo.identifier(); }
        @Override public @NotNull String getAuthor() { return placeholderInfo.author(); }
        @Override public @NotNull String getVersion() { return placeholderInfo.version(); }
        @Override public boolean persist() { return true; }

        @Override
        public String onPlaceholderRequest(Player one, Player two, String params) {
            if (one == null || two == null) return null;

            for (Map.Entry<String, PlaceholderMethod> entry : relationalMethods.entrySet()) {
                String id = entry.getKey();
                if (params.equalsIgnoreCase(id) || params.toLowerCase().startsWith(id + "_")) {
                    String arg = params.length() > id.length() ? params.substring(id.length() + 1) : null;
                    return handleRelational(one, two, entry.getValue(), arg, params);
                }
            }
            return null;
        }

        private String getErrorText(PlaceholderMethod method) {
            if (method.annotation != null && !method.annotation.onError().isEmpty()) {
                return method.annotation.onError();
            }
            return this.defaultErrorText;
        }

        private String getRelationalErrorText(PlaceholderMethod method) {
            if (method.relAnnotation != null && !method.relAnnotation.onError().isEmpty()) {
                return method.relAnnotation.onError();
            }
            return this.defaultErrorText;
        }

        @Override
        public String onRequest(OfflinePlayer player, @NotNull String params) {
            if (debug) {
                plugin.getLogger().info(String.format("[DEBUG] Placeholder request: %%%s_%s%% for player %s",
                        getIdentifier(), params, (player != null ? player.getName() : "null")));
            }

            String matchedId = null;
            PlaceholderMethod matchedMethod = null;

            for (Map.Entry<String, PlaceholderMethod> entry : standardMethods.entrySet()) {
                String id = entry.getKey();
                if (params.equalsIgnoreCase(id) || params.toLowerCase().startsWith(id + "_")) {
                    if (matchedId == null || id.length() > matchedId.length()) {
                        matchedId = id;
                        matchedMethod = entry.getValue();
                    }
                }
            }

            if (matchedMethod != null) {
                String argument = params.length() > matchedId.length() ? params.substring(matchedId.length() + 1) : null;
                return handleStandard(player, matchedMethod, argument, params);
            }

            return null;
        }

        private String handleStandard(OfflinePlayer viewer, PlaceholderMethod pMethod, String arg, String fullParams) {
            if (pMethod.permissionInfo != null) {
                if (viewer != null && viewer.isOnline()) {
                    if (!viewer.getPlayer().hasPermission(pMethod.permissionInfo.value())) {
                        return pMethod.permissionInfo.onDeny();
                    }
                }
            }

            String cacheKey = "std:" + (viewer != null ? viewer.getUniqueId() : "null") + ":" + fullParams;

            if (pMethod.cacheInfo != null) {
                CachedResult cached = cache.get(cacheKey);
                long duration = pMethod.cacheInfo.unit().toMillis(pMethod.cacheInfo.duration());
                if (cached != null && !cached.isExpired(duration)) {
                    return cached.value;
                }
            }

            if (pMethod.annotation.async()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String rawResult = executeStandard(viewer, pMethod, arg);
                    String finalResult = applyMiddleware(rawResult, pMethod);

                    cache.put(cacheKey, new CachedResult(finalResult));
                });

                CachedResult existing = cache.get(cacheKey);
                if (existing != null && pMethod.cacheInfo == null) {
                    if (!existing.isExpired(2000)) return existing.value;
                }

                return pMethod.annotation.onLoading();
            }

            String rawResult = executeStandard(viewer, pMethod, arg);
            String finalResult = applyMiddleware(rawResult, pMethod);

            if (pMethod.cacheInfo != null) {
                cache.put(cacheKey, new CachedResult(finalResult));
            }

            return finalResult;
        }

        private String handleRelational(Player one, Player two, PlaceholderMethod rMethod, String arg, String fullParams) {
            if (rMethod.permissionInfo != null) {
                if (!one.hasPermission(rMethod.permissionInfo.value())) {
                    return rMethod.permissionInfo.onDeny();
                }
            }

            String cacheKey = "rel:" + one.getUniqueId() + ":" + two.getUniqueId() + ":" + fullParams;

            if (rMethod.cacheInfo != null) {
                CachedResult cached = cache.get(cacheKey);
                long duration = rMethod.cacheInfo.unit().toMillis(rMethod.cacheInfo.duration());
                if (cached != null && !cached.isExpired(duration)) {
                    return cached.value;
                }
            }

            if (rMethod.relAnnotation.async()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String rawResult = executeRelational(one, two, rMethod, arg);
                    String finalResult = applyMiddleware(rawResult, rMethod);
                    cache.put(cacheKey, new CachedResult(finalResult));
                });

                CachedResult existing = cache.get(cacheKey);
                if (existing != null && rMethod.cacheInfo == null) {
                    if (!existing.isExpired(2000)) return existing.value;
                }

                return rMethod.relAnnotation.onLoading();
            }

            String rawResult = executeRelational(one, two, rMethod, arg);
            String finalResult = applyMiddleware(rawResult, rMethod);

            if (rMethod.cacheInfo != null) {
                cache.put(cacheKey, new CachedResult(finalResult));
            }

            return finalResult;
        }

        private String applyMiddleware(String rawResult, PlaceholderMethod pMethod) {
            if (pMethod.middlewareInfo == null || rawResult == null || rawResult.isEmpty()) {
                return rawResult;
            }

            Object currentResult = rawResult;
            try {
                for (Class<? extends PlaceholderMiddleware> middlewareClass : pMethod.middlewareInfo.value()) {
                    PlaceholderMiddleware middleware = middlewareInstances.computeIfAbsent(middlewareClass, clazz -> {
                        try {
                            return (PlaceholderMiddleware) clazz.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "Middleware sınıfı başlatılamadı: " + clazz.getName(), e);
                            return null;
                        }
                    });

                    if (middleware != null) {
                        currentResult = middleware.process(currentResult);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Middleware uygulanırken hata oluştu (" + pMethod.method.getName() + ")", e);
                return getErrorText(pMethod);
            }

            return String.valueOf(currentResult);
        }

        private String executeStandard(OfflinePlayer viewer, PlaceholderMethod pMethod, String argument) {
            try {
                Method method = pMethod.method;
                Object instance = pMethod.instance;
                Object result = null;
                int count = method.getParameterCount();

                if (count == 0) {
                    result = method.invoke(instance);
                } else if (count == 1) {
                    if (method.getParameterTypes()[0] == String.class) {
                        result = method.invoke(instance, argument);
                    } else {
                        Object pArg = viewer != null && viewer.isOnline() ? viewer.getPlayer() : viewer;
                        if (pArg != null && method.getParameterTypes()[0].isAssignableFrom(pArg.getClass())) {
                            result = method.invoke(instance, pArg);
                        } else if (pArg == null) {
                            return "";
                        } else {
                            return getErrorText(pMethod);
                        }
                    }
                } else if (count == 2) {
                    Object pArg = viewer != null && viewer.isOnline() ? viewer.getPlayer() : viewer;
                    result = method.invoke(instance, pArg, argument);
                }

                return result == null ? "" : String.valueOf(result);
            } catch (Exception e) {
                logError(pMethod.method, e);
                return getErrorText(pMethod);
            }
        }

        private String executeRelational(Player one, Player two, PlaceholderMethod rMethod, String argument) {
            try {
                Method method = rMethod.method;
                Object result;

                if (method.getParameterCount() == 3) {
                    result = method.invoke(rMethod.instance, one, two, argument);
                } else {
                    result = method.invoke(rMethod.instance, one, two);
                }
                return result == null ? "" : String.valueOf(result);
            } catch (Exception e) {
                logError(rMethod.method, e);
                return getRelationalErrorText(rMethod);
            }
        }

        private void logError(Method method, Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            plugin.getLogger().log(Level.WARNING, "--- BenthPAPI Hata Raporu ---");
            plugin.getLogger().warning("Placeholder: %" + getIdentifier() + "_...");
            plugin.getLogger().warning("İşleyen Sınıf: " + method.getDeclaringClass().getSimpleName());
            plugin.getLogger().warning("İşleyen Metot: " + method.getName());
            plugin.getLogger().warning("Hata Tipi: " + cause.getClass().getSimpleName());
            plugin.getLogger().log(Level.WARNING, "Hata Mesajı ve Stack Trace:", cause);
            plugin.getLogger().warning("---------------------------------");
        }
    }

    private static class CachedResult {
        final String value;
        final long timestamp;

        CachedResult(String value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long durationMillis) {
            return System.currentTimeMillis() - timestamp > durationMillis;
        }
    }
}