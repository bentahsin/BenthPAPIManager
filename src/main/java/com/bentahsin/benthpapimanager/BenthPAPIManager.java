package com.bentahsin.benthpapimanager;

import com.bentahsin.benthpapimanager.annotations.Inject;
import com.bentahsin.benthpapimanager.annotations.Placeholder;
import com.bentahsin.benthpapimanager.annotations.PlaceholderIdentifier;
import com.bentahsin.benthpapimanager.annotations.RelationalPlaceholder;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * PlaceholderAPI için anotasyon tabanlı, yüksek performanslı ve kararlı bir placeholder yönetim kütüphanesi.
 */
public final class BenthPAPIManager {

    private final JavaPlugin plugin;

    public BenthPAPIManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Belirtilen paketteki tüm @Placeholder anotasyonuna sahip sınıfları tarar,
     * metotlarını ön belleğe alır ve PlaceholderAPI'ye kaydeder.
     *
     * @param packageName Taranacak paket adı (örn: "com.benimpluginim.placeholders").
     */
    public void registerPlaceholders(String packageName) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().warning("PlaceholderAPI bulunamadı, BenthPAPIManager placeholder'ları kaydedemedi.");
            return;
        }

        Reflections reflections = new Reflections(packageName);
        Set<Class<?>> placeholderClasses = reflections.getTypesAnnotatedWith(Placeholder.class);

        if (placeholderClasses.isEmpty()) {
            plugin.getLogger().info("'" + packageName + "' paketinde kaydedilecek placeholder bulunamadı.");
            return;
        }

        for (Class<?> clazz : placeholderClasses) {
            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                handleInjections(clazz, instance);

                Placeholder placeholderInfo = clazz.getAnnotation(Placeholder.class);
                PlaceholderExpansion expansion = createExpansion(placeholderInfo, clazz, instance);

                if (expansion.register()) {
                    plugin.getLogger().info("'" + placeholderInfo.identifier() + "' placeholder'ları başarıyla kaydedildi.");
                } else {
                    plugin.getLogger().warning("'" + placeholderInfo.identifier() + "' placeholder'ları kaydedilemedi.");
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, clazz.getName() + " placeholder sınıfı işlenirken bir hata oluştu:", e);
            }
        }
    }

    /**
     * @Inject anotasyonuna sahip alanlara ana plugin'i enjekte eder.
     */
    private void handleInjections(Class<?> clazz, Object instance) throws IllegalAccessException {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class) && JavaPlugin.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                field.set(instance, this.plugin);
            }
        }
    }

    /**
     * Bir sınıfı tarayarak placeholder metotlarını ön belleğe alır ve
     * bu metotları işleyecek yüksek performanslı bir PlaceholderExpansion oluşturur.
     */
    private PlaceholderExpansion createExpansion(Placeholder info, Class<?> clazz, Object instance) {
        final Map<String, Method> relationalMethods = new HashMap<>();
        final Map<String, PlaceholderMethod> standardMethods = new HashMap<>();

        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(RelationalPlaceholder.class)) {
                relationalMethods.put(method.getAnnotation(RelationalPlaceholder.class).identifier().toLowerCase(), method);
            }
            if (method.isAnnotationPresent(PlaceholderIdentifier.class)) {
                standardMethods.put(method.getAnnotation(PlaceholderIdentifier.class).identifier().toLowerCase(), new PlaceholderMethod(method));
            }
        }
        return new DynamicExpansion(plugin, info, instance, relationalMethods, standardMethods);
    }

    /**
     * Metot ve anotasyon bilgilerini bir arada tutan yardımcı sınıf.
     */
    private static final class PlaceholderMethod {
        final Method method;
        final PlaceholderIdentifier annotation;

        PlaceholderMethod(Method method) {
            this.method = method;
            this.annotation = method.getAnnotation(PlaceholderIdentifier.class);
        }
    }

    /**
     * Gelen istekleri ön belleğe alınmış metotlar üzerinden hızlıca işleyen özel PlaceholderExpansion uygulaması.
     */
    private static class DynamicExpansion extends PlaceholderExpansion {
        private final JavaPlugin plugin;
        private final Placeholder placeholderInfo;
        private final Object placeholderInstance;
        private final Map<String, Method> relationalMethods;
        private final Map<String, PlaceholderMethod> standardMethods;

        DynamicExpansion(JavaPlugin plugin, Placeholder info, Object instance, Map<String, Method> relationalMethods, Map<String, PlaceholderMethod> standardMethods) {
            this.plugin = plugin;
            this.placeholderInfo = info;
            this.placeholderInstance = instance;
            this.relationalMethods = relationalMethods;
            this.standardMethods = standardMethods;
        }

        @Override public @NotNull String getIdentifier() { return placeholderInfo.identifier(); }
        @Override public @NotNull String getAuthor() { return placeholderInfo.author(); }
        @Override public @NotNull String getVersion() { return placeholderInfo.version(); }
        @Override public boolean persist() { return true; }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String identifier) {
            if (player == null) return "";
            Method relationalMethod = relationalMethods.get(identifier.toLowerCase());
            if (relationalMethod != null) {
                try {
                    return (String) relationalMethod.invoke(placeholderInstance, player);
                } catch (Exception e) {
                    logError(relationalMethod, e);
                    return relationalMethod.getAnnotation(RelationalPlaceholder.class).onError();
                }
            }
            return null;
        }

        @Override
        public String onRequest(OfflinePlayer player, @NotNull String params) {
            String lowerParams = params.toLowerCase();

            // 1. Önce tam eşleşme ara (örn: "toplam_eslesme" veya "durum").
            PlaceholderMethod pMethod = standardMethods.get(lowerParams);
            if (pMethod != null) {
                return invokeStandardMethod(pMethod, player, null); // Parametresiz çağrı
            }

            // 2. Tam eşleşme yoksa, parametreli formatı (`identifier_args`) dene.
            String[] parts = lowerParams.split("_", 2);
            if (parts.length > 1) {
                String identifier = parts[0];
                String arguments = parts[1];

                pMethod = standardMethods.get(identifier);
                if (pMethod != null) {
                    return invokeStandardMethod(pMethod, player, arguments); // Parametreli çağrı
                }
            }

            return null; // Hiçbir uygun placeholder bulunamadı.
        }

        private String invokeStandardMethod(PlaceholderMethod pMethod, OfflinePlayer player, String args) {
            try {
                Method method = pMethod.method;
                int paramCount = method.getParameterCount();

                // Durum 1: Çağrı parametreli (args != null) ve metot 2 parametre bekliyor.
                if (args != null && paramCount == 2) {
                    if (player == null) return pMethod.annotation.onError();
                    Class<?> argType = method.getParameterTypes()[0];
                    if (argType == Player.class) return player.isOnline() ? (String) method.invoke(placeholderInstance, player.getPlayer(), args) : pMethod.annotation.onError();
                    if (argType == OfflinePlayer.class) return (String) method.invoke(placeholderInstance, player, args);
                }
                // Durum 2: Çağrı parametresiz (args == null).
                else if (args == null) {
                    // Alt Durum 2a: Metot hiç parametre beklemiyor (Sunucu placeholder'ı).
                    if (paramCount == 0) {
                        return (String) method.invoke(placeholderInstance);
                    }
                    // Alt Durum 2b: Metot 1 parametre bekliyor (Oyuncu placeholder'ı).
                    if (paramCount == 1) {
                        if (player == null) return pMethod.annotation.onError();
                        Class<?> argType = method.getParameterTypes()[0];
                        if (argType == Player.class) return player.isOnline() ? (String) method.invoke(placeholderInstance, player.getPlayer()) : pMethod.annotation.onError();
                        if (argType == OfflinePlayer.class) return (String) method.invoke(placeholderInstance, player);
                    }
                }
            } catch (Exception e) {
                logError(pMethod.method, e);
                return pMethod.annotation.onError();
            }

            return null; // Metodun imza yapısı yapılan çağrıyla eşleşmedi.
        }

        private void logError(Method method, Exception e) {
            Throwable cause = e.getCause();
            plugin.getLogger().log(Level.WARNING, "Placeholder metodu '" + method.getName() + "' çalıştırılırken bir hata oluştu:", cause != null ? cause : e);
        }
    }
}