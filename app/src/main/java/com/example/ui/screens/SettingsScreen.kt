package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessSettings
import com.example.ui.components.ConfirmDestructiveDialog
import com.example.util.FormatUtils
import java.io.File
import java.io.FileOutputStream

@Composable
fun SettingsScreen(
    settings: BusinessSettings,
    userEmail: String,
    isBusy: Boolean,
    onSaveSettings: (BusinessSettings) -> Unit,
    onSignOut: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current

    var businessName by rememberSaveable { mutableStateOf(settings.businessName) }
    var businessAddress by rememberSaveable { mutableStateOf(settings.businessAddress) }
    var businessPhone by rememberSaveable { mutableStateOf(settings.businessPhone) }
    var currencySymbol by rememberSaveable { mutableStateOf(settings.currencySymbol) }
    var currencyCode by rememberSaveable { mutableStateOf(settings.currencyCode) }
    var commissionEnabled by rememberSaveable { mutableStateOf(settings.commissionEnabled) }
    var commissionRateText by rememberSaveable {
        mutableStateOf(FormatUtils.formatNumber(settings.defaultCommissionRate, useBengaliNumerals = false))
    }
    var invoicePrefix by rememberSaveable { mutableStateOf(settings.invoicePrefix) }
    var nextInvoiceSeqText by rememberSaveable { mutableStateOf(settings.nextInvoiceSeq.toString()) }
    var defaultPrintFormat by rememberSaveable { mutableStateOf(settings.defaultPrintFormat) }
    var logoPreset by rememberSaveable { mutableStateOf(settings.logoPreset) }
    var dateFormatPattern by rememberSaveable { mutableStateOf(settings.dateFormatPattern) }
    var useBengaliNumerals by rememberSaveable { mutableStateOf(settings.useBengaliNumerals) }
    var languagePref by rememberSaveable { mutableStateOf(settings.languagePref) }

    var logoRefreshTick by remember { mutableIntStateOf(0) }
    var showSignOutConfirm by rememberSaveable { mutableStateOf(false) }

    // Sync local form fields when cloud settings load
    LaunchedEffect(settings) {
        businessName = settings.businessName
        businessAddress = settings.businessAddress
        businessPhone = settings.businessPhone
        currencySymbol = settings.currencySymbol
        currencyCode = settings.currencyCode
        commissionEnabled = settings.commissionEnabled
        commissionRateText = FormatUtils.formatNumber(settings.defaultCommissionRate, useBengaliNumerals = false)
        invoicePrefix = settings.invoicePrefix
        nextInvoiceSeqText = settings.nextInvoiceSeq.toString()
        defaultPrintFormat = settings.defaultPrintFormat
        logoPreset = settings.logoPreset
        dateFormatPattern = settings.dateFormatPattern
        useBengaliNumerals = settings.useBengaliNumerals
        languagePref = settings.languagePref
    }

    // Zero-permission Android Photo Picker per Google Play Policy
    val pickLogoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val raw = BitmapFactory.decodeStream(stream)
                    if (raw != null) {
                        val scaled = Bitmap.createScaledBitmap(raw, 240, 240, true)
                        val outFile = File(context.filesDir, "business_logo.png")
                        FileOutputStream(outFile).use { fos ->
                            scaled.compress(Bitmap.CompressFormat.PNG, 95, fos)
                        }
                        logoPreset = "CUSTOM"
                        logoRefreshTick += 1
                        onMessage("ব্যবসায়িক লোগো সংরক্ষিত হয়েছে — সেটিংস সেভ করুন")
                    }
                }
            } catch (e: Exception) {
                onMessage(e.localizedMessage ?: "লোগো লোড করতে সমস্যা হয়েছে")
            }
        }
    }

    val customLogoBitmap = remember(logoRefreshTick, logoPreset) {
        val file = File(context.filesDir, "business_logo.png")
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Business Info & Logo
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "১. ব্যবসা প্রতিষ্ঠানের তথ্য ও লোগো",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("প্রতিষ্ঠানের নাম *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_business_name_input")
                    )

                    OutlinedTextField(
                        value = businessAddress,
                        onValueChange = { businessAddress = it },
                        label = { Text("প্রতিষ্ঠানের ঠিকানা") },
                        placeholder = { Text("রিয়াজউদ্দিন বাজার, চট্টগ্রাম") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = businessPhone,
                        onValueChange = { businessPhone = it },
                        label = { Text("ব্যবসায়িক মোবাইল নম্বর") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "চালানের লোগো / প্রতীক:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "BASKET" to "🧺 ঝুড়ি",
                            "LEAF" to "🌿 সবুজ পাতা",
                            "SCALE" to "⚖️ পাল্লা",
                            "CUSTOM" to "🖼️ কাস্টম লোগো"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = logoPreset == key,
                                onClick = { logoPreset = key },
                                label = { Text(label) }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (customLogoBitmap != null) {
                            Image(
                                bitmap = customLogoBitmap.asImageBitmap(),
                                contentDescription = "কাস্টম লোগো",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                pickLogoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ছবি থেকে লোগো নিন")
                        }
                    }
                }
            }
        }

        // 2. Commission & Invoice Sequence Configuration
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "২. কমিশন ও চালান নম্বর সেটিংস",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ডিফল্ট কমিশন চালু রাখুন", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "নতুন চালান তৈরির সময় স্বয়ংক্রিয়ভাবে কমিশন যুক্ত থাকবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = commissionEnabled,
                            onCheckedChange = { commissionEnabled = it },
                            modifier = Modifier.testTag("settings_commission_switch")
                        )
                    }

                    OutlinedTextField(
                        value = commissionRateText,
                        onValueChange = { commissionRateText = it },
                        label = { Text("ডিফল্ট কমিশন হার (%) — যেমন ৭%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_commission_rate_input")
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "সুরক্ষা নোট: এখানে কমিশন হার পরিবর্তন করলে পূর্বের সংরক্ষিত চালানগুলোর হিসাব অপরিবর্তিত থাকবে; শুধুমাত্র নতুন চালানে নতুন হার প্রযোজ্য হবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = invoicePrefix,
                            onValueChange = { invoicePrefix = it },
                            label = { Text("চালান প্রিফিক্স") },
                            placeholder = { Text("KVT") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = nextInvoiceSeqText,
                            onValueChange = { nextInvoiceSeqText = it },
                            label = { Text("পরবর্তী ক্রমিক নং") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. Print Format, Currency, Date & Numeral Preferences
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "৩. প্রিন্ট ফরম্যাট, মুদ্রা ও সংখ্যা প্রদর্শন",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text("ডিফল্ট প্রিন্ট লেআউট:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = defaultPrintFormat == "A4",
                            onClick = { defaultPrintFormat = "A4" },
                            label = { Text("Standard A4 চালান") }
                        )
                        FilterChip(
                            selected = defaultPrintFormat == "THERMAL_80MM",
                            onClick = { defaultPrintFormat = "THERMAL_80MM" },
                            label = { Text("80mm থার্মাল প্রিন্ট") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = currencySymbol,
                            onValueChange = { currencySymbol = it },
                            label = { Text("মুদ্রা চিহ্ন") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = currencyCode,
                            onValueChange = { currencyCode = it },
                            label = { Text("মুদ্রা কোড") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("তারিখের ফরম্যাট:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("dd/MM/yyyy", "dd MMM yyyy", "yyyy-MM-dd").forEach { pat ->
                            FilterChip(
                                selected = dateFormatPattern == pat,
                                onClick = { dateFormatPattern = pat },
                                label = { Text(pat) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("বাংলা সংখ্যা প্রদর্শন (০, ১, ২...)", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "বন্ধ করলে ইংরেজি সংখ্যা (0, 1, 2...) দেখাবে (ইনপুটে উভয়ই কাজ করবে)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useBengaliNumerals,
                            onCheckedChange = { useBengaliNumerals = it }
                        )
                    }

                    Text("ভাষা পছন্দ (Language):", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = languagePref == "BN",
                            onClick = { languagePref = "BN" },
                            label = { Text("বাংলা (প্রধান)") }
                        )
                        FilterChip(
                            selected = languagePref == "BN_EN",
                            onClick = { languagePref = "BN_EN" },
                            label = { Text("বাংলা + English") }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val parsedComm = (FormatUtils.parseFlexibleDouble(commissionRateText) ?: 7.0)
                                .coerceIn(0.0, 100.0)
                            val parsedSeq = (FormatUtils.parseFlexibleDouble(nextInvoiceSeqText)?.toLong() ?: 1L)
                                .coerceAtLeast(1L)
                            val updated = settings.copy(
                                businessName = businessName.trim().ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" },
                                businessAddress = businessAddress.trim(),
                                businessPhone = businessPhone.trim(),
                                currencySymbol = currencySymbol.trim().ifBlank { "৳" },
                                currencyCode = currencyCode.trim().ifBlank { "BDT" },
                                defaultCommissionRate = parsedComm,
                                commissionEnabled = commissionEnabled,
                                invoicePrefix = invoicePrefix.trim().ifBlank { "KVT" },
                                nextInvoiceSeq = parsedSeq,
                                defaultPrintFormat = defaultPrintFormat,
                                logoPreset = logoPreset,
                                dateFormatPattern = dateFormatPattern,
                                useBengaliNumerals = useBengaliNumerals,
                                languagePref = languagePref
                            )
                            onSaveSettings(updated)
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_settings_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("সেটিংস সংরক্ষণ করুন", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // 4. Account & Sign Out
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("ক্লাউড অ্যাকাউন্ট সংযুক্ত", fontWeight = FontWeight.Bold)
                            if (userEmail.isNotBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showSignOutConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("সাইন আউট (Sign Out)")
                    }
                }
            }
        }
    }

    if (showSignOutConfirm) {
        ConfirmDestructiveDialog(
            title = "সাইন আউট করবেন?",
            message = "আপনার সকল ডাটা ক্লাউডে নিরাপদে সংরক্ষিত আছে। পরবর্তীতে একই Google অ্যাকাউন্ট দিয়ে লগইন করলে সব তথ্য ফিরে পাবেন।",
            confirmText = "সাইন আউট",
            onConfirm = {
                showSignOutConfirm = false
                onSignOut()
            },
            onDismiss = { showSignOutConfirm = false }
        )
    }
}
