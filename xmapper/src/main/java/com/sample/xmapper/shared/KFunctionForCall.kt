package com.sample.xmapper.shared


import com.sample.xmapper.shared.annotations.KParameterFlatten
import com.sample.xmapper.shared.internal.ParameterNameConverter
import com.sample.xmapper.shared.internal.*
import com.sample.xmapper.shared.internal.ArgumentBinder
import com.sample.xmapper.shared.internal.BucketGenerator
import com.sample.xmapper.shared.internal.FullInitializedFunctionWrapper
import com.sample.xmapper.shared.internal.getAliasOrName
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.isAccessible

class KFunctionForCall<T> internal constructor(

    internal val function: KFunction<T>,
    parameterNameConverter: ParameterNameConverter,
    instance: Any? = null
) {
    constructor(function: KFunction<T>, parameterNameConverter: ((String) -> String)?, instance: Any? = null) : this(
        function,
        ParameterNameConverter.Simple(parameterNameConverter),
        instance
    )


    internal val fullInitializedWrapper: FullInitializedFunctionWrapper<T>


    internal val parameters: List<KParameter> = function.parameters

    // 上は外部への公開用、下はArgumentAdaptor生成用
    val requiredParameters: List<ValueParameter<*>>
    private val requiredParametersMap: Map<String, ValueParameter<*>>

    private val bucketGenerator: BucketGenerator

    init {
        if (parameters.isEmpty() || (instance != null && parameters.size == 1))
            throw IllegalArgumentException("This function is not require arguments.")

        // この関数には確実にアクセスするためアクセシビリティ書き換え
        function.isAccessible = true

        fullInitializedWrapper = FullInitializedFunctionWrapper(function, instance, parameters.size)

        val tempBinders = ArrayList<ArgumentBinder>()
        val tempList = ArrayList<ValueParameter<*>>()
        val tempMap = HashMap<String, ValueParameter<*>>()

        parameters.forEach { param ->
            if (param.kind == KParameter.Kind.VALUE && !param.isUseDefaultArgument()) {
                val binder = param.toArgumentBinder(parameterNameConverter)
                tempBinders.add(binder)

                when (binder) {
                    is ArgumentBinder.Value<*> -> addArgs(binder, tempList, tempMap)
                    is ArgumentBinder.Function -> binder.requiredParameters.forEach {
                        addArgs(it, tempList, tempMap)
                    }
                }
            }
        }

        bucketGenerator = BucketGenerator(parameters, tempBinders, instance)
        requiredParameters = tempList
        requiredParametersMap = tempMap
    }

    private fun addArgs(
        parameter: ValueParameter<*>,
        tempList: ArrayList<ValueParameter<*>>,
        tempMap: MutableMap<String, ValueParameter<*>>
    ) {
        if (tempMap.containsKey(parameter.name))
            throw IllegalArgumentException("The argument name ${parameter.name} is duplicated.")
        tempMap[parameter.name] = parameter
        tempList.add(parameter)
    }

    fun getArgumentAdaptor(): ArgumentAdaptor = ArgumentAdaptor(requiredParametersMap)

    fun call(adaptor: ArgumentAdaptor): T {
        val bucket = bucketGenerator.generate(adaptor)
        return if (bucket.isInitialized) fullInitializedWrapper.call(bucket.valueArray) else function.callBy(bucket)
    }
}

fun <T : Any> KClass<T>.toKConstructor(parameterNameConverter: ((String) -> String)?): KFunctionForCall<T> =
    this.getKConstructor().let { (instance, function) ->
        KFunctionForCall(function, ParameterNameConverter.Simple(parameterNameConverter), instance)
    }

private fun KParameter.toArgumentBinder(parameterNameConverter: ParameterNameConverter): ArgumentBinder {
    val name = getAliasOrName()!!

    return findAnnotation<KParameterFlatten>()?.let { annotation ->
        // 名前の変換処理
        val converter: ParameterNameConverter = if (annotation.fieldNameToPrefix) {
            // 結合が必要な場合は結合機能のインスタンスを持ってきて対応する
            parameterNameConverter.nest(name, annotation.nameJoiner.objectInstance!!)
        } else {
            // プレフィックスを要求しない場合は全てsimpleでマップするように修正
            parameterNameConverter.toSimple()
        }

        getKClass().getKConstructor().let { (instance, function) ->
            ArgumentBinder.Function(KFunctionForCall(function, converter, instance), index, annotations)
        }
    } ?: ArgumentBinder.Value(
        index,
        annotations,
        isOptional,
        parameterNameConverter.convert(name),
        getKClass()
    )
}