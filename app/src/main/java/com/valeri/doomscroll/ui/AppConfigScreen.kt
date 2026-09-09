package com.valeri.doomscroll.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valeri.doomscroll.classifier.MatchType
import com.valeri.doomscroll.classifier.RuleKind
import com.valeri.doomscroll.data.db.ContextRuleEntity
import com.valeri.doomscroll.ui.theme.Palette

@Composable
fun AppConfigScreen(
    vm: SettingsViewModel,
    packageName: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val rules by vm.rulesFor(packageName).collectAsStateWithLifecycle(emptyList())
    val reasons by vm.reasonsFor(packageName).collectAsStateWithLifecycle(emptyList())
    val apps by vm.monitoredApps.collectAsStateWithLifecycle()
    val app = apps.firstOrNull { it.packageName == packageName }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
            Text("Back", color = Palette.Mist)
        }
        Text(
            AppCatalog.labelFor(context, packageName),
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.TextPrimary,
        )
        Text(packageName, color = Palette.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

        SectionHeader("Monitoring")
        SettingsCard {
            ToggleRow(
                title = "Watch this app",
                checked = app?.enabled ?: false,
                onChange = { vm.setAppEnabled(packageName, it) },
            )
            TextButton(
                onClick = { vm.removeApp(packageName); onBack() },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Stop watching and remove", color = Palette.Ember)
            }
        }

        SectionHeader("Screen rules")
        Text(
            "Legit rules are checked first and always win. A screen matching nothing is left " +
                "alone — that is why nothing fires until a doomscroll rule actually matches.",
            color = Palette.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SettingsCard {
            val legit = rules.filter { it.kind == RuleKind.LEGIT }
            val doom = rules.filter { it.kind == RuleKind.DOOMSCROLL }

            RuleGroup("Legit — never interrupt", legit, Palette.Mist, vm)
            RuleGroup("Doomscroll — interrupt", doom, Palette.Ember, vm)
            if (rules.isEmpty()) {
                Text(
                    "No rules for this app yet. Use Learn Mode to capture real view ids.",
                    color = Palette.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        AddRuleForm(packageName, vm)

        SectionHeader("Reasons offered")
        SettingsCard {
            reasons.forEach { reason ->
                // The last reason can't be removed — an empty list would leave the "why are
                // you here" prompt with nothing to offer.
                val isLastOne = reasons.size <= 1
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(reason.label, color = Palette.TextPrimary, fontSize = 14.sp)
                        val subtitle = when {
                            isLastOne -> "Last reason — can't be removed"
                            reason.packageName == null -> "Shown for every app"
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(subtitle, color = Palette.TextMuted, fontSize = 11.sp)
                        }
                    }
                    IconButton(
                        onClick = { vm.deleteReason(reason) },
                        enabled = !isLastOne,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Default.Close, "Delete reason", tint = Palette.TextMuted)
                    }
                }
            }
        }
        AddReasonForm(packageName, vm)
    }
}

@Composable
private fun RuleGroup(
    title: String,
    rules: List<ContextRuleEntity>,
    accent: androidx.compose.ui.graphics.Color,
    vm: SettingsViewModel,
) {
    if (rules.isEmpty()) return
    Text(
        title,
        color = accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
    )
    rules.forEach { rule ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    rule.pattern,
                    color = if (rule.enabled) Palette.TextPrimary else Palette.TextMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    if (rule.isBuiltIn) "${rule.matchType.name} · built in" else rule.matchType.name,
                    color = Palette.TextMuted,
                    fontSize = 11.sp,
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = { vm.setRuleEnabled(rule, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Palette.Ink,
                    checkedTrackColor = accent,
                    uncheckedTrackColor = Palette.SurfaceAlt,
                ),
            )
            // Built-ins can be switched off but not deleted, so a bad edit is always recoverable.
            if (!rule.isBuiltIn) {
                IconButton(onClick = { vm.deleteRule(rule) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Close, "Delete rule", tint = Palette.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun AddRuleForm(packageName: String, vm: SettingsViewModel) {
    var pattern by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(RuleKind.DOOMSCROLL) }
    var match by remember { mutableStateOf(MatchType.VIEW_ID) }

    Column(Modifier.padding(top = 10.dp)) {
        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("View id or class name", color = Palette.TextMuted) },
            placeholder = { Text("clips_viewer_view_pager", color = Palette.TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Palette.TextPrimary,
                unfocusedTextColor = Palette.TextPrimary,
                focusedBorderColor = Palette.Mist,
                unfocusedBorderColor = Palette.SurfaceAlt,
                cursorColor = Palette.Mist,
            ),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectChip("Doomscroll", kind == RuleKind.DOOMSCROLL) { kind = RuleKind.DOOMSCROLL }
            SelectChip("Legit", kind == RuleKind.LEGIT) { kind = RuleKind.LEGIT }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectChip("View id", match == MatchType.VIEW_ID) { match = MatchType.VIEW_ID }
            SelectChip("Class name", match == MatchType.CLASS_CONTAINS) { match = MatchType.CLASS_CONTAINS }
        }
        TextButton(
            onClick = { vm.addRule(packageName, kind, match, pattern); pattern = "" },
            enabled = pattern.isNotBlank(),
        ) {
            Text("Add rule", color = if (pattern.isNotBlank()) Palette.Mist else Palette.TextMuted)
        }
    }
}

@Composable
private fun AddReasonForm(packageName: String, vm: SettingsViewModel) {
    var label by remember { mutableStateOf("") }
    Column(Modifier.padding(top = 10.dp)) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("New reason for this app", color = Palette.TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Palette.TextPrimary,
                unfocusedTextColor = Palette.TextPrimary,
                focusedBorderColor = Palette.Mist,
                unfocusedBorderColor = Palette.SurfaceAlt,
                cursorColor = Palette.Mist,
            ),
        )
        TextButton(
            onClick = { vm.addReason(packageName, label); label = "" },
            enabled = label.isNotBlank(),
        ) {
            Text("Add reason", color = if (label.isNotBlank()) Palette.Mist else Palette.TextMuted)
        }
    }
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text, fontSize = 13.sp) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) Palette.Mist else Palette.SurfaceAlt,
            labelColor = if (selected) Palette.Ink else Palette.TextPrimary,
        ),
    )
}
