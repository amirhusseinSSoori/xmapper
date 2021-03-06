package com.sample.xmapper.mapper.kmapper


import com.sample.xmapper.shared.ValueParameter
import java.lang.IllegalArgumentException
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaGetter

@Suppress("UNCHECKED_CAST")
internal sealed class BoundParameterForMap<S> {
    abstract val name: String
    protected abstract val propertyGetter: Method

    abstract fun map(src: S): Any?

    internal class Plain<S : Any>(
        override val name: String,
        override val propertyGetter: Method
    ) : BoundParameterForMap<S>() {
        override fun map(src: S): Any? = propertyGetter.invoke(src)
    }

    internal class UseConverter<S : Any>(
        override val name: String,
        override val propertyGetter: Method,
        private val converter: KFunction<*>
    ) : BoundParameterForMap<S>() {
        override fun map(src: S): Any? = propertyGetter.invoke(src)?.let { converter.call(it) }
    }

    internal class UseKMapper<S : Any>(
        override val name: String,
        override val propertyGetter: Method,
        private val kMapper: KMapper<*>
    ) : BoundParameterForMap<S>() {
        // 1引数で呼び出すとMap/Pairが適切に処理されないため、2引数目にダミーを噛ませている
        override fun map(src: S): Any? = propertyGetter.invoke(src)?.let { kMapper.map(it, PARAMETER_DUMMY) }
    }

    internal class UseBoundKMapper<S : Any, T : Any>(
        override val name: String,
        override val propertyGetter: Method,
        private val boundKMapper: BoundKMapper<T, *>
    ) : BoundParameterForMap<S>() {
        override fun map(src: S): Any? = (propertyGetter.invoke(src))?.let { boundKMapper.map(it as T) }
    }

    internal class ToEnum<S : Any>(
        override val name: String,
        override val propertyGetter: Method,
        private val paramClazz: Class<*>
    ) : BoundParameterForMap<S>() {
        override fun map(src: S): Any? =
            EnumMapper.getEnum(paramClazz as Class<Any?>, propertyGetter.invoke(src) as String?)
    }

    internal class ToString<S : Any>(
        override val name: String,
        override val propertyGetter: Method
    ) : BoundParameterForMap<S>() {
        override fun map(src: S): String? = propertyGetter.invoke(src)?.toString()
    }

    companion object {
        fun <S : Any> newInstance(
            param: ValueParameter<*>,
            property: KProperty1<S, *>,
            parameterNameConverter: ((String) -> String)?
        ): BoundParameterForMap<S> {
            // ゲッターが無いならエラー
            val propertyGetter = property.javaGetter
                ?: throw IllegalArgumentException("${property.name} does not have getter.")
            propertyGetter.isAccessible = true

            val paramClazz = param.requiredClazz
            val propertyClazz = property.returnType.classifier as KClass<*>

            // コンバータが取れた場合
            (param.getConverters() + paramClazz.getConverters())
                .filter { (key, _) -> propertyClazz.isSubclassOf(key) }
                .let {
                    if (1 < it.size) throw IllegalArgumentException("${param.name} has multiple converter. $it")

                    it.singleOrNull()?.let { (_, converter) ->
                        return UseConverter(param.name, propertyGetter, converter)
                    }
                }

            if (paramClazz.isSubclassOf(propertyClazz)) {
                return Plain(param.name, propertyGetter)
            }

            val javaClazz = paramClazz.java

            return when {
                javaClazz.isEnum && propertyClazz == String::class -> ToEnum(param.name, propertyGetter, javaClazz)
                paramClazz == String::class -> ToString(param.name, propertyGetter)
                // SrcがMapやPairならKMapperを使わないとマップできない
                propertyClazz.isSubclassOf(Map::class) || propertyClazz.isSubclassOf(Pair::class) -> UseKMapper(
                    param.name,
                    propertyGetter,
                    KMapper(paramClazz, parameterNameConverter)
                )
                // 何にも当てはまらなければBoundKMapperでマップを試みる
                else -> UseBoundKMapper(
                    param.name,
                    propertyGetter,
                    BoundKMapper(paramClazz, propertyClazz, parameterNameConverter)
                )
            }
        }
    }
}
