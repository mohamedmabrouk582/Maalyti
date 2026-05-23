package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionEntity
import com.example.ui.viewmodel.MaalytiViewModel
import com.example.ui.theme.*

@Composable
fun ReportBars(
    vm: MaalytiViewModel,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val txList by vm.transactions.collectAsState()
    val isPremiumLocked by vm.isPremiumLocked.collectAsState()
    val baseDisplayCurrency by vm.selectedTargetCurrency.collectAsState()

    // Aggregate category expenditures dynamically
    val categoriesMap = remember(txList) {
        val map = mutableMapOf<String, Double>()
        txList.filter { it.type == "withdraw" }.forEach { item ->
            val cat = when {
                item.merchant.lowercase().contains("starbucks") || item.merchant.lowercase().contains("coffee") -> "المقاهي والمطاعم"
                item.merchant.lowercase().contains("noon") || item.merchant.lowercase().contains("amazon") || item.merchant.lowercase().contains("jumia") || item.merchant.lowercase().contains("mall") -> "التسوق الإلكتروني"
                item.merchant.lowercase().contains("uber") || item.merchant.lowercase().contains("taxi") || item.merchant.lowercase().contains("fuel") -> "النقل والمواصلات"
                else -> "مصاريف عامة"
            }
            map[cat] = (map[cat] ?: 0.0) + item.amount
        }
        map
    }

    val totalSpent = categoriesMap.values.sum()

    // Build smart behavior custom Arabic insights
    val aiInsightText = remember(txList, totalSpent) {
        val sb = StringBuilder()
        if (txList.isEmpty()) {
            sb.append("بانتظار بدء الرسائل البنكية لتحليل النمط المعيشي وتقديم نصائح مالية مخصصة.")
        } else {
            val starbucksSpent = txList.filter { it.merchant.lowercase().contains("starbucks") || it.merchant.lowercase().contains("coffee") }.sumOf { it.amount }
            if (starbucksSpent > 100) {
                sb.append("🚨 لقد أنفقت حوالي ")
                    .append(starbucksSpent.toInt())
                    .append(" ريال على المقاهي وتناول الطعام بالخارج هذا الأسبوع. تقليص هذا النمط سيوفر لك مبالغ إضافية لهدفك الإدخاري.\n\n")
            }

            val otpCount = txList.count { it.type == "otp" }
            if (otpCount > 2) {
                sb.append("⚠️ تم رصد محاولات سحب متعددة برمز تحقق (OTP) متكرر. يرجى مراجعة اشتراكاتك التلقائية لضمان الحماية.\n\n")
            }

            if (totalSpent > 3000) {
                sb.append("📉 مصاريفك الإجمالية مرتفعة نسبياً مقارنة بالمعدل المستهدف. نقوم الآن بإعادة حساب الميزانية اليومية المسموحة ديناميكياً لتفادي تجاوز الحد المسموح.\n\n")
            } else {
                sb.append("✅ ميزانيتك الحالية تحت السيطرة والالتزام بها متميز! معدل نفاد السيولة مستقر، استمر لتوليد وفر إضافي هذا الشهر.")
            }
        }
        sb.toString()
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title block
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = "Reports title",
                tint = CosmicPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ArabicGlossary.get("reports", isArabic),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CosmicTextWhite
            )
        }

        // Spend Categories Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicSurfaceElevated.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "مصروفاتك حسب التصنيف مجهرياً (Core Categories)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CosmicTextWhite
                )

                if (categoriesMap.isEmpty()) {
                    Text(
                        text = "لا توجد مصاريف ومشتريات مسجلة حالياً لعرض المخطط البياني.",
                        fontSize = 12.sp,
                        color = CosmicTextMuted
                    )
                } else {
                    categoriesMap.forEach { (catName, sumAmount) ->
                        val ratio = if (totalSpent > 0) (sumAmount / totalSpent).toFloat() else 0f
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = catName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CosmicTextWhite)
                                Text(text = "${sumAmount.toInt()} $baseDisplayCurrency (${(ratio * 100).toInt()}%)", fontSize = 11.sp, color = CosmicPrimary, fontWeight = FontWeight.Bold)
                            }
                            // Custom painted progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(CosmicSurfaceElevated)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(CosmicPrimary)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Smart Generative Advisor insights Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicSurfaceElevated.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI insights",
                        tint = CosmicSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ArabicGlossary.get("smart_advise", isArabic),
                        fontWeight = FontWeight.Bold,
                        color = CosmicTextWhite,
                        fontSize = 14.sp
                    )
                }

                Divider(color = CosmicSurfaceElevated)

                Text(
                    text = aiInsightText,
                    fontSize = 13.sp,
                    color = CosmicTextWhite,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Start
                )

                if (isPremiumLocked) {
                    // Pay warning to unlock export reports
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CosmicSecondary.copy(alpha = 0.12f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "info",
                                tint = CosmicSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "العضوية الممتازة المقفلة تتيح لك تحميل تقارير pdf/csv كاملة لمصرف الراجحي والأهلي.",
                                fontSize = 11.sp,
                                color = CosmicSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    // Unlocked premium panel
                    Button(
                        onClick = { /* trigger demo download */ },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("تصدير تقرير PDF الذكي للمصروفات 📥", color = CosmicTextOnPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
