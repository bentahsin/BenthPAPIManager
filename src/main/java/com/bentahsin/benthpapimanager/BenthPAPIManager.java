package com.bentahsin.benthpapimanager;

import com.bentahsin.benthpapimanager.annotations.Inject;
import com.bentahsin.benthpapimanager.annotations.Placeholder;
import com.bentahsin.benthpapimanager.annotations.PlaceholderIdentifier;
import com.bentahsin.benthpapimanager.annotations.RelationalPlaceholder;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * PlaceholderAPI için anotasyon tabanlı, doğrudan sınıf kaydını destekleyen,
 * mevcut PAPI sürümüyle %100 uyumlu, kararlı bir placeholder yönetim kütüphanesi.
 */
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

        PlaceholderMethod(Method method, Object instance) {
            this.method = method;
            this.instance = instance;
            this.annotation = method.getAnnotation(PlaceholderIdentifier.class);
            this.relAnnotation = method.getAnnotation(RelationalPlaceholder.class);
        }
    }

    private static class DynamicExpansion extends PlaceholderExpansion implements Relational {
        private final JavaPlugin plugin;
        private final Placeholder placeholderInfo;
        private final Map<String, PlaceholderMethod> standardMethods;
        private final Map<String, PlaceholderMethod> relationalMethods;
        private final String defaultErrorText;
        private final boolean debug;

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
            if (pMethod.annotation.async()) {
                String cacheKey = "std:" + (viewer != null ? viewer.getUniqueId() : "null") + ":" + fullParams;
                CachedResult cached = cache.get(cacheKey);

                if (cached != null && !cached.isExpired()) {
                    return cached.value;
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String result = executeStandard(viewer, pMethod, arg);
                    cache.put(cacheKey, new CachedResult(result));
                });

                return cached != null ? cached.value : pMethod.annotation.onLoading();
            }

            return executeStandard(viewer, pMethod, arg);
        }

        private String handleRelational(Player one, Player two, PlaceholderMethod rMethod, String arg, String fullParams) {
            if (rMethod.relAnnotation.async()) {
                String cacheKey = "rel:" + one.getUniqueId() + ":" + two.getUniqueId() + ":" + fullParams;
                CachedResult cached = cache.get(cacheKey);

                if (cached != null && !cached.isExpired()) {
                    return cached.value;
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String result = executeRelational(one, two, rMethod, arg);
                    cache.put(cacheKey, new CachedResult(result));
                });

                return cached != null ? cached.value : rMethod.relAnnotation.onLoading();
            }

            return executeRelational(one, two, rMethod, arg);
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

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 2000;
        }
    }
}