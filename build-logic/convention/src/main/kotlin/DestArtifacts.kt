import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property

open class DestArtifacts(objectFactory: ObjectFactory) {
    val binaryJar: RegularFileProperty = objectFactory.fileProperty()
    val sourcesJar: RegularFileProperty = objectFactory.fileProperty()

    open class VersionInfo(objectFactory: ObjectFactory) {
        val display: Property<String> = objectFactory.property<String>()
        val range: ListProperty<String> = objectFactory.listProperty<String>()
    }

    val versionInfo = VersionInfo(objectFactory)
}
