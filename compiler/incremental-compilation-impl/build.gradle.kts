
plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(project(":core:descriptors"))
    compile(project(":core:descriptors.jvm"))
    compile(project(":core:deserialization"))
    compile(project(":compiler:util"))
    compile(project(":compiler:frontend"))
    compile(project(":compiler:frontend.java"))
    compile(project(":compiler:cli"))
    compile(project(":compiler:cli-js"))
    compile(project(":kotlin-build-common"))
    compile(project(":daemon-common"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }

    testCompile(commonDep("junit:junit"))
    testCompile(project(":kotlin-test:kotlin-test-junit"))
    testCompile(kotlinStdlib())
    testCompile(projectTests(":kotlin-build-common"))
    testCompile(projectTests(":compiler:tests-common"))
    testCompile(intellijCoreDep()) { includeJars("intellij-core") }
    testCompile(intellijDep()) { includeJars("log4j", "jdom") }

    Platform[192].orHigher {
        testRuntimeOnly(intellijDep()) { includeJars("platform-objectSerializer") }
    }
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

projectTest(parallel = true) {
    workingDir = rootDir
}

testsJar()
