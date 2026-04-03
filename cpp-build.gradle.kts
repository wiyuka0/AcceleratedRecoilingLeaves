repositories {
    ivy {
        name = "GitHub Toolchain Release"
        url = uri("https://github.com/wiyuka0/AcceleratedRecoiling/releases/download")
        patternLayout {
            artifact("[revision]/[artifact].[ext]")
        }
        metadataSources {
            artifact()
        }
    }
}

val cppToolchain by configurations.creating

dependencies {
    cppToolchain("com.wiyuka.env:AcceleratedRecoiling-third-party:toolchain_20260403_0@zip")
}

val cppProjectDir = file("./AcceleratedRecoiling-third-party")
val isWindows = System.getProperty("os.name").lowercase().contains("windows")

// =====================
// 解压工具链
// =====================
val extractToolchain by tasks.registering(Copy::class) {
    group = "build native"
    dependsOn(cppToolchain)

    onlyIf {
        !file("${cppProjectDir}/env/add_env.bat").exists()
    }

    from({
        zipTree(cppToolchain.singleFile)
    })
    into(cppProjectDir)

    doLast {
        println("> 解压完成")
    }
}

// =====================
// 注册 native 编译任务函数
// =====================
fun registerNativeTask(
        taskName: String,
        buildBatName: String,
        description: String
) = tasks.register<Exec>(taskName) {

    group = "build native"
    dependsOn(extractToolchain)

    onlyIf { isWindows }

    workingDir = cppProjectDir

    val envScript = file("${cppProjectDir}/env/add_env.bat").absolutePath
    val buildScript = file("${cppProjectDir}/build/$buildBatName").absolutePath

    commandLine(
            "cmd", "/c",
            "call \"$envScript\" && call \"$buildScript\""
    )

    doFirst {
        println("> 开始编译 $description ($buildBatName)")
    }
}

// =====================
// 各平台任务
// =====================
val compileNativeWin = registerNativeTask(
        "compileNativeWin",
        "win-x64.bat",
        "Windows 动态库"
)

val compileNativeLinux = registerNativeTask(
        "compileNativeLinux",
        "linux-x64.bat",
        "Linux 动态库"
)

val compileNativeMac = registerNativeTask(
        "compileNativeMac",
        "macos-x64.bat",
        "macOS 动态库"
)

// =====================
// 打包 jar
// =====================
tasks.named<Jar>("jar") {

    dependsOn(
            compileNativeWin,
            compileNativeLinux,
            compileNativeMac
    )

    from("${cppProjectDir}/out/win-x64/") {
        include("*.dll")
        into("natives/windows-x64")
    }

    from("${cppProjectDir}/out/linux-x64/") {
        include("*.so")
        into("natives/linux-x64")
    }

    from("${cppProjectDir}/out/macos-x64/") {
        include("*.dylib")
        into("natives/macos-x64")
    }

    from("${cppProjectDir}/out/macos-arm64/") {
        include("*.dylib")
        into("natives/macos-arm64")
    }
}