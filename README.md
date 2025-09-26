# BenthPapiManager

**Annotation tabanlı, yüksek performanslı ve geliştirici dostu bir PlaceholderAPI kütüphanesi.**

BenthPapiManager yeni placeholder'lar eklemeyi son derece basit ve düzenli hale getirir. Sadece annotation'ları kullanarak sınıflarınızı ve metotlarınızı işaretleyin, gerisini kütüphane halletsin.

![Java 8+](https://img.shields.io/badge/Java-8%2B-blue?style=for-the-badge&logo=java)
![Spigot API](https://img.shields.io/badge/API-Spigot-orange?style=for-the-badge)
![Maven](https://img.shields.io/badge/Maven-v1.0.0-brightgreen?style=for-the-badge&logo=apache-maven)

---

## ✨ Özellikler

- **Annotation Tabanlı:** Karmaşık `PlaceholderExpansion` sınıfları yazmak yerine basit annotation'lar kullanın.
- **Yüksek Performans:** Placeholder metotları başlangıçta ön belleğe alınır, bu sayede istekler anında işlenir.
- **Dependency Injection:** `@Inject` ile ana plugin sınıfınızı placeholder sınıflarınıza kolayca enjekte edin.
- **Parametre Desteği:** `%identifier_params%` formatındaki placeholder'ları kolayca oluşturun.
- **Relational Placeholder Desteği:** `%rel_...%` placeholder'ları için destek sunar.
- **Zarif Hata Yönetimi:** Hata durumunda konsolu kirletmek yerine özelleştirilebilir varsayılan değerler döndürün.
- **"Shaded" Bağımlılıklar:** Harici kütüphaneler (Reflections) JAR içine gömülüdür, projenizde çakışma yaratmaz.

---

## 🚀 Kurulum

### Maven

`pom.xml` dosyanıza aşağıdaki depoyu ve bağımlılığı ekleyin:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.bentahsin</groupId>
        <artifactId>benth-papi-manager</artifactId>
        <version>v1.0.0</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

*(Not: Henüz JitPack'e yüklenmedi. Bu adımlar GitHub reposu oluşturulduktan sonra geçerli olacaktır.)*

---

## 🛠️ Kullanım

### 1. Kütüphaneyi Başlatma

Plugin'inizin `onEnable()` metodunda, BenthPapiManager'ı başlatın ve placeholder'larınızın bulunduğu paketi taratın.

```java
import com.bentahsin.benthpapimanager.BenthPAPIManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BenthPAPIManager(this).registerPlaceholders("com.myplugin.placeholders");
        }
    }
}
```

### 2. Placeholder Sınıfı Oluşturma

`@Placeholder` annotation'ı ile bir sınıf oluşturun ve metotlarınızı ilgili annotation'lar ile işaretleyin.

```java
package com.myplugin.placeholders;

import com.bentahsin.benthpapimanager.annotations.Inject;
import com.bentahsin.benthpapimanager.annotations.Placeholder;
import com.bentahsin.benthpapimanager.annotations.PlaceholderIdentifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@Placeholder(identifier = "myplugin", author = "YourName", version = "1.0")
public class ExamplePlaceholders {

    @Inject
    private JavaPlugin plugin; // Ana plugin sınıfı buraya enjekte edilecek

    // Basit placeholder -> %myplugin_welcome_message%
    @PlaceholderIdentifier(identifier = "welcome_message")
    public String onWelcomeMessage() {
        return plugin.getConfig().getString("messages.welcome", "Sunucuya hoş geldin!");
    }

    // Oyuncu bilgisi gerektiren placeholder -> %myplugin_player_ping%
    @PlaceholderIdentifier(identifier = "player_ping", onError = "N/A")
    public String onPlayerPing(Player player) {
        return String.valueOf(player.getPing());
    }

    // Parametreli placeholder -> %myplugin_has_item_diamond%
    @PlaceholderIdentifier(identifier = "has_item", onError = "§cHata§r")
    public String onHasItem(Player player, String material) {
        try {
            return player.getInventory().contains(Material.valueOf(material.toUpperCase())) ? "Evet" : "Hayır";
        } catch (IllegalArgumentException e) {
            return "Geçersiz Eşya";
        }
    }
}
```
---
## 🤝 Katkıda Bulunma

Katkılarınız projenin gelişimi için çok değerlidir. Lütfen bir "pull request" açmaktan çekinmeyin.