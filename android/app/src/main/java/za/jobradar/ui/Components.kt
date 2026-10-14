@file:OptIn(ExperimentalLayoutApi::class)

package za.jobradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.jobradar.data.ScoredJob
import za.jobradar.data.Store
import za.jobradar.search.JobSearch

val Blue = Color(0xFF2557A7)
val DarkBlue = Color(0xFF1D4A91)
val Orange = Color(0xFFF26B21)
val Green = Color(0xFF1E8E3E)
val Amber = Color(0xFFB26A00)
val Grey = Color(0xFF6B6B6B)
val Bg = Color(0xFFF3F2F1)
val ClaudeTan = Color(0xFFC96442)

fun scoreColor(s: Int) = when {
    s >= 75 -> Green
    s >= 50 -> Amber
    else -> Grey
}

@Composable
fun Pill(text: String, color: Color, filled: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = if (filled) Color.White else color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ScoreBadge(score: Int, label: String = "match") {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(scoreColor(score).copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score%", color = scoreColor(score), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(label, color = scoreColor(score), fontSize = 9.sp)
        }
    }
}

@Composable
fun ChipsRow(items: List<String>, color: Color) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { Pill(it, color) }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
fun JobCard(sj: ScoredJob, onClick: () -> Unit) {
    val j = sj.job
    val status = Store.statusOf(j.id)
    val fit = Store.fits[j.id]
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (sj.match.score >= 75) Text("Strong match", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(j.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2D2D2D),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (j.company.isNotBlank()) Text(j.company, fontSize = 14.sp, color = Color(0xFF2D2D2D))
                if (j.location.isNotBlank()) Text(j.location, fontSize = 13.sp, color = Grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.size(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (j.salary.isNotBlank()) Pill(j.salary, Color(0xFF424242))
                    if (j.contract.isNotBlank()) Pill(j.contract, Color(0xFF424242))
                    Pill(j.source, Blue)
                    if (status != null) Pill(status, Orange, filled = true)
                    if (fit != null) Pill("Claude ${fit.score}%", ClaudeTan, filled = true)
                }
                Spacer(Modifier.size(6.dp))
                val ago = JobSearch.ago(j.postedAt)
                Text(
                    listOfNotNull(
                        ago.ifBlank { null },
                        if (sj.match.cvName.isNotBlank()) "Best CV: ${sj.match.cvName}" else null,
                    ).joinToString(" · "),
                    fontSize = 12.sp, color = Grey, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (Store.cvs.isNotEmpty()) ScoreBadge(sj.match.score)
        }
    }
}

@Composable
fun StarButton(saved: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (saved) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = "Save",
            tint = if (saved) Orange else Grey,
        )
    }
}

@Composable
fun Hint(text: String) {
    Text(text, color = Grey, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun cardColors() = CardDefaults.cardColors(containerColor = Color.White)

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = cardColors(), elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun accent() = MaterialTheme.colorScheme.primary
