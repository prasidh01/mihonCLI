package mihon.cli.loader

import android.app.Application
import android.content.Context
import android.content.MockContext
import androidx.preference.PreferenceScreen
import com.googlecode.d2j.dex.Dex2jar
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipFile

data class LoadedExtension(
    val pkgName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val sources: List<Source>,
)

class ExtensionLoader(
    val cacheDir: File = File(System.getProperty("user.home"), ".local/share/mihon/cache"),
) {
    init {
        cacheDir.mkdirs()
        ensureInjektInitialized()
    }

    private fun ensureInjektInitialized() {
        try {
            Injekt.addSingleton<Application>(Application.getInstance())
        } catch (_: Exception) {}
        try {
            Injekt.addSingleton<Context>(Application.getInstance())
        } catch (_: Exception) {}
        try {
            Injekt.addSingleton<NetworkHelper>(NetworkHelper())
        } catch (_: Exception) {}
        try {
            Injekt.addSingleton<kotlinx.serialization.json.Json>(
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                }
            )
        } catch (_: Exception) {}
    }

    fun loadExtension(apkFile: File): LoadedExtension {
        if (!apkFile.exists()) {
            throw IllegalArgumentException("APK file not found: ${apkFile.absolutePath}")
        }

        val zip = ZipFile(apkFile)
        val manifestEntry = zip.getEntry("AndroidManifest.xml")
            ?: throw IllegalArgumentException("Missing AndroidManifest.xml in ${apkFile.name}")
        val manifest = zip.getInputStream(manifestEntry).use { AxmlParser.parse(it) }

        val dexEntry = zip.getEntry("classes.dex")
            ?: throw IllegalArgumentException("Missing classes.dex in ${apkFile.name}")
        val dexBytes = zip.getInputStream(dexEntry).use { it.readBytes() }
        zip.close()

        val rawJar = File(cacheDir, "${apkFile.nameWithoutExtension}.jar")
        if (!rawJar.exists() || rawJar.lastModified() < apkFile.lastModified()) {
            Dex2jar.from(dexBytes).to(rawJar.toPath())
        }

        val parentLoader = javaClass.classLoader
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
                    val classNode = org.objectweb.asm.tree.ClassNode()
                    cr.accept(classNode, ClassReader.SKIP_FRAMES)

                    // If class had no constructor at all, synthesize constructor
                    if (hasInitMap[internalName] == false && (classNode.access and Opcodes.ACC_INTERFACE) == 0) {
                        if (directSuper == "java/lang/Enum") {
                            val initNode = org.objectweb.asm.tree.MethodNode(
                                Opcodes.ACC_PROTECTED,
                                "<init>",
                                "(Ljava/lang/String;I)V",
                                null,
                                null
                            )
                            initNode.instructions.add(org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0))
                            initNode.instructions.add(org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1))
                            initNode.instructions.add(org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 2))
                            initNode.instructions.add(
                                org.objectweb.asm.tree.MethodInsnNode(
                                    Opcodes.INVOKESPECIAL,
                                    "java/lang/Enum",
                                    "<init>",
                                    "(Ljava/lang/String;I)V",
                                    false
                                )
                            )
                            initNode.instructions.add(org.objectweb.asm.tree.InsnNode(Opcodes.RETURN))
                            initNode.maxStack = 3
                            initNode.maxLocals = 3
                            classNode.methods.add(initNode)
                        } else {
                            val initNode = org.objectweb.asm.tree.MethodNode(
                                Opcodes.ACC_PUBLIC,
                                "<init>",
                                "()V",
                                null,
                                null
                            )
                            initNode.instructions.add(org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0))
                            initNode.instructions.add(
                                org.objectweb.asm.tree.MethodInsnNode(
                                    Opcodes.INVOKESPECIAL,
                                    directSuper,
                                    "<init>",
                                    "()V",
                                    false
                                )
                            )
                            initNode.instructions.add(org.objectweb.asm.tree.InsnNode(Opcodes.RETURN))
                            initNode.maxStack = 1
                            initNode.maxLocals = 1
                            classNode.methods.add(initNode)
                        }
                    }

                    for (method in classNode.methods) {
                        val insns = method.instructions
                        var current = insns.first
                        while (current != null) {
                            val next = current.next

                            // Check pattern: NEW java/lang/Enum in <clinit> -> replace with NEW currentEnumClass
                            if (directSuper == "java/lang/Enum") {
                                val tInsn = current as? org.objectweb.asm.tree.TypeInsnNode
                                if (tInsn != null && tInsn.opcode == Opcodes.NEW && tInsn.desc == "java/lang/Enum") {
                                    tInsn.desc = internalName
                                }
                                val mInsn = current as? org.objectweb.asm.tree.MethodInsnNode
                                if (method.name != "<init>" && mInsn != null && mInsn.opcode == Opcodes.INVOKESPECIAL && mInsn.owner == "java/lang/Enum" && mInsn.name == "<init>") {
                                    mInsn.owner = internalName
                                }
                            }

                            // Check pattern: NEW java/lang/Object -> DUP -> INVOKESPECIAL java/lang/Object.<init>
                            if (current.opcode == Opcodes.NEW && (current as? org.objectweb.asm.tree.TypeInsnNode)?.desc == "java/lang/Object") {
                                var n1 = next
                                while (n1 != null && (n1 is org.objectweb.asm.tree.LineNumberNode || n1 is org.objectweb.asm.tree.LabelNode)) {
                                    n1 = n1.next
                                }
                                if (n1?.opcode == Opcodes.DUP) {
                                    var n2 = n1.next
                                    while (n2 != null && (n2 is org.objectweb.asm.tree.LineNumberNode || n2 is org.objectweb.asm.tree.LabelNode)) {
                                        n2 = n2.next
                                    }
                                    val mInsn = n2 as? org.objectweb.asm.tree.MethodInsnNode
                                    if (mInsn != null && mInsn.opcode == Opcodes.INVOKESPECIAL && mInsn.owner == "java/lang/Object" && mInsn.name == "<init>") {
                                        var n3 = n2.next
                                        while (n3 != null && (n3 is org.objectweb.asm.tree.LineNumberNode || n3 is org.objectweb.asm.tree.LabelNode)) {
                                            n3 = n3.next
                                        }

                                        if (n3 is org.objectweb.asm.tree.FieldInsnNode && (n3.opcode == Opcodes.PUTSTATIC || n3.opcode == Opcodes.PUTFIELD)) {
                                            val fieldType = if (n3.desc.startsWith("L") && n3.desc.endsWith(";")) {
                                                n3.desc.substring(1, n3.desc.length - 1)
                                            } else {
                                                null
                                            }
                                            if (fieldType != null && fieldType != "java/lang/Object") {
                                                current.desc = fieldType
                                                mInsn.owner = fieldType
                                            } else {
                                                insns.set(current, org.objectweb.asm.tree.InsnNode(Opcodes.ACONST_NULL))
                                                insns.remove(n1)
                                                insns.remove(n2)
                                            }
                                        } else if (n3 is org.objectweb.asm.tree.VarInsnNode && n3.opcode in Opcodes.ISTORE..Opcodes.ASTORE) {
                                            val varIndex = n3.`var`
                                            // Scan forward to see which type this local variable is used as
                                            var targetType: String? = null
                                            var scan = n3.next
                                            while (scan != null && targetType == null) {
                                                if (scan is org.objectweb.asm.tree.VarInsnNode && scan.`var` == varIndex && scan.opcode in Opcodes.ILOAD..Opcodes.ALOAD) {
                                                    var look = scan.next
                                                    while (look != null && (look is org.objectweb.asm.tree.LineNumberNode || look is org.objectweb.asm.tree.LabelNode)) {
                                                        look = look.next
                                                    }
                                                    while (look != null) {
                                                        if (look is org.objectweb.asm.tree.FieldInsnNode && (look.opcode == Opcodes.PUTFIELD || look.opcode == Opcodes.PUTSTATIC)) {
                                                            targetType = if (look.desc.startsWith("L") && look.desc.endsWith(";")) {
                                                                look.desc.substring(1, look.desc.length - 1)
                                                            } else {
                                                                look.owner
                                                            }
                                                            break
                                                        }
                                                        if (look is org.objectweb.asm.tree.TypeInsnNode && look.opcode == Opcodes.CHECKCAST) {
                                                            targetType = look.desc
                                                            break
                                                        }
                                                        if (look is org.objectweb.asm.tree.LdcInsnNode && look.cst is org.objectweb.asm.Type) {
                                                            targetType = (look.cst as org.objectweb.asm.Type).internalName
                                                            break
                                                        }
                                                        look = look.next
                                                    }
                                                }
                                                scan = scan.next
                                            }

                                            if (targetType != null) {
                                                current.desc = targetType
                                                mInsn.owner = targetType
                                            } else {
                                                insns.set(current, org.objectweb.asm.tree.InsnNode(Opcodes.ACONST_NULL))
                                                insns.remove(n1)
                                                insns.remove(n2)
                                            }
                                        } else {
                                            // Dummy argument passed to method
                                            insns.set(current, org.objectweb.asm.tree.InsnNode(Opcodes.ACONST_NULL))
                                            insns.remove(n1)
                                            insns.remove(n2)
                                        }
                                    }
                                }
                            }

                            // Fix superclass constructor calls
                            val mCall = current as? org.objectweb.asm.tree.MethodInsnNode
                            if (mCall != null) {
                                if (mCall.name == "<init>" && mCall.opcode == Opcodes.INVOKESPECIAL && mCall.owner != internalName) {
                                    if (isAssignable(mCall.owner, directSuper)) {
                                        mCall.owner = directSuper
                                    }
                                } else if (mCall.name.contains("_")) {
                                    val withHyphen = mCall.name.replaceFirst("_", "-")
                                    try {
                                        val ownerCls = Class.forName(mCall.owner.replace('/', '.'), false, parentLoader)
                                        if (ownerCls.methods.any { it.name == withHyphen }) {
                                            mCall.name = withHyphen
                                        }
                                    } catch (_: Exception) {}
                                }
                            }

                            current = next
                        }
                    }

                    val cw = object : ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                        override fun getCommonSuperClass(type1: String, type2: String): String {
                            return findCommonSuperClass(type1, type2)
                        }
                    }
                    classNode.accept(cw)
                    cw.toByteArray()
                } catch (e: Exception) {
                    System.err.println("Warn: frame computation failed for $name: ${e.javaClass.name}: ${e.message}")
                    e.printStackTrace()
                    rawBytes
                }

                val clazz = defineClass(name, processedBytes, 0, processedBytes.size)
                classCache[name] = clazz
                return clazz
            }

            override fun getResourceAsStream(name: String): java.io.InputStream? {
                val cleanName = name.removePrefix("/")
                try {
                    val apkZip = ZipFile(apkFile)
                    val entry = apkZip.getEntry(cleanName) ?: apkZip.getEntry("assets/$cleanName")
                    if (entry != null) {
                        val bytes = apkZip.getInputStream(entry).use { it.readBytes() }
                        apkZip.close()
                        return java.io.ByteArrayInputStream(bytes)
                    }
                    apkZip.close()
                } catch (_: Exception) {}
                return super.getResourceAsStream(name)
            }

            override fun findResource(name: String): java.net.URL? {
                val cleanName = name.removePrefix("/")
                try {
                    val apkZip = ZipFile(apkFile)
                    val entry = apkZip.getEntry(cleanName) ?: apkZip.getEntry("assets/$cleanName")
                    apkZip.close()
                    if (entry != null) {
                        return java.net.URI("jar:file:${apkFile.absolutePath}!/$cleanName").toURL()
                    }
                } catch (_: Exception) {}
                return super.findResource(name)
            }
        }

        val allSources = mutableListOf<Source>()
        for (targetClass in manifest.sourceClasses) {
            try {
                val clazz = classLoader.loadClass(targetClass)
                val constructor = clazz.getDeclaredConstructor()
                constructor.isAccessible = true
                val instance = constructor.newInstance()
                when (instance) {
                    is Source -> allSources.add(instance)
                    is SourceFactory -> allSources.addAll(instance.createSources())
                }

                if (instance is ConfigurableSource) {
                    try {
                        val mockContext = MockContext(manifest.packageName)
                        val screen = PreferenceScreen(mockContext)
                        instance.setupPreferenceScreen(screen)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                System.err.println("Warning: failed to instantiate $targetClass: ${e.message}")
            }
        }

        return LoadedExtension(
            pkgName = manifest.packageName,
            name = manifest.extensionName,
            versionName = manifest.versionName,
            versionCode = manifest.versionCode,
            sources = allSources,
        )
    }
}
