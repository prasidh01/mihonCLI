package mihon.cli.loader

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.File
import java.util.jar.JarFile

class AsmBytecodeFixerTest {

    @Test
    fun testFixAndInstantiate() {
        val rawJar = File("/tmp/sample_raw.jar")
        if (!rawJar.exists()) return

        Injekt.addSingleton(Application.getInstance())
        Injekt.addSingleton(NetworkHelper())

        val parentLoader = this.javaClass.classLoader
        val jarFile = JarFile(rawJar)

        val classBytesMap = mutableMapOf<String, ByteArray>()
        val superMap = mutableMapOf<String, String>()
        val interfacesMap = mutableMapOf<String, List<String>>()
        val hasInitMap = mutableMapOf<String, Boolean>()

        val entries = jarFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.endsWith(".class")) {
                val bytes = jarFile.getInputStream(entry).use { it.readBytes() }
                val cr = ClassReader(bytes)
                val className = cr.className
                classBytesMap[className] = bytes
                val superName = cr.superName ?: "java/lang/Object"
                superMap[className] = superName
                interfacesMap[className] = cr.interfaces.toList()

                var hasInit = false
                cr.accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitMethod(
                        access: Int,
                        name: String?,
                        descriptor: String?,
                        signature: String?,
                        exceptions: Array<out String>?,
                    ): MethodVisitor? {
                        if (name == "<init>") hasInit = true
                        return null
                    }
                }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                hasInitMap[className] = hasInit
            }
        }

        fun getSuper(type: String): String {
            val s = superMap[type]
            if (s != null) return s
            return try {
                val cls = Class.forName(type.replace('/', '.'), false, parentLoader)
                cls.superclass?.name?.replace('.', '/') ?: "java/lang/Object"
            } catch (_: Throwable) {
                "java/lang/Object"
            }
        }

        fun isAssignable(superType: String, subType: String): Boolean {
            if (superType == subType) return true
            if (superType == "java/lang/Object") return true
            var curr = subType
            while (curr != "java/lang/Object") {
                if (curr == superType) return true
                val ifaces = interfacesMap[curr] ?: emptyList()
                if (superType in ifaces) return true
                curr = getSuper(curr)
            }
            return false
        }

        fun findCommonSuperClass(type1: String, type2: String): String {
            if (isAssignable(type1, type2)) return type1
            if (isAssignable(type2, type1)) return type2
            var curr = type1
            while (curr != "java/lang/Object") {
                curr = getSuper(curr)
                if (isAssignable(curr, type2)) return curr
            }
            return "java/lang/Object"
        }

        val classLoader = object : ClassLoader(parentLoader) {
            val classCache = mutableMapOf<String, Class<*>>()

            override fun findClass(name: String): Class<*> {
                val cached = classCache[name]
                if (cached != null) return cached

                val internalName = name.replace('.', '/')
                val rawBytes = classBytesMap[internalName] ?: return super.findClass(name)
                val directSuper = superMap[internalName] ?: "java/lang/Object"

                val processedBytes = try {
                    val cr = ClassReader(rawBytes)
                    val cw = object : ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                        override fun getCommonSuperClass(type1: String, type2: String): String {
                            return findCommonSuperClass(type1, type2)
                        }
                    }

                    val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
                        override fun visitEnd() {
                            // If class had no constructor at all, synthesize default constructor
                            if (hasInitMap[internalName] == false) {
                                val mv = super.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
                                mv.visitCode()
                                mv.visitVarInsn(Opcodes.ALOAD, 0)
                                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, directSuper, "<init>", "()V", false)
                                mv.visitInsn(Opcodes.RETURN)
                                mv.visitMaxs(1, 1)
                                mv.visitEnd()
                            }
                            super.visitEnd()
                        }

                        override fun visitMethod(
                            access: Int,
                            name: String,
                            descriptor: String,
                            signature: String?,
                            exceptions: Array<out String>?,
                        ): MethodVisitor {
                            val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                            return object : MethodVisitor(Opcodes.ASM9, mv) {
                                private var state = 0

                                override fun visitTypeInsn(opcode: Int, type: String?) {
                                    if (opcode == Opcodes.NEW && type == "java/lang/Object") {
                                        state = 1
                                    } else {
                                        flush()
                                        super.visitTypeInsn(opcode, type)
                                    }
                                }

                                override fun visitInsn(opcode: Int) {
                                    if (state == 1 && opcode == Opcodes.DUP) {
                                        state = 2
                                    } else {
                                        flush()
                                        super.visitInsn(opcode)
                                    }
                                }

                                override fun visitMethodInsn(
                                    opcode: Int,
                                    owner: String?,
                                    name: String?,
                                    descriptor: String?,
                                    isInterface: Boolean,
                                ) {
                                    if (state == 2 && opcode == Opcodes.INVOKESPECIAL && owner == "java/lang/Object" && name == "<init>" && descriptor == "()V") {
                                        state = 0
                                        super.visitInsn(Opcodes.ACONST_NULL)
                                        return
                                    }
                                    flush()

                                    // Fix superclass constructor call:
                                    // In <init>, if invokespecial is called on an ancestor constructor that isn't direct super, redirect to directSuper
                                    var fixedOwner = owner
                                    if (name == "<init>" && opcode == Opcodes.INVOKESPECIAL && owner != internalName) {
                                        if (owner != null && isAssignable(owner, directSuper)) {
                                            fixedOwner = directSuper
                                        }
                                    }

                                    super.visitMethodInsn(opcode, fixedOwner, name, descriptor, isInterface)
                                }

                                private fun flush() {
                                    if (state >= 1) {
                                        super.visitTypeInsn(Opcodes.NEW, "java/lang/Object")
                                    }
                                    if (state >= 2) {
                                        super.visitInsn(Opcodes.DUP)
                                    }
                                    state = 0
                                }
                            }
                        }
                    }

                    cr.accept(cv, ClassReader.SKIP_FRAMES)
                    cw.toByteArray()
                } catch (e: Exception) {
                    println("Warn: frame computation failed for $name: ${e.message}")
                    rawBytes
                }

                val clazz = defineClass(name, processedBytes, 0, processedBytes.size)
                classCache[name] = clazz
                return clazz
            }
        }

        val targetClass = "keiyoushi.source.Generated"
        println("Loading class $targetClass...")
        val clazz = classLoader.loadClass(targetClass)
        println("Class loaded: ${clazz.name}")

        val constructor = clazz.getDeclaredConstructor()
        constructor.isAccessible = true
        val instance = constructor.newInstance()
        println("Instance created: $instance (type: ${instance.javaClass})")

        val sources: List<Source> = when (instance) {
            is Source -> listOf(instance)
            is SourceFactory -> instance.createSources()
            else -> error("Unknown type: ${instance.javaClass}")
        }

        println("Loaded ${sources.size} sources:")
        for (s in sources) {
            println(" - ID: ${s.id}, Name: ${s.name}, Lang: ${s.lang}, SupportsLatest: ${s.supportsLatest}")
            assertTrue(s is CatalogueSource, "Source should implement CatalogueSource")
        }
        assertTrue(sources.isNotEmpty())
    }
}
