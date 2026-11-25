plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
}

tasks.register("checkTestVectors") {
    val copies = listOf(
        "core/src/test/resources/test-vectors.json",
        "paymentelement/src/test/resources/test-vectors.json",
        "paymentsheet/src/test/resources/test-vectors.json",
        "test-vectors/test-vectors.json",
    ).map { layout.projectDirectory.file(it) }
    inputs.files(copies)
    doLast {
        val bytes = copies.map { it.asFile.readBytes() }
        bytes.drop(1).forEachIndexed { index, copy ->
            if (!copy.contentEquals(bytes.first())) {
                throw GradleException("test-vectors.json copies differ: ${copies[index + 1].asFile}")
            }
        }
    }
}

subprojects {
    tasks.matching { it.name == "test" || it.name.startsWith("testDebug") }.configureEach {
        dependsOn(rootProject.tasks.named("checkTestVectors"))
    }
}
