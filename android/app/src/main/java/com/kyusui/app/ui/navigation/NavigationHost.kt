package com.kyusui.app.ui.navigation

import com.kyusui.app.ui.courier.design.CourierTheme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.kyusui.app.data.api.ImageLoaderEntryPoint
import com.kyusui.app.domain.model.PaymentMethod
import com.kyusui.app.domain.model.GeoPoint
import com.kyusui.app.ui.auth.AuthEntryPoint
import com.kyusui.app.ui.auth.AuthUiState
import com.kyusui.app.ui.auth.AuthViewModel
import com.kyusui.app.ui.auth.LoginScreen
import com.kyusui.app.ui.auth.OnboardingScreen
import com.kyusui.app.ui.auth.ForgotPasswordScreen
import com.kyusui.app.ui.settings.SettingsScreen
import com.kyusui.app.ui.auth.RegisterScreen
import com.kyusui.app.ui.auth.SplashScreen
import com.kyusui.app.ui.components.ProvideSnackbarManager
import com.kyusui.app.ui.customer.createorder.CartViewModel
import com.kyusui.app.ui.customer.createorder.CartScreen
import com.kyusui.app.ui.customer.createorder.CreateOrderScreen
import com.kyusui.app.ui.customer.createorder.CreateOrderViewModel
import com.kyusui.app.ui.customer.createorder.LocationStatus
import com.kyusui.app.ui.courier.assignments.AssignedOrderScreen
import com.kyusui.app.ui.courier.assignments.AssignedOrderViewModel
import com.kyusui.app.ui.courier.delivery.DeliveryDetailScreen
import com.kyusui.app.ui.courier.delivery.DeliveryDetailViewModel
import com.kyusui.app.ui.courier.home.CourierDashboardScreen
import com.kyusui.app.ui.courier.home.CourierDashboardViewModel
import com.kyusui.app.ui.courier.location.CustomerLocationScreen
import com.kyusui.app.ui.courier.location.CustomerLocationViewModel
import com.kyusui.app.ui.courier.payment.CashConfirmationScreen
import com.kyusui.app.ui.courier.payment.CashConfirmationViewModel
import com.kyusui.app.ui.courier.tracking.CourierTrackingScreen
import com.kyusui.app.ui.courier.tracking.CourierTrackingViewModel
import com.kyusui.app.ui.customer.tracking.TrackingScreen
import com.kyusui.app.ui.customer.tracking.TrackingViewModel
import com.kyusui.app.ui.notifications.NotificationDeepLinkViewModel
import com.kyusui.app.ui.notifications.NotificationPermissionEffect
import com.kyusui.app.ui.notifications.NotificationScreen
import com.kyusui.app.ui.notifications.NotificationTapRouter
import com.kyusui.app.ui.notifications.NotificationViewModel
import com.kyusui.app.ui.location.readLastKnownLocation
import com.kyusui.app.ui.location.rememberLocationRequestState
import com.kyusui.app.ui.customer.home.CustomerHomeScreen
import com.kyusui.app.ui.customer.home.CustomerHomeViewModel
import com.kyusui.app.ui.customer.profile.CustomerProfileScreen
import com.kyusui.app.ui.customer.profile.CustomerProfileViewModel
import com.kyusui.app.ui.customer.orders.OrderDetailScreen
import com.kyusui.app.ui.customer.orders.OrderDetailViewModel
import com.kyusui.app.ui.customer.orders.OrderHistoryScreen
import com.kyusui.app.ui.customer.orders.OrderHistoryViewModel
import com.kyusui.app.ui.customer.orders.OrderStatusFilter
import com.kyusui.app.ui.customer.orders.OrderSummaryScreen
import com.kyusui.app.ui.customer.payment.CashPaymentScreen
import com.kyusui.app.ui.customer.payment.PaymentMethodSelectionScreen
import com.kyusui.app.ui.customer.payment.PaymentViewModel
import com.kyusui.app.ui.customer.payment.QrisPaymentScreen
import com.kyusui.app.ui.customer.products.ProductDetailScreen
import com.kyusui.app.ui.customer.products.ProductDetailUiState
import com.kyusui.app.ui.customer.products.ProductDetailViewModel
import com.kyusui.app.ui.customer.products.ProductListScreen
import com.kyusui.app.ui.customer.products.ProductListViewModel
import com.kyusui.app.ui.owner.payment.QrisManagementViewModel
import com.kyusui.app.ui.owner.payment.QrisManagementScreen
import com.kyusui.app.ui.owner.payment.PendingQrisVerificationViewModel
import com.kyusui.app.ui.owner.payment.PendingQrisVerificationScreen
import com.kyusui.app.ui.owner.orders.OwnerOrderListViewModel
import com.kyusui.app.ui.owner.orders.OwnerOrderListScreen
import com.kyusui.app.ui.owner.orders.OwnerOrderFilter
import com.kyusui.app.ui.owner.orders.OwnerOrderDetailViewModel
import com.kyusui.app.ui.owner.orders.OwnerOrderDetailScreen
import com.kyusui.app.ui.owner.products.OwnerProductFormScreen
import com.kyusui.app.ui.owner.products.OwnerProductFormViewModel
import com.kyusui.app.ui.owner.products.OwnerProductsScreen
import com.kyusui.app.ui.owner.products.OwnerProductsViewModel
import com.kyusui.app.ui.owner.home.OwnerHomeViewModel
import com.kyusui.app.ui.owner.home.OwnerHomeScreen
import com.kyusui.app.ui.owner.finance.FinanceScreen
import com.kyusui.app.ui.owner.finance.FinanceViewModel
import com.kyusui.app.ui.owner.components.OwnerMainTab
import com.kyusui.app.ui.owner.components.OwnerBottomNavigation
import com.kyusui.app.ui.shared.directorder.DirectOrderScreen
import com.kyusui.app.ui.shared.directorder.DirectOrderViewModel
import com.kyusui.app.ui.profile.AccountProfileScreen
import com.kyusui.app.ui.profile.AccountProfileViewModel
import com.kyusui.app.ui.state.SubmitState
import androidx.compose.runtime.remember
import dagger.hilt.android.EntryPointAccessors

private fun NavBackStackEntry.argument(key: String): String? = arguments?.getString(key)

