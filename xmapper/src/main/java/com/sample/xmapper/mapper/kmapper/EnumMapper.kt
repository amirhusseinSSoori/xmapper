package com.sample.xmapper.mapper.kmapper

/**
 * Kotlinの型推論バグでクラスからvalueOfが使えないため、ここだけJavaで書いている（型引数もT extends Enumでは書けなかった）
 */
object EnumMapper {
    /**
     * 文字列 -> Enumのマッピング
     * @param clazz Class of Enum
     * @param value StringValue
     * @param <T> enumClass
     * @return Enum.valueOf
    </T> */
    fun <T> getEnum(clazz: Class<T>, value: String?): T? {
        return if (value == null || value.isEmpty()) {
            null
        } else java.lang.Enum.valueOf(
            clazz as Class<out Enum<*>?>,
            value
        ) as T
    }
}