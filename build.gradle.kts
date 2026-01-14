plugins {
    `java-library`

    id("com.github.johnrengelman.shadow") version "8.1.1"

	// auto update dependencies with 'useLatestVersions' task
	id("se.patrikerdes.use-latest-versions") version "0.2.18"
	id("com.github.ben-manes.versions") version "0.50.0"
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-core:2.20.1")
	implementation("io.javalin:javalin:6.7.0")
	implementation("org.slf4j:slf4j-simple:2.0.16")
	
	// MCP Client dependencies
	implementation("io.modelcontextprotocol.sdk:mcp:0.17.0")
    implementation("io.modelcontextprotocol.sdk:mcp-bom:0.17.0")
    
    // JUnit dependencies for testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    val appScanJar by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        archiveBaseName.set("AppScan")
        archiveClassifier.set("")

        manifest {
            attributes["Main-Class"] = "com.wrlus.vulscan.AppScanMain"
        }

        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.getByName("runtimeClasspath"))
    }

    val frameworkScanJar by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        archiveBaseName.set("FrameworkScan")
        archiveClassifier.set("")

        manifest {
            attributes["Main-Class"] = "com.wrlus.vulscan.FrameworkScanMain"
        }

        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.getByName("runtimeClasspath"))
    }

    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        enabled = false
    }

    register("buildScanners") {
        dependsOn(appScanJar, frameworkScanJar)
    }

    withType<Test> {
        useJUnitPlatform()
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
