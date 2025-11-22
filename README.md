# BenthPapiManager

**Annotation tabanlı, modern ve tam özellikli bir PlaceholderAPI yönetim kütüphanesi.**

BenthPapiManager, PlaceholderAPI için yeni placeholder'lar oluşturma sürecini kökten değiştirir. Karmaşık `PlaceholderExpansion` sınıfları yazmak, metotları manuel olarak yönlendirmek ve bağımlılıkları yönetmek yerine, BenthPapiManager size temiz, okunabilir ve "sadece çalışan" bir yapı sunar.

![Java 8+](https://img.shields.io/badge/Java-8%2B-blue?style=for-the-badge&logo=java)![Spigot API](https://img.shields.io/badge/API-Spigot%20Uyumlu-orange?style=for-the-badge)![Maven](https://img.shields.io/badge/Maven-v1.0.3--b3-brightgreen?style=for-the-badge&logo=apache-maven)![Uyumluluk](https://img.shields.io/badge/Uyumlu-Spigot%2C_Paper_%26_Other_Forks_Of_Spigot-brightgreen?style=for-the-badge)

---

## 🤔 Neden BenthPapiManager?

Geleneksel PAPI genişlemesi oluşturmak, çok fazla tekrar eden kod (boilerplate), karmaşık yaşam döngüsü yönetimi (`register`/`unregister`) ve eklentinin diğer kısımlarına erişimde zorluklar anlamına gelir. BenthPapiManager bu sorunları çözmek için tasarlanmıştır.

## ✨ Özellikler

- **🅾️ Sıfır Bağımlılık:** `Reflections` gibi harici kütüphanelere ihtiyaç duymaz. Sadece PAPI ve Spigot API'si yeterlidir. Bu, çakışma riskini ortadan kaldırır ve JAR dosyanızı küçük tutar.
- **🚀 Akıcı Builder API'si:** Kütüphaneyi `BenthPAPIManager.create(this).with(...).register(...)` gibi zincirleme ve okunabilir bir yapıyla kurun.
- **💉 Gelişmiş Dependency Injection:** Sadece ana plugin sınıfınızı değil, `DatabaseManager` veya `UserManager` gibi **herhangi bir yönetici sınıfınızı** `@Inject` ile placeholder sınıflarınıza zahmetsizce enjekte edin.
- **⚡ Asenkron Placeholder'lar:** Veritabanı sorguları veya web istekleri gibi yavaş işlemler için metotlarınızı `async = true` olarak işaretleyin ve sunucunuzu lag'dan koruyun.
- **🔗 Tam Parametre & Relational Desteği:** Hem `%identifier_param%` hem de `%rel_identifier_target%` formatındaki placeholder'ları tam olarak destekler.
- **⚙️ Esnek Hata Yönetimi:** Builder üzerinden tüm placeholder'lar için global bir varsayılan hata metni belirleyin veya `@...Identifier(onError = "...")` ile yerel olarak ezin.
- **🐞 Kolay Hata Ayıklama:** `.withDebugMode()` ile kütüphaneyi debug modunda başlatın ve konsolda placeholder istekleri ve detaylı hata raporları hakkında ayrıntılı bilgi alın.
- **🔄 Otomatik Yaşam Döngüsü:** Kütüphane, kaydedilen tüm placeholder'ları `onDisable`'da `unregisterAll()` ile otomatik olarak temizlemenizi sağlayarak "reload" güvenliği sunar.

---

## ⚙️ Uyumluluk

- **Geniş Sunucu Desteği:** Kütüphane, Spigot API'si temel alınarak geliştirilmiştir. Bu sayede Spigot ve Paper, Purpur gibi tüm alt sunucu türlerinde (forks) sorunsuz bir şekilde çalışır.

---

## 🚀 Kurulum

### Adım 1: Maven Bağımlılığını Ekleme

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
        <groupId>com.github.bentahsin</groupId>
        <artifactId>BenthPAPIManager</artifactId>
        <version>VERSION</version> <!-- GitHub'daki son sürüm etiketini kullanın -->
        <scope>compile</scope>
    </dependency>
</dependencies>
```

### Adım 2: Shading (Gölgeleme) - ÇOK ÖNEMLİ

Kütüphaneyi kendi eklentinizin JAR dosyasına dahil etmek ve diğer eklentilerle çakışmasını önlemek için `maven-shade-plugin`'i kullanmalısınız.

`pom.xml` dosyanızın `<build><plugins>` bölümüne ekleyin:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <relocations>
                    <!-- Kütüphanenin paket yolunu kendi paket yolunuzun altına taşıyın -->
                    <relocation>
                        <pattern>com.bentahsin.benthpapimanager</pattern>
                        <shadedPattern>com.senineklentin.libs.benthpapimanager</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```
*Not: `com.senineklentin` kısmını kendi paket adınızla değiştirmeyi unutmayın!*

---

## 🛠️ Kullanım

### 1. Kütüphaneyi Başlatma

Plugin'inizin `onEnable()` metodunda, BenthPapiManager'ın akıcı builder'ını kullanarak her şeyi yapılandırın.

```java
import com.bentahsin.benthpapimanager.BenthPAPIManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin {

    private BenthPAPIManager papiManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // PlaceholderAPI'nin yüklü olduğundan emin olun
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("PlaceholderAPI bulunamadı! Plugin devre dışı bırakılıyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Diğer yönetici sınıflarınızı başlatın
        this.databaseManager = new DatabaseManager();

        // BenthPAPIManager'ı yapılandırın ve başlatın
        this.papiManager = BenthPAPIManager.create(this)
                // Diğer sınıflarınızın enjekte edilmesini sağlayın
                .withInjectable(DatabaseManager.class, this.databaseManager)
                // Tüm placeholder'lar için genel bir hata metni belirleyin
                .withDefaultErrorText("§c-")
                // Hata ayıklamayı kolaylaştırmak için debug modunu açın
                .withDebugMode()
                // Placeholder sınıflarınızı kaydedin
                .register(
                    GenelPlaceholders.class,
                    OyuncuPlaceholders.class,
                    IliskiselPlaceholders.class
                );
    }
    
    @Override
    public void onDisable() {
        // Sunucu kapanırken veya reload atılırken placeholder'ları temizleyin
        if (this.papiManager != null) {
            this.papiManager.unregisterAll();
        }
    }
}
```

### 2. Temel Placeholder Sınıfı Oluşturma

```java
package com.myplugin.placeholders;

import com.bentahsin.benthpapimanager.annotations.*;
import org.bukkit.entity.Player;

@Placeholder(identifier = "myplugin", author = "YourName", version = "1.0")
public class OyuncuPlaceholders {

    @Inject
    private DatabaseManager db; // `withInjectable` ile kaydettiğiniz nesne buraya gelecek

    // Basit placeholder -> %myplugin_ping%
    @PlaceholderIdentifier(identifier = "ping")
    public int onPlayerPing(Player player) {
        // Kütüphane 'int' gibi tipleri otomatik olarak String'e çevirir.
        return player.getPing();
    }

    // Parametreli placeholder -> %myplugin_hasperm_essentials.fly%
    @PlaceholderIdentifier(identifier = "hasperm", onError = "§cHayır")
    public String onHasPerm(Player player, String permission) {
        return player.hasPermission(permission) ? "§aEvet" : "§cHayır";
    }
}
```

### 3. İlişkisel (Relational) Placeholder'lar

İlişkisel placeholder'lar için ana `identifier`'ın **`rel_`** ile başlaması gerekir.

```java
package com.myplugin.placeholders;

import com.bentahsin.benthpapimanager.annotations.*;
import org.bukkit.entity.Player;

// Identifier 'rel_' ile başlamalı!
@Placeholder(identifier = "rel_myplugin", author = "YourName", version = "1.0")
public class IliskiselPlaceholders {

    // İlişkisel placeholder -> %rel_myplugin_distance_Bentahsin%
    @RelationalPlaceholder(identifier = "distance", onError = "Farklı Dünya")
    public String onDistance(Player viewer, Player target) {
        if (!viewer.getWorld().equals(target.getWorld())) {
            return "Farklı Dünya";
        }
        int distance = (int) viewer.getLocation().distance(target.getLocation());
        return distance + "m";
    }
}
```

### 4. Asenkron Placeholder'lar (İleri Düzey)

Sunucuyu yormamak için veritabanı gibi yavaş işlemler `async = true` ile işaretlenmelidir. En iyi pratik, veriyi asenkron olarak bir önbelleğe (cache) yazan ve önbellekten senkron olarak okuyan iki ayrı placeholder oluşturmaktır.

```java
@Placeholder(identifier = "myplugin", author = "YourName", version = "1.0")
public class AsenkronPlaceholders {

    @Inject
    private DatabaseManager db;
    private final Map<UUID, Integer> killCache = new ConcurrentHashMap<>();

    // ADIM 1: Veriyi önbelleğe yazan ASENKRON placeholder.
    // Bu placeholder doğrudan kullanılmaz, sadece PAPI.setPlaceholders ile tetiklenir.
    @PlaceholderIdentifier(identifier = "db_update_kills", async = true)
    public String onUpdateKills(Player player) {
        // Bu metot arka planda çalışır, sunucuyu yormaz.
        int kills = db.getPlayerKills(player.getUniqueId());
        killCache.put(player.getUniqueId(), kills);
        return null; // Asenkron metotların dönüş değeri kullanılmaz.
    }
    
    // ADIM 2: Veriyi önbellekten okuyan SENKRON placeholder.
    // Skorbordda veya chat'te kullanılacak olan budur: %myplugin_kills%
    @PlaceholderIdentifier(identifier = "kills", onLoading = "§7...")
    public int onGetKills(Player player) {
        // Oyuncu verisi önbellekte yoksa 'db_update_kills' tetiklenir ve 'onLoading' metni döner.
        if (!killCache.containsKey(player.getUniqueId())) {
             PlaceholderAPI.setPlaceholders(player, "%myplugin_db_update_kills%");
             return -1; // veya hata kodu/metni
        }
        return killCache.get(player.getUniqueId());
    }
}
```

---
## 🤝 Katkıda Bulunma

Katkılarınız projenin gelişimi için çok değerlidir. Lütfen bir "pull request" açmaktan veya bir "issue" oluşturmaktan çekinmeyin.