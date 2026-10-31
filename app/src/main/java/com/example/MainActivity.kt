package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.Customer
import com.example.data.model.Expense
import com.example.data.model.Invoice
import com.example.data.model.Product
import com.example.data.repository.WholesaleRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.performSignOut
import com.example.ui.screens.CreateInvoiceScreen
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomerListScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpenseManagementScreen
import com.example.ui.screens.InvoiceDetailScreen
import com.example.ui.screens.InvoiceHistoryScreen
import com.example.ui.screens.ProductManagementScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.ReportTab
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.WholesaleViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                KhandakerVegAppRoot()
            }
        }
    }
}

private fun observeCurrentFirebaseUser(): Flow<FirebaseUser?> = callbackFlow {
    val auth = Firebase.auth
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    auth.addAuthStateListener(listener)
    awaitClose { auth.removeAuthStateListener(listener) }
}

@Composable
fun KhandakerVegAppRoot() {
    val authFlow = remember { observeCurrentFirebaseUser() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                // AuthStateListener reacts automatically when sign-in completes
            }
        )
    } else {
        val context = LocalContext.current.applicationContext
        val viewModel: WholesaleViewModel = viewModel(
            key = user.uid,
            factory = viewModelFactory {
                initializer {
                    WholesaleViewModel(
                        repository = WholesaleRepository(context),
                        currentUserId = user.uid,
                        currentUserEmail = user.email ?: ""
                    )
                }
            }
        )
        AuthenticatedWholesaleShell(viewModel = viewModel)
    }
}

