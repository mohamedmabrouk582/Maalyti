package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MaalytiViewModel
import com.example.ui.theme.*

@Composable
fun HomeDashboard(
    vm: MaalytiViewModel,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val budgetState by vm.budget.collectAsState()
    val accountsState by vm.accounts.collectAsState()
    val transactionsState by vm.transactions.collectAsState()
    val burnDays by vm.burnRateDays.collectAsState()
    val targetCurrency by vm.selectedTargetCurrency.collectAsState()
    
    val isParsingState by vm.isParsing.collectAsState()
    val parsingAlertState by vm.parsingResultAlert.collectAsState()

    var customSmsText by remember { mutableStateOf("") }

    val presetAlerts = listOf(
        "SNB Alert: A withdrawal of 120.00 SAR was made at Riyadh Mall. Remaining balance: 8,330 SAR.",
        "تمت عملية شراء بقيمة 450 ريال من بطاقتك الائتمانية المنتهية بـ 4512 لدى نون. رصيدك الحالي هو 7,880.00 ريال",
        "CIB Alert: Purchase of 1,200 EGP at Jumia Cairo. Available: 18,500 EGP.",
        "رمز التحقق لعملية الشراء هو 99281. بنك الرياض ينصحك بعدم مشاركة الرمز.",
        "Your account was credited with SAR 5,400.00 Salaries payout. Total balance is 40,400 SAR."
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Parsing Toast Indicator
        AnimatedVisibility(
            visible = isParsingState,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "TFLite model analyzing SMS semantics ...",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // On-Device AI Parsing Dialog Success Summary
        parsingAlertState?.let { alert ->
            AlertDialog(
                onDismissRequest = { vm.dismissParsingAlert() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Success AI",
                            tint = if (alert.type == "deposit") CosmicPrimary else CosmicSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (alert.type == "withdraw") "تم رصد مشتريات (Parsed Transaction)" else "تم رصد إيداع (Parsed Alert)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "رابط محرك الذكاء الاصطناعي (Parsed Entity Result):", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = CosmicTextMuted)
                        
                        Text("• البنك (Entity Bank): ${alert.bankName}", fontWeight = FontWeight.Bold)
                        Text("• القيمة (Value): ${alert.amount} ${alert.currency}", fontWeight = FontWeight.Bold, color = if (alert.type == "deposit") CosmicPrimary else CosmicCrimson)
                        Text("• التاجر (Merchant ID): ${alert.merchant}")
                        Text("• المتبقي (Total Balance): ${alert.balance ?: "N/A"}")
                        Text("• نوع العملية (Sub-Type): ${alert.type.uppercase()}")
                        
                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = CosmicSurfaceElevated)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (alert.confidence > 0.6) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = "Confidence info",
                                tint = if (alert.confidence > 0.6) CosmicPrimary else CosmicSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "دقة الموديل (AI Confidence): ${(alert.confidence * 100).toInt()}%",
                                fontSize = 13.sp,
                                color = CosmicTextWhite
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { vm.dismissParsingAlert() },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary)
                    ) {
                        Text("تأكيد وحفظ (Accept & Close)", color = Color.White)
                    }
                },
                containerColor = CosmicSurface,
                titleContentColor = CosmicTextWhite,
                textContentColor = CosmicTextWhite
            )
        }

        // 1. Budget Velocity Circular Ring
        val monthlyIncome = budgetState?.monthlyIncome ?: 10000.0
        val savingsGoal = budgetState?.monthlySavingsGoal ?: 2000.0
        val dailyLimit = budgetState?.customOverriddenDailyLimit ?: 200.0
        val currentSpent = budgetState?.currentDaySpent ?: 0.0
        val remaining = maxOf(0.0, dailyLimit - currentSpent)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, CosmicSurfaceElevated.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF381E72), CosmicSurface),
                            radius = 555f
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = ArabicGlossary.get("budget_ring", isArabic),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CosmicTextWhite
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(180.dp)
                ) {
                    val progressRatio = if (dailyLimit > 0) (currentSpent / dailyLimit).toFloat() else 0f
                    val sweepAngle = minOf(1.0f, progressRatio) * 360f

                    // Ring Colors (Heuristic safety lights)
                    val ringColor = when {
                        progressRatio >= 1.0f -> CosmicCrimson
                        progressRatio > 0.70f -> CosmicSecondary
                        else -> CosmicPrimary
                    }

                    Canvas(modifier = Modifier.size(160.dp)) {
                        // Background track
                        drawCircle(
                            color = CosmicSurfaceElevated,
                            radius = size.width / 2,
                            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Active colored ring
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${remaining.toInt()} $targetCurrency",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextWhite
                        )
                        Text(
                            text = ArabicGlossary.get("remaining", isArabic),
                            fontSize = 12.sp,
                            color = CosmicTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${dailyLimit.toInt()} $targetCurrency",
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextWhite
                        )
                        Text(
                            text = "اليومي المخطط (Limit)",
                            fontSize = 12.sp,
                            color = CosmicTextMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${currentSpent.toInt()} $targetCurrency",
                            fontWeight = FontWeight.Bold,
                            color = if (currentSpent > dailyLimit) CosmicCrimson else CosmicTextWhite
                        )
                        Text(
                            text = ArabicGlossary.get("spent_today", isArabic),
                            fontSize = 12.sp,
                            color = CosmicTextMuted
                        )
                    }
                }
            }
        }

        // 2. Adaptive budget calculations alert 
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicSurfaceElevated.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored start ribbon bar (Adaptive indicator)
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(68.dp)
                        .background(CosmicPrimary)
                )
                
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Recalculation icon",
                        tint = CosmicPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = ArabicGlossary.get("limit_recalculated", isArabic),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CosmicTextWhite
                        )
                        Text(
                            text = ArabicGlossary.get("limit_recalc_desc", isArabic),
                            fontSize = 11.sp,
                            color = CosmicTextMuted
                        )
                    }
                }
            }
        }

        // 3. Burn Rate / Runway Estimator state
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicSurfaceElevated.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Peach-Coral start ribbon bar for expense runway Alert
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(80.dp)
                        .background(CosmicCrimson)
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF311111)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Burn Icon",
                            tint = CosmicCrimson,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ArabicGlossary.get("burn_rate_title", isArabic),
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextWhite
                        )
                        Text(
                            text = ArabicGlossary.get("burn_rate_desc", isArabic),
                            fontSize = 12.sp,
                            color = CosmicTextMuted
                        )
                    }
                    Text(
                        text = "$burnDays ${ArabicGlossary.get("days", isArabic)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (burnDays < 7) CosmicCrimson else CosmicPrimary
                    )
                }
            }
        }

        // 4. Interactive Bank SMS Simulator Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CosmicSurfaceElevated)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = "sms simulation",
                        tint = CosmicSecondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = ArabicGlossary.get("simulate_sms", isArabic),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = ArabicGlossary.get("sandbox_alert", isArabic),
                    fontSize = 12.sp,
                    color = CosmicTextMuted
                )

                // Quick pre-filled prompt templates
                Text(
                    text = "رسائل جاهزة للاختبار (Pre-composed Bank SMS Templates):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CosmicTextMuted
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetAlerts.forEach { alert ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CosmicSurfaceElevated)
                                .clickable { customSmsText = alert }
                                .padding(8.dp)
                        ) {
                            Text(
                                text = alert,
                                fontSize = 11.sp,
                                color = CosmicTextWhite,
                                maxLines = 1
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customSmsText,
                    onValueChange = { customSmsText = it },
                    label = { Text(ArabicGlossary.get("custom_sms_prompt", isArabic)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CosmicTextWhite,
                        unfocusedTextColor = CosmicTextWhite,
                        focusedBorderColor = CosmicSecondary,
                        unfocusedBorderColor = CosmicSurfaceElevated
                    ),
                    maxLines = 3
                )

                Button(
                    onClick = {
                        if (customSmsText.isNotBlank()) {
                            vm.parseIncomingSms(sender = "90000000", body = customSmsText)
                            customSmsText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary)
                ) {
                    Text(
                        text = ArabicGlossary.get("parse", isArabic),
                        color = CosmicTextOnPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
