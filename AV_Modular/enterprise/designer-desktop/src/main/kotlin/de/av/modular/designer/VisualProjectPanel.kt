package de.av.modular.designer

import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

/** Structured editor for common administration tasks; JSON tabs remain available for expert fields. */
class VisualProjectPanel(
    private val project: () -> DesignerProject?,
    private val changed: () -> Unit,
) : JPanel(BorderLayout()) {
    private val workItems = table("ID", "Bezeichnung", "Ziel (s)", "Unterbrechungen")
    private val rules = table("ID", "Ereignis", "Bedingungen", "Aktionen")
    private val reports = table("ID", "Titel", "Dimensionen", "Kennzahlen")
    private val roles = table("ID", "Rolle", "Berechtigungen")

    init {
        add(JLabel(" Häufige Einstellungen strukturiert bearbeiten; Expertenfelder stehen in den JSON-Registern bereit."), BorderLayout.NORTH)
        add(JTabbedPane().apply {
            addTab("Vorgänge", page(workItems, "VORGANG BEARBEITEN", ::editWorkItem))
            addTab("Regeln", page(rules, "REGEL BEARBEITEN", ::editRule))
            addTab("Berichte", page(reports, "BERICHT BEARBEITEN", ::editReport))
            addTab("Rollen", page(roles, "ROLLE BEARBEITEN", ::editRole))
        }, BorderLayout.CENTER)
    }

    fun refresh() {
        val current = project() ?: return clear()
        rows(workItems, current.module.processes.workItems.map { arrayOf<Any>(it.id, it.displayName, it.targetDurationSeconds, it.allowedInterruptions.joinToString(", ")) })
        rows(rules, current.module.rules.rules.map { arrayOf<Any>(it.id, it.event, it.conditions.size, it.actions.joinToString { action -> action.type }) })
        rows(reports, current.module.reports.reports.map { arrayOf<Any>(it.reportId, it.title, it.dimensions.joinToString(", "), it.metrics.size) })
        rows(roles, current.profile.profile.roles.map { arrayOf<Any>(it.id, it.displayName, it.permissions.joinToString(", ")) })
    }

    private fun editWorkItem() {
        val current = project() ?: return
        val selected = workItems.selectedRow.takeIf { it >= 0 }?.let(workItems::convertRowIndexToModel) ?: return message("Bitte einen Vorgang auswählen.")
        val original = current.module.processes.workItems[selected]
        val name = JTextField(original.displayName)
        val target = JTextField(original.targetDurationSeconds.toString())
        val interruptions = JTextField(original.allowedInterruptions.joinToString(","))
        if (!confirm("Vorgang bearbeiten", "Bezeichnung" to name, "Zielzeit in Sekunden" to target, "Unterbrechungs-IDs" to interruptions)) return
        val updated = original.copy(
            displayName = name.text.trim().also { require(it.isNotBlank()) },
            targetDurationSeconds = target.text.trim().toLong().also { require(it > 0) },
            allowedInterruptions = interruptions.text.split(',').map(String::trim).filter(String::isNotBlank).distinct(),
        )
        current.module = current.module.copy(processes = current.module.processes.copy(
            workItems = current.module.processes.workItems.map { if (it.id == original.id) updated else it },
        ))
        changed()
    }

    private fun editRule() {
        val current = project() ?: return
        val selected = rules.selectedRow.takeIf { it >= 0 }?.let(rules::convertRowIndexToModel) ?: return message("Bitte eine Regel auswählen.")
        val original = current.module.rules.rules[selected]
        val event = JTextField(original.event)
        if (!confirm("Regelassistent", "Auslösendes Ereignis" to event)) return
        val updated = original.copy(event = event.text.trim().also { require(it.isNotBlank()) })
        current.module = current.module.copy(rules = current.module.rules.copy(
            rules = current.module.rules.rules.map { if (it.id == original.id) updated else it },
        ))
        changed()
    }

    private fun editReport() {
        val current = project() ?: return
        val selected = reports.selectedRow.takeIf { it >= 0 }?.let(reports::convertRowIndexToModel) ?: return message("Bitte einen Bericht auswählen.")
        val original = current.module.reports.reports[selected]
        val title = JTextField(original.title)
        val dimensions = JTextField(original.dimensions.joinToString(","))
        if (!confirm("Bericht bearbeiten", "Titel" to title, "Dimensionen" to dimensions)) return
        val updated = original.copy(
            title = title.text.trim().also { require(it.isNotBlank()) },
            dimensions = dimensions.text.split(',').map(String::trim).filter(String::isNotBlank).distinct(),
        )
        current.module = current.module.copy(reports = current.module.reports.copy(
            reports = current.module.reports.reports.map { if (it.reportId == original.reportId) updated else it },
        ))
        changed()
    }

    private fun editRole() {
        val current = project() ?: return
        val selected = roles.selectedRow.takeIf { it >= 0 }?.let(roles::convertRowIndexToModel) ?: return message("Bitte eine Rolle auswählen.")
        val original = current.profile.profile.roles[selected]
        val name = JTextField(original.displayName)
        val permissions = JTextField(original.permissions.joinToString(","))
        if (!confirm("Rolle bearbeiten", "Bezeichnung" to name, "Berechtigungen" to permissions)) return
        val updated = original.copy(
            displayName = name.text.trim().also { require(it.isNotBlank()) },
            permissions = permissions.text.split(',').map(String::trim).filter(String::isNotBlank).distinct(),
        )
        current.profile = current.profile.copy(profile = current.profile.profile.copy(
            roles = current.profile.profile.roles.map { if (it.id == original.id) updated else it },
        ))
        changed()
    }

    private fun page(table: JTable, label: String, action: () -> Unit): JPanel = JPanel(BorderLayout()).apply {
        add(JScrollPane(table), BorderLayout.CENTER)
        add(JButton(label).apply { addActionListener { runCatching(action).onFailure { message(it.message ?: "Eingabe ungültig") } } }, BorderLayout.SOUTH)
    }

    private fun table(vararg columns: String) = JTable(object : DefaultTableModel(columns, 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        autoCreateRowSorter = true
    }

    private fun rows(table: JTable, values: List<Array<out Any>>) {
        val model = table.model as DefaultTableModel
        model.rowCount = 0
        values.forEach(model::addRow)
    }

    private fun clear() = listOf(workItems, rules, reports, roles).forEach { (it.model as DefaultTableModel).rowCount = 0 }

    private fun confirm(title: String, vararg fields: Pair<String, JTextField>): Boolean {
        val panel = JPanel(GridLayout(fields.size, 2, 8, 8))
        fields.forEach { (label, field) -> panel.add(JLabel(label)); panel.add(field) }
        return JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION
    }

    private fun message(value: String) { JOptionPane.showMessageDialog(this, value) }
}
