package com.bentahsin.benthpapimanager.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bir sınıfın PlaceholderAPI için placeholder'lar içerdiğini belirtir.
 * Bu sınıf, BenthPAPIManager tarafından taranacak ve otomatik olarak kaydedilecektir.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Placeholder {

    /**
     * Placeholder'ın ana tanımlayıcısı (örn: "server", "player").
     * @return Placeholder tanımlayıcısı.
     */
    String identifier();

    /**
     * Placeholder'ların yazarını belirtir.
     * @return Yazarın adı.
     */
    String author();

    /**
     * Placeholder'ların versiyonunu belirtir.
     * @return Versiyon numarası.
     */
    String version();
}