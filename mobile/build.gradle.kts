plugins {
    kotlin("jvm") version "2.2.10"
    application
}

repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(kotlin("test-junit")) }
application { mainClass.set("rova.ReplayKt") }