@Composable
fun AppNavHost(
    startRoute: String = RoutePaths.SPLASH,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // Satu AuthViewModel untuk seluruh flow auth, di-scope ke host activity
    // supaya splash, login, dan register berbagi state yang sama.
    val authViewModel: AuthViewModel = hiltViewModel()

    // State notification tap, di-scope ke activity yang sama.
    val deepLinkViewModel: NotificationDeepLinkViewModel = hiltViewModel()

    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val pendingNotification by deepLinkViewModel.pendingNotification.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val authenticatedUser = (authState as? AuthUiState.Authenticated)?.user

    // Device token didaftarkan setiap sesi tervalidasi: baik setelah login
    // maupun setelah session dipulihkan. Endpoint registrasi berada di dalam
    // `auth:sanctum`, jadi tidak dipanggil saat belum authenticated.
    LaunchedEffect(authenticatedUser?.id) {
        if (authenticatedUser != null) {
            deepLinkViewModel.syncDeviceToken()
        }
    }

    // Izin POST_NOTIFICATIONS diminta setelah role sesi diketahui.
    NotificationPermissionEffect(enabled = authenticatedUser != null)

    // Deep link dari system notification. Dijalankan hanya setelah role sesi
    // diketahui dan Splash sudah berpindah, supaya tidak bertabrakan dengan
    // navigasi awal Splash. Payload dikonsumsi setelah dieksekusi sehingga
    // satu Intent tidak memicu navigasi dua kali.
    LaunchedEffect(
        authenticatedUser?.id,
        pendingNotification,
        backStackEntry?.destination?.route
    ) {
        val payload = pendingNotification ?: return@LaunchedEffect
        val user = authenticatedUser ?: return@LaunchedEffect
        val currentRoute = backStackEntry?.destination?.route
        if (currentRoute == null || currentRoute == RoutePaths.SPLASH) return@LaunchedEffect

        val route = NotificationTapRouter.resolveRoute(user.role, payload)
        if (route != null && route != currentRoute) {
            navController.navigate(route) { launchSingleTop = true }
        }
        deepLinkViewModel.consumePendingNotification()
    }

    ProvideSnackbarManager {
        Box(modifier = modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.fillMaxSize()
            ) {
                authGraph(navController, authViewModel)
                customerGraph(navController, authViewModel)
                ownerGraph(navController, authViewModel)
                courierGraph(navController, authViewModel)
                sharedGraph(navController, authViewModel)
            }
        }
    }
}

