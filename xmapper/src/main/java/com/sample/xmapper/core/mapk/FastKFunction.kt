package com.sample.xmapper.core.mapk




import com.sample.xmapper.core.ForConstructor
import com.sample.xmapper.core.ForKFunction
import com.sample.xmapper.core.ForMethod
import com.sample.xmapper.core.mapk.argumentbucket.ArgumentBucket
import com.sample.xmapper.core.mapk.argumentbucket.BucketGenerator
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod
import java.lang.reflect.Constructor as JavaConstructor

sealed class FastKFunction<T> {
    abstract val valueParameters: List<KParameter>
    internal abstract val bucketGenerator: BucketGenerator
    fun generateBucket(): ArgumentBucket = bucketGenerator.generateBucket()

    abstract fun callBy(bucket: ArgumentBucket): T
    abstract fun callByCollection(args: Collection<Any?>): T
    abstract fun call(vararg args: Any?): T

    internal class Constructor<T>(
        private val function: KFunction<T>,
        constructor: JavaConstructor<T>,
        override val valueParameters: List<KParameter>
    ) : FastKFunction<T>() {
        private val spreadWrapper = ForConstructor(constructor)
        override val bucketGenerator = BucketGenerator(valueParameters, null)

        override fun callBy(bucket: ArgumentBucket): T = if (bucket.isFullInitialized()) {
            spreadWrapper.call(bucket.getValueArray())
        } else {
            function.callBy(bucket)
        }

        override fun callByCollection(args: Collection<Any?>): T = spreadWrapper.call(args.toTypedArray())

        override fun call(vararg args: Any?): T = spreadWrapper.call(args)
    }

    internal class Function<T>(
        private val function: KFunction<T>,
        override val valueParameters: List<KParameter>
    ) : FastKFunction<T>() {
        private val spreadWrapper = ForKFunction(function)
        override val bucketGenerator = BucketGenerator(valueParameters, null)

        override fun callBy(bucket: ArgumentBucket): T = if (bucket.isFullInitialized()) {
            spreadWrapper.call(bucket.getValueArray())
        } else {
            function.callBy(bucket)
        }

        override fun callByCollection(args: Collection<Any?>): T = spreadWrapper.call(args.toTypedArray())

        override fun call(vararg args: Any?): T = spreadWrapper.call(args)
    }

    internal class TopLevelFunction<T>(
        private val function: KFunction<T>,
        method: Method,
        override val valueParameters: List<KParameter>
    ) : FastKFunction<T>() {
        private val spreadWrapper = ForMethod(method, null)
        override val bucketGenerator = BucketGenerator(valueParameters, null)

        @Suppress("UNCHECKED_CAST")
        override fun callBy(bucket: ArgumentBucket): T = if (bucket.isFullInitialized()) {
            spreadWrapper.call(bucket.getValueArray()) as T
        } else {
            function.callBy(bucket)
        }

        @Suppress("UNCHECKED_CAST")
        override fun callByCollection(args: Collection<Any?>): T = spreadWrapper.call(args.toTypedArray()) as T

        @Suppress("UNCHECKED_CAST")
        override fun call(vararg args: Any?): T = spreadWrapper.call(args) as T
    }

    // NOTE: ????????????????????????????????????????????????????????????????????????????????????????????????Bucket????????????????????????????????????????????????????????????????????????
    internal class TopLevelExtensionFunction<T>(
        private val function: KFunction<T>,
        private val method: Method,
        private val extensionReceiver: Any,
        override val bucketGenerator: BucketGenerator,
        override val valueParameters: List<KParameter>
    ) : FastKFunction<T>() {
        @Suppress("UNCHECKED_CAST")
        override fun callBy(bucket: ArgumentBucket): T = if (bucket.isFullInitialized()) {
            method.invoke(null, extensionReceiver, *bucket.getValueArray()) as T
        } else {
            function.callBy(bucket)
        }

        @Suppress("UNCHECKED_CAST")
        override fun callByCollection(args: Collection<Any?>): T =
            method.invoke(null, extensionReceiver, *args.toTypedArray()) as T

        @Suppress("UNCHECKED_CAST")
        override fun call(vararg args: Any?): T = method.invoke(null, extensionReceiver, *args) as T
    }

