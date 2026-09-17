// Корневой build-файл: версии плагинов объявляются один раз и применяются
// в модулях (см. app/build.gradle.kts).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
