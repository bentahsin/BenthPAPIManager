package com.bentahsin.benthpapimanager.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bir metodun belirli bir placeholder'ı işlediğini belirtir.
 * Metot, String tipinde bir değer döndürmelidir.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PlaceholderIdentifier {

    /**
     * İşlenecek olan placeholder'ın alt tanımlayıcısı (örn: "name", "ping").
     * Örneğin, ana tanımlayıcı "player" ise ve bu "name" ise, sonuç %player_name% olur.
     * @return Alt tanımlayıcı.
     */
    String identifier();

    /**
     * Placeholder işlenirken bir hata (exception) oluşursa döndürülecek varsayılan değer.
     * @return Hata durumunda gösterilecek metin.
     */
    String onError() default "§cError§r";
}