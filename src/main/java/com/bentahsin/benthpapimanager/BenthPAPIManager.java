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
 * PlaceholderAPI için anotasyon tabanlı, yüksek performanslı bir placeholder yönetim kütüphanesi.
 * Bu sınıf, belirtilen paketteki sınıfları tarayarak placeholder'ları otomatik olarak kaydeder.
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
     * Bir sınıfın örneğini tarayarak @Inject anotasyonuna sahip alanlara ana plugin'i enjekte eder.
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
                RelationalPlaceholder annotation = method.getAnnotation(RelationalPlaceholder.class);
                relationalMethods.put(annotation.identifier().toLowerCase(), method);
            }
            if (method.isAnnotationPresent(PlaceholderIdentifier.class)) {
                PlaceholderIdentifier annotation = method.getAnnotation(PlaceholderIdentifier.class);
                standardMethods.put(annotation.identifier().toLowerCase(), new PlaceholderMethod(method, annotation));
            }
        }
        return new DynamicExpansion(plugin, info, instance, relationalMethods, standardMethods);
    }

    /**
     * Standart placeholder metotlarını ve ilgili anotasyonlarını bir arada tutan bir veri sınıfı.
     */
    private static final class PlaceholderMethod {
        final Method method;
        final PlaceholderIdentifier annotation;

        PlaceholderMethod(Method method, PlaceholderIdentifier annotation) {
            this.method = method;
            this.annotation = annotation;
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
                    plugin.getLogger().log(Level.WARNING, "Relational placeholder hatası: " + relationalMethod.getName(), e.getCause());
                    return relationalMethod.getAnnotation(RelationalPlaceholder.class).onError();
                }
            }
            return null;
        }

        @Override
        public String onRequest(OfflinePlayer player, @NotNull String params) {
            String lowerParams = params.toLowerCase();

            // 1. Önce gelen parametrenin tamamıyla eşleşen bir metot var mı diye kontrol et.
            // Bu, "toplam_eslesme" gibi alt tireli ama parametresiz olanları yakalar.
            PlaceholderMethod pMethod = standardMethods.get(lowerParams);
            if (pMethod != null) {
                return invokeStandardMethod(pMethod, player, null);
            }

            // 2. Tam eşleşme yoksa, "identifier_parametreler" formatında olup olmadığını kontrol et.
            String[] parts = lowerParams.split("_", 2);
            if (parts.length > 1) {
                String identifier = parts[0];
                String methodParams = parts[1];

                pMethod = standardMethods.get(identifier);
                if (pMethod != null) {
                    return invokeStandardMethod(pMethod, player, methodParams);
                }
            }

            return null; // Uygun placeholder bulunamadı.
        }

        private String invokeStandardMethod(PlaceholderMethod placeholderMethod, OfflinePlayer player, String params) {
            Method method = placeholderMethod.method;
            PlaceholderIdentifier annotation = placeholderMethod.annotation;

            try {
                if (method.getReturnType() != String.class) return null;
                int paramCount = method.getParameterCount();

                // Parametreli metot çağrısı (örn: onSomething(Player, String))
                if (params != null && paramCount == 2) {
                    if (player == null) return annotation.onError();
                    Class<?> argType = method.getParameterTypes()[0];

                    if (argType == Player.class) return player.isOnline() ? (String) method.invoke(placeholderInstance, player.getPlayer(), params) : annotation.onError();
                    if (argType == OfflinePlayer.class) return (String) method.invoke(placeholderInstance, player, params);
                }
                // Parametresiz metot çağrısı
                else if (params == null) {
                    if (paramCount == 0) { // onSomething()
                        return (String) method.invoke(placeholderInstance);
                    }
                    if (paramCount == 1) { // onSomething(Player)
                        if (player == null) return annotation.onError();
                        Class<?> argType = method.getParameterTypes()[0];

                        if (argType == Player.class) return player.isOnline() ? (String) method.invoke(placeholderInstance, player.getPlayer()) : annotation.onError();
                        if (argType == OfflinePlayer.class) return (String) method.invoke(placeholderInstance, player);
                    }
                }

            } catch (Exception e) {
                // InvocationTargetException durumunda asıl hatayı (cause) logla, bu geliştirici için daha anlamlıdır.
                Throwable cause = e.getCause();
                plugin.getLogger().log(Level.WARNING, "Placeholder metodu çalıştırılırken hata oluştu: " + method.getName(), cause != null ? cause : e);
                return annotation.onError();
            }

            // Metodun imza yapısı çağrıyla eşleşmedi (örn: 2 parametreli metot parametresiz çağrıldı).
            return null;
        }
    }
}