plugins {
    id("java")
    id("application")
}

group = "dev.s1ck"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(22))

tasks.withType<JavaExec>().configureEach {
    jvmArgs( "--enable-native-access=ALL-UNNAMED")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs( "--enable-native-access=ALL-UNNAMED")
}