    internal class InstanceFunction<T>(
        private val function: KFunction<T>,
        method: Method,
        instance: Any,
        override val bucketGenerator: BucketGenerator,
        override val valueParameters: List<KParameter>
    ) : FastKFunction<T>() {
        private val spreadWrapper = ForMethod(method, instance)

        @Suppress("UNCHECKED_CAST")
        override fun callBy(bucket: ArgumentBucket): T = if (bucket.isFullInitialized()) {
            spreadWrapper.call(bucket.getValueArray()) as T
        } else {
            function.callBy(bucket)
        }

        @Suppress("UNCHECKED_CAST")
        override fun callByCollection(args: Collection<Any?>): T = spreadWrapper.call(args.toTypedArray()) as T

        @Suppress("UNCHECKED_CAST")
        override fun call(vararg args: Any?): T = spreadWrapper.call(args) as T
    }

    companion object {
        @TestOnly
        internal fun List<KParameter>.checkParameters() = also {
            if (isEmpty() || (this[0].kind != KParameter.Kind.VALUE && size == 1))
                throw IllegalArgumentException("This function is not require arguments.")

            if (2 <= size && this[1].kind != KParameter.Kind.VALUE)
                throw IllegalArgumentException("This function is require multiple instances.")
        }


        @TestOnly
        internal fun <T> topLevelFunctionOf(
            function: KFunction<T>,
            instance: Any?,
            parameters: List<KParameter>,
            method: Method
        ): FastKFunction<T> = when {
            // KParameter.Kind.EXTENSION_RECEIVER??????????????????????????????????????????
            parameters[0].kind == KParameter.Kind.EXTENSION_RECEIVER ->
                // ???????????????????????????instance???receiver?????????????????????????????????
                instance.instanceOrThrow(KParameter.Kind.EXTENSION_RECEIVER).let {
                    checkInstanceClass(parameters[0].clazz, it::class)

                    val generator = BucketGenerator(parameters, it)
                    val valueParameters = parameters.subList(1, parameters.size)

                    TopLevelExtensionFunction(function, method, it, generator, valueParameters)
                }
            // javaMethod??????????????????????????????KFunction?????????????????????????????????????????????????????????
            // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            method.parameters.size != parameters.size ->
                instance
                    ?.let {
                        checkInstanceClass(method.parameters[0].type.kotlin, it::class)

                        // KFunction???????????????????????????????????????????????????????????????????????????????????????????????????????????????
                        TopLevelExtensionFunction(function, method, it, BucketGenerator(parameters, null), parameters)
                    } ?: Function(function, parameters)
            // ????????????????????????
            else -> TopLevelFunction(function, method, parameters)
        }

        @TestOnly
        internal fun <T> instanceFunctionOf(
            function: KFunction<T>,
            inputtedInstance: Any?,
            parameters: List<KParameter>,
            method: Method
        ): FastKFunction<T> {
            val instance = inputtedInstance ?: method.declaringObject

            return if (parameters[0].kind == KParameter.Kind.INSTANCE) {
                instance.instanceOrThrow(KParameter.Kind.INSTANCE).let { nonNullInstance ->
                    checkInstanceClass(parameters[0].clazz, nonNullInstance::class)

                    val generator = BucketGenerator(parameters, instance)
                    val valueParameters = parameters.subList(1, parameters.size)

                    InstanceFunction(function, method, nonNullInstance, generator, valueParameters)
                }
            } else {
                instance
                    ?.let {
                        checkInstanceClass(method.declaringClass.kotlin, it::class)

                        InstanceFunction(function, method, it, BucketGenerator(parameters, null), parameters)
                    }
                    ?: Function(function, parameters)
            }
        }


        fun <T> of(function: KFunction<T>, instance: Any? = null): FastKFunction<T> {
            // ?????????????????????????????????????????????????????????????????????????????????????????????
            val parameters: List<KParameter> = function.parameters.checkParameters()

            // ???????????????????????????????????????????????????????????????????????????????????????
            function.isAccessible = true

            val constructor = function.javaConstructor

            return if (constructor != null) {
                Constructor(function, constructor, parameters)
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