private fun NavGraphBuilder.authGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    composable(RoutePaths.SPLASH) {
        SplashScreen(
            viewModel = authViewModel,
            onEntryPointResolved = { entryPoint ->
                navController.navigate(entryPoint.toRouteString()) {
                    popUpTo(RoutePaths.SPLASH) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }

    composable(RoutePaths.LOGIN) {
        LoginScreen(
            viewModel = authViewModel,
            onRegisterClick = {
                navController.navigate(RoutePaths.REGISTER) { launchSingleTop = true }
            },
            onForgotPasswordClick = {
                navController.navigate(RoutePaths.FORGOT_PASSWORD) { launchSingleTop = true }
            },
            onAboutClick = {
                navController.navigate(RoutePaths.ONBOARDING) { launchSingleTop = true }
            }
        )

        // LoginScreen hanya menampilkan state; perpindahan layar setelah login
        // sukses ditangani di sini supaya role routing berlaku untuk semua form.
        RoleRoutingEffect(authViewModel, navController)
    }

    composable(RoutePaths.REGISTER) {
        RegisterScreen(
            viewModel = authViewModel,
            onLoginClick = { navController.popBackStack() }
        )

        RoleRoutingEffect(authViewModel, navController)
    }

    composable(RoutePaths.ONBOARDING) {
        OnboardingScreen(
            onBack = { navController.popBackStack() },
            onContinue = {
                navController.navigate(RoutePaths.LOGIN) {
                    popUpTo(RoutePaths.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }

    composable(RoutePaths.FORGOT_PASSWORD) {
        ForgotPasswordScreen(onBack = { navController.popBackStack() })
    }
}

/**
 * Memindahkan navigasi ke home sesuai role begitu login atau register sukses.
 *
 * `SplashScreen` sudah melakukan perpindahan yang sama saat session dipulihkan,
 * jadi effect ini hanya menutup jalur login dan register.
 */
@Composable
private fun RoleRoutingEffect(
    authViewModel: AuthViewModel,
    navController: NavHostController
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        val user = (uiState as? AuthUiState.Authenticated)?.user ?: return@LaunchedEffect
        val route = AuthEntryPoint.fromRole(user.role).toRouteString()
        if (navController.currentDestination?.route == route) return@LaunchedEffect

        navController.navigate(route) {
            popUpTo(RoutePaths.LOGIN) { inclusive = true }
            popUpTo(RoutePaths.REGISTER) { inclusive = true }
            launchSingleTop = true
        }
    }
}

private fun AuthEntryPoint.toRouteString(): String = when (this) {
    AuthEntryPoint.CUSTOMER_HOME -> RoutePaths.CUSTOMER_HOME
    AuthEntryPoint.OWNER_HOME -> RoutePaths.OWNER_HOME
    AuthEntryPoint.COURIER_HOME -> RoutePaths.COURIER_HOME
    AuthEntryPoint.LOGIN -> RoutePaths.LOGIN
}


/**
 * Entry back stack yang dipakai bersama oleh semua layar dalam alur order.
 *
 * `hiltViewModel()` tanpa owner akan mengikat ViewModel ke entry layar itu
 * sendiri, sehingga keranjang yang diisi di daftar produk akan terlihat kosong
 * lagi di form dan ringkasan. Dengan di-scope ke entry `CUSTOMER_HOME`, semua
 * destination membaca instance yang sama. Logout menghapus `CUSTOMER_HOME`
 * secara inclusive, sehingga state ikut tercemar tanpa perlu pembersih manual.
 */

private fun NavHostController.sharedCustomerOwner(): NavBackStackEntry {
    return runCatching {
        getBackStackEntry(RoutePaths.CUSTOMER_HOME)
    }.getOrElse {
        requireNotNull(currentBackStackEntry) {
            "Tidak ada NavBackStackEntry aktif untuk scope CartViewModel."
        }
    }
}

@Composable
private fun NavHostController.sharedCartViewModel(): CartViewModel =
    hiltViewModel(sharedCustomerOwner())

@Composable
private fun NavHostController.sharedCreateOrderViewModel(): CreateOrderViewModel =
    hiltViewModel(sharedCustomerOwner())

/**
 * Menjalankan navigasi ke detail order setelah order dibuat.
 *
 * Dipisah dari tiap layar karena `navigateToOrderId` dimiliki ViewModel yang
 * dibagi; layar mana pun yang merender efek ini akan bereaksi atas hasil yang
 * sama, dan `consumeNavigation()` mencegah perpindahan ganda.
 */
@Composable
private fun OrderSubmitNavigationEffect(
    viewModel: CreateOrderViewModel,
    onOrderCreated: (String) -> Unit
) {
    val navigateToOrderId by viewModel.navigateToOrderId.collectAsStateWithLifecycle()

    LaunchedEffect(navigateToOrderId) {
        val orderId = navigateToOrderId ?: return@LaunchedEffect
        viewModel.consumeNavigation()
        onOrderCreated(orderId)
    }
}

/**
 * Navigasi tab utama pelanggan. Selalu kembali ke root customer (Beranda)
 * terlebih dahulu agar halaman detail/checkout tidak menumpuk di back stack.
 * Setelah itu buka tab tujuan. Ini lebih andal daripada navigate()+popUpTo()
 * berulang ketika tab dipanggil dari nested destination.
 */
private fun NavHostController.navigateCustomerTab(route: String) {
    val homeRoute = RoutePaths.CUSTOMER_HOME
    val currentRoute = currentBackStackEntry?.destination?.route

    if (route == homeRoute) {
        val poppedToHome = popBackStack(homeRoute, inclusive = false)
        if (!poppedToHome && currentRoute != homeRoute) {
            navigate(homeRoute) { launchSingleTop = true }
        }
        return
    }

    val poppedToHome = popBackStack(homeRoute, inclusive = false)
    if (!poppedToHome && currentRoute != homeRoute) {
        navigate(homeRoute) { launchSingleTop = true }
    }

    if (currentBackStackEntry?.destination?.route != route) {
        navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
    }
}

private fun NavGraphBuilder.customerGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    navigation(
        route = RoutePaths.CUSTOMER_GRAPH,
        startDestination = RoutePaths.CUSTOMER_HOME
    ) {
        composable(RoutePaths.CUSTOMER_HOME) {
            val authState by authViewModel.uiState.collectAsStateWithLifecycle()
            val homeViewModel: CustomerHomeViewModel = hiltViewModel()
            val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val cartViewModel = navController.sharedCartViewModel()
            val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()

            CustomerHomeScreen(
                customerName = (authState as? AuthUiState.Authenticated)?.user?.name,
                homeState = homeState,
                cartItemCount = cartState.totalQuantity,
                onBrowseProducts = {
                    navController.navigate(RoutePaths.CUSTOMER_PRODUCT_LIST)
                },
                onOpenCart = {
                    navController.navigate(RoutePaths.CUSTOMER_CART) {
                        launchSingleTop = true
                    }
                },
                onViewActiveOrders = {
                    navController.navigate(
                        AppRoute.CustomerOrderList(OrderStatusFilter.ACTIVE.name).toRouteString()
                    )
                },
                onViewOrderDetail = { orderId ->
                    navController.navigate(
                        AppRoute.CustomerOrderDetail(orderId).toRouteString()
                    )
                },
                onTrackOrder = { orderId ->
                    navController.navigate(
                        AppRoute.CustomerOrderTracking(orderId).toRouteString()
                    )
                },
                onRetry = homeViewModel::loadHome,
                onViewProfile = {
                    navController.navigate(RoutePaths.CUSTOMER_PROFILE)
                },
                selectedDeliveryPoint = cartState.location,
            )
        }

        composable(RoutePaths.CUSTOMER_PRODUCT_LIST) {
            val productListViewModel: ProductListViewModel = hiltViewModel()
            val cartViewModel = navController.sharedCartViewModel()
            val uiState by productListViewModel.uiState.collectAsStateWithLifecycle()
            val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()

            ProductListScreen(
                uiState = uiState,
                cartCount = cartState.totalQuantity,
                onBack = { navController.popBackStack() },
                onRetry = productListViewModel::loadProducts,
                onLoadMore = productListViewModel::loadNextPage,
                onRetryLoadMore = productListViewModel::retryLoadNextPage,
                onGoToCart = {
                    navController.navigate(RoutePaths.CUSTOMER_CART)
                },
                onProductClick = { product ->
                    navController.navigate(
                        AppRoute.CustomerProductDetail(
                            ProductRouteArgs.from(product)
                        ).toRouteString()
                    )
                },
                onAddToCart = cartViewModel::addProduct,
                onChangeQuantity = { product, delta ->
                    val current = cartViewModel.uiState.value.lines
                        .firstOrNull { it.product.id == product.id }
                        ?.quantity ?: 0

                    cartViewModel.setQuantity(product.id, current + delta)
                },
                onNavigateHome = { navController.navigateCustomerTab(RoutePaths.CUSTOMER_HOME) },
                onNavigateOrders = { navController.navigateCustomerTab(AppRoute.CustomerOrderList(OrderStatusFilter.ACTIVE.name).toRouteString()) },
                onNavigateAccount = { navController.navigateCustomerTab(RoutePaths.CUSTOMER_PROFILE) }
            )
        }

        composable(
            route = RoutePaths.CUSTOMER_PRODUCT_DETAIL,
            arguments = listOf(
                navArgument(RoutePaths.ARG_PRODUCT_ID) { type = NavType.StringType },
                navArgument(RoutePaths.ARG_PRODUCT_NAME) { type = NavType.StringType },
                navArgument(RoutePaths.ARG_PRODUCT_PRICE) { type = NavType.StringType },
                navArgument(RoutePaths.ARG_PRODUCT_DESCRIPTION) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(RoutePaths.ARG_PRODUCT_IS_AVAILABLE) { type = NavType.BoolType }
            )
        ) {
            val productDetailViewModel: ProductDetailViewModel = hiltViewModel()
            val cartViewModel = navController.sharedCartViewModel()
            val uiState by productDetailViewModel.uiState.collectAsStateWithLifecycle()
            val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()

            ProductDetailScreen(
                uiState = uiState,
                inCartQuantity = cartState.lines
                    .firstOrNull { it.product.id == (uiState as? ProductDetailUiState.Ready)?.product?.id }
                    ?.quantity
                    ?: 0,
                onBack = { navController.popBackStack() },
                onAddToCart = cartViewModel::addProduct
            )
        }

        composable(RoutePaths.CUSTOMER_CART) {
            val cartViewModel = navController.sharedCartViewModel()
            val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()

            CartScreen(
                cartState = cartState,
                onBack = { navController.popBackStack() },
                onQuantityChange = cartViewModel::setQuantity,
                onContinueShopping = {
                    navController.navigate(RoutePaths.CUSTOMER_PRODUCT_LIST) {
                        launchSingleTop = true
                    }
                },
                onContinueToCheckout = {
                    navController.navigate(RoutePaths.CUSTOMER_CHECKOUT) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(RoutePaths.CUSTOMER_CHECKOUT) {
            val cartViewModel = navController.sharedCartViewModel()
            val createOrderViewModel = navController.sharedCreateOrderViewModel()
            val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()
            val createOrderState by createOrderViewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current

            // Status "Locating" diset sebelum izin diminta. Jika izin diberikan,
            // callback langsung membaca lokasi tanpa meminta customer menekan dua kali.
            val readLocation = {
                val point = readLastKnownLocation(context)
                if (point != null) {
                    cartViewModel.updateLocation(point)
                } else {
                    cartViewModel.updateLocationStatus(LocationStatus.Unavailable)
                }
            }

            val locationRequest = rememberLocationRequestState(
                onPermissionDenied = {
                    cartViewModel.updateLocationStatus(LocationStatus.PermissionDenied)
                },
                onPermissionGranted = { readLocation() }
            )

            val requestLocation = {
                cartViewModel.updateLocationStatus(LocationStatus.Locating)
                if (locationRequest.isGranted(context)) {
                    readLocation()
                } else {
                    locationRequest.launcher()
                }
            }

            CreateOrderScreen(
                cartState = cartState,
                submitState = createOrderState.submitState,
                onBack = { navController.popBackStack() },
                onAddressChange = cartViewModel::updateAddress,
                onRequestLocation = requestLocation,
                onConfirmMapLocation = { point -> cartViewModel.updateLocation(point) },
                onContinueToSummary = {
                    navController.navigate(RoutePaths.CUSTOMER_ORDER_SUMMARY)
                }
            )
        }

        composable(RoutePaths.CUSTOMER_ORDER_SUMMARY) {
            val cartViewModel = navController.sharedCartViewModel()
            val createOrderViewModel = navController.sharedCreateOrderViewModel()
            val cartState by cartViewModel.uiState.collectAsStateWithLifecycle()
            val createOrderState by createOrderViewModel.uiState.collectAsStateWithLifecycle()

            OrderSubmitNavigationEffect(
                viewModel = createOrderViewModel,
                onOrderCreated = { orderId ->
                    // Keranjang dikosongkan hanya setelah order benar-benar dibuat,
                    // supaya gagal submit tidak menghilangkan isian customer.
                    cartViewModel.clear()
                    createOrderViewModel.clearSubmitState()
                    navController.navigate(RoutePaths.CUSTOMER_ORDER_DETAIL.replaceOrderId(orderId)) {
                        popUpTo(RoutePaths.CUSTOMER_HOME)
                    }
                    navController.navigate(RoutePaths.CUSTOMER_PAYMENT_SELECTION.replaceOrderId(orderId))
                }
            )

            OrderSummaryScreen(
                cartState = cartState,
                isSubmitting = createOrderState.isSubmitting,
                onBack = { navController.popBackStack() },
                onSubmit = { createOrderViewModel.submitOrder(cartState) }
            )
        }

        composable(
            route = RoutePaths.CUSTOMER_ORDER_LIST,
            arguments = listOf(
                navArgument(RoutePaths.ARG_FILTER) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            val historyViewModel: OrderHistoryViewModel = hiltViewModel()
            val uiState by historyViewModel.uiState.collectAsStateWithLifecycle()

            // Filter dari query arg diterapkan sekali saat layar dibuka, lalu
            // dikendalikan filter chip. Grouping "Aktif"/"Selesai" dilakukan di
            // client karena `GET /customer/orders` hanya menerima satu nilai
            // `order_status` pasti, sedangkan "Aktif" mencakup banyak status.
            val routeFilter = entry.argument(RoutePaths.ARG_FILTER)
            LaunchedEffect(routeFilter) {
                historyViewModel.setStatusFilter(
                    OrderStatusFilter.entries.firstOrNull { it.name == routeFilter }
                        ?: OrderStatusFilter.ALL
                )
            }

            OrderHistoryScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onRetry = historyViewModel::loadOrders,
                onLoadMore = historyViewModel::loadNextPage,
                onRetryLoadMore = historyViewModel::loadNextPage,
                onFilterChange = historyViewModel::setStatusFilter,
                onOrderClick = { order ->
                    navController.navigate(
                        AppRoute.CustomerOrderDetail(order.id).toRouteString()
                    )
                },
                onNavigateHome = { navController.navigateCustomerTab(RoutePaths.CUSTOMER_HOME) },
                onNavigateProducts = { navController.navigateCustomerTab(RoutePaths.CUSTOMER_PRODUCT_LIST) },
                onNavigateAccount = { navController.navigateCustomerTab(RoutePaths.CUSTOMER_PROFILE) }
            )
        }

        composable(
            route = RoutePaths.CUSTOMER_ORDER_DETAIL,
            arguments = listOf(navArgument(RoutePaths.ARG_ORDER_ID) { type = NavType.StringType })
        ) {
            val orderDetailViewModel: OrderDetailViewModel = hiltViewModel()
            val uiState by orderDetailViewModel.uiState.collectAsStateWithLifecycle()
            val cancelState by orderDetailViewModel.cancelState.collectAsStateWithLifecycle()

            OrderDetailScreen(
                uiState = uiState,
                cancelState = cancelState,
                onBack = { navController.popBackStack() },
                onRetry = orderDetailViewModel::loadOrder,
                onCancel = orderDetailViewModel::cancelOrder,
                onPay = { orderId ->
                    val payment = (uiState as? com.kyusui.app.ui.state.LoadState.Success)?.data?.payment
                    val path = when (payment?.paymentMethod) {
                        PaymentMethod.QRIS -> RoutePaths.CUSTOMER_QRIS_PAYMENT
                        PaymentMethod.CASH -> RoutePaths.CUSTOMER_CASH_PAYMENT
                        null -> RoutePaths.CUSTOMER_PAYMENT_SELECTION
                    }
                    navController.navigate(path.replaceOrderId(orderId))
                },
                onTrack = { orderId ->
                    navController.navigate(
                        AppRoute.CustomerOrderTracking(orderId).toRouteString()
                    )
                }
            )
        }

        composable(
            route = RoutePaths.CUSTOMER_ORDER_TRACKING,
            arguments = listOf(navArgument(RoutePaths.ARG_ORDER_ID) { type = NavType.StringType })
        ) {
            val trackingViewModel: TrackingViewModel = hiltViewModel()

            TrackingScreen(
                viewModel = trackingViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = RoutePaths.CUSTOMER_PAYMENT_SELECTION,
            arguments = listOf(navArgument(RoutePaths.ARG_ORDER_ID) { type = NavType.StringType })
        ) {
            val paymentViewModel: PaymentViewModel = hiltViewModel()
            val uiState by paymentViewModel.uiState.collectAsStateWithLifecycle()

            // Navigasi hanya setelah backend mengonfirmasi method tersimpan.
            // Memindah layar saat request masih berjalan akan membuka layar
            // pembayaran untuk payment yang belum tentu ada.
            LaunchedEffect(uiState.selectState) {
                if (uiState.selectState is SubmitState.Success) {
                    val route = when (uiState.method) {
                        PaymentMethod.QRIS -> RoutePaths.CUSTOMER_QRIS_PAYMENT
                        PaymentMethod.CASH -> RoutePaths.CUSTOMER_CASH_PAYMENT
                        // Method tidak dikenal tidak boleh membuka layar pembayaran.
                        null -> return@LaunchedEffect
                    }
                    paymentViewModel.clearSubmitStates()
                    navController.navigate(route.replaceOrderId(uiState.orderId)) {
                        popUpTo(RoutePaths.CUSTOMER_ORDER_DETAIL)
                    }
                }
            }

            PaymentMethodSelectionScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onSelect = paymentViewModel::selectMethod,
                onRetryPayment = paymentViewModel::refreshPayment
            )
        }
        composable(
            route = RoutePaths.CUSTOMER_QRIS_PAYMENT,
            arguments = listOf(navArgument(RoutePaths.ARG_ORDER_ID) { type = NavType.StringType })
        ) {
            val paymentViewModel: PaymentViewModel = hiltViewModel()
            val uiState by paymentViewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            // QRIS image adalah aset privat toko dan dilayani lewat endpoint ber-token.
            // ImageLoader yang di-taking dari Hilt memakai OkHttpClient yang sama
            // dengan Retrofit, jadi header Authorization ikut terbawa.
            val imageLoader = remember(context) {
                EntryPointAccessors
                    .fromApplication(context, ImageLoaderEntryPoint::class.java)
                    .imageLoader()
            }

            QrisPaymentScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onRefresh = paymentViewModel::refreshPayment,
                onRetryQris = paymentViewModel::loadActiveQris,
                onProofPicked = paymentViewModel::onProofPicked,
                onClearProof = paymentViewModel::clearProofSelection,
                onUploadProof = { paymentViewModel.uploadProof() },
                imageLoader = imageLoader
            )
        }
        composable(
            route = RoutePaths.CUSTOMER_CASH_PAYMENT,
            arguments = listOf(navArgument(RoutePaths.ARG_ORDER_ID) { type = NavType.StringType })
        ) {
            val paymentViewModel: PaymentViewModel = hiltViewModel()
            val uiState by paymentViewModel.uiState.collectAsStateWithLifecycle()

            CashPaymentScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onRefresh = paymentViewModel::refreshPayment
            )
        }
        composable(RoutePaths.CUSTOMER_PROFILE) {
            val profileViewModel: CustomerProfileViewModel = hiltViewModel()
            val state by profileViewModel.uiState.collectAsStateWithLifecycle()
            CustomerProfileScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onNameChange = profileViewModel::onNameChange,
                onPhoneChange = profileViewModel::onPhoneChange,
                onEmailChange = profileViewModel::onEmailChange,
                onAddressChange = profileViewModel::onAddressChange,
                onSave = profileViewModel::saveProfile,
                onRetry = profileViewModel::loadProfile,
                onEdit = { navController.navigate(RoutePaths.CUSTOMER_EDIT_PROFILE) },
                editing = false,
                onNavigateHome = { navController.navigateCustomerTab(RoutePaths.CUSTOMER_HOME) },
                onNavigateProducts = { navController.navigateCustomerTab(RoutePaths.CUSTOMER_PRODUCT_LIST) },
                onNavigateOrders = { navController.navigateCustomerTab(AppRoute.CustomerOrderList(OrderStatusFilter.ACTIVE.name).toRouteString()) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(RoutePaths.LOGIN) {
                        popUpTo(RoutePaths.CUSTOMER_HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(RoutePaths.CUSTOMER_EDIT_PROFILE) {
            val profileViewModel: CustomerProfileViewModel = hiltViewModel()
            val state by profileViewModel.uiState.collectAsStateWithLifecycle()
            CustomerProfileScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onNameChange = profileViewModel::onNameChange,
                onPhoneChange = profileViewModel::onPhoneChange,
                onEmailChange = profileViewModel::onEmailChange,
                onAddressChange = profileViewModel::onAddressChange,
                onSave = profileViewModel::saveProfile,
                onRetry = profileViewModel::loadProfile,
                editing = true
            )
        }
    }

}

private fun String.replaceOrderId(orderId: String): String =
    replace("{${RoutePaths.ARG_ORDER_ID}}", orderId)

/**
 * Graph Owner.
 *
 * Hanya layar yang endpoint-nya benar-benar ada pada
 * `06_Berkah Water_API_SPECIFICATION_REBUILT.md` yang di-dessous ini yang punya
 * implementasi. Sisanya tetap [PlaceholderScreen] dengan alasan eksplisit,
 * karena membuat layar yang bergantung pada endpoint fiktif sama saja dengan
 * mengarang kontrak.
 *
 * Route yang tersedia untuk implementasi:
 *
 * - `OWNER_HOME`             navigasi + ringkasan dari `meta.total` endpoint nyata
 * - `OWNER_ORDER_LIST`       `GET /owner/orders`
 * - `OWNER_ORDER_DETAIL`     `GET /owner/orders/{order}` + PATCH status + POST assignment
 * - `OWNER_QRIS_VERIFICATION`antrean + bukti + approve/reject
 * - `OWNER_QRIS_SETTINGS`    `GET` + `PUT /owner/payment/qris`
 *
 * Yang dipakai lewat preserved endpoint (bukan katalog spec 06 section 30):
 *
 * - `OWNER_PRODUCTS`, `OWNER_PRODUCT_CREATE`, `OWNER_PRODUCT_EDIT`:
 *   `GET/POST/PUT/DELETE owner/products` pada `backend/routes/api.php`.
 * - `OWNER_PROFILE`: `GET/PUT /profile`, endpoint profil generik untuk Owner
 *   dan Courier.
 */
private fun NavGraphBuilder.ownerGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    // Satu dispatcher dipakai oleh seluruh bottom navigation Owner.
    // Semua tab membuka route utamanya sendiri, bukan memanggil aksi shortcut layar.
    val onSelectOwnerTab: (OwnerMainTab) -> Unit = { tab ->
        val destination = when (tab) {
            OwnerMainTab.HOME -> RoutePaths.OWNER_HOME
            OwnerMainTab.ORDERS -> AppRoute.OwnerOrderList(status = null).toRouteString()
            OwnerMainTab.FINANCE -> RoutePaths.OWNER_FINANCE
            OwnerMainTab.PROFILE -> AppRoute.OwnerProfile.toRouteString()
        }

        val currentRoute = navController.currentDestination?.route
        val alreadyOnSelectedTab = when (tab) {
            OwnerMainTab.HOME -> currentRoute == RoutePaths.OWNER_HOME
            OwnerMainTab.ORDERS -> currentRoute == RoutePaths.OWNER_ORDER_LIST
            OwnerMainTab.FINANCE -> currentRoute == RoutePaths.OWNER_FINANCE
            OwnerMainTab.PROFILE -> currentRoute == RoutePaths.OWNER_PROFILE
        }

        if (!alreadyOnSelectedTab) {
            navController.navigate(destination) {
                // Kembali ke root Owner agar perpindahan tab tidak menumpuk
                // halaman tab yang sama di back stack.
                popUpTo(RoutePaths.OWNER_HOME) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    composable(RoutePaths.OWNER_HOME) {
        val homeViewModel: OwnerHomeViewModel = hiltViewModel()
        val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

        OwnerHomeScreen(
            uiState = uiState,
            onOpenOrderList = {
                navController.navigate(
                    AppRoute.OwnerOrderList(status = null).toRouteString()
                )
            },
            onOpenQrisVerification = {
                // Route verifikasi tanpa order id: layar menampilkan seluruh
                // antrean `GET /owner/payments/pending`.
                navController.navigate(RoutePaths.OWNER_QRIS_QUEUE)
            },
            onOpenQrisSettings = {
                navController.navigate(AppRoute.OwnerQrisSettings.toRouteString())
            },
            onOpenProducts = {
                navController.navigate(RoutePaths.OWNER_PRODUCTS)
            },
            onOpenProfile = {
                navController.navigate(AppRoute.OwnerProfile.toRouteString())
            },
            onOpenSettings = {
                navController.navigate(RoutePaths.SETTINGS)
            },
            onOpenFinance = { navController.navigate(RoutePaths.OWNER_FINANCE) },
            onOpenDirectOrder = { navController.navigate("direct_order/OWNER") },
            onRetry = homeViewModel::loadSummary,
            selectedTab = OwnerMainTab.HOME,
            onSelectMainTab = onSelectOwnerTab,
            onLogout = {
                authViewModel.logout()
                // `OWNER_HOME` dihapus secara inklusif supaya seluruh state
                // Owner, termasuk ringkasan dan antrean verifikasi, hilang
                // bersama sesi. Ini yang mencegah layar lama reopening dengan
                // angka dari sesi sebelumnya.
                navController.navigate(RoutePaths.LOGIN) {
                    popUpTo(RoutePaths.OWNER_HOME) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }

    composable(RoutePaths.OWNER_FINANCE) {
        val vm: FinanceViewModel = hiltViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        FinanceScreen(
            state = state,
            onBack = { navController.popBackStack() },
            vm = vm,
            onSelectMainTab = onSelectOwnerTab
        )
    }

    composable(
        route = RoutePaths.DIRECT_ORDER,
        arguments = listOf(navArgument(RoutePaths.ARG_DIRECT_ORDER_ROLE) { type = NavType.StringType })
    ) {
        val vm: DirectOrderViewModel = hiltViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        CourierTheme {
            DirectOrderScreen(state = state, onBack = { navController.popBackStack() }, vm = vm)
        }
    }

    composable(
        route = RoutePaths.OWNER_ORDER_LIST,
        arguments = listOf(
            navArgument(RoutePaths.ARG_STATUS) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { entry ->
        val listViewModel: OwnerOrderListViewModel = hiltViewModel()
        val uiState by listViewModel.uiState.collectAsStateWithLifecycle()

        // Filter yang datang dari query arg route diterapkan sekali saat layar
        // dibuka. Setelah itu filter berasal dari filter chip, sehingga ada
        // satu sumber kebenaran untuk filter.
        val routeStatus = entry.argument(RoutePaths.ARG_STATUS)
        LaunchedEffect(routeStatus) {
            listViewModel.setStatusFilter(
                OwnerOrderFilter.entries.firstOrNull { it.status?.apiValue == routeStatus }
                    ?: OwnerOrderFilter.ALL
            )
        }

        OwnerOrderListScreen(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            onRetry = listViewModel::loadOrders,
            onLoadMore = listViewModel::loadNextPage,
            onRetryLoadMore = listViewModel::retryNextPage,
            selectedTab = OwnerMainTab.ORDERS,
            onSelectMainTab = onSelectOwnerTab,
            onFilterChange = listViewModel::setStatusFilter,
            onOrderClick = { order ->
                navController.navigate(
                    AppRoute.OwnerOrderDetail(order.id).toRouteString()
                )
            }
        )
    }

    composable(
        route = RoutePaths.OWNER_ORDER_DETAIL,
        arguments = listOf(navArgument(RoutePaths.ARG_ORDER_ID) { type = NavType.StringType })
    ) {
        val detailViewModel: OwnerOrderDetailViewModel = hiltViewModel()
        val uiState by detailViewModel.uiState.collectAsStateWithLifecycle()

        OwnerOrderDetailScreen(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            onRetry = detailViewModel::loadOrder,
            onUpdateStatus = detailViewModel::updateStatus,
            onAssignCourier = detailViewModel::assignCourier,
            onRetryCouriers = detailViewModel::loadCouriers,
            onConfirmCash = detailViewModel::confirmCash,
            onOpenQrisVerification = { orderId ->
                navController.navigate(
                    AppRoute.OwnerQrisVerification(orderId).toRouteString()
                )
            }
        )
    }

    composable(
        route = RoutePaths.OWNER_QRIS_QUEUE,
    ) {
        val verificationViewModel: PendingQrisVerificationViewModel = hiltViewModel()
        val uiState by verificationViewModel.uiState.collectAsStateWithLifecycle()
        val imageLoader = rememberOwnerImageLoader()

        CourierTheme {
            PendingQrisVerificationScreen(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            onRetry = verificationViewModel::loadPendingPayments,
            onLoadMore = verificationViewModel::loadNextPage,
            onViewProof = verificationViewModel::loadProof,
            onDismissProof = verificationViewModel::dismissProof,
            onVerify = verificationViewModel::verify,
            imageLoader = imageLoader,
            onViewHistory = verificationViewModel::loadProofHistory,
            onDismissHistory = verificationViewModel::dismissProofHistory,
            onRetryHistory = verificationViewModel::loadProofHistory,
            onViewHistoricalProof = verificationViewModel::loadHistoricalProof,
            onRetryHistoricalProof = verificationViewModel::loadHistoricalProof,
            onDismissHistoricalProof = verificationViewModel::dismissHistoricalProof
        )
        }
    }

    composable(
        route = RoutePaths.OWNER_QRIS_VERIFICATION,
        arguments = listOf(navArgument(RoutePaths.ARG_ORDER_ID) { type = NavType.StringType })
    ) {
        val verificationViewModel: PendingQrisVerificationViewModel = hiltViewModel()
        val uiState by verificationViewModel.uiState.collectAsStateWithLifecycle()
        val imageLoader = rememberOwnerImageLoader()

        PendingQrisVerificationScreen(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            onRetry = verificationViewModel::loadPendingPayments,
            onLoadMore = verificationViewModel::loadNextPage,
            onViewProof = verificationViewModel::loadProof,
            onDismissProof = verificationViewModel::dismissProof,
            onVerify = verificationViewModel::verify,
            imageLoader = imageLoader,
            onViewHistory = verificationViewModel::loadProofHistory,
            onDismissHistory = verificationViewModel::dismissProofHistory,
            onRetryHistory = verificationViewModel::loadProofHistory,
            onViewHistoricalProof = verificationViewModel::loadHistoricalProof,
            onRetryHistoricalProof = verificationViewModel::loadHistoricalProof,
            onDismissHistoricalProof = verificationViewModel::dismissHistoricalProof
        )
    }

    composable(RoutePaths.OWNER_QRIS_SETTINGS) {
        val qrisViewModel: QrisManagementViewModel = hiltViewModel()
        val uiState by qrisViewModel.uiState.collectAsStateWithLifecycle()
        val imageLoader = rememberOwnerImageLoader()

        QrisManagementScreen(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            onRefresh = qrisViewModel::loadActiveQris,
            onImagePicked = qrisViewModel::onQrisImagePicked,
            onClearSelection = qrisViewModel::clearSelection,
            onReplace = qrisViewModel::replaceActiveQris,
            imageLoader = imageLoader
        )
    }

    composable(RoutePaths.OWNER_PRODUCTS) {
        val productsViewModel: OwnerProductsViewModel = hiltViewModel()
        val productsState by productsViewModel.uiState.collectAsStateWithLifecycle()

        OwnerProductsScreen(
            state = productsState,
            onBack = { navController.popBackStack() },
            onRefresh = productsViewModel::loadProducts,
            onAddProduct = { navController.navigate(RoutePaths.OWNER_PRODUCT_CREATE) },
            onEditProduct = { product ->
                navController.navigate(
                    AppRoute.OwnerProductEdit(
                        productId = product.id,
                        name = product.name,
                        price = product.price,
                        description = product.description,
                        isAvailable = product.isAvailable
                    ).toRouteString()
                )
            },
            onActivateProduct = productsViewModel::activate,
            onDeactivateProduct = productsViewModel::deactivate,
            onLoadMore = productsViewModel::loadMore
        )
    }
    composable(RoutePaths.OWNER_PRODUCT_CREATE) {
        val formViewModel: OwnerProductFormViewModel = hiltViewModel()
        val formState by formViewModel.uiState.collectAsStateWithLifecycle()

        OwnerProductFormScreen(
            state = formState,
            onBack = { navController.popBackStack() },
            onNameChange = formViewModel::onNameChange,
            onPriceChange = formViewModel::onPriceChange,
            onDescriptionChange = formViewModel::onDescriptionChange,
            onAvailabilityChange = formViewModel::onAvailabilityChange,
            onSave = formViewModel::save,
            onSaved = {
                formViewModel.onSavedConsumed()
                navController.popBackStack()
            }
        )
    }
    composable(
        route = RoutePaths.OWNER_PRODUCT_EDIT,
        arguments = listOf(
            navArgument(RoutePaths.ARG_PRODUCT_ID) { type = NavType.StringType },
            navArgument(RoutePaths.ARG_PRODUCT_NAME) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(RoutePaths.ARG_PRODUCT_PRICE) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(RoutePaths.ARG_PRODUCT_DESCRIPTION) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(RoutePaths.ARG_PRODUCT_IS_AVAILABLE) {
                type = NavType.StringType
                defaultValue = "true"
            }
        )
    ) {
        val formViewModel: OwnerProductFormViewModel = hiltViewModel()
        val formState by formViewModel.uiState.collectAsStateWithLifecycle()

        OwnerProductFormScreen(
            state = formState,
            onBack = { navController.popBackStack() },
            onNameChange = formViewModel::onNameChange,
            onPriceChange = formViewModel::onPriceChange,
            onDescriptionChange = formViewModel::onDescriptionChange,
            onAvailabilityChange = formViewModel::onAvailabilityChange,
            onSave = formViewModel::save,
            onSaved = {
                formViewModel.onSavedConsumed()
                navController.popBackStack()
            }
        )
    }
    composable(RoutePaths.OWNER_PROFILE) {
        val profileViewModel: AccountProfileViewModel = hiltViewModel()
        val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()

        AccountProfileScreen(
            title = "Profil Owner",
            state = profileState,
            onBack = { navController.popBackStack() },
            onRetry = profileViewModel::loadProfile,
            onNameChange = profileViewModel::onNameChange,
            onPhoneChange = profileViewModel::onPhoneChange,
            onVehicleChange = profileViewModel::onVehicleChange,
            onPasswordChange = profileViewModel::onPasswordChange,
            onPasswordConfirmationChange = profileViewModel::onPasswordConfirmationChange,
            onSave = profileViewModel::saveProfile,
            bottomBar = {
                OwnerBottomNavigation(
                    selectedTab = OwnerMainTab.PROFILE,
                    onSelect = onSelectOwnerTab
                )
            }
        )
    }
}

/**
 * Coil `ImageLoader` dengan header autentikasi.
 *
 * Bukti pembayaran dan QRIS aktif berada di storage privat backend, jadi
 * gambarnya hanya bisa dimuat dengan `Authorization` milik sesi Owner. Loader
 * yang dipakai customer dan Owner sengaja sama supaya tidak ada jalur gambar
 * yang hanya membawa header untuk satu role.
 */
@Composable
private fun rememberOwnerImageLoader(): coil.ImageLoader {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ImageLoaderEntryPoint::class.java
        ).imageLoader()
    }
}

/**
 * Graph Courier.
 *
 * Seluruh rute di graph ini memakai id assignment, bukan id order, karena
 * endpoint kurir di `backend/routes/api.php` bekerja pada `courier_assignments`:
 *
 * - `COURIER_HOME`              `GET dashboard/courier`
 * - `COURIER_ASSIGNMENT_LIST`   `GET courier/assignments`
 * - `COURIER_DELIVERY_DETAIL`   `GET courier/assignments/{assignment}` serta aksi
 *   `start`, `location`, dan `complete`
 * - `COURIER_CUSTOMER_LOCATION` alamat tujuan dan aksi `POST .../location`
 * - `COURIER_CASH_CONFIRMATION` aksi `POST .../cash/confirm`
 *
 * - `COURIER_PROFILE`           `GET/PUT /profile` (preserved). Endpoint ini
 *   tidak ada di katalog spec 06 section 30, tetapi backend memakainya untuk
 *   mengelola profil Courier, jadi layar ini memakai kontrak yang berjalan.
 *
 * Backend memakai penamaan `courier/assignments`, sedangkan specification 06
 * section 15 menuliskan `courier/orders`. Rute di sini mengikuti backend yang
 * berjalan, dan specification dicatat sebagai pekerjaan revisi terpisah.
 */
private fun NavGraphBuilder.courierGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    composable(RoutePaths.COURIER_QRIS_QUEUE) {
        val verificationViewModel: PendingQrisVerificationViewModel = hiltViewModel()
        val uiState by verificationViewModel.uiState.collectAsStateWithLifecycle()
        val imageLoader = rememberOwnerImageLoader()

        CourierTheme {
            PendingQrisVerificationScreen(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            onRetry = verificationViewModel::loadPendingPayments,
            onLoadMore = verificationViewModel::loadNextPage,
            onViewProof = verificationViewModel::loadProof,
            onDismissProof = verificationViewModel::dismissProof,
            onVerify = verificationViewModel::verify,
            imageLoader = imageLoader,
            onViewHistory = verificationViewModel::loadProofHistory,
            onDismissHistory = verificationViewModel::dismissProofHistory,
            onRetryHistory = verificationViewModel::loadProofHistory,
            onViewHistoricalProof = verificationViewModel::loadHistoricalProof,
            onRetryHistoricalProof = verificationViewModel::loadHistoricalProof,
                onDismissHistoricalProof = verificationViewModel::dismissHistoricalProof
            )
        }
    }

    composable(RoutePaths.COURIER_HOME) {
        val dashboardViewModel: CourierDashboardViewModel = hiltViewModel()
        val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()

        CourierTheme {
            CourierDashboardScreen(
            uiState = uiState,
            onOpenAssignments = {
                navController.navigate(RoutePaths.COURIER_ASSIGNMENT_LIST)
            },
            onOpenDelivery = { assignmentId ->
                navController.navigate(
                    AppRoute.CourierDeliveryDetail(assignmentId).toRouteString()
                )
            },
            onRefresh = { dashboardViewModel.loadDashboard(force = true) },
            onStartDuty = dashboardViewModel::startDuty,
            onStopDuty = dashboardViewModel::stopDuty,
            onClearDutyMessage = dashboardViewModel::clearDutyMessage,
            onOpenProfile = {
                navController.navigate(AppRoute.CourierProfile.toRouteString())
            },
            onOpenSettings = {
                navController.navigate(RoutePaths.SETTINGS)
            },
            onOpenDirectOrder = { navController.navigate("courier_direct_order/COURIER") },
            onOpenQrisVerification = { navController.navigate(RoutePaths.COURIER_QRIS_QUEUE) },
            onOpenNotifications = { navController.navigate(RoutePaths.NOTIFICATIONS) },
            onLogout = {
                authViewModel.logout()
                // `COURIER_HOME` dihapus secara inklusif supaya state kurir,
                // termasuk daftar assignment dan ringkasan, hilang bersama sesi.
                // Tanpa itu, layar lama bisa terbuka lagi dengan data sesi
                // sebelumnya setelah kurir lain login di perangkat yang sama.
                navController.navigate(RoutePaths.LOGIN) {
                    popUpTo(RoutePaths.COURIER_HOME) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
        }
    }

    composable(
        route = RoutePaths.COURIER_DIRECT_ORDER,
        arguments = listOf(navArgument(RoutePaths.ARG_DIRECT_ORDER_ROLE) { type = NavType.StringType })
    ) {
        val vm: DirectOrderViewModel = hiltViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        CourierTheme {
            DirectOrderScreen(state = state, onBack = { navController.popBackStack() }, vm = vm)
        }
    }

    composable(RoutePaths.COURIER_ASSIGNMENT_LIST) {
        val listViewModel: AssignedOrderViewModel = hiltViewModel()
        val uiState by listViewModel.uiState.collectAsStateWithLifecycle()

        CourierTheme {
            AssignedOrderScreen(
            uiState = uiState,
            onOpenDelivery = { assignmentId ->
                navController.navigate(
                    AppRoute.CourierDeliveryDetail(assignmentId).toRouteString()
                )
            },
            onSelectFilter = listViewModel::setFilter,
            onRefresh = { listViewModel.loadAssignments(force = true) },
            onClaim = listViewModel::claimOrder,
            onRefreshPool = listViewModel::loadOpenOrders,
            onOpenHome = {
                navController.navigate(RoutePaths.COURIER_HOME) {
                    launchSingleTop = true
                }
            },
            onOpenProfile = {
                navController.navigate(AppRoute.CourierProfile.toRouteString()) {
                    launchSingleTop = true
                }
            }
        )
        }
    }

    composable(
        route = RoutePaths.COURIER_DELIVERY_DETAIL,
        arguments = listOf(
            navArgument(RoutePaths.ARG_ASSIGNMENT_ID) { type = NavType.StringType }
        )
    ) { entry ->
        val detailViewModel: DeliveryDetailViewModel = hiltViewModel()
        val uiState by detailViewModel.uiState.collectAsStateWithLifecycle()

        // Id assignment dibaca sekali dari argumen rute. Rute anak memakai id
        // yang sama, jadi detail dan konfirmasi tunai tidak mungkin memakai
        // assignment yang berbeda karena salah membaca argumen rute.
        val assignmentId = entry.argument(RoutePaths.ARG_ASSIGNMENT_ID).orEmpty()

        CourierTheme {
            DeliveryDetailScreen(
            uiState = uiState,
            onStartDelivery = detailViewModel::startDelivery,
            onCompleteDelivery = detailViewModel::completeDelivery,
            onOpenCustomerLocation = {
                if (assignmentId.isNotBlank()) {
                    navController.navigate(
                        AppRoute.CourierCustomerLocation(assignmentId).toRouteString()
                    )
                }
            },
            onOpenTracking = {
                if (assignmentId.isNotBlank()) {
                    navController.navigate(
                        AppRoute.CourierTracking(assignmentId).toRouteString()
                    )
                }
            },
            onOpenCashConfirmation = {
                if (assignmentId.isNotBlank()) {
                    navController.navigate(
                        AppRoute.CourierCashConfirmation(assignmentId).toRouteString()
                    )
                }
            },
            onOpenQrisVerification = { navController.navigate(RoutePaths.COURIER_QRIS_QUEUE) },
            onRetry = detailViewModel::loadDelivery,
            onMessageShown = detailViewModel::clearActionMessage,
            onBack = { navController.popBackStack() }
        )
        }
    }

    composable(
        route = RoutePaths.COURIER_CUSTOMER_LOCATION,
        arguments = listOf(
            navArgument(RoutePaths.ARG_ASSIGNMENT_ID) { type = NavType.StringType }
        )
    ) {
        val locationViewModel: CustomerLocationViewModel = hiltViewModel()
        val uiState by locationViewModel.uiState.collectAsStateWithLifecycle()

        CourierTheme {
            CustomerLocationScreen(
            uiState = uiState,
            onSendLocation = locationViewModel::submitFix,
            onLocationReadStatusChanged = locationViewModel::setLocationStatus,
            onRetry = locationViewModel::loadDelivery,
            onMessageShown = locationViewModel::clearMessage,
            onBack = { navController.popBackStack() }
        )
        }
    }

    composable(
        route = RoutePaths.COURIER_CASH_CONFIRMATION,
        arguments = listOf(
            navArgument(RoutePaths.ARG_ASSIGNMENT_ID) { type = NavType.StringType }
        )
    ) {
        val cashViewModel: CashConfirmationViewModel = hiltViewModel()
        val uiState by cashViewModel.uiState.collectAsStateWithLifecycle()

        CourierTheme {
            CashConfirmationScreen(
            uiState = uiState,
            onConfirmCashReceived = cashViewModel::confirmCashReceived,
            onRetry = cashViewModel::loadDelivery,
            onBack = { navController.popBackStack() }
        )
        }
    }

    composable(
        route = RoutePaths.COURIER_TRACKING,
        arguments = listOf(
            navArgument(RoutePaths.ARG_ASSIGNMENT_ID) { type = NavType.StringType }
        )
    ) {
        val trackingViewModel: CourierTrackingViewModel = hiltViewModel()

        CourierTheme {
            CourierTrackingScreen(
            viewModel = trackingViewModel,
            onBack = { navController.popBackStack() }
        )
        }
    }

    composable(RoutePaths.COURIER_PROFILE) {
        val profileViewModel: AccountProfileViewModel = hiltViewModel()
        val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()

        CourierTheme {
            AccountProfileScreen(
                title = "Profil Kurir",
                state = profileState,
                onBack = { navController.popBackStack() },
                onRetry = profileViewModel::loadProfile,
                onNameChange = profileViewModel::onNameChange,
                onPhoneChange = profileViewModel::onPhoneChange,
                onVehicleChange = profileViewModel::onVehicleChange,
                onSave = profileViewModel::saveProfile,
                bottomBar = {
                    com.kyusui.app.ui.courier.design.CourierRootBottomBar(
                        selected = com.kyusui.app.ui.courier.design.CourierRootTab.PROFILE,
                        onHome = {
                            navController.navigate(RoutePaths.COURIER_HOME) {
                                launchSingleTop = true
                            }
                        },
                        onTasks = {
                            navController.navigate(RoutePaths.COURIER_ASSIGNMENT_LIST) {
                                launchSingleTop = true
                            }
                        },
                        onProfile = {}
                    )
                }
            )
        }
    }
}

private fun NavGraphBuilder.sharedGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    composable(
        route = RoutePaths.WEBVIEW,
        arguments = listOf(
            navArgument(RoutePaths.ARG_URL) { type = NavType.StringType },
            navArgument(RoutePaths.ARG_TITLE) { type = NavType.StringType }
        )
    ) { entry ->
        PlaceholderScreen(
            title = entry.argument(RoutePaths.ARG_TITLE).orEmpty(),
            subtitle = entry.argument(RoutePaths.ARG_URL).orEmpty()
        )
    }
    composable(RoutePaths.SETTINGS) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onOpenAbout = { navController.navigate(RoutePaths.ONBOARDING) },
            onLogout = {
                // Logout tetap lewat AuthViewModel agar sesi lokal dicabut.
                authViewModel.logout()
                navController.navigate(RoutePaths.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }
    composable(RoutePaths.NOTIFICATIONS) {
        val notificationViewModel: NotificationViewModel = hiltViewModel()
        val authState by authViewModel.uiState.collectAsStateWithLifecycle()

        NotificationScreen(
            viewModel = notificationViewModel,
            onBack = { navController.popBackStack() },
            onNotificationClick = { notification ->
                // Tujuan ditentukan dari role sesi yang sedang aktif, bukan
                // dari asumsi role customer. Bila payload tidak menghasilkan
                // rute untuk role ini, tidak ada navigasi yang dilakukan.
                val role = (authState as? AuthUiState.Authenticated)?.user?.role
                val route = role?.let {
                    NotificationTapRouter.resolveRoute(it, notification.payload)
                }
                if (route != null) {
                    notificationViewModel.markAsRead(notification.id)
                    navController.navigate(route) { launchSingleTop = true }
                }
            }
        )
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.displayMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
