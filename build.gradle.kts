plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    tasks.withType<Test>().configureEach {
        (findProperty("tests") as String?)?.let { pattern ->
            filter {
                includeTestsMatching(pattern)
                isFailOnNoMatchingTests = false
            }
        }
        val label = "${project.name}:$name"
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}

            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                if (result.resultType != TestResult.ResultType.FAILURE) return
                val message = result.exception?.message?.trim()?.take(400) ?: return
                println("    $message")
            }

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent != null) return
                println(
                    "$label — тестов: ${result.testCount}, " +
                        "упало: ${result.failedTestCount}, пропущено: ${result.skippedTestCount}",
                )
            }
        })
    }
}
