import org.gradle.kotlin.dsl.annotationProcessor
import org.objectweb.asm.*
import java.util.jar.*
import java.io.FileOutputStream
import java.io.File
buildscript {
    repositories {
        // These repositories are only for Gradle plugins, put any other repositories in the repository block further below
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        mavenCentral()
    }
    dependencies {
        classpath("org.spongepowered:mixingradle:0.7-SNAPSHOT")
        classpath("org.ow2.asm:asm:9.6")
    }
}



plugins {
    id("java")
    id("org.leavesmc.leavesweight.userdev") version "2.1.0-SNAPSHOT"
//    id("org.spongepowered.mixin") version "0.8.7"
    id("com.github.johnrengelman.shadow") version "8.1.0"
}

tasks.register<Exec>("compileNativeLib") {
    group = "build"
    description = "Compiles the C++ Native Library using MSVC."
    // 仅在 Windows 主机上执行
    onlyIf {
        System.getProperty("os.name").lowercase().contains("windows")
    }
    val cppSource = layout.projectDirectory.file("acceleratedRecoilingLib.cpp")
    val dllOutput = layout.projectDirectory.file("build/acceleratedRecoilingLib.dll")
    inputs.file(cppSource)
    outputs.file(dllOutput)
    workingDir = layout.projectDirectory.asFile
    val vcvarsScript = "I:\\vs\\VC\\Auxiliary\\Build\\vcvars64.bat"

    // 注意：已经加上了 /EHsc，并移除了 vcpkg 的 /LIBPATH
    val compileCmd = """call "${vcvarsScript}" && cl.exe /std:c++latest /EHsc /O2 /fp:fast /arch:AVX2 /openmp /LD /MD /Zi /W3 /I"E:\\qucistart\\acceleratedrecoilingnative" acceleratedRecoilingLib.cpp /Fe"build\\acceleratedRecoilingLib.dll" /Fd"build\\acceleratedRecoilingLib.pdb" /link /OPT:REF /OPT:ICF"""
    commandLine("cmd", "/c", compileCmd)
    doFirst {
        val bDir = File(workingDir, "build")
        if (!bDir.exists()) {
            bDir.mkdirs()
        }
        println("==== 正在使用 MSVC 编译 acceleratedRecoilingLib.dll (Windows) ====")
    }
}
tasks.register<Exec>("compileNativeLibLinux") {
    group = "build"
    description = "Compiles the C++ Native Library for Linux (.so) using WSL and g++."
    onlyIf {
        System.getProperty("os.name").lowercase().contains("windows")
    }
    val cppSource = layout.projectDirectory.file("acceleratedRecoilingLib.cpp")
    val soOutput = layout.projectDirectory.file("build/libacceleratedRecoilingLib.so")
    inputs.file(cppSource)
    outputs.file(soOutput)
    workingDir = layout.projectDirectory.asFile
    val compileCmd = "g++ -std=c++20 -O3 -ffast-math -mavx2 -fopenmp -shared -fPIC acceleratedRecoilingLib.cpp -o build/libacceleratedRecoilingLib.so"
    commandLine("wsl", "bash", "-c", compileCmd)
    doFirst {
        val bDir = File(workingDir, "build")
        if (!bDir.exists()) {
            bDir.mkdirs()
        }
        println("==== 正在使用 WSL (g++) 编译 libacceleratedRecoilingLib.so (Linux) ====")
    }
}
tasks.named<ProcessResources>("processResources") {
    dependsOn("compileNativeLib", "compileNativeLibLinux")
    from(layout.projectDirectory.file("build/acceleratedRecoilingLib.dll")) {
        into("")
    }
    from(layout.projectDirectory.file("build/libacceleratedRecoilingLib.so")) {
        into("")
    }
}

tasks.withType(JavaCompile::class).configureEach {
    options.compilerArgs.add("--enable-preview")
}



        tasks.named<org.gradle.jvm.tasks.Jar>("shadowJar") {
            doLast {
                val jarFile = archiveFile.get().asFile
                println("正在移除预览版标记: ${jarFile.name}...")

                val tempFile = File(jarFile.parent, "${jarFile.name}.temp")

                JarFile(jarFile).use { jar ->
                    JarOutputStream(FileOutputStream(tempFile)).use { jos ->
                        val entries = jar.entries()

                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()

                            // 获取输入流，并在使用后自动关闭
                            jar.getInputStream(entry).use { ins ->
                                if (!entry.name.endsWith(".class")) {
                                    jos.putNextEntry(JarEntry(entry.name))
                                    ins.copyTo(jos) // Kotlin 扩展方法，等同 transferTo
                                    jos.closeEntry()
                                    return@use // 相当于 continue，跳过当前 entry 的后续逻辑
                                }

                                // ASM 读取和修改
                                val cr = ClassReader(ins)
                                val cw = ClassWriter(0)


                                // 自定义 Visitor (Kotlin 的匿名内部类写法)
                                val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
                                    override fun visit(
                                        version: Int,
                                        access: Int,
                                        name: String?,
                                        signature: String?,
                                        superName: String?,
                                        interfaces: Array<out String>?
                                    ) {
                                        val newVersion = version and Opcodes.V_PREVIEW.inv()

                                        super.visit(newVersion, access, name, signature, superName, interfaces)
                                    }
                                }

                                cr.accept(cv, 0)

                                val newEntry = JarEntry(entry.name)
                                jos.putNextEntry(newEntry)
                                jos.write(cw.toByteArray())
                                jos.closeEntry()
                            }
                        }
                    }
                }

                // 3. 用修复后的文件覆盖原文件
                if (jarFile.delete()) {
                    if (!tempFile.renameTo(jarFile)) {
                        throw RuntimeException("无法重命名临时文件！")
                    }
                    println("预览版标记移除成功！")
                } else {
                    throw RuntimeException("无法删除原始 Jar 文件！")
                }
            }
        }
//apply(plugin = "org.spongepowered.mixin")

group = "com.wiyuka"
version = "0.9.2-alpha-leaves"

repositories {
    mavenCentral()
    maven {
        name = "leavesmc-repo"
        url = uri("https://repo.leavesmc.org/snapshots/")
    }
    maven {
        name = "leavesmc-repo"
        url = uri("https://repo.leavesmc.org/releases/")
    }
    maven("https://repo.spongepowered.org/repository/maven-public/")
}


dependencies {
    val mixinExtras = "io.github.llamalad7:mixinextras-common:0.5.0"

    compileOnly(mixinExtras)
    annotationProcessor(mixinExtras)
//    leavesweight.leavesDevBundle("1.21.8-R0.1-SNAPSHOT")
    paperweight.devBundle(libs.leavesDevBundle)
    compileOnly("org.leavesmc.leaves:leaves-api:1.21.8-R0.1-SNAPSHOT")
//    compileOnly(files("I:\\downloads\\Downloads\\testserverleaves\\leaves-1.21.8.jar"))
//    compileOnly("org.leavesmc.leaves:mixin:1.21.8-R0.1-SNAPSHOT")
    implementation("org.spongepowered:mixin:0.8.5")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    jar {
        manifest {
            attributes(
                "MixinConfigs" to "mixins.acceleratedrecoiling.json"
            )
        }
    }
    assemble {
        dependsOn(reobfJar)
    }
}

tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
    manifest {
        attributes(
            // 关键：告诉 LeavesClip 你的 Mixin 配置文件名
            "Leaves-Mixin-Config" to "acceleratedrecoiling.mixins.json"
        )
    }

//    mixin.apply {
//    }
}