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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountEntity
import com.example.data.model.CardEntity
import com.example.ui.viewmodel.MaalytiViewModel
import com.example.ui.theme.*

@Composable
fun CardsAlerts(
    vm: MaalytiViewModel,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val accounts by vm.accounts.collectAsState()
    val cards by vm.cards.collectAsState()
    val netWorthVal by vm.netWorth.collectAsState()
    val baseDisplayCurrency by vm.selectedTargetCurrency.collectAsState()
    val isPremiumLocked by vm.isPremiumLocked.collectAsState()

    var showNewAccountPrompt by remember { mutableStateOf(false) }
    var showNewCardPrompt by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // 1. Consolidated Net Worth section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicSurfaceElevated.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(CosmicSurface, CosmicSurfaceElevated.copy(alpha = 0.4f))
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = ArabicGlossary.get("net_worth", isArabic),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CosmicTextMuted
                )

                Text(
                    text = "${String.format("%.2f", netWorthVal)} $baseDisplayCurrency",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicPrimary
                )

                // Select Display Currency Currency Accords
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val supportedCurrencies = listOf("SAR", "EGP", "AED", "USD")
                    supportedCurrencies.forEach { curr ->
                        val active = baseDisplayCurrency == curr
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) CosmicPrimary else CosmicSurface)
                                .clickable { vm.changeTargetCurrency(curr) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = curr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) CosmicTextOnPrimary else CosmicTextWhite
                            )
                        }
                    }
                }
            }
        }

        // 2. Play Multi-account list with Roles panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicSurfaceElevated.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "accounts",
                        tint = CosmicPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ArabicGlossary.get("multi_account", isArabic),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CosmicTextWhite,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { showNewAccountPrompt = true },
                        modifier = Modifier.size(28.dp).testTag("add_account_btn")
                    ) {
                        Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Add Card", tint = CosmicPrimary)
                    }
                }

                Divider(color = CosmicSurfaceElevated)

                accounts.forEach { acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = acc.accountName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            when (acc.role) {
                                                "primary" -> CosmicCrimson.copy(alpha = 0.2f)
                                                "monitor" -> CosmicSecondary.copy(alpha = 0.2f)
                                                else -> CosmicPrimary.copy(alpha = 0.2f)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = acc.role.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (acc.role) {
                                            "primary" -> CosmicCrimson
                                            "monitor" -> CosmicSecondary
                                            else -> CosmicPrimary
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = acc.bankName, fontSize = 11.sp, color = CosmicTextMuted)
                            }
                        }
                        Text(
                            text = "${acc.balance} ${acc.currency}",
                            fontWeight = FontWeight.Bold,
                            color = CosmicTextWhite,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // 3. Credit Cards Alerts countdown & cashback efficiency 
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicSurfaceElevated.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = "cards section",
                        tint = CosmicSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ArabicGlossary.get("card_alerts", isArabic),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CosmicTextWhite,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { showNewCardPrompt = true },
                        modifier = Modifier.size(28.dp).testTag("add_card_btn")
                    ) {
                        Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Add account", tint = CosmicSecondary)
                    }
                }

                Divider(color = CosmicSurfaceElevated)

                cards.forEach { card ->
                    val diff = card.dueDateMillis - System.currentTimeMillis()
                    val daysRemaining = maxOf(0, (diff / (24 * 60 * 60 * 1000L)).toInt())

                    // Calculated reward analytics: cash back yields vs standard fees
                    val yearlyEstimatedCashbackSum = card.outstandingAmount * (card.cashbackPercentage / 100.0) * 12
                    val thresholdAlertActive = yearlyEstimatedCashbackSum < 200.0 && card.annualFeeFlag

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = card.cardName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            
                            // Days countdown badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (daysRemaining < 8) CosmicCrimson.copy(alpha = 0.2f) else CosmicPrimary.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "يستحق السداد خلال $daysRemaining أيام",
                                    fontSize = 10.sp,
                                    color = if (daysRemaining < 8) CosmicCrimson else CosmicPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Outstanding stats and comparison warnings
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "المستحق: ${card.outstandingAmount} $baseDisplayCurrency / الحد: ${card.limitAmount}", fontSize = 11.sp, color = CosmicTextMuted)
                            Text(text = "عائد Cashback: ${card.cashbackPercentage}%", fontSize = 11.sp, color = CosmicPrimary)
                        }

                        if (thresholdAlertActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CosmicCrimson.copy(alpha = 0.15f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "🚨 تنبيه: عائد الكاش باك السنوي المتوقع (${yearlyEstimatedCashbackSum.toInt()} ر.س) يقل عن رسوم الخدمة السنوية للبطاقة. بطاقة خاسرة نسبياً!",
                                    fontSize = 10.sp,
                                    color = CosmicCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. One-time Premium membership widget (19 SAR / 49 EGP)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, CosmicAccentGold)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "Premium icon",
                        tint = CosmicAccentGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ArabicGlossary.get("premium_upgrade", isArabic),
                        fontWeight = FontWeight.Bold,
                        color = CosmicTextWhite,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = ArabicGlossary.get("premium_upgrade_desc", isArabic),
                    fontSize = 12.sp,
                    color = CosmicTextMuted,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isPremiumLocked) {
                    Button(
                        onClick = { vm.processPremiumPurchase() },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicAccentGold),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = ArabicGlossary.get("unlock", isArabic),
                            color = CosmicTextOnPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CosmicPrimary.copy(alpha = 0.2f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Verified, contentDescription = "verified premium", tint = CosmicPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = ArabicGlossary.get("unlocked_msg", isArabic),
                            fontWeight = FontWeight.Bold,
                            color = CosmicPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // Add Account Dialog Modal
    if (showNewAccountPrompt) {
        AlertDialog(
            onDismissRequest = { showNewAccountPrompt = false },
            title = { Text("إضافة حساب مصرفي جديد", fontWeight = FontWeight.Bold) },
            text = {
                var name by remember { mutableStateOf("حساب جاري مالي") }
                var bank by remember { mutableStateOf("Al Rajhi") }
                var balance by remember { mutableStateOf("5000") }
                var role by remember { mutableStateOf("primary") } // primary, monitor, transfer

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الحساب") })
                    OutlinedTextField(value = bank, onValueChange = { bank = it }, label = { Text("البنك التابع") })
                    OutlinedTextField(value = balance, onValueChange = { balance = it }, label = { Text("الرصيد الافتتاحي") })
                    Text("دور الحساب الاستراتيجي (Strategic Role):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CosmicTextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("primary", "monitor", "transfer").forEach { r ->
                            Button(
                                onClick = { role = r },
                                colors = ButtonDefaults.buttonColors(containerColor = if (role == r) CosmicPrimary else CosmicSurfaceElevated),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(r.uppercase(), fontSize = 9.sp, color = Color.White)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val doubleBal = balance.toDoubleOrNull() ?: 0.0
                            vm.triggerNewAccount(name, bank, doubleBal, "SAR", role)
                            showNewAccountPrompt = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        Text("حفظ الحساب", color = Color.White)
                    }
                }
            },
            confirmButton = {},
            containerColor = CosmicSurface,
            titleContentColor = CosmicTextWhite
        )
    }

    // Add Card Dialog Modal
    if (showNewCardPrompt) {
        AlertDialog(
            onDismissRequest = { showNewCardPrompt = false },
            title = { Text("إدراج بطاقة ائتمانية جديدة", fontWeight = FontWeight.Bold) },
            text = {
                var name by remember { mutableStateOf("البطاقة البلاتينية") }
                var limit by remember { mutableStateOf("10000") }
                var outstanding by remember { mutableStateOf("1500") }
                var dueLimitDays by remember { mutableStateOf("10") }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم البطاقة") })
                    OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("الحد الائتماني") })
                    OutlinedTextField(value = outstanding, onValueChange = { outstanding = it }, label = { Text("المبلغ المستحق حالياً") })
                    OutlinedTextField(value = dueLimitDays, onValueChange = { dueLimitDays = it }, label = { Text("أيام متبقية للاستحقاق") })
                    
                    Button(
                        onClick = {
                            val doubleLimit = limit.toDoubleOrNull() ?: 0.0
                            val doubleOutstanding = outstanding.toDoubleOrNull() ?: 0.0
                            val intDays = dueLimitDays.toIntOrNull() ?: 10
                            vm.configureCard(name, doubleLimit, doubleOutstanding, intDays, true)
                            showNewCardPrompt = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSecondary),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        Text("حفظ البطاقة", color = CosmicTextOnPrimary)
                    }
                }
            },
            confirmButton = {},
            containerColor = CosmicSurface,
            titleContentColor = CosmicTextWhite
        )
    }
}
