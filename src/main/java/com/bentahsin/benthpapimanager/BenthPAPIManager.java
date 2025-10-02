package com.bentahsin.benthpapimanager;

import com.bentahsin.benthpapimanager.annotations.Inject;
import com.bentahsin.benthpapimanager.annotations.Placeholder;
import com.bentahsin.benthpapimanager.annotations.PlaceholderIdentifier;
import com.bentahsin.benthpapimanager.annotations.RelationalPlaceholder;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
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
            Object instance = clazz.getDeclaredConstructor().newInstance();
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

    private static class DynamicExpansion extends PlaceholderExpansion {
        private final JavaPlugin plugin;
        private final Placeholder placeholderInfo;
        private final Map<String, PlaceholderMethod> standardMethods;
        private final Map<String, PlaceholderMethod> relationalMethods;
        private final String defaultErrorText;
        private final boolean debug;

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
        public String onPlaceholderRequest(Player player, @NotNull String params) {
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

            if (player == null) return "";

            String[] relParts = params.split("_", 2);
            if (relParts.length > 1) {
                String relIdentifier = relParts[0].toLowerCase();
                PlaceholderMethod relMethod = relationalMethods.get(relIdentifier);

                if (relMethod != null) {
                    if (relMethod.relAnnotation.async()) {
                        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> handleRelationalRequest(player, params, relMethod));
                        return relMethod.relAnnotation.onLoading();
                    }
                    return handleRelationalRequest(player, params, relMethod);
                }
            }

            String[] parts = params.split("_", 2);
            String identifier = parts[0].toLowerCase();
            PlaceholderMethod pMethod = standardMethods.get(identifier);

            if (pMethod != null) {
                if (pMethod.annotation.async()) {
                    Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> handleStandardRequest(player, params, pMethod));
                    return pMethod.annotation.onLoading();
                }
                return handleStandardRequest(player, params, pMethod);
            }

            return null;
        }

        private String handleStandardRequest(OfflinePlayer viewer, String params, PlaceholderMethod pMethod) {
            try {
                String[] parts = params.split("_", 2);
                String argument = (parts.length > 1) ? parts[1] : null;

                Method method = pMethod.method;
                int paramCount = method.getParameterCount();
                Object result = null;

                if (paramCount == 0) {
                    result = method.invoke(pMethod.instance);
                } else if (paramCount == 1) {
                    Class<?> argType = method.getParameterTypes()[0];
                    if (argType.isAssignableFrom(Player.class)) result = viewer.isOnline() ? method.invoke(pMethod.instance, viewer.getPlayer()) : getErrorText(pMethod);
                    else if (argType.isAssignableFrom(OfflinePlayer.class)) result = method.invoke(pMethod.instance, viewer);
                } else if (paramCount == 2) {
                    if (argument == null) return getErrorText(pMethod);
                    Class<?> playerType = method.getParameterTypes()[0];
                    Class<?> argType = method.getParameterTypes()[1];
                    if (argType != String.class) return getErrorText(pMethod);
                    if (playerType.isAssignableFrom(Player.class)) result = viewer.isOnline() ? method.invoke(pMethod.instance, viewer.getPlayer(), argument) : getErrorText(pMethod);
                    else if (playerType.isAssignableFrom(OfflinePlayer.class)) result = method.invoke(pMethod.instance, viewer, argument);
                }
                return result != null ? String.valueOf(result) : null;
            } catch (Exception e) {
                logError(pMethod.method, e);
                return getErrorText(pMethod);
            }
        }

        private String handleRelationalRequest(OfflinePlayer viewer, String params, PlaceholderMethod relMethod) {
            try {
                String[] relParts = params.split("_", 2);
                String targetPlayerName = relParts[1];
                Player targetPlayer = Bukkit.getPlayerExact(targetPlayerName);

                if (!viewer.isOnline()) return getRelationalErrorText(relMethod);
                if (targetPlayer == null) return getRelationalErrorText(relMethod);

                if (relMethod.method.getParameterCount() == 2 &&
                        relMethod.method.getParameterTypes()[0] == Player.class &&
                        relMethod.method.getParameterTypes()[1] == Player.class) {
                    Object result = relMethod.method.invoke(relMethod.instance, viewer.getPlayer(), targetPlayer);
                    return String.valueOf(result);
                } else {
                    plugin.getLogger().warning("Relational placeholder metodu '" + relMethod.method.getName() + "' (Player, Player) parametrelerine sahip olmalıdır.");
                    return getRelationalErrorText(relMethod);
                }
            } catch (Exception e) {
                logError(relMethod.method, e);
                return getRelationalErrorText(relMethod);
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
}