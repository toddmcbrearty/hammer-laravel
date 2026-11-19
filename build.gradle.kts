plugins {
    id("java")
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "com.github.todd"
version = "1.0.2"

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
        freeCompilerArgs.add("-jvm-default=no-compatibility")
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
            <h3>Hammer Laravel</h3>
            <p>
                A high-performance developer toolkit designed specifically for Laravel development in PhpStorm and IntelliJ Platform IDEs. It provides purpose-built tools that streamline navigating and working with Laravel codebases, starting with an <b>Intelligent Route Search and Navigation engine</b>.
            </p>
            <p>
                Locating route definitions in large Laravel applications can be frustrating when URLs contain dynamic parameters, controller aliases, or route prefixes spread across multiple files. Hammer Laravel solves this by introducing intuitive <b>wildcard parameter matching (<code>{}</code>)</b>, allowing developers to search for routes like <code>/users/{}/edit</code> or paste real-world URLs directly to jump instantly to route definitions or controller actions.
            </p>
            <h4>Key Features:</h4>
            <ul>
                <li><b>Wildcard Parameter Search (<code>{}</code>):</b> Type <code>/go/to/{}/edit</code> to match <code>{id}</code>, <code>{user}</code>, <code>{slug}</code>, etc.</li>
                <li><b>Paste Real URLs:</b> Paste concrete browser URLs like <code>admin/company/188/accounts</code> to find matching route templates.</li>
                <li><b>Jump to Route & Jump to Function:</b> Jump instantly to route file definitions (<kbd>Enter</kbd>) or controller methods/closures (<kbd>Cmd+B</kbd> / <kbd>Shift+Enter</kbd>).</li>
                <li><b>Color-Coded HTTP Badges:</b> Visual method badges for GET, POST, PUT, DELETE, and ANY with method filtering.</li>
                <li><b>Dockable Tool Window:</b> Right sidebar panel with live file watcher that auto-indexes routes as you code.</li>
            </ul>
            <h4>Keyboard Shortcuts:</h4>
            <ul>
                <li><b>Search Laravel Routes:</b> <kbd>Shift+Option+R</kbd> (macOS) / <kbd>Shift+Alt+R</kbd> (Win/Linux)</li>
                <li><b>Open Routes Tool Window:</b> <kbd>Ctrl+Option+R</kbd> (macOS) / <kbd>Ctrl+Alt+R</kbd> (Win/Linux)</li>
                <li><b>Jump to Route Source:</b> <kbd>Enter</kbd> or <kbd>Double-Click</kbd> (inside Routes panel)</li>
                <li><b>Jump to Controller Function:</b> <kbd>Cmd+B</kbd> (macOS) / <kbd>Ctrl+B</kbd> (Win/Linux) or <kbd>Shift+Enter</kbd></li>
            </ul>
        """.trimIndent()
        changeNotes = """
            <ul>
                <li>Bump version to 1.0.1</li>
                <li>Add <code>-jvm-default=no-compatibility</code> compiler argument for JVM 25 target compatibility</li>
            </ul>
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
