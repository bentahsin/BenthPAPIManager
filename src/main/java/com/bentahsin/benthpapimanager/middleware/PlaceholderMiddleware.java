package com.bentahsin.benthpapimanager.middleware;

/**
 * Placeholder'dan dönen ham veriyi işlemek için bir arayüz.
 * Bu arayüzü uygulayan sınıflar, @Middleware anotasyonu ile kullanılabilir.
 */
@FunctionalInterface
public interface PlaceholderMiddleware {
    /**
     * Placeholder metodundan gelen ham sonucu işler ve formatlanmış bir String döndürür.
     * @param input Placeholder metodunun orijinal dönüş değeri (örn: Integer, Double, String).
     * @return Formatlanmış veya işlenmiş sonuç.
     */
    String process(Object input);
}