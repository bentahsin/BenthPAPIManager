package com.bentahsin.benthpapimanager.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bir placeholder'ı kullanmak için oyuncunun sahip olması gereken yetkiyi belirtir.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequirePermission {
    /**
     * Gerekli yetki düğümü (örn: "admin.see.ram").
     */
    String value();

    /**
     * Oyuncunun yetkisi yoksa döndürülecek mesaj.
     * Boş bırakılırsa hiçbir şey döndürmez (boş string).
     */
    String onDeny() default "";
}