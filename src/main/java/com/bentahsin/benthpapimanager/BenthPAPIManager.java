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
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class BenthPAPIManager {

    private final JavaPlugin plugin;

    public BenthPAPIManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Anotasyonlu placeholder'ları tarar, ön belleğe alır ve PlaceholderAPI'ye kaydeder.
     * @param packageName Taranacak paket adı.
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

            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.SEVERE, clazz.getName() + " placeholder sınıfı işlenirken bir reflection hatası oluştu:", e);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, clazz.getName() + " placeholder sınıfı işlenirken beklenmedik bir hata oluştu:", e);
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
     * Placeholder metotlarını ön belleğe alarak yüksek performanslı bir PlaceholderExpansion oluşturur.
     */
    private PlaceholderExpansion createExpansion(Placeholder info, Class<?> clazz, Object instance) {
        final Map<String, Method> relationalMethods = new HashMap<>();
        final Map<String, PlaceholderMethod> standardMethods = new HashMap<>();

        // Metotları sadece bir kez tara ve haritalara yerleştir (Caching)
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

    // --- Yardımcı Veri Sınıfları ---

    /**
     * Standart placeholder metotlarını ve anotasyonlarını bir arada tutan bir sınıf.
     */
    private static final class PlaceholderMethod {
        final Method method;
        final PlaceholderIdentifier annotation;

        public PlaceholderMethod(Method method, PlaceholderIdentifier annotation) {
            this.method = method;
            this.annotation = annotation;
        }
    }

    /**
     * Okunabilirlik ve performans için anonim sınıf yerine kullanılan özel PlaceholderExpansion uygulaması.
     */
    private static class DynamicExpansion extends PlaceholderExpansion {
        private final JavaPlugin plugin;
        private final Placeholder placeholderInfo;
        private final Object placeholderInstance;
        private final Map<String, Method> relationalMethods;
        private final Map<String, PlaceholderMethod> standardMethods;

        public DynamicExpansion(JavaPlugin plugin, Placeholder info, Object instance, Map<String, Method> relationalMethods, Map<String, PlaceholderMethod> standardMethods) {
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

            // Önbellekten hızlıca metodu bul (O(1) karmaşıklık)
            Method relationalMethod = relationalMethods.get(identifier.toLowerCase());
            if (relationalMethod != null) {
                try {
                    if (relationalMethod.getParameterCount() == 1 && relationalMethod.getParameterTypes()[0] == Player.class) {
                        return (String) relationalMethod.invoke(placeholderInstance, player);
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    plugin.getLogger().log(Level.WARNING, "Relational Placeholder metodu çalıştırılırken hata oluştu: " + relationalMethod.getName(), e);
                    return relationalMethod.getAnnotation(RelationalPlaceholder.class).onError();
                }
            }
            // PAPI'nin onRequest'e düşmesine izin ver
            return null;
        }

        @Override
        public String onRequest(OfflinePlayer player, @NotNull String params) {
            String lowerParams = params.toLowerCase();
            String[] parts = lowerParams.split("_", 2);
            String identifier = parts[0];
            String methodParams = parts.length > 1 ? parts[1] : null;

            // Önce parametreli haliyle ara (örn: "level_formatted")
            PlaceholderMethod pMethod = standardMethods.get(identifier);

            // Bulamazsa, tam eşleşme ara (örn: "playername")
            if (pMethod == null) {
                pMethod = standardMethods.get(lowerParams);
                if (pMethod != null) {
                    methodParams = null; // Tam eşleşme durumunda parametre olmaz.
                }
            }

            if (pMethod != null) {
                return invokeStandardMethod(pMethod.method, pMethod.annotation, player, methodParams);
            }

            return null;
        }

        private String invokeStandardMethod(Method method, PlaceholderIdentifier annotation, OfflinePlayer player, String params) {
            try {
                if (method.getReturnType() != String.class) return null;
                Class<?>[] paramTypes = method.getParameterTypes();
                int paramCount = method.getParameterCount();

                if (params != null && paramCount == 2 && paramTypes[1] == String.class) {
                    if (player == null) return annotation.onError();
                    if (paramTypes[0] == Player.class) return player.isOnline() ? (String) method.invoke(placeholderInstance, player.getPlayer(), params) : annotation.onError();
                    if (paramTypes[0] == OfflinePlayer.class) return (String) method.invoke(placeholderInstance, player, params);
                }
                else if (paramCount == 1) {
                    if (player == null) return annotation.onError();
                    if (paramTypes[0] == Player.class) return player.isOnline() ? (String) method.invoke(placeholderInstance, player.getPlayer()) : annotation.onError();
                    if (paramTypes[0] == OfflinePlayer.class) return (String) method.invoke(placeholderInstance, player);
                }
                else if (paramCount == 0) {
                    return (String) method.invoke(placeholderInstance);
                }

            } catch (IllegalAccessException | InvocationTargetException e) {
                plugin.getLogger().log(Level.WARNING, "Placeholder metodu çalıştırılırken hata oluştu: " + method.getName(), e);
                return annotation.onError();
            }

            return null;
        }
    }
}