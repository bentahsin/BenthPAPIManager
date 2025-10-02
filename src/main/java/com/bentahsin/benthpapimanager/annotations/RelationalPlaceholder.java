package com.bentahsin.benthpapimanager.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bir metodun PlaceholderAPI'nin Relational (İlişkisel) placeholder'ını işlediğini belirtir.
 * Bu metotlar iki Player objesi almalıdır (görüntüleyen ve hedef).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RelationalPlaceholder {
    /**
     * İşlenecek olan ilişkisel placeholder'ın alt tanımlayıcısı (örn: "distance").
     * Örneğin, ana tanımlayıcı "rel_benimpluginim" ise ve bu "distance" ise,
     * sonuç %rel_benimpluginim_distance% olur.
     * @return Alt tanımlayıcı.
     */
    String identifier();

    /**
     * Placeholder işlenirken bir hata (exception) oluşursa döndürülecek varsayılan değer.
     * @return Hata durumunda gösterilecek metin.
     */
    String onError() default "§cError§r";

    /**
     * Bu placeholder'ın asenkron olarak çalıştırılıp çalıştırılmayacağını belirtir.
     * @return true ise asenkron, false ise senkron.
     */
    boolean async() default false;

    /**
     * Asenkron bir placeholder'ın verisi henüz hazır değilken gösterilecek metin.
     * @return Yükleniyor metni.
     */
    String onLoading() default "§eHesaplanıyor...§r";
}