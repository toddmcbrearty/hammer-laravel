plugins {
    id("java")
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "com.github.todd"
version = "1.0.0"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        pluginVerifier()
        zipSigner()
    }

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.github.todd.hammerlaravel"
        name = "Hammer Laravel"
        version = project.version.toString()
        vendor {
            name = "Todd"
        }
        description = """
            Hammer Laravel - Developer toolkit for Laravel in PhpStorm.
            Features intelligent route search with wildcard parameter matching ({}),
            dockable tool window, keyboard shortcuts, and instant file navigation.
        """.trimIndent()
        changeNotes = """
            Initial release of Hammer Laravel.
        """.trimIndent()
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
        // Default to hidden=true so releases are unlisted/hidden on Marketplace
        hidden = providers.gradleProperty("publishHidden").map { it.toBoolean() }.orElse(true)
        channels = providers.gradleProperty("publishChannels").map { it.split(",") }.orElse(listOf("default"))
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
