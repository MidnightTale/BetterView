import net.fabricmc.loom.task.AbstractRemapJarTask

plugins {
    alias(libs.plugins.loom)
}

dependencies {
    minecraft(libs.minecraft.v1211)
    mappings(loom.layered {
        officialMojangMappings()
        parchment(variantOf(libs.parchment.v1211) { artifactType("zip") })
    })
    modImplementation(libs.fabric.loader)

    // common project setup
    api(projects.common) {
        exclude(group = "net.kyori")
    }

    // adventure platform for better integration with everything
    modImplementation(libs.adventure.platform.fabric.v1211)
    include(libs.adventure.platform.fabric.v1211)

    // depend on moonrise for chunk loading stuff
    modApi(libs.moonrise.v1211)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<AbstractRemapJarTask> {
    archiveBaseName = "${rootProject.name}-${project.name}".lowercase()
}

loom {
    accessWidenerPath = file("src/main/resources/betterview.accesswidener")
    mixin.defaultRefmapName = "${rootProject.name}-${project.name}-refmap.json".lowercase()
}
