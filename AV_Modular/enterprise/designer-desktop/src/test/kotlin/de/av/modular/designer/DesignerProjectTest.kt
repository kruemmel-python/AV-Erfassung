package de.av.modular.designer

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignerProjectTest {
    @Test
    fun `designer loads edits saves and validates project`() {
        val root = Path.of(requireNotNull(System.getProperty("avm.root")))
        val sourceModule = root.resolve("modules/mail_processing")
        val sourceProfile = root.resolve("enterprise/profiles/demo_dhl")
        val temp = Files.createTempDirectory("av-designer")
        val module = copyDirectory(sourceModule, temp.resolve("module"))
        val profile = copyDirectory(sourceProfile, temp.resolve("profile"))
        val service = DesignerProjectService()
        val project = service.open(module, profile)
        val updated = project.module.processes.copy(workItems = project.module.processes.workItems.map {
            if (it.id == "routing") it.copy(targetDurationSeconds = 2400) else it
        })
        project.module = project.module.copy(processes = updated)
        assertTrue(service.validate(project).none { it.severity.name == "ERROR" })
        service.save(project)
        assertEquals(2400, service.open(module, profile).module.processes.workItems.single { it.id == "routing" }.targetDurationSeconds)
    }

    private fun copyDirectory(source: Path, target: Path): Path {
        Files.walk(source).use { paths -> paths.forEach { path ->
            val destination = target.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) Files.createDirectories(destination) else Files.copy(path, destination)
        } }
        return target
    }
}
