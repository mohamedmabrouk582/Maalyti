package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionEntity
import com.example.ui.viewmodel.MaalytiViewModel
import com.example.ui.theme.*

@Composable
fun TransactionsHistory(
    vm: MaalytiViewModel,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val query by vm.searchQuery.collectAsState()
    val activeFilter by vm.filterType.collectAsState()
    val transactionsFiltered by vm.filteredTransactions.collectAsState()
    val baseDisplayCurrency by vm.selectedTargetCurrency.collectAsState()

    var showManualPrompt by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Upper row - Filter layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "transactions section icon",
                tint = CosmicPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ArabicGlossary.get("transactions", isArabic),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CosmicTextWhite,
                modifier = Modifier.weight(1f)
            )

            // Manual Adding Option
            Button(
                onClick = { showManualPrompt = true },
                colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "add manually",
                    tint = CosmicTextOnPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة يدوية", fontSize = 12.sp, color = CosmicTextOnPrimary)
            }
        }

        // Search Input field
        OutlinedTextField(
            value = query,
            onValueChange = { vm.updateSearchQuery(it) },
            label = { Text(ArabicGlossary.get("search", isArabic), color = CosmicTextMuted) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CosmicTextWhite,
                unfocusedTextColor = CosmicTextWhite,
                focusedBorderColor = CosmicPrimary,
                unfocusedBorderColor = CosmicSurfaceElevated
            ),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "search",
                    tint = CosmicTextMuted
                )
            }
        )

        // Filter Pills mapping
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf(
                "all" to ArabicGlossary.get("all", isArabic),
                "withdraw" to ArabicGlossary.get("withdraw", isArabic),
                "deposit" to ArabicGlossary.get("deposit", isArabic),
                "otp" to ArabicGlossary.get("otp", isArabic)
            )

            filters.forEach { (key, label) ->
                val selected = activeFilter == key
                FilterChip(
                    selected = selected,
                    onClick = { vm.updateFilterType(key) },
                    label = { Text(label, fontSize = 11.sp, color = if (selected) CosmicTextOnPrimary else CosmicTextWhite) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CosmicPrimary,
                        containerColor = CosmicSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Transaction list or empty layout
        if (transactionsFiltered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "empty transactions",
                        tint = CosmicTextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "لا توجد عمليات تطابق البحث الحسابي",
                        fontWeight = FontWeight.Bold,
                        color = CosmicTextWhite,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "جرب تغيير الفلاتر أو محاكاة استقبال رسالة بنك جديدة.",
                        fontSize = 11.sp,
                        color = CosmicTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactionsFiltered, key = { it.id }) { item ->
                    TransactionRow(item = item, vm = vm, baseCurrency = baseDisplayCurrency, isArabic = isArabic)
                }
            }
        }

        // Slide Dialog Manual Transaction adding 
        if (showManualPrompt) {
            ManualTxDialog(
                isArabic = isArabic,
                onDismiss = { showManualPrompt = false },
                onAdd = { b, amt, curr, m, ty, last4 ->
                    vm.triggerManualItem(b, amt, curr, m, ty, last4)
                    showManualPrompt = false
                }
            )
        }
    }
}