private data class NavMenuItem(
    val label: String,
    val icon: ImageVector,
    val screen: AppScreen,
    val tag: String,
    val matches: (AppScreen) -> Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedWholesaleShell(viewModel: WholesaleViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val canNavigateBack by viewModel.canNavigateBack.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val productsUi by viewModel.productsState.collectAsStateWithLifecycle()
    val customersUi by viewModel.customersState.collectAsStateWithLifecycle()
    val invoicesUi by viewModel.invoicesState.collectAsStateWithLifecycle()
    val invoiceItems by viewModel.invoiceItemsState.collectAsStateWithLifecycle()
    val expensesUi by viewModel.expensesState.collectAsStateWithLifecycle()
    val invoiceForm by viewModel.invoiceForm.collectAsStateWithLifecycle()

    val products: List<Product> = (productsUi as? UiState.Success)?.data ?: emptyList()
    val customers: List<Customer> = (customersUi as? UiState.Success)?.data ?: emptyList()
    val invoices: List<Invoice> = (invoicesUi as? UiState.Success)?.data ?: emptyList()
    val expenses: List<Expense> = (expensesUi as? UiState.Success)?.data ?: emptyList()

    // System back button pops custom ViewModel screen stack cleanly
    BackHandler(enabled = canNavigateBack) {
        viewModel.navigateBack()
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val drawerItems = remember {
        listOf(
            NavMenuItem(
                label = "ড্যাশবোর্ড",
                icon = Icons.Default.Dashboard,
                screen = AppScreen.Dashboard,
                tag = "nav_dashboard",
                matches = { it is AppScreen.Dashboard }
            ),
            NavMenuItem(
                label = "নতুন চালান তৈরি",
                icon = Icons.Default.AddCircle,
                screen = AppScreen.CreateOrEditInvoice,
                tag = "nav_create_invoice",
                matches = { it is AppScreen.CreateOrEditInvoice }
            ),
            NavMenuItem(
                label = "চালান ইতিহাস",
                icon = Icons.Default.History,
                screen = AppScreen.InvoiceHistory(),
                tag = "nav_invoice_history",
                matches = { it is AppScreen.InvoiceHistory || it is AppScreen.InvoiceDetail }
            ),
            NavMenuItem(
                label = "কাস্টমার তালিকা",
                icon = Icons.Default.People,
                screen = AppScreen.Customers,
                tag = "nav_customers",
                matches = { it is AppScreen.Customers || it is AppScreen.CustomerDetail }
            ),
            NavMenuItem(
                label = "পণ্য তালিকা",
                icon = Icons.Default.Inventory2,
                screen = AppScreen.Products,
                tag = "nav_products",
                matches = { it is AppScreen.Products }
            ),
            NavMenuItem(
                label = "খরচ যোগ করুন",
                icon = Icons.Default.MoneyOff,
                screen = AppScreen.Expenses,
                tag = "nav_expenses",
                matches = { it is AppScreen.Expenses }
            ),
            NavMenuItem(
                label = "ব্যবসা রিপোর্ট",
                icon = Icons.Default.Assessment,
                screen = AppScreen.Reports(ReportTab.DAILY),
                tag = "nav_reports",
                matches = { it is AppScreen.Reports }
            ),
            NavMenuItem(
                label = "সেটিংস",
                icon = Icons.Default.Settings,
                screen = AppScreen.Settings,
                tag = "nav_settings",
                matches = { it is AppScreen.Settings }
            )
        )
    }

    val bottomBarItems = remember(drawerItems) {
        listOf(
            drawerItems[0], // ড্যাশবোর্ড
            drawerItems[1], // নতুন চালান
            drawerItems[2], // চালান ইতিহাস
            drawerItems[6], // রিপোর্ট
            drawerItems[7]  // সেটিংস
        )
    }

    val topBarTitle = when (val scr = currentScreen) {
        is AppScreen.Dashboard -> settings.businessName.ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" }
        is AppScreen.CreateOrEditInvoice -> if (invoiceForm.editingInvoice == null) "নতুন চালান তৈরি" else "চালান সম্পাদনা"
        is AppScreen.InvoiceHistory -> "চালান ইতিহাস"
        is AppScreen.InvoiceDetail -> "চালান প্রিভিউ ও প্রিন্ট"
        is AppScreen.Customers -> "কাস্টমার ব্যবস্থাপনা"
        is AppScreen.CustomerDetail -> "কাস্টমার প্রোফাইল ও ইতিহাস"
        is AppScreen.Products -> "সবজি ও পণ্য ব্যবস্থাপনা"
        is AppScreen.Expenses -> "ম্যানুয়াল ব্যবসায়িক খরচ"
        is AppScreen.Reports -> when (scr.initialTab) {
            ReportTab.DAILY -> "ব্যবসা রিপোর্ট"
            ReportTab.MONTHLY -> "মাসিক ও ব্যবসা রিপোর্ট"
            ReportTab.CUSTOM -> "কাস্টম রিপোর্ট"
        }
        is AppScreen.Settings -> "সেটিংস"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        val mainScaffold: @Composable () -> Unit = {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    text = topBarTitle,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                if (canNavigateBack) {
                                    IconButton(
                                        onClick = { viewModel.navigateBack() },
                                        modifier = Modifier.testTag("top_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "পিছনে যান"
                                        )
                                    }
                                } else if (!isExpandedScreen) {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.testTag("top_menu_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "মেনু খুলুন"
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (currentScreen !is AppScreen.CreateOrEditInvoice) {
                                    IconButton(
                                        onClick = { viewModel.startNewInvoice() },
                                        modifier = Modifier.testTag("top_quick_new_invoice")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                            contentDescription = "নতুন চালান তৈরি"
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )
                        if (isBusy) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!isExpandedScreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            bottomBarItems.forEach { item ->
                                val selected = item.matches(currentScreen)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (item.screen is AppScreen.CreateOrEditInvoice) {
                                            viewModel.startNewInvoice()
                                        } else {
                                            viewModel.navigateToRoot(item.screen)
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = {
                                        Text(
                                            text = if (item.screen is AppScreen.CreateOrEditInvoice) "নতুন চালান" else item.label.split(" ").first(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("bottom_${item.tag}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            drawerItems.forEach { item ->
                                NavigationRailItem(
                                    selected = item.matches(currentScreen),
                                    onClick = {
                                        if (item.screen is AppScreen.CreateOrEditInvoice) {
                                            viewModel.startNewInvoice()
                                        } else {
                                            viewModel.navigateToRoot(item.screen)
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = {
                                        Text(
                                            text = item.label.split(" ").first(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("rail_${item.tag}")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = if (isExpandedScreen) 980.dp else 640.dp)
                        ) {
                            when (val scr = currentScreen) {
                                is AppScreen.Dashboard -> {
                                    DashboardScreen(
                                        settings = settings,
                                        invoices = invoices,
                                        expenses = expenses,
                                        customers = customers,
                                        products = products,
                                        onStartNewInvoice = { viewModel.startNewInvoice() },
                                        onNavigate = { target -> viewModel.pushScreen(target) },
                                        onOpenInvoiceDetail = { invId ->
                                            viewModel.pushScreen(AppScreen.InvoiceDetail(invId))
                                        }
                                    )
                                }

                                is AppScreen.CreateOrEditInvoice -> {
                                    val activeProducts = remember(products) { products.filter { it.isActive } }
                                    val activeCustomers = remember(customers) { customers.filter { it.isActive } }
                                    CreateInvoiceScreen(
                                        form = invoiceForm,
                                        settings = settings,
                                        activeProducts = activeProducts,
                                        activeCustomers = activeCustomers,
                                        isBusy = isBusy,
                                        onUpdateHeader = { invNo, selCust, clearCust, cName, cPhone, addr, dt, status, notes ->
                                            viewModel.updateInvoiceHeader(
                                                invoiceNumber = invNo,
                                                selectedCustomer = selCust,
                                                clearSavedCustomer = clearCust,
                                                customerName = cName,
                                                customerPhone = cPhone,
                                                deliveryAddress = addr,
                                                invoiceDateMillis = dt,
                                                status = status,
                                                notes = notes
                                            )
                                        },
                                        onUpdateFinancials = { commEnabled, commRateTxt, addTxt, discTxt ->
                                            viewModel.updateInvoiceFinancials(
                                                commissionEnabled = commEnabled,
                                                commissionRateText = commRateTxt,
                                                additionalChargesText = addTxt,
                                                discountText = discTxt
                                            )
                                        },
                                        onAddLine = { pId, pName, qty, unit, rate ->
                                            viewModel.addDraftProductLine(pId, pName, qty, unit, rate)
                                        },
                                        onUpdateLine = { lineId, pName, qty, unit, rate ->
                                            viewModel.updateDraftProductLine(lineId, pName, qty, unit, rate)
                                        },
                                        onRemoveLine = { lineId ->
                                            viewModel.removeDraftProductLine(lineId)
                                        },
                                        onQuickSaveCustomer = { draftCust, onSaved ->
                                            viewModel.saveCustomer(draftCust) { savedId ->
                                                onSaved(draftCust.copy(id = savedId))
                                            }
                                        },
                                        onSaveInvoice = { openDetail ->
                                            viewModel.saveInvoice(openDetailAfterSave = openDetail)
                                        }
                                    )
                                }

                                is AppScreen.InvoiceHistory -> {
                                    InvoiceHistoryScreen(
                                        invoices = invoices,
                                        settings = settings,
                                        initialFilterCustomerId = scr.filterCustomerId,
                                        onOpenInvoice = { invId ->
                                            viewModel.pushScreen(AppScreen.InvoiceDetail(invId))
                                        },
                                        onCreateNewInvoice = { viewModel.startNewInvoice() }
                                    )
                                }

                                is AppScreen.InvoiceDetail -> {
                                    val targetInvoice = remember(invoices, scr.invoiceId) {
                                        invoices.find { it.id == scr.invoiceId }
                                    }
                                    val targetItems = remember(invoiceItems, scr.invoiceId) {
                                        invoiceItems
                                            .filter { it.invoiceId == scr.invoiceId }
                                            .sortedBy { it.sortOrder }
                                    }
                                    InvoiceDetailScreen(
                                        invoice = targetInvoice,
                                        items = targetItems,
                                        settings = settings,
                                        onEditInvoice = { inv -> viewModel.startEditInvoice(inv) },
                                        onDeleteInvoice = { invId ->
                                            viewModel.deleteInvoice(invId) {
                                                viewModel.navigateBack()
                                            }
                                        },
                                        onMessage = { msg -> viewModel.postMessage(msg) }
                                    )
                                }

                                is AppScreen.Customers -> {
                                    CustomerListScreen(
                                        customers = customers,
                                        invoices = invoices,
                                        settings = settings,
                                        isBusy = isBusy,
                                        onOpenCustomerDetail = { custId ->
                                            viewModel.pushScreen(AppScreen.CustomerDetail(custId))
                                        },
                                        onStartInvoiceForCustomer = { cust ->
                                            viewModel.startNewInvoice(cust)
                                        },
                                        onSaveCustomer = { cust, onDone ->
                                            viewModel.saveCustomer(cust) { onDone() }
                                        },
                                        onToggleActive = { cust ->
                                            viewModel.toggleCustomerActive(cust)
                                        }
                                    )
                                }

                                is AppScreen.CustomerDetail -> {
                                    val targetCustomer = remember(customers, scr.customerId) {
                                        customers.find { it.id == scr.customerId }
                                    }
                                    CustomerDetailScreen(
                                        customer = targetCustomer,
                                        allInvoices = invoices,
                                        allInvoiceItems = invoiceItems,
                                        settings = settings,
                                        isBusy = isBusy,
                                        onStartInvoiceForCustomer = { cust ->
                                            viewModel.startNewInvoice(cust)
                                        },
                                        onOpenInvoiceDetail = { invId ->
                                            viewModel.pushScreen(AppScreen.InvoiceDetail(invId))
                                        },
                                        onSaveCustomer = { cust, onDone ->
                                            viewModel.saveCustomer(cust) { onDone() }
                                        },
                                        onMessage = { msg -> viewModel.postMessage(msg) }
                                    )
                                }

                                is AppScreen.Products -> {
                                    ProductManagementScreen(
                                        products = products,
                                        settings = settings,
                                        isBusy = isBusy,
                                        onSaveProduct = { prod, onDone ->
                                            viewModel.saveProduct(prod, onDone)
                                        },
                                        onToggleActive = { prod ->
                                            viewModel.toggleProductActive(prod)
                                        },
                                        onSeedStarterProducts = {
                                            viewModel.seedStarterVegetables()
                                        }
                                    )
                                }

                                is AppScreen.Expenses -> {
                                    ExpenseManagementScreen(
                                        expenses = expenses,
                                        recentInvoices = invoices,
                                        settings = settings,
                                        isBusy = isBusy,
                                        onSaveExpense = { exp, onDone ->
                                            viewModel.saveExpense(exp, onDone)
                                        },
                                        onDeleteExpense = { expId ->
                                            viewModel.deleteExpense(expId)
                                        }
                                    )
                                }

                                is AppScreen.Reports -> {
                                    ReportsScreen(
                                        initialTab = scr.initialTab,
                                        invoices = invoices,
                                        invoiceItems = invoiceItems,
                                        expenses = expenses,
                                        customers = customers,
                                        products = products,
                                        settings = settings,
                                        onOpenInvoiceDetail = { invId ->
                                            viewModel.pushScreen(AppScreen.InvoiceDetail(invId))
                                        },
                                        onMessage = { msg -> viewModel.postMessage(msg) }
                                    )
                                }

                                is AppScreen.Settings -> {
                                    SettingsScreen(
                                        settings = settings,
                                        userEmail = viewModel.currentUserEmail,
                                        isBusy = isBusy,
                                        onSaveSettings = { updated ->
                                            viewModel.saveBusinessSettings(updated)
                                        },
                                        onSignOut = {
                                            performSignOut(
                                                context = context,
                                                onSignOutComplete = {},
                                                scope = scope
                                            )
                                        },
                                        onMessage = { msg -> viewModel.postMessage(msg) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isExpandedScreen) {
            mainScaffold()
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(300.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = settings.businessName.ifBlank { "মেসার্স কর্ণফুলী ট্রেডার্স" },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Khandaker Veg Invoice",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                if (viewModel.currentUserEmail.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = viewModel.currentUserEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            drawerItems.forEach { item ->
                                NavigationDrawerItem(
                                    label = { Text(item.label, fontWeight = FontWeight.Medium) },
                                    selected = item.matches(currentScreen),
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        if (item.screen is AppScreen.CreateOrEditInvoice) {
                                            viewModel.startNewInvoice()
                                        } else {
                                            viewModel.navigateToRoot(item.screen)
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    modifier = Modifier
                                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                                        .testTag("drawer_${item.tag}")
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Spacer(modifier = Modifier.height(8.dp))

                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = "সাইন আউট",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    performSignOut(
                                        context = context,
                                        onSignOutComplete = {},
                                        scope = scope
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "সাইন আউট",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            ) {
                mainScaffold()
            }
        }
    }
}
