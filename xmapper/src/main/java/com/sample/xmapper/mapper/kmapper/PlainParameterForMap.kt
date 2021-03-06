package com.sample.xmapper.mapper.kmapper


import com.sample.xmapper.shared.ValueParameter
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.isSuperclassOf

internal class PlainParameterForMap<T : Any>(
    param: ValueParameter<T>,
    private val parameterNameConverter: ((String) -> String)?
) {
    private val clazz: KClass<T> = param.requiredClazz

    private val javaClazz: Class<T> by lazy {
        clazz.java
    }
    // リストの長さが小さいと期待されるためこの形で実装しているが、理想的にはmap的なものが使いたい
    @Suppress("UNCHECKED_CAST")
    private val converters: Set<Pair<KClass<*>, KFunction<T>>> by lazy {
        (param.getConverters() as Set<Pair<KClass<*>, KFunction<T>>>) + clazz.getConverters()
    }

    fun <U : Any> mapObject(value: U): Any? {
        val valueClazz: KClass<*> = value::class

        // パラメータに対してvalueが代入可能（同じもしくは親クラス）であればそのまま用いる
        if (clazz.isSuperclassOf(valueClazz)) return value

        val converter: KFunction<*>? = converters.getConverter(valueClazz)

        return when {
            // converterに一致する組み合わせが有れば設定されていればそれを使う
            converter != null -> converter.call(value)
            // 要求された値がenumかつ元が文字列ならenum mapperでマップ
            javaClazz.isEnum && value is String -> EnumMapper.getEnum(javaClazz, value)
            // 要求されているパラメータがStringならtoStringする
            clazz == String::class -> value.toString()
            // それ以外の場合PlainKMapperを作り再帰的なマッピングを試みる
            else -> PlainKMapper(clazz, parameterNameConverter).map(value, PARAMETER_DUMMY)
        }
    }
}