@Composable
fun TransactionRow(
    item: TransactionEntity,
    vm: MaalytiViewModel,
    baseCurrency: String,
    isArabic: Boolean
) {
    // Dynamic item colors per transaction type constraints
    val textAndBgColor = when (item.type) {
        "withdraw" -> Pair(CosmicCrimson, "سحب ومشتريات (Spend)")
        "deposit" -> Pair(CosmicDepositGreen, "إيداع راتب (Credit)")
        "otp" -> Pair(CosmicSecondary, "رمز حماية (Security)")
        else -> Pair(CosmicTextMuted, "أخرى")
    }

    val iconInfo = when (item.type) {
        "withdraw" -> Triple(Icons.Default.ShoppingCart, Color(0xFF311111), Color(0xFFFFB4AB))
        "deposit" -> Triple(Icons.Default.Payments, Color(0xFF0F3721), Color(0xFFB4EBB2))
        "otp" -> Triple(Icons.Default.Lock, Color(0xFF49454F), Color(0xFFCAC4D0))
        else -> Triple(Icons.Default.ReceiptLong, Color(0xFF49454F), Color(0xFFE6E1E5))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Immersive Custom Icon container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconInfo.second),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconInfo.first,
                    contentDescription = "transaction type icon",
                    tint = iconInfo.third,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.bankName,
                        fontWeight = FontWeight.Bold,
                        color = CosmicTextWhite,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${textAndBgColor.second})",
                        fontSize = 10.sp,
                        color = CosmicTextMuted
                    )
                }
                
                Text(
                    text = item.merchant,
                    fontWeight = FontWeight.Medium,
                    color = CosmicTextWhite,
                    fontSize = 12.sp
                )

                Text(
                    text = item.dateString,
                    fontSize = 10.sp,
                    color = CosmicTextMuted
                )

                // High/Low confidence tags or fallback indicators
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.wasFallbackUsed) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1B3A1E))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = if (isArabic) "ذكاء اصطناعي: جيميناي ✨" else "AI Model: Gemini 3.5",
                                fontSize = 9.sp,
                                color = CosmicPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(CosmicPrimary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = "On-Device NER ${(item.confidence * 100).toInt()}%",
                                fontSize = 9.sp,
                                color = CosmicPrimary
                            )
                        }
                    }

                    if (item.cardLast4 != null) {
                        Box(
                            modifier = Modifier
                                .background(CosmicSurfaceElevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = "💳 *${item.cardLast4}",
                                fontSize = 9.sp,
                                color = CosmicTextWhite
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Values with signs
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                val operatorSign = if (item.type == "deposit") "+" else if (item.type == "withdraw") "-" else ""
                val amountColor = textAndBgColor.first

                Text(
                    text = "$operatorSign ${item.amount} ${item.currency}",
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                // Delete transaction activator
                IconButton(
                    onClick = { vm.removeTransaction(item.id) },
                    modifier = Modifier.testTag("delete_tx_btn").size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "dismiss transaction alert",
                        tint = CosmicCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ManualTxDialog(
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, Double, String, String, String, String?) -> Unit
) {
    var bankInput by remember { mutableStateOf("SNB") }
    var amountInput by remember { mutableStateOf("") }
    var currencyInput by remember { mutableStateOf("SAR") }
    var merchantInput by remember { mutableStateOf("Manual Shop") }
    var typeSelection by remember { mutableStateOf("withdraw") }
    var cardInput by remember { mutableStateOf("1234") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إدراج عملية مالية يدوياً", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Bank Name
                OutlinedTextField(
                    value = bankInput,
                    onValueChange = { bankInput = it },
                    label = { Text("اسم البنك (Bank Entity)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = CosmicTextWhite, unfocusedTextColor = CosmicTextWhite)
                )

                // Cost / value
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("المبلغ (Amount)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = CosmicTextWhite, unfocusedTextColor = CosmicTextWhite)
                )

                // Currency select
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val listCurrs = listOf("SAR", "EGP", "AED", "USD")
                    listCurrs.forEach { curr ->
                        Button(
                            onClick = { currencyInput = curr },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currencyInput == curr) CosmicPrimary else CosmicSurfaceElevated
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(curr, color = Color.White, fontSize = 10.sp)
                        }
                    }
                }

                // Merchant
                OutlinedTextField(
                    value = merchantInput,
                    onValueChange = { merchantInput = it },
                    label = { Text("الجهة المستفيدة (Merchant)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = CosmicTextWhite, unfocusedTextColor = CosmicTextWhite)
                )

                // Last 4 Digits
                OutlinedTextField(
                    value = cardInput,
                    onValueChange = { cardInput = it },
                    label = { Text("آخر 4 أرقام من البطاقة (Card digits)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = CosmicTextWhite, unfocusedTextColor = CosmicTextWhite)
                )

                // Row Category type
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { typeSelection = "withdraw" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (typeSelection == "withdraw") CosmicCrimson else CosmicSurfaceElevated
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("سحب/مصروف", color = Color.White, fontSize = 9.sp)
                    }
                    Button(
                        onClick = { typeSelection = "deposit" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (typeSelection == "deposit") CosmicPrimary else CosmicSurfaceElevated
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إيداع وراتب", color = Color.White, fontSize = 9.sp)
                    }
                    Button(
                        onClick = { typeSelection = "otp" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (typeSelection == "otp") CosmicGrayOtp else CosmicSurfaceElevated
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("رمز تفعيل", color = Color.White, fontSize = 9.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val doubleAmt = amountInput.toDoubleOrNull() ?: 0.0
                    onAdd(bankInput, doubleAmt, currencyInput, merchantInput, typeSelection, cardInput.ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimary)
            ) {
                Text("إضافة", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = CosmicTextWhite)
            }
        },
        containerColor = CosmicSurface,
        titleContentColor = CosmicTextWhite,
        textContentColor = CosmicTextWhite
    )
}
