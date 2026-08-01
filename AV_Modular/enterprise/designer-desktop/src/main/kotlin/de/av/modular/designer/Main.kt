package de.av.modular.designer

import de.av.modular.packages.ConfigurationPackageService
import de.av.modular.validation.Severity
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.nio.file.Path
import java.nio.file.Files
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

fun main(args: Array<String>) {
    SwingUtilities.invokeLater {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        DesignerWindow(args.firstOrNull()?.let(Path::of)).isVisible = true
    }
}

class DesignerWindow(initialRoot: Path?) : JFrame("AV Designer Enterprise 1.0") {
    private val service = DesignerProjectService()
    private var project: DesignerProject? = null
    private val status = JLabel("Kein Projekt geöffnet")
    private val moduleEditor = editor()
    private val processesEditor = editor()
    private val rulesEditor = editor()
    private val reportsEditor = editor()
    private val profileEditor = editor()
    private val visualEditor = VisualProjectPanel({ project }) { refreshEditors(); validateProject() }
    private val findings = JTextArea().apply { isEditable = false; font = Font(Font.MONOSPACED, Font.PLAIN, 13) }

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(1100, 760)
        size = Dimension(1280, 850)
        setLocationRelativeTo(null)
        jMenuBar = menu()
        contentPane.layout = BorderLayout()
        contentPane.add(header(), BorderLayout.NORTH)
        contentPane.add(workspace(), BorderLayout.CENTER)
        contentPane.add(status.apply { border = BorderFactory.createEmptyBorder(7, 12, 7, 12) }, BorderLayout.SOUTH)
        initialRoot?.let { root -> open(root.resolve("modules/mail_processing"), root.resolve("enterprise/profiles/demo_dhl")) }
    }

    private fun header(): JPanel = JPanel(BorderLayout()).apply {
        background = Color(0xD4, 0x05, 0x11)
        border = BorderFactory.createEmptyBorder(12, 16, 12, 16)
        add(JLabel("AV DESIGNER").apply { foreground = Color.WHITE; font = font.deriveFont(Font.BOLD, 24f) }, BorderLayout.WEST)
        add(JPanel(GridLayout(1, 3, 8, 0)).apply {
            isOpaque = false
            add(button("PRÜFEN", ::validateProject))
            add(button("FORMATIEREN", ::applyEditors))
            add(button("SPEICHERN", ::saveProject))
        }, BorderLayout.EAST)
    }

    private fun workspace(): JSplitPane {
        val tabs = JTabbedPane().apply {
            addTab("Visueller Editor", visualEditor)
            addTab("Modul", JScrollPane(moduleEditor))
            addTab("Vorgänge & Formulare", JScrollPane(processesEditor))
            addTab("Regeln", JScrollPane(rulesEditor))
            addTab("Berichte", JScrollPane(reportsEditor))
            addTab("Kundenprofil", JScrollPane(profileEditor))
        }
        return JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, JScrollPane(findings)).apply {
            resizeWeight = 0.78
            dividerLocation = 600
            border = null
        }
    }

    private fun menu(): JMenuBar = JMenuBar().apply {
        add(JMenu("Projekt").apply {
            add(JMenuItem("Öffnen …").apply { addActionListener { chooseProject() } })
            add(JMenuItem("Prüfen").apply { addActionListener { validateProject() } })
            add(JMenuItem("Speichern").apply { addActionListener { saveProject() } })
            add(JMenuItem("Signiertes Paket exportieren …").apply { addActionListener { exportPackage() } })
            addSeparator()
            add(JMenuItem("Beenden").apply { addActionListener { dispose() } })
        })
    }

    private fun chooseProject() {
        val module = chooseDirectory("Modulordner auswählen") ?: return
        val profile = chooseDirectory("Profilordner auswählen") ?: return
        open(module, profile)
    }

    private fun open(module: Path, profile: Path) = runUi("Projekt konnte nicht geöffnet werden") {
        project = service.open(module, profile)
        refreshEditors()
        validateProject()
        status.text = "${module.fileName} · ${profile.fileName}"
    }

    private fun applyEditors() = runUi("Konfiguration ist ungültig") {
        val current = project ?: error("Kein Projekt geöffnet")
        service.updateFromJson(current, moduleEditor.text, processesEditor.text, rulesEditor.text, reportsEditor.text, profileEditor.text)
        refreshEditors()
        validateProject()
    }

    private fun saveProject() = runUi("Speichern fehlgeschlagen") {
        applyEditors()
        val current = project ?: error("Kein Projekt geöffnet")
        val errors = service.validate(current).filter { it.severity == Severity.ERROR }
        require(errors.isEmpty()) { "Konfiguration enthält ${errors.size} Fehler" }
        service.save(current)
        status.text = "Gespeichert: ${current.moduleDirectory}"
    }

    private fun validateProject() = runUi("Prüfung fehlgeschlagen") {
        val current = project ?: error("Kein Projekt geöffnet")
        val issues = service.validate(current)
        findings.text = if (issues.isEmpty()) "OK – keine Konfigurationsfehler" else issues.joinToString("\n") { "${it.severity}  ${it.path}  ${it.message}" }
        findings.caretPosition = 0
        status.text = "Prüfung: ${issues.count { it.severity == Severity.ERROR }} Fehler, ${issues.count { it.severity == Severity.WARNING }} Warnungen"
    }

    private fun exportPackage() = runUi("Paketexport fehlgeschlagen") {
        saveProject()
        val current = project ?: error("Kein Projekt geöffnet")
        val privateKeyFile = JFileChooser().run {
            dialogTitle = "Privaten Ed25519-Schlüssel auswählen"
            fileFilter = FileNameExtensionFilter("PEM-Schlüssel", "pem")
            if (showOpenDialog(this@DesignerWindow) == JFileChooser.APPROVE_OPTION) selectedFile.toPath() else return@runUi
        }
        val destination = JFileChooser().run {
            dialogTitle = "Konfigurationspaket speichern"
            selectedFile = java.io.File("${current.profile.profile.profileId}-${current.module.manifest.moduleVersion}.avpkg")
            if (showSaveDialog(this@DesignerWindow) == JFileChooser.APPROVE_OPTION) selectedFile.toPath() else return@runUi
        }
        val keyId = JOptionPane.showInputDialog(this, "Schlüssel-ID", "Signierung", JOptionPane.QUESTION_MESSAGE)?.trim().orEmpty()
        require(keyId.isNotBlank()) { "Schlüssel-ID fehlt" }
        val staging = Files.createTempDirectory("av-designer-package")
        try {
            copyDirectory(current.moduleDirectory, staging.resolve("module"))
            copyDirectory(current.profileDirectory, staging.resolve("profile"))
            val pem = Files.readString(privateKeyFile).lineSequence().filterNot { it.startsWith("---") }.joinToString("")
            val key = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)))
            ConfigurationPackageService().create(
                staging,
                destination,
                "${current.profile.profile.profileId}_${current.module.manifest.moduleId}",
                current.module.manifest.moduleVersion,
                keyId,
                key,
            )
            status.text = "Signiertes Paket erstellt: $destination"
        } finally {
            staging.toFile().deleteRecursively()
        }
    }

    private fun refreshEditors() {
        val current = project ?: return
        moduleEditor.text = service.moduleJson(current)
        processesEditor.text = service.processesJson(current)
        rulesEditor.text = service.rulesJson(current)
        reportsEditor.text = service.reportsJson(current)
        profileEditor.text = service.profileJson(current)
        listOf(moduleEditor, processesEditor, rulesEditor, reportsEditor, profileEditor).forEach { it.caretPosition = 0 }
        visualEditor.refresh()
    }

    private fun chooseDirectory(title: String): Path? = JFileChooser().run {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (showOpenDialog(this@DesignerWindow) == JFileChooser.APPROVE_OPTION) selectedFile.toPath() else null
    }

    private fun copyDirectory(source: Path, target: Path) {
        Files.walk(source).use { paths -> paths.forEach { path ->
            val destination = target.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) Files.createDirectories(destination) else Files.copy(path, destination)
        } }
    }

    private fun runUi(title: String, action: () -> Unit) {
        runCatching(action).onFailure { JOptionPane.showMessageDialog(this, it.message, title, JOptionPane.ERROR_MESSAGE) }
    }

    private fun editor(): JTextArea = JTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 14)
        tabSize = 2
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }

    private fun button(text: String, action: () -> Unit): JButton = JButton(text).apply {
        background = Color(0xFF, 0xCC, 0x00)
        foreground = Color.BLACK
        font = font.deriveFont(Font.BOLD)
        addActionListener { action() }
    }
}
