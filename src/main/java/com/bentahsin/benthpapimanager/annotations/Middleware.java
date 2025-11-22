package com.bentahsin.benthpapimanager.annotations;

import com.bentahsin.benthpapimanager.middleware.PlaceholderMiddleware;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bir placeholder metodunun sonucunu, PAPI'ye döndürmeden önce
 * işleyecek olan bir veya daha fazla ara katman sınıfını belirtir.
 * Bu, sonuçları formatlamak veya değiştirmek için kullanılır.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Middleware {
    /**
     * Sonucu işleyecek olan PlaceholderMiddleware arayüzünü uygulayan sınıflar.
     * Birden fazla middleware belirtilirse, sırayla uygulanırlar.
     * @return Middleware sınıfları.
     */
    Class<? extends PlaceholderMiddleware>[] value();
}