package com.bentahsin.benthpapimanager.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Bir placeholder metodunun sonucunun ne kadar süreyle önbelleğe alınacağını belirtir.
 * Sık değişmeyen veriler için performansı artırır.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cache {

    /**
     * Önbelleğin geçerlilik süresi.
     * @return Süre değeri.
     */
    long duration();

    /**
     * Sürenin zaman birimi (saniye, dakika vb.).
     * Varsayılan olarak saniyedir.
     * @return Zaman birimi.
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}