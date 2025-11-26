rootProject.name = "AcceleratedRecoilingLeaves"
pluginManagement {
    repositories {
        maven("https://repo.leavesmc.org/releases") {
            name = "leavesmc-releases"
        }
        maven("https://repo.leavesmc.org/snapshots") {
            name = "leavesmc-snapshots"
        }

        maven("https://repo.spongepowered.org/repository/maven-public/")
        mavenCentral()
        gradlePluginPortal()
    }
}