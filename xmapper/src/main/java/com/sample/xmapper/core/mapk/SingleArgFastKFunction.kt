package com.sample.xmapper.core.mapk


import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod
import java.lang.reflect.Constructor as JavaConstructor

sealed class SingleArgFastKFunction<T> {
    abstract val valueParameter: KParameter
    abstract fun call(arg: Any?): T

    internal class Constructor<T>(
        override val valueParameter: KParameter,
        private val constructor: JavaConstructor<T>
    ) : SingleArgFastKFunction<T>() {
        override fun call(arg: Any?): T = constructor.newInstance(arg)
    }

    internal class Function<T>(
        override val valueParameter: KParameter,
        private val function: KFunction<T>
    ) : SingleArgFastKFunction<T>() {
        override fun call(arg: Any?): T = function.call(arg)
    }

    internal class TopLevelFunction<T>(
        override val valueParameter: KParameter,
        private val method: Method
    ) : SingleArgFastKFunction<T>() {
        @Suppress("UNCHECKED_CAST")
        override fun call(arg: Any?): T = method.invoke(null, arg) as T
    }

    internal class TopLevelExtensionFunction<T>(
        override val valueParameter: KParameter,
        private val method: Method,
        private val extensionReceiver: Any
    ) : SingleArgFastKFunction<T>() {
        @Suppress("UNCHECKED_CAST")
        override fun call(arg: Any?): T = method.invoke(null, extensionReceiver, arg) as T
    }

    internal class InstanceFunction<T>(
        override val valueParameter: KParameter,
        private val method: Method,
        private val instance: Any
    ) : SingleArgFastKFunction<T>() {
        @Suppress("UNCHECKED_CAST")
        override fun call(arg: Any?): T = method.invoke(instance, arg) as T
    }

    companion object {
        @TestOnly
        internal fun List<KParameter>.checkParameters() = also {
            val requireInstanceParameter = !isEmpty() && this[0].kind != KParameter.Kind.VALUE

            if (isEmpty() || (requireInstanceParameter && size == 1))
                throw IllegalArgumentException("This function is not require arguments.")

            if (!(this.size == 1 || (this.size == 2 && requireInstanceParameter)))
                throw IllegalArgumentException("This function is require multiple arguments.")

            if (this.size == 2 && this[1].kind != KParameter.Kind.VALUE)
                throw IllegalArgumentException("This function is require multiple instances.")
        }


        @TestOnly
        internal fun <T> topLevelFunctionOf(
            function: KFunction<T>,
            instance: Any?,
            parameters: List<KParameter>,
            method: Method
        ): SingleArgFastKFunction<T> = when {
            // KParameter.Kind.EXTENSION_RECEIVER??????????????????????????????????????????
            parameters[0].kind == KParameter.Kind.EXTENSION_RECEIVER ->
                // ???????????????????????????instance???receiver?????????????????????????????????
                instance.instanceOrThrow(KParameter.Kind.EXTENSION_RECEIVER).let {
                    checkInstanceClass(parameters[0].clazz, it::class)

                    TopLevelExtensionFunction(parameters[1], method, it)
                }
            // javaMethod??????????????????????????????KFunction?????????????????????????????????????????????????????????
            // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            method.parameters.size != parameters.size ->
                instance
                    ?.let {
                        checkInstanceClass(method.parameters[0].type.kotlin, it::class)

                        TopLevelExtensionFunction(parameters[0], method, instance)
                    }
                    ?: Function(parameters[0], function)
            // ????????????????????????
            else -> TopLevelFunction(parameters[0], method)
        }

        @TestOnly
        internal fun <T> instanceFunctionOf(
            function: KFunction<T>,
            inputtedInstance: Any?,
            parameters: List<KParameter>,
            method: Method
        ): SingleArgFastKFunction<T> {
            val instance = inputtedInstance ?: method.declaringObject

            return when {
                parameters[0].kind == KParameter.Kind.INSTANCE ->
                    instance.instanceOrThrow(KParameter.Kind.INSTANCE).let {
                        checkInstanceClass(method.declaringClass.kotlin, it::class)

                        InstanceFunction(parameters[1], method, it)
                    }
                instance != null -> {
                    checkInstanceClass(method.declaringClass.kotlin, instance::class)

                    InstanceFunction(parameters[0], method, instance)
                }
                else -> Function(parameters[0], function)
            }
        }

        fun <T> of(function: KFunction<T>, instance: Any? = null): SingleArgFastKFunction<T> {
            // ?????????????????????????????????????????????????????????????????????????????????????????????
            val parameters: List<KParameter> = function.parameters.checkParameters()

            // ???????????????????????????????????????????????????????????????????????????????????????
            function.isAccessible = true

            val constructor = function.javaConstructor

            return if (constructor != null) {
                Constructor(parameters[0], constructor)
            } else {
                val method = function.javaMethod!!

                // Method???static????????????function???????????????????????????
                if (Modifier.isStatic(method.modifiers)) {
                    topLevelFunctionOf(function, instance, parameters, method)
                } else {
                    instanceFunctionOf(function, instance, parameters, method)
                }
            }
        }
    }
}