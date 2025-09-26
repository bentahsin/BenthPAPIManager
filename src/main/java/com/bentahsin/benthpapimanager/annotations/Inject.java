package com.bentahsin.benthpapimanager.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bir alana, kütüphaneyi başlatan ana plugin sınıfının (JavaPlugin)
 * bir örneğinin enjekte edilmesini sağlar.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Inject {
}