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

public final class BenthPAPIManager {

    private final JavaPlugin plugin;

    public BenthPAPIManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

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

    private void handleInjections(Class<?> clazz, Object instance) throws IllegalAccessException {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class) && JavaPlugin.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                field.set(instance, this.plugin);
            }
        }
    }

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

    private static final class PlaceholderMethod {
        final Method method;
        final PlaceholderIdentifier annotation;

        PlaceholderMethod(Method method) {
            this.method = method;
            this.annotation = method.getAnnotation(PlaceholderIdentifier.class);
        }
    }

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
            String currentParams = params.toLowerCase();

            while (true) {
                // 1. En uzun/tam eşleşmeyi ara (örn: "toplam_eslesme" veya "has_item_diamond")
                PlaceholderMethod pMethod = standardMethods.get(currentParams);
                if (pMethod != null) {
                    int lastUnderscore = currentParams.length() == params.length() ? -1 : currentParams.length();
                    String methodArgs = lastUnderscore == -1 ? null : params.substring(lastUnderscore + 1);
                    return invokeStandardMethod(pMethod, player, methodArgs);
                }

                // 2. Eşleşme bulunamazsa, sondan bir önceki alt tireye kadar olan kısmı dene
                int lastUnderscore = currentParams.lastIndexOf('_');
                if (lastUnderscore == -1) {
                    break; // Daha fazla bölünecek alt tire kalmadı.
                }
                currentParams = currentParams.substring(0, lastUnderscore);
            }

            return null; // Hiçbir eşleşme bulunamadı
        }

        private String invokeStandardMethod(PlaceholderMethod pMethod, OfflinePlayer player, String args) {
            try {
                Method method = pMethod.method;
                int paramCount = method.getParameterCount();

                // Durum 1: Metot parametreli (örn: onSomething(Player, String))
                if (args != null && paramCount == 2) {
                    if (player == null) return pMethod.annotation.onError();
                    Class<?> argType = method.getParameterTypes()[0];
                    if (argType == Player.class) return player.isOnline() ? (String) method.invoke(placeholderInstance, player.getPlayer(), args) : pMethod.annotation.onError();
                    if (argType == OfflinePlayer.class) return (String) method.invoke(placeholderInstance, player, args);
                }
                // Durum 2: Metot parametresiz
                else if (args == null) {
                    if (paramCount == 0) return (String) method.invoke(placeholderInstance); // onSomething()
                    if (paramCount == 1) { // onSomething(Player)
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
            return null; // Metod imzası çağrıyla eşleşmedi
        }

        private void logError(Method method, Exception e) {
            Throwable cause = e.getCause();
            plugin.getLogger().log(Level.WARNING, "Placeholder metodu çalıştırılırken hata oluştu: " + method.getName(), cause != null ? cause : e);
        }
    }
}