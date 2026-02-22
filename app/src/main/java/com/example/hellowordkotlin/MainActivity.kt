package com.example.hellowordkotlin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import com.google.firebase.storage.storage
import com.google.maps.android.compose.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException

// ─────────────────────────────────────────────────────────────
// COULEURS
// ─────────────────────────────────────────────────────────────
val PrimaryRed   = Color(0xFFE53935)
val DarkRed      = Color(0xFFC62828)
val SoftPink     = Color(0xFFFFF1F1)
val SuccessGreen = Color(0xFF43A047)
val PendingGold  = Color(0xFFFFB300)
val DeliveryBlue = Color(0xFF1565C0)
val LightBlue    = Color(0xFF42A5F5)
val CardBg       = Color(0xFFFFFFFF)

val GradientRed  = listOf(PrimaryRed, DarkRed, Color(0xFF8B0000))
val GradientBlue = listOf(DeliveryBlue, LightBlue)

// ─────────────────────────────────────────────────────────────
// MODÈLE PANIER
// ─────────────────────────────────────────────────────────────
data class CartItem(
    val id: String?,
    val name: String,
    val price: Double,
    val imageUrl: String,
    var quantity: Int = 1
) {
    val subtotal: Double get() = price * quantity
    constructor(s: DataSnapshot) : this(
        id       = s.key,
        name     = s.child("name").value.toString(),
        price    = s.child("price").value.toString().toDoubleOrNull() ?: 0.0,
        imageUrl = s.child("imageUrl").value.toString(),
        quantity = 1
    )
}

// ─────────────────────────────────────────────────────────────
// MAIN ACTIVITY
// ─────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        setContent { MaterialTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    var user        by remember { mutableStateOf(Firebase.auth.currentUser) }
    var userData    by remember { mutableStateOf<DataSnapshot?>(null) }
    var isAdmin     by remember { mutableStateOf(false) }
    var isLivreur   by remember { mutableStateOf(false) }
    var screen      by remember { mutableStateOf("home") }
    var selProduct  by remember { mutableStateOf<DataSnapshot?>(null) }
    var selOrder    by remember { mutableStateOf<DataSnapshot?>(null) }
    var editProduct by remember { mutableStateOf<DataSnapshot?>(null) }
    val cart    = remember { mutableStateListOf<CartItem>() }
    val context = LocalContext.current

    LaunchedEffect(user?.uid) {
        val uid = user?.uid
        if (uid != null) {
            Firebase.database.reference.child("users").child(uid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        userData  = s
                        isAdmin   = s.child("isAdmin").value == true
                        isLivreur = s.child("isLivreur").value == true &&
                                s.child("livreurAccepted").value == true
                        if (isLivreur) startLocationUpdates(context, uid)
                    }
                    override fun onCancelled(e: DatabaseError) {}
                })
        } else { userData = null; isAdmin = false; isLivreur = false }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun navigate(dest: String) { screen = dest }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut()) },
        label = "nav"
    ) { s ->
        Box(Modifier.fillMaxSize().background(SoftPink)) {
            when (s) {
                "home" -> HomeScreen(
                    isAdmin = isAdmin,
                    isLivreur = isLivreur,
                    onProductClick = { selProduct = it; navigate("detail") },
                    onCartClick = { navigate(if (user == null) "auth" else "cart") },
                    onProfileClick = { navigate(if (user == null) "auth" else "profile") }, // Supprimez le texte après la virgule
                    onAdminProducts = { navigate("admin_products") },
                    onAdminUsers = { navigate("admin_users") },
                    onLivreur = { navigate("livreur_home") },
                    onQuickAdd = { p -> addToCart(cart, p) }, // Assurez-vous d'ajouter cette ligne si elle manque
                    cartSize = cart.sumOf { it.quantity }
                )
                "auth"           -> AuthScreen(onSuccess = { user = Firebase.auth.currentUser; navigate("home") }, onBack = { navigate("home") })
                "detail"         -> selProduct?.let { DetailScreen(it, { navigate("home") }) { p -> addToCart(cart, p) } }
                "cart"           -> CartScreen(cart, userData, { navigate("home") }) { cart.clear(); navigate("orders_client") }
                "profile"        -> ProfileScreen(userData, isAdmin, isLivreur, { navigate("home") }, { navigate(it) }) {
                    Firebase.auth.signOut(); user = null; userData = null; isAdmin = false; isLivreur = false; navigate("home")
                }
                "orders_client"  -> ClientOrdersScreen({ navigate("profile") }) { selOrder = it; navigate("order_tracking") }
                "order_tracking" -> selOrder?.let { OrderTrackingScreen(it, isLivreur) { navigate(if (isLivreur) "livreur_home" else "orders_client") } }
                "livreur_home"   -> LivreurHomeScreen(userData, { navigate("profile") }) { selOrder = it; navigate("order_tracking") }
                "admin_orders"   -> AdminOrdersScreen { navigate("profile") }
                "admin_products" -> AdminProductsScreen({ navigate("profile") }, { editProduct = it; navigate("add_product") }) { editProduct = null; navigate("add_product") }
                "add_product"    -> AddProductScreen(editProduct) { navigate("admin_products") }
                "admin_users"    -> AdminUsersScreen { navigate("profile") }
                "admin_livreurs" -> AdminLivreursScreen { navigate("profile") }
            }
        }
    }
}

fun addToCart(cart: MutableList<CartItem>, product: DataSnapshot) {
    val idx = cart.indexOfFirst { it.id == product.key }
    if (idx >= 0) cart[idx] = cart[idx].copy(quantity = cart[idx].quantity + 1)
    else cart.add(CartItem(product))
}

// ═══════════════════════════════════════════════════════════════
// ✅ COMPOSANTS PARTAGÉS — définition obligatoire avant usage
// ═══════════════════════════════════════════════════════════════

/** Barre de titre avec dégradé, bouton retour et slot optionnel d'actions. */
@Composable
fun TopBar(
    title: String,
    gradient: List<Color> = GradientRed,
    onBack: () -> Unit,
    extra: @Composable RowScope.() -> Unit = {}
) {
    Box(
        Modifier.fillMaxWidth().height(110.dp)
            .shadow(10.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.verticalGradient(gradient), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color.White.copy(0.2f), CircleShape)) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Text(
                title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f).padding(start = 10.dp)
            )
            extra()
        }
    }
}

/** Surcharge sans gradient (utilise GradientRed par défaut). */
@Composable
fun TopBar(title: String, onBack: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) =
    TopBar(title, GradientRed, onBack, extra)

/** Carte 3D avec ombre et coins arrondis. */
@Composable
fun Card3D(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = (if (onClick != null) modifier.clickable { onClick() } else modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(6.dp, RoundedCornerShape(22.dp)),
        shape     = RoundedCornerShape(22.dp),
        colors    = CardDefaults.cardColors(CardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) { Column(Modifier.padding(14.dp), content = content) }
}

/** Badge coloré selon le statut de la commande. */
@Composable
fun StatusChip(status: String) {
    val (color, label, emoji) = when (status) {
        "EN_ATTENTE" -> Triple(PendingGold,  "En attente", "⏳")
        "EN_COURS"   -> Triple(DeliveryBlue, "En cours",   "🚴")
        "LIVRE"      -> Triple(SuccessGreen, "Livré",      "✅")
        "ANNULE"     -> Triple(Color.Red,    "Annulé",     "❌")
        else         -> Triple(Color.Gray,   status,       "•")
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(0.15f), border = BorderStroke(1.dp, color.copy(0.4f))) {
        Text("$emoji $label", color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

// ─────────────────────────────────────────────────────────────
// BARRE DE NAVIGATION INFÉRIEURE
// ─────────────────────────────────────────────────────────────
@Composable
fun BottomNavBar(onHome: () -> Unit, onCart: () -> Unit, onProfile: () -> Unit, cartSize: Int) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Surface(Modifier.fillMaxWidth().height(66.dp), RoundedCornerShape(33.dp), Color.White, shadowElevation = 16.dp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                IconButton(onClick = onHome) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Home, null, tint = PrimaryRed, modifier = Modifier.size(24.dp))
                        Text("Accueil", fontSize = 10.sp, color = PrimaryRed)
                    }
                }
                Box {
                    Surface(Modifier.size(52.dp).offset(y = (-4).dp), CircleShape, PrimaryRed, shadowElevation = 8.dp) {
                        IconButton(onClick = onCart) { Icon(Icons.Default.ShoppingCart, null, tint = Color.White) }
                    }
                    if (cartSize > 0) {
                        Surface(Modifier.size(18.dp).align(Alignment.TopEnd).offset(x = 4.dp), CircleShape, PendingGold) {
                            Text("$cartSize", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.wrapContentSize())
                        }
                    }
                }
                IconButton(onClick = onProfile) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(24.dp), tint = Color.Gray)
                        Text("Profil", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : ACCUEIL
// ─────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    isAdmin: Boolean, isLivreur: Boolean,
    onProductClick: (DataSnapshot) -> Unit, onCartClick: () -> Unit, onProfileClick: () -> Unit,
    onAdminProducts: () -> Unit, onAdminUsers: () -> Unit, onLivreur: () -> Unit,
    onQuickAdd: (DataSnapshot) -> Unit, cartSize: Int
) {
    var search      by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("Tous") }
    var products    by remember { mutableStateOf(listOf<DataSnapshot>()) }
    var categories  by remember { mutableStateOf(listOf("Tous", "Favoris")) }
    var likedIds    by remember { mutableStateOf(setOf<String>()) }
    val uid = Firebase.auth.currentUser?.uid

    LaunchedEffect(Unit) {
        Firebase.database.reference.child("products").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                products   = s.children.toList()
                categories = listOf("Tous", "Favoris") + products
                    .map { it.child("category").value?.toString() ?: "" }.distinct().filter { it.isNotEmpty() }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }
    LaunchedEffect(uid) {
        if (uid != null) Firebase.database.reference.child("likes").child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) { likedIds = s.children.mapNotNull { it.key }.toSet() }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    val filtered = products.filter { p ->
        val name = p.child("name").value?.toString()?.lowercase() ?: ""
        val cat  = p.child("category").value?.toString() ?: ""
        name.contains(search.lowercase()) && when (selectedCat) {
            "Tous"    -> true
            "Favoris" -> likedIds.contains(p.key)
            else      -> cat == selectedCat
        }
    }

    Scaffold(
        bottomBar  = { BottomNavBar(onHome = {}, onCart = onCartClick, onProfile = onProfileClick, cartSize = cartSize) },
        containerColor = SoftPink
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Header rouge
            Box(Modifier.fillMaxWidth().height(220.dp)
                .background(Brush.verticalGradient(GradientRed), RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))) {
                Column(Modifier.fillMaxSize().padding(20.dp), Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("Saveurs", color = Color.White.copy(0.8f), fontSize = 14.sp)
                            Text("Express 🍔", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isLivreur) IconButton(onClick = onLivreur, Modifier.background(Color.White.copy(0.2f), CircleShape)) {
                                Icon(Icons.Default.DeliveryDining, null, tint = Color.White)
                            }
                            IconButton(onClick = onProfileClick, Modifier.background(Color.White.copy(0.2f), CircleShape)) {
                                Icon(Icons.Default.Person, null, tint = Color.White)
                            }
                        }
                    }
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), Color.White, shadowElevation = 4.dp) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
                                decorationBox = { inner ->
                                    if (search.isEmpty()) Text("Rechercher un plat...", color = Color.LightGray); inner()
                                })
                        }
                    }
                }
            }
            // Catégories
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    Surface(onClick = { selectedCat = cat }, shape = RoundedCornerShape(20.dp),
                        color = if (selectedCat == cat) PrimaryRed else Color.White,
                        shadowElevation = if (selectedCat == cat) 6.dp else 2.dp) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (cat == "Favoris") Text("❤️ ", fontSize = 13.sp)
                            Text(cat, color = if (selectedCat == cat) Color.White else Color.DarkGray,
                                fontWeight = if (selectedCat == cat) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                        }
                    }
                }
            }
            // Contenu
            when {
                selectedCat == "Favoris" && likedIds.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("❤️", fontSize = 50.sp)
                            Text("Aucun favori encore", color = Color.Gray, fontSize = 16.sp)
                            Text("Appuie sur ♡ pour liker un plat", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                filtered.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🍽️", fontSize = 50.sp); Text("Aucun résultat", color = Color.Gray) }
                    }
                else -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered) { p ->
                        ProductCard(p, likedIds.contains(p.key), { onProductClick(p) }, { onQuickAdd(p) }) {
                            val u = uid ?: return@ProductCard
                            val ref = Firebase.database.reference.child("likes").child(u).child(p.key ?: "")
                            if (likedIds.contains(p.key)) ref.removeValue() else ref.setValue(true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCard(doc: DataSnapshot, isLiked: Boolean, onClick: () -> Unit, onAdd: () -> Unit, onLike: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale   by animateFloatAsState(if (pressed) 0.96f else 1f, label = "sc")
    Card(
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale).padding(5.dp)
            .shadow(if (pressed) 2.dp else 7.dp, RoundedCornerShape(18.dp)).clickable { pressed = true; onClick() },
        shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White)
    ) {
        Column {
            Box {
                Image(rememberAsyncImagePainter(doc.child("imageUrl").value), null, Modifier.fillMaxWidth().height(130.dp), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.25f)))))
                Surface(Modifier.padding(7.dp).align(Alignment.TopStart), RoundedCornerShape(9.dp), PrimaryRed.copy(0.85f)) {
                    Text(doc.child("category").value?.toString() ?: "", color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Box(Modifier.padding(5.dp).size(30.dp).align(Alignment.TopEnd).background(Color.White.copy(0.85f), CircleShape).clickable { onLike() }, Alignment.Center) {
                    Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isLiked) PrimaryRed else Color.Gray, modifier = Modifier.size(15.dp))
                }
            }
            Column(Modifier.padding(9.dp)) {
                Text(doc.child("name").value?.toString() ?: "", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("${doc.child("price").value} Fc", color = PrimaryRed, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Surface(onClick = onAdd, shape = RoundedCornerShape(10.dp), color = PrimaryRed, shadowElevation = 3.dp) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.padding(5.dp).size(16.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : DÉTAIL PRODUIT
// ─────────────────────────────────────────────────────────────
@Composable
fun DetailScreen(
    product: DataSnapshot,
    onBack: () -> Unit,
    onAddCart: (DataSnapshot) -> Unit
) {
    val ctx = LocalContext.current
    var qty by remember { mutableStateOf(1) }
    val price = product.child("price").value?.toString()?.toDoubleOrNull() ?: 0.0

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // --- Image + overlay + back
        Box {
            Image(
                painter = rememberAsyncImagePainter(product.child("imageUrl").value),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.5f))))
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(16.dp).background(Color.White.copy(0.85f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }

            Column(modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Surface(shape = RoundedCornerShape(8.dp), color = PrimaryRed) {
                    Text(
                        text = product.child("category").value?.toString() ?: "",
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(product.child("name").value?.toString() ?: "", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("${product.child("price").value} Fc", color = PendingGold, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        // --- Card contenu (C'est ici que se trouvait votre erreur)
        Surface(
            modifier = Modifier.fillMaxSize().offset(y = (-18).dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                Text("Description", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = product.child("description").value?.toString()?.ifEmpty { "Un délicieux plat préparé avec soin." } ?: "Un délicieux plat préparé avec soin.",
                    color = Color.DarkGray,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(22.dp))

                Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Quantité", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Bouton MOINS
                        Surface(
                            shape = CircleShape,
                            color = if (qty > 1) PrimaryRed else Color.LightGray,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(enabled = qty > 1) { qty-- }
                        ) {
                            Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.padding(7.dp))
                        }

                        Text("$qty", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 18.dp))

                        // Bouton PLUS
                        Surface(
                            shape = CircleShape,
                            color = PrimaryRed,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { qty++ }
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.padding(7.dp))
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = {
                        repeat(qty) { onAddCart(product) }
                        Toast.makeText(ctx, "✅ $qty × ${product.child("name").value} ajouté!", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(PrimaryRed),
                    shape = RoundedCornerShape(18.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.padding(end = 8.dp))
                    Text("Ajouter · ${price * qty} Fc", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            } // Fin Column
        } // Fin Surface
    } // Fin Column principale
} // Fin DetailScreen
// ─────────────────────────────────────────────────────────────
// ÉCRAN : PANIER
// ─────────────────────────────────────────────────────────────
@Composable
fun CartScreen(cart: MutableList<CartItem>, userData: DataSnapshot?, onBack: () -> Unit, onSuccess: () -> Unit) {
    val total = cart.sumOf { it.subtotal }
    val ctx = LocalContext.current
    var showPayment by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(SoftPink)) {
        // 1. BARRE SUPÉRIEURE
        TopBar("Mon Panier 🛒", onBack = onBack) {
            if (cart.isNotEmpty()) {
                TextButton(onClick = { cart.clear() }) {
                    Text("Vider", color = Color.White.copy(0.85f))
                }
            }
        }

        // 2. CONTENU (VIDE OU LISTE)
        if (cart.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛒", fontSize = 60.sp)
                    Text("Panier vide", fontSize = 18.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("Ajoute des plats pour commander", color = Color.LightGray, fontSize = 13.sp)
                }
            }
        } else {
            // Liste des articles
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(cart.toList(), key = { it.id ?: "" }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = rememberAsyncImagePainter(item.imageUrl),
                                contentDescription = null,
                                modifier = Modifier.size(70.dp).clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
                                Text("${item.price} Fc/u", color = Color.Gray, fontSize = 11.sp)
                                Text("= ${item.subtotal} Fc", color = PrimaryRed, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Bouton PLUS (+)
                                Surface(
                                    modifier = Modifier.size(26.dp).clip(CircleShape).clickable {
                                        val idx = cart.indexOf(item)
                                        if (idx >= 0) cart[idx] = item.copy(quantity = item.quantity + 1)
                                    },
                                    shape = CircleShape,
                                    color = PrimaryRed
                                ) {
                                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                                }

                                Text("${item.quantity}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))

                                // Bouton MOINS (-)
                                Surface(
                                    modifier = Modifier.size(26.dp).clip(CircleShape).clickable {
                                        val idx = cart.indexOf(item)
                                        if (idx >= 0) {
                                            if (item.quantity > 1) cart[idx] = item.copy(quantity = item.quantity - 1)
                                            else cart.remove(item)
                                        }
                                    },
                                    shape = CircleShape,
                                    color = Color.LightGray
                                ) {
                                    Icon(Icons.Default.Remove, null, tint = Color.DarkGray, modifier = Modifier.padding(4.dp))
                                }
                            }

                            IconButton(onClick = { cart.remove(item) }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // 3. PIED DE PAGE (RÉSUMÉ ET BOUTON) - Uniquement si panier non vide
            Card3D(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("${cart.sumOf { it.quantity }} articles", color = Color.Gray, fontSize = 13.sp)
                    Text("$total Fc", fontWeight = FontWeight.ExtraBold, color = PrimaryRed, fontSize = 17.sp)
                }
                Spacer(Modifier.height(5.dp))
                val adresseStr = userData?.child("adresse")?.value?.toString() ?: ""
                Text("📍 ${if (adresseStr.isEmpty()) "Adresse non définie" else adresseStr}", color = Color.Gray, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val addr = userData?.child("adresse")?.value?.toString()
                    if (addr.isNullOrEmpty()) {
                        Toast.makeText(ctx, "Complétez votre adresse dans le profil !", Toast.LENGTH_LONG).show()
                    } else {
                        showPayment = true
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                colors = ButtonDefaults.buttonColors(PrimaryRed),
                shape = RoundedCornerShape(18.dp),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, null, Modifier.padding(end = 8.dp))
                Text("Commander · $total Fc", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // 4. DIALOGUE DE PAIEMENT (À l'intérieur de la fonction, mais en dehors de la Column)
    if (showPayment) {
        PaymentDialog(
            total = total,
            onDismiss = { showPayment = false },
            onCash = {
                showPayment = false
                placeOrder(cart, userData, "PAIEMENT_RECEPTION", onSuccess, ctx)
            },
            onMpesa = { method ->
                showPayment = false
                placeOrder(cart, userData, method, onSuccess, ctx)
            }
        )
    }
}
@Composable
fun PaymentDialog(
    total: Double,
    onDismiss: () -> Unit,
    onCash: () -> Unit,
    onMpesa: (String) -> Unit // Gardé 'onMpesa' pour la compatibilité avec ton CartScreen
) {
    var subMenu by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(20.dp)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("Mode de paiement", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("Total : $total Fc", color = PrimaryRed, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(18.dp))

                if (!subMenu) {
                    // Option Cash
                    PayOption("💵", "Payer à la réception", SuccessGreen) { onCash() }

                    Spacer(Modifier.height(10.dp))

                    // Option Mobile Money -> Ouvre le sous-menu
                    PayOption("📱", "Payer en ligne (Mobile Money)", DeliveryBlue) { subMenu = true }
                } else {
                    // Sous-menu des opérateurs
                    PayOption("🟠", "Airtel Money", PrimaryRed) { onMpesa("AIRTEL_MONEY") }
                    Spacer(Modifier.height(8.dp))
                    PayOption("🟡", "Orange Money", PendingGold) { onMpesa("ORANGE_MONEY") }
                    Spacer(Modifier.height(8.dp))
                    PayOption("🟢", "M-Pesa", SuccessGreen) { onMpesa("MPESA") }

                    Spacer(Modifier.height(12.dp))

                    TextButton(onClick = { subMenu = false }) {
                        Text("← Retour", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Annuler", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun PayOption(emoji: String, label: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(0.07f)),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.5.dp, color.copy(0.4f))
    ) {
        Row(
            Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Text(label, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = color)
        }
    }
}

fun placeOrder(cart: List<CartItem>, userData: DataSnapshot?, paymentMethod: String, onSuccess: () -> Unit, ctx: Context) {
    val uid   = Firebase.auth.currentUser?.uid ?: return
    val items = cart.map { mapOf("productId" to it.id, "name" to it.name, "price" to it.price, "quantity" to it.quantity, "subtotal" to it.subtotal) }
    Firebase.database.reference.child("orders").push().setValue(mapOf(
        "userId" to uid, "userName" to (userData?.child("nom")?.value ?: ""),
        "userAddr" to (userData?.child("adresse")?.value ?: ""), "userTel" to (userData?.child("tel")?.value ?: ""),
        "items" to items, "total" to cart.sumOf { it.subtotal }, "paymentMethod" to paymentMethod,
        "status" to "EN_ATTENTE", "livreurId" to "", "livreurName" to "",
        "livreurLat" to 0.0, "livreurLng" to 0.0, "timestamp" to System.currentTimeMillis()
    )).addOnSuccessListener {
        sendLocalNotification(ctx, "Commande envoyée !", "Votre commande est en cours de traitement 🍔")
        onSuccess()
    }.addOnFailureListener { Toast.makeText(ctx, "Erreur lors de la commande", Toast.LENGTH_SHORT).show() }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : SUIVI COMMANDE
// ─────────────────────────────────────────────────────────────
@Composable
fun OrderTrackingScreen(order: DataSnapshot, isLivreur: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(order) }
    val status by derivedStateOf { data.child("status").value?.toString() ?: "" }
    var livreurPos by remember { mutableStateOf(LatLng(0.0, 0.0)) }

    LaunchedEffect(order.key) {
        order.ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                data = s
                val lat = s.child("livreurLat").value?.toString()?.toDoubleOrNull() ?: 0.0
                val lng = s.child("livreurLng").value?.toString()?.toDoubleOrNull() ?: 0.0
                if (lat != 0.0 && lng != 0.0) livreurPos = LatLng(lat, lng)
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    val defaultPos = LatLng(-11.6642, 27.4794)
    val mapPos = if (livreurPos.latitude != 0.0) livreurPos else defaultPos

    Column(Modifier.fillMaxSize().background(SoftPink)) {
        TopBar("Suivi commande #${order.key?.takeLast(5)}", onBack = onBack)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Card3D(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    StatusChip(status)
                    Text("${data.child("total").value} Fc", color = PrimaryRed, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text("💳 ${data.child("paymentMethod").value}", color = Color.Gray, fontSize = 12.sp)
                Text("📍 ${data.child("userAddr").value}", color = Color.Gray, fontSize = 12.sp)
                val lName = data.child("livreurName").value?.toString()
                if (!lName.isNullOrEmpty()) {
                    Text("🚴 Livreur : $lName", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }

            if (status == "EN_COURS") {
                // CORRECTION ICI : Ajout de 'modifier =' pour éviter l'erreur de type
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(250.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    val cam = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(mapPos, 13f)
                    }
                    val livreurMarker = rememberMarkerState(position = mapPos)
                    val livraisonMarker = rememberMarkerState(position = defaultPos)

                    LaunchedEffect(mapPos) {
                        cam.position = CameraPosition.fromLatLngZoom(mapPos, 13f)
                        livreurMarker.position = mapPos
                    }

                    GoogleMap(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                        cameraPositionState = cam
                    ) {
                        Marker(state = livreurMarker, title = "Livreur 🚴")
                        Marker(state = livraisonMarker, title = "Livraison 📍")
                    }
                }
            }

            Card3D(Modifier.fillMaxWidth()) {
                Text("Articles commandés", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                data.child("items").children.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                        Text("• ${item.child("name").value} ×${item.child("quantity").value}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("${item.child("subtotal").value} Fc", fontSize = 13.sp, color = PrimaryRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!isLivreur && status == "EN_ATTENTE") {
                TextButton(
                    onClick = { data.ref.child("status").setValue("ANNULE") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Annuler la commande")
                }
            }

            if (isLivreur && status == "EN_COURS") {
                Button(
                    onClick = {
                        data.ref.child("status").setValue("LIVRE")
                        val uid = Firebase.auth.currentUser?.uid
                        if (uid != null) {
                            val ref = Firebase.database.reference.child("users").child(uid).child("points")
                            ref.get().addOnSuccessListener { snap ->
                                val currentPoints = snap.value?.toString()?.toIntOrNull() ?: 0
                                ref.setValue(currentPoints + 10)
                            }
                        }
                        sendLocalNotification(ctx, "Livraison terminée !", "Commande livrée +10 points 🎉")
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
                    colors = ButtonDefaults.buttonColors(SuccessGreen),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.padding(end = 8.dp))
                    Text("Confirmer la livraison ✅", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : PROFIL
// ─────────────────────────────────────────────────────────────
@Composable
fun ProfileScreen(userData: DataSnapshot?, isAdmin: Boolean, isLivreur: Boolean, onBack: () -> Unit, onNav: (String) -> Unit, onLogout: () -> Unit) {
    var nom by remember { mutableStateOf("") }
    var adr by remember { mutableStateOf("") }
    var tel by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var photoUploading by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val uid = Firebase.auth.currentUser?.uid

    LaunchedEffect(userData) {
        nom = userData?.child("nom")?.value?.toString()     ?: ""
        adr = userData?.child("adresse")?.value?.toString() ?: ""
        tel = userData?.child("tel")?.value?.toString()     ?: ""
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && uid != null) {
            photoUploading = true
            val ref = Firebase.storage.reference.child("profiles/$uid.jpg")
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    Firebase.database.reference.child("users").child(uid).child("photoUrl").setValue(url.toString())
                    photoUploading = false; Toast.makeText(ctx, "Photo mise à jour !", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { photoUploading = false; Toast.makeText(ctx, "Erreur upload photo", Toast.LENGTH_SHORT).show() }
        }
    }
    val photoUrl = userData?.child("photoUrl")?.value?.toString()

    Column(Modifier.fillMaxSize().background(SoftPink).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(210.dp).background(Brush.verticalGradient(GradientRed), RoundedCornerShape(bottomStart = 45.dp, bottomEnd = 45.dp))) {
            IconButton(onClick = onBack, Modifier.padding(16.dp)) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Surface(Modifier.size(86.dp), CircleShape, Color.White, shadowElevation = 8.dp) {
                        if (!photoUrl.isNullOrEmpty()) Image(rememberAsyncImagePainter(photoUrl), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Person, null, Modifier.padding(16.dp), tint = PrimaryRed)
                    }
                    if (photoUploading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(30.dp), color = PrimaryRed)
                    Surface(Modifier.size(26.dp).align(Alignment.BottomEnd), CircleShape, PrimaryRed, shadowElevation = 4.dp) {
                        IconButton(onClick = { photoLauncher.launch("image/*") }, Modifier.size(26.dp)) {
                            Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(nom.ifEmpty { "Mon Profil" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isAdmin) Surface(shape = RoundedCornerShape(8.dp), color = PendingGold) { Text("ADMIN", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }
                    if (isLivreur) Surface(shape = RoundedCornerShape(8.dp), color = DeliveryBlue) { Text("LIVREUR", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }
                }
            }
        }
        Column(Modifier.padding(14.dp)) {
            Card3D(Modifier.fillMaxWidth()) {
                Text("Mes informations", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Spacer(Modifier.height(10.dp))
                OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text("Nom complet") }, shape = RoundedCornerShape(14.dp), leadingIcon = { Icon(Icons.Default.Person, null) })
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(adr, { adr = it }, Modifier.fillMaxWidth(), label = { Text("Adresse") }, shape = RoundedCornerShape(14.dp), leadingIcon = { Icon(Icons.Default.LocationOn, null) })
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(tel, { tel = it }, Modifier.fillMaxWidth(), label = { Text("Téléphone") }, shape = RoundedCornerShape(14.dp), leadingIcon = { Icon(Icons.Default.Phone, null) })
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (uid == null) return@Button; saving = true
                    Firebase.database.reference.child("users").child(uid).updateChildren(mapOf("nom" to nom, "adresse" to adr, "tel" to tel))
                        .addOnSuccessListener { saving = false; Toast.makeText(ctx, "✅ Profil mis à jour !", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener { saving = false }
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(PrimaryRed), shape = RoundedCornerShape(14.dp), enabled = !saving) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else { Icon(Icons.Default.Save, null, Modifier.padding(end = 6.dp)); Text("Enregistrer") }
                }
            }
            Card3D(Modifier.fillMaxWidth(), onClick = { onNav("orders_client") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ListAlt, null, tint = PrimaryRed, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
                    Text("Mes commandes", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                }
            }
            if (isLivreur) {
                Card3D(Modifier.fillMaxWidth()) {
                    Text("Espace Livreur 🚴", color = DeliveryBlue, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    val pts = userData?.child("points")?.value?.toString() ?: "0"; Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = PendingGold.copy(0.12f), modifier = Modifier.fillMaxWidth()) {
                        Text("⭐ $pts points", color = PendingGold, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { onNav("livreur_home") }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(DeliveryBlue), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.DeliveryDining, null, Modifier.padding(end = 6.dp)); Text("Tableau de bord Livreur")
                    }
                }
            }
            if (isAdmin) {
                Card3D(Modifier.fillMaxWidth()) {
                    Text("Administration ⚙️", color = PrimaryRed, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { AdminShortcut("Commandes", Icons.Default.ShoppingCart) { onNav("admin_orders") } }
                        item { AdminShortcut("Plats",     Icons.Default.Restaurant)  { onNav("admin_products") } }
                        item { AdminShortcut("Clients",   Icons.Default.People)      { onNav("admin_users") } }
                        item { AdminShortcut("Livreurs",  Icons.Default.DeliveryDining) { onNav("admin_livreurs") } }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onLogout, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(Color(0xFF2D2D2D)), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Logout, null, Modifier.padding(end = 8.dp)); Text("Déconnexion", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun AdminShortcut(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.size(88.dp).clickable { onClick() }, RoundedCornerShape(16.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Icon(icon, null, tint = PrimaryRed, modifier = Modifier.size(24.dp)); Spacer(Modifier.height(5.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : LIVREUR HOME
// ─────────────────────────────────────────────────────────────
@Composable
fun LivreurHomeScreen(userData: DataSnapshot?, onBack: () -> Unit, onOrder: (DataSnapshot) -> Unit) {
    var available by remember { mutableStateOf(listOf<DataSnapshot>()) }
    var myOrders  by remember { mutableStateOf(listOf<DataSnapshot>()) }
    var tab       by remember { mutableStateOf(0) }
    val uid = Firebase.auth.currentUser?.uid
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        Firebase.database.reference.child("orders").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                available = s.children.filter { it.child("status").value == "EN_ATTENTE" }.toList().reversed()
                if (uid != null) myOrders = s.children.filter { it.child("livreurId").value == uid && it.child("status").value == "EN_COURS" }.toList()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    Column(Modifier.fillMaxSize().background(SoftPink)) {
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(GradientBlue), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).padding(18.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    Text("Espace Livreur 🚴", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("Bonjour ${userData?.child("nom")?.value} 👋", color = Color.White.copy(0.85f), modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    StatPill("${available.size}", "Dispo", "📦"); StatPill("${myOrders.size}", "En cours", "🚴"); StatPill("${userData?.child("points")?.value ?: 0}", "Pts", "⭐")
                }
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = PrimaryRed) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Disponibles (${available.size})", fontWeight = FontWeight.Bold) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mes livraisons (${myOrders.size})", fontWeight = FontWeight.Bold) })
        }
        when (tab) {
            0 -> {
                if (available.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🎉", fontSize = 50.sp); Text("Aucune commande disponible", color = Color.Gray) } }
                LazyColumn {
                    items(available, key = { it.key ?: "" }) { order ->
                        val payMethod = order.child("paymentMethod").value?.toString() ?: ""
                        Card3D(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("#${order.key?.takeLast(5)}", fontWeight = FontWeight.ExtraBold, color = PrimaryRed); StatusChip(order.child("status").value?.toString() ?: "") }
                            Spacer(Modifier.height(5.dp))
                            Text("👤 ${order.child("userName").value}", fontWeight = FontWeight.Medium)
                            Text("📍 ${order.child("userAddr").value}", color = Color.Gray, fontSize = 12.sp)
                            Text("📞 ${order.child("userTel").value}", color = Color.Gray, fontSize = 12.sp)
                            Spacer(Modifier.height(5.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = if (payMethod == "PAIEMENT_RECEPTION") PendingGold.copy(0.15f) else SuccessGreen.copy(0.12f), modifier = Modifier.fillMaxWidth()) {
                                Text(if (payMethod == "PAIEMENT_RECEPTION") "💵 Encaisser ${order.child("total").value} Fc" else "✅ Déjà payé en ligne",
                                    color = if (payMethod == "PAIEMENT_RECEPTION") PendingGold else SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onOrder(order) }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, DeliveryBlue)) { Text("Détails", color = DeliveryBlue) }
                                Button(onClick = {
                                    order.ref.updateChildren(mapOf("status" to "EN_COURS", "livreurId" to uid, "livreurName" to (userData?.child("nom")?.value ?: "Livreur")))
                                    sendLocalNotification(ctx, "Livraison acceptée !", "Tu livres la commande #${order.key?.takeLast(5)} 🚴"); tab = 1
                                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(DeliveryBlue)) {
                                    Icon(Icons.Default.DeliveryDining, null, Modifier.padding(end = 4.dp)); Text("Accepter")
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                if (myOrders.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🚴", fontSize = 50.sp); Text("Aucune livraison en cours", color = Color.Gray) } }
                LazyColumn {
                    items(myOrders, key = { it.key ?: "" }) { order ->
                        Card3D(Modifier.fillMaxWidth(), onClick = { onOrder(order) }) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("#${order.key?.takeLast(5)}", fontWeight = FontWeight.ExtraBold, color = PrimaryRed, fontSize = 16.sp); StatusChip(order.child("status").value?.toString() ?: "") }
                            Text("👤 ${order.child("userName").value}", fontWeight = FontWeight.Medium)
                            Text("📍 ${order.child("userAddr").value}", color = Color.Gray, fontSize = 12.sp)
                            Text("💰 ${order.child("total").value} Fc", color = PrimaryRed, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Spacer(Modifier.height(5.dp)); Text("Appuyer pour voir la map →", color = DeliveryBlue, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatPill(value: String, label: String, emoji: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(0.18f)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 18.sp); Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp); Text(label, color = Color.White.copy(0.8f), fontSize = 10.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : COMMANDES CLIENT
// ─────────────────────────────────────────────────────────────
@Composable
fun ClientOrdersScreen(onBack: () -> Unit, onOrder: (DataSnapshot) -> Unit) {
    var orders by remember { mutableStateOf(listOf<DataSnapshot>()) }
    val uid = Firebase.auth.currentUser?.uid
    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        Firebase.database.reference.child("orders").orderByChild("userId").equalTo(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) { orders = s.children.toList().reversed() }
                override fun onCancelled(e: DatabaseError) {}
            })
    }
    Column(Modifier.fillMaxSize().background(SoftPink)) {
        TopBar("Mes Commandes", onBack = onBack)
        if (orders.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🍕", fontSize = 50.sp); Text("Aucune commande", color = Color.Gray) } }
        LazyColumn {
            items(orders, key = { it.key ?: "" }) { order ->
                Card3D(Modifier.fillMaxWidth(), onClick = { onOrder(order) }) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("#${order.key?.takeLast(5)}", fontWeight = FontWeight.ExtraBold, color = PrimaryRed); StatusChip(order.child("status").value?.toString() ?: "") }
                    Spacer(Modifier.height(4.dp))
                    Text("💰 ${order.child("total").value} Fc", fontWeight = FontWeight.Medium)
                    Text("💳 ${order.child("paymentMethod").value}", color = Color.Gray, fontSize = 11.sp)
                    order.child("items").children.take(2).forEach { item -> Text("  • ${item.child("name").value} ×${item.child("quantity").value}", fontSize = 12.sp, color = Color.DarkGray) }
                    val extra = order.child("items").childrenCount - 2
                    if (extra > 0) Text("  +$extra autres...", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp)); Text("Voir le suivi →", color = DeliveryBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : ADMIN COMMANDES
// ─────────────────────────────────────────────────────────────
@Composable
fun AdminOrdersScreen(onBack: () -> Unit) {
    var orders by remember { mutableStateOf(listOf<DataSnapshot>()) }
    var filter by remember { mutableStateOf("TOUS") }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Firebase.database.reference.child("orders").addValueEventListener(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { orders = s.children.toList().reversed() }
        override fun onCancelled(e: DatabaseError) {}
    }) }
    val filtered = if (filter == "TOUS") orders else orders.filter { it.child("status").value == filter }
    Column(Modifier.fillMaxSize().background(SoftPink)) {
        TopBar("Commandes (${filtered.size})", onBack = onBack)
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("TOUS","EN_ATTENTE","EN_COURS","LIVRE","ANNULE")) { s ->
                FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s, fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryRed, selectedLabelColor = Color.White))
            }
        }
        LazyColumn {
            items(filtered, key = { it.key ?: "" }) { order ->
                val st = order.child("status").value?.toString() ?: ""
                Card3D(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("#${order.key?.takeLast(5)}", fontWeight = FontWeight.ExtraBold, color = PrimaryRed); StatusChip(st) }
                    Text("👤 ${order.child("userName").value}", fontWeight = FontWeight.Medium)
                    Text("📍 ${order.child("userAddr").value}", color = Color.Gray, fontSize = 12.sp)
                    Text("💳 ${order.child("paymentMethod").value}", color = Color.Gray, fontSize = 12.sp)
                    Text("💰 ${order.child("total").value} Fc", color = PrimaryRed, fontWeight = FontWeight.ExtraBold)
                    order.child("items").children.forEach { item -> Text("  • ${item.child("name").value} ×${item.child("quantity").value} = ${item.child("subtotal").value} Fc", fontSize = 11.sp) }
                    if (order.child("livreurName").value?.toString()?.isNotEmpty() == true) Text("🚴 ${order.child("livreurName").value}", fontSize = 12.sp, color = DeliveryBlue)
                    Divider(Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                        val tel = order.child("userTel").value?.toString()
                        if (!tel.isNullOrEmpty()) IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$tel"))) }) { Icon(Icons.Default.Phone, null, tint = SuccessGreen) }
                        if (st == "EN_ATTENTE") IconButton(onClick = { order.ref.child("status").setValue("EN_COURS") }) { Icon(Icons.Default.CheckCircle, null, tint = DeliveryBlue) }
                        if (st == "EN_COURS")   IconButton(onClick = { order.ref.child("status").setValue("LIVRE") })   { Icon(Icons.Default.ThumbUp, null, tint = SuccessGreen) }
                        if (st != "ANNULE" && st != "LIVRE") IconButton(onClick = { order.ref.child("status").setValue("ANNULE") }) { Icon(Icons.Default.Close, null, tint = Color.Red) }
                        IconButton(onClick = { order.ref.removeValue() }) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f)) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : ADMIN UTILISATEURS
// ─────────────────────────────────────────────────────────────
@Composable
fun AdminUsersScreen(onBack: () -> Unit) {
    var users by remember { mutableStateOf(listOf<DataSnapshot>()) }
    var filterRole by remember { mutableStateOf("TOUS") }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        Firebase.database.reference.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { users = s.children.toList() }
            override fun onCancelled(e: DatabaseError) { Toast.makeText(ctx, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show() }
        })
    }

    val filtered = when (filterRole) {
        "ADMIN"   -> users.filter { it.child("isAdmin").value == true }
        "LIVREUR" -> users.filter { it.child("isLivreur").value == true }
        "CLIENT"  -> users.filter { it.child("isAdmin").value != true && it.child("isLivreur").value != true }
        else      -> users
    }

    Column(Modifier.fillMaxSize().background(SoftPink)) {
        TopBar("Utilisateurs (${filtered.size})", onBack = onBack)

        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("TOUS","CLIENT","LIVREUR","ADMIN")) { role ->
                FilterChip(
                    selected = filterRole == role,
                    onClick = { filterRole = role },
                    label = { Text(role, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryRed, selectedLabelColor = Color.White)
                )
            }
        }

        if (users.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PrimaryRed)
                    Spacer(Modifier.height(12.dp))
                    Text("Chargement...", color = Color.Gray)
                }
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Aucun utilisateur", color = Color.Gray) }
        } else {
            LazyColumn {
                items(filtered, key = { it.key ?: "" }) { u ->
                    val isAdminU   = u.child("isAdmin").value == true
                    val isLivreurU = u.child("isLivreur").value == true
                    val accepted   = u.child("livreurAccepted").value == true
                    val photoUrl   = u.child("photoUrl").value?.toString()

                    Card3D(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                                color = if (isAdminU) PrimaryRed else if (isLivreurU) DeliveryBlue else Color(0xFFBBBBBB),
                                shadowElevation = 4.dp
                            ) {
                                if (!photoUrl.isNullOrEmpty()) {
                                    Image(rememberAsyncImagePainter(photoUrl), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(if (isAdminU) Icons.Default.AdminPanelSettings else if (isLivreurU) Icons.Default.DeliveryDining else Icons.Default.Person, null, Modifier.padding(12.dp), tint = Color.White)
                                }
                            }

                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(u.child("nom").value?.toString()?.ifEmpty { "(sans nom)" } ?: "(sans nom)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (isAdminU) BadgeTag("Admin", PrimaryRed)
                                    if (isLivreurU) BadgeTag(if (accepted) "Livreur ✓" else "Livreur ⏳", DeliveryBlue)
                                    if (!isAdminU && !isLivreurU) BadgeTag("Client", SuccessGreen)
                                }

                                // --- BOUTON DE VALIDATION LIVREUR ---
                                if (isLivreurU && !accepted) {
                                    Button(
                                        onClick = {
                                            val up = mapOf("isLivreur" to true, "livreurAccepted" to true)
                                            u.ref.updateChildren(up).addOnSuccessListener {
                                                Toast.makeText(ctx, "Livreur validé !", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.padding(top = 5.dp).height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(SuccessGreen),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Accepter la demande", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                                // -------------------------------------
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                val tel = u.child("tel").value?.toString()
                                Row {
                                    if (!tel.isNullOrEmpty()) {
                                        IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$tel"))) }) {
                                            Icon(Icons.Default.Phone, null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(onClick = {
                                        u.ref.child("isAdmin").setValue(!isAdminU)
                                    }) {
                                        Icon(Icons.Default.AdminPanelSettings, null, tint = if (isAdminU) PrimaryRed else Color.Gray, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { u.ref.removeValue() }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (isLivreurU) Text("⭐ ${u.child("points").value ?: 0} pts", color = PendingGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeTag(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.15f)) {
        Text(label, color = color, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontWeight = FontWeight.Bold)
    }
}
// ─────────────────────────────────────────────────────────────
// ÉCRAN : ADMIN LIVREURS
// ─────────────────────────────────────────────────────────────
@Composable
fun AdminLivreursScreen(onBack: () -> Unit) {
    var livreurs by remember { mutableStateOf(listOf<DataSnapshot>()) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        // On écoute toute la table users pour éviter les filtres qui font sauter l'UI trop vite
        Firebase.database.reference.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                // On ne garde que ceux qui sont livreurs OU qui ont une demande en attente
                livreurs = s.children.filter {
                    it.child("isLivreur").value == true || it.child("pendingLivreur").value == true
                }.toList()
            }
            override fun onCancelled(e: DatabaseError) {
                Toast.makeText(ctx, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    val pending  = livreurs.filter { it.child("livreurAccepted").value != true }
    val accepted = livreurs.filter { it.child("livreurAccepted").value == true }

    Column(Modifier.fillMaxSize().background(SoftPink)) {
        TopBar("Gestion Livreurs (${livreurs.size})", onBack = onBack)

        if (livreurs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🚴", fontSize = 50.sp)
                    Text("Aucune demande de livreur", color = Color.Gray)
                }
            }
        } else {
            LazyColumn {
                if (pending.isNotEmpty()) {
                    item { Text("⏳ Demandes en attente (${pending.size})", fontWeight = FontWeight.ExtraBold, color = PendingGold, fontSize = 14.sp, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)) }
                    items(pending, key = { it.key ?: "" }) { livreur ->
                        LivreurCard(livreur, ctx,
                            onAccept = {
                                val updates = mapOf(
                                    "isLivreur" to true,
                                    "livreurAccepted" to true,
                                    "pendingLivreur" to false
                                )
                                livreur.ref.updateChildren(updates).addOnSuccessListener {
                                    Toast.makeText(ctx, "✅ Livreur accepté !", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRefuse = {
                                val updates = mapOf(
                                    "isLivreur" to false,
                                    "livreurAccepted" to false,
                                    "pendingLivreur" to false
                                )
                                livreur.ref.updateChildren(updates).addOnSuccessListener {
                                    Toast.makeText(ctx, "❌ Demande refusée", Toast.LENGTH_SHORT).show()
                                }
                            })
                    }
                }

                if (accepted.isNotEmpty()) {
                    item { Text("✅ Livreurs actifs (${accepted.size})", fontWeight = FontWeight.ExtraBold, color = SuccessGreen, fontSize = 14.sp, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)) }
                    items(accepted, key = { "acc_${it.key}" }) { livreur ->
                        LivreurCard(livreur, ctx, isAccepted = true,
                            onSuspend = {
                                val updates = mapOf(
                                    "livreurAccepted" to false,
                                    "pendingLivreur" to false
                                )
                                livreur.ref.updateChildren(updates).addOnSuccessListener {
                                    Toast.makeText(ctx, "Livreur suspendu", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = {
                                // Attention : supprimer l'utilisateur est radical.
                                // On préfère souvent juste lui retirer ses droits.
                                val updates = mapOf(
                                    "isLivreur" to false,
                                    "livreurAccepted" to false,
                                    "pendingLivreur" to false
                                )
                                livreur.ref.updateChildren(updates).addOnSuccessListener {
                                    Toast.makeText(ctx, "Droits livreur retirés", Toast.LENGTH_SHORT).show()
                                }
                            })
                    }
                }
            }
        }
    }
}
@Composable
fun LivreurCard(livreur: DataSnapshot, ctx: Context, isAccepted: Boolean = false, onAccept: (() -> Unit)? = null, onRefuse: (() -> Unit)? = null, onSuspend: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    val points   = livreur.child("points").value?.toString()?.toIntOrNull() ?: 0
    val photoUrl = livreur.child("photoUrl").value?.toString()
    Card3D(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(56.dp), CircleShape, if (isAccepted) SuccessGreen else PendingGold, shadowElevation = 6.dp) {
                if (!photoUrl.isNullOrEmpty()) Image(rememberAsyncImagePainter(photoUrl), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.DeliveryDining, null, Modifier.padding(14.dp), tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(livreur.child("nom").value?.toString() ?: "(sans nom)", fontWeight = FontWeight.Bold)
                Text(livreur.child("tel").value?.toString() ?: "-", color = Color.Gray, fontSize = 12.sp)
                Text("⭐ $points points", color = PendingGold, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                Text(livreur.child("adresse").value?.toString() ?: "", color = Color.LightGray, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isAccepted && onAccept != null) {
                    IconButton(onClick = onAccept) { Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(28.dp)) }
                    IconButton(onClick = { onRefuse?.invoke() }) { Icon(Icons.Default.Cancel, null, tint = Color.Red, modifier = Modifier.size(28.dp)) }
                } else {
                    Surface(shape = RoundedCornerShape(10.dp), color = SuccessGreen.copy(0.15f)) { Text("✅ Actif", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(6.dp)) }
                    Spacer(Modifier.height(4.dp))
                    IconButton(onClick = { onSuspend?.invoke() }) { Icon(Icons.Default.PauseCircle, null, tint = PendingGold, modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = { onDelete?.invoke() })  { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.7f), modifier = Modifier.size(22.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : ADMIN PRODUITS
// ─────────────────────────────────────────────────────────────
@Composable
fun AdminProductsScreen(onBack: () -> Unit, onEdit: (DataSnapshot) -> Unit, onAdd: () -> Unit) {
    var products by remember { mutableStateOf(listOf<DataSnapshot>()) }
    LaunchedEffect(Unit) { Firebase.database.reference.child("products").addValueEventListener(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { products = s.children.toList() }
        override fun onCancelled(e: DatabaseError) {}
    }) }
    Scaffold(floatingActionButton = { FloatingActionButton(onClick = onAdd, containerColor = PrimaryRed, shape = CircleShape, elevation = FloatingActionButtonDefaults.elevation(8.dp)) { Icon(Icons.Default.Add, null, tint = Color.White) } }, containerColor = SoftPink) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TopBar("Catalogue (${products.size})", onBack = onBack)
            LazyColumn {
                items(products, key = { it.key ?: "" }) { prod ->
                    Card3D(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(rememberAsyncImagePainter(prod.child("imageUrl").value), null, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(prod.child("name").value?.toString() ?: "", fontWeight = FontWeight.Bold)
                                Text("${prod.child("price").value} Fc", color = PrimaryRed, fontWeight = FontWeight.ExtraBold)
                                Text(prod.child("category").value?.toString() ?: "", color = Color.Gray, fontSize = 12.sp)
                            }
                            IconButton(onClick = { onEdit(prod) }) { Icon(Icons.Default.Edit, null, tint = DeliveryBlue) }
                            IconButton(onClick = { prod.ref.removeValue() }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : AJOUT / MODIFICATION PRODUIT
// ─────────────────────────────────────────────────────────────
@Composable
fun AddProductScreen(existing: DataSnapshot?, onBack: () -> Unit) {
    var name     by remember { mutableStateOf(existing?.child("name")?.value?.toString()        ?: "") }
    var price    by remember { mutableStateOf(existing?.child("price")?.value?.toString()       ?: "") }
    var category by remember { mutableStateOf(existing?.child("category")?.value?.toString()    ?: "") }
    var desc     by remember { mutableStateOf(existing?.child("description")?.value?.toString() ?: "") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var uploading by remember { mutableStateOf(false) }
    val ctx      = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it }

    Column(Modifier.fillMaxSize().background(SoftPink)) {
        TopBar(if (existing == null) "Ajouter un plat" else "Modifier le plat", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).border(2.dp, PrimaryRed.copy(0.35f), RoundedCornerShape(22.dp)).clickable { launcher.launch("image/*") }.shadow(4.dp, RoundedCornerShape(22.dp)), Alignment.Center) {
                val imgSrc = imageUri ?: existing?.child("imageUrl")?.value
                if (imgSrc == null) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(48.dp), tint = PrimaryRed); Text("Ajouter une photo", color = PrimaryRed, fontWeight = FontWeight.Medium) } }
                else { Image(rememberAsyncImagePainter(imgSrc), null, Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(Color.Black.copy(0.2f), RoundedCornerShape(22.dp))); Icon(Icons.Default.Edit, null, Modifier.align(Alignment.Center).size(38.dp), tint = Color.White) }
            }
            Spacer(Modifier.height(14.dp))
            listOf(Triple(name, { v: String -> name = v }, "Nom du plat"), Triple(price, { v: String -> price = v }, "Prix (Fc)"), Triple(category, { v: String -> category = v }, "Catégorie (Pizza, Burger…)")).forEach { (v, onChange, label) ->
                OutlinedTextField(v, onChange, Modifier.fillMaxWidth().padding(bottom = 8.dp), label = { Text(label) }, shape = RoundedCornerShape(14.dp))
            }
            OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, shape = RoundedCornerShape(14.dp), minLines = 3)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (name.isBlank() || price.isBlank()) { Toast.makeText(ctx, "Nom et prix sont obligatoires", Toast.LENGTH_SHORT).show(); return@Button }
                uploading = true
                if (imageUri != null) {
                    uploadToImgBB(imageUri!!, ctx) { url ->
                        val data = mapOf("name" to name, "price" to price, "category" to category, "description" to desc, "imageUrl" to url)
                        if (existing == null) Firebase.database.reference.child("products").push().setValue(data).addOnSuccessListener { onBack() }
                        else existing.ref.updateChildren(data).addOnSuccessListener { onBack() }
                    }
                } else {
                    if (existing == null) { uploading = false; Toast.makeText(ctx, "Veuillez choisir une image", Toast.LENGTH_SHORT).show() }
                    else { val data = mapOf("name" to name, "price" to price, "category" to category, "description" to desc); existing.ref.updateChildren(data).addOnSuccessListener { onBack() }.addOnFailureListener { uploading = false } }
                }
            }, Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(PrimaryRed), shape = RoundedCornerShape(18.dp), enabled = !uploading, elevation = ButtonDefaults.buttonElevation(8.dp)) {
                if (uploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                else { Icon(Icons.Default.Save, null, Modifier.padding(end = 6.dp)); Text(if (existing == null) "Enregistrer le plat" else "Mettre à jour", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ÉCRAN : AUTH
// ─────────────────────────────────────────────────────────────
@Composable
fun AuthScreen(onSuccess: () -> Unit, onBack: () -> Unit) {
    var email       by remember { mutableStateOf("") }
    var pass        by remember { mutableStateOf("") }
    var nom         by remember { mutableStateOf("") }
    var isRegister  by remember { mutableStateOf(false) }
    var wantLivreur by remember { mutableStateOf(false) }
    var loading     by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(GradientRed)))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("🍔", fontSize = 56.sp)
            Text("Saveurs Express", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (isRegister) "Créer un compte" else "Bon retour !", color = Color.White.copy(0.8f), fontSize = 15.sp)
            Spacer(Modifier.height(26.dp))
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(14.dp)) {
                Column(Modifier.padding(22.dp)) {
                    if (isRegister) { OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text("Nom complet") }, shape = RoundedCornerShape(14.dp), leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true); Spacer(Modifier.height(10.dp)) }
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, shape = RoundedCornerShape(14.dp), leadingIcon = { Icon(Icons.Default.Email, null) }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("Mot de passe") }, shape = RoundedCornerShape(14.dp), visualTransformation = PasswordVisualTransformation(), leadingIcon = { Icon(Icons.Default.Lock, null) }, singleLine = true)
                    if (isRegister) {
                        Spacer(Modifier.height(12.dp))
                        Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { wantLivreur = !wantLivreur }, RoundedCornerShape(14.dp),
                            CardDefaults.cardColors(if (wantLivreur) DeliveryBlue.copy(0.08f) else Color(0xFFF5F5F5)), border = if (wantLivreur) BorderStroke(2.dp, DeliveryBlue) else null) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(wantLivreur, { wantLivreur = it }, colors = CheckboxDefaults.colors(checkedColor = DeliveryBlue))
                                Column(Modifier.padding(start = 8.dp)) { Text("Devenir livreur 🚴", fontWeight = FontWeight.Bold, color = if (wantLivreur) DeliveryBlue else Color.DarkGray); Text("Demande soumise à validation admin", fontSize = 10.sp, color = Color.Gray) }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = {
                        if (loading) return@Button
                        if (email.isBlank() || pass.isBlank()) { Toast.makeText(ctx, "Email et mot de passe requis", Toast.LENGTH_SHORT).show(); return@Button }
                        if (isRegister && nom.isBlank()) { Toast.makeText(ctx, "Le nom est requis", Toast.LENGTH_SHORT).show(); return@Button }
                        if (pass.length < 6) { Toast.makeText(ctx, "Mot de passe : 6 caractères minimum", Toast.LENGTH_SHORT).show(); return@Button }
                        loading = true
                        if (isRegister) {
                            Firebase.auth.createUserWithEmailAndPassword(email.trim(), pass.trim())
                                .addOnSuccessListener { result ->
                                    val uid = result.user?.uid ?: return@addOnSuccessListener
                                    Firebase.database.reference.child("users").child(uid).setValue(mapOf(
                                        "nom" to nom.trim(), "adresse" to "", "tel" to "", "photoUrl" to "",
                                        "isAdmin" to false, "isLivreur" to wantLivreur, "livreurAccepted" to false, "points" to 0
                                    )).addOnSuccessListener {
                                        loading = false
                                        if (wantLivreur) sendLocalNotification(ctx, "Demande livreur envoyée !", "L'admin examinera votre demande 🚴")
                                        sendLocalNotification(ctx, "Bienvenue sur Saveurs Express ! 🍔", "Compte créé avec succès."); onSuccess()
                                    }.addOnFailureListener { e -> loading = false; Toast.makeText(ctx, "Erreur: ${e.message}", Toast.LENGTH_LONG).show() }
                                }
                                .addOnFailureListener { e -> loading = false; Toast.makeText(ctx, when { e.message?.contains("email-already-in-use") == true -> "Email déjà utilisé"; e.message?.contains("invalid-email") == true -> "Email invalide"; e.message?.contains("weak-password") == true -> "Mot de passe trop faible"; else -> "Erreur: ${e.message}" }, Toast.LENGTH_LONG).show() }
                        } else {
                            Firebase.auth.signInWithEmailAndPassword(email.trim(), pass.trim())
                                .addOnSuccessListener { loading = false; onSuccess() }
                                .addOnFailureListener { e -> loading = false; Toast.makeText(ctx, when { e.message?.contains("wrong-password") == true || e.message?.contains("invalid-credential") == true -> "Email ou mot de passe incorrect"; e.message?.contains("user-not-found") == true -> "Aucun compte avec cet email"; else -> "Erreur: ${e.message}" }, Toast.LENGTH_LONG).show() }
                        }
                    }, Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(PrimaryRed), shape = RoundedCornerShape(16.dp), enabled = !loading, elevation = ButtonDefaults.buttonElevation(6.dp)) {
                        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White)
                        else Text(if (isRegister) "Créer mon compte" else "Se connecter", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = { isRegister = !isRegister; wantLivreur = false; loading = false }) { Text(if (isRegister) "Déjà inscrit ? Se connecter" else "Pas de compte ? S'inscrire", color = Color.White) }
            TextButton(onClick = onBack) { Text("Retour à l'accueil", color = Color.White.copy(0.7f)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// NOTIFICATIONS + GPS + UPLOAD
// ─────────────────────────────────────────────────────────────
fun createNotificationChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel("saveurs_express", "Saveurs Express", NotificationManager.IMPORTANCE_HIGH).apply { description = "Notifications commandes et livraisons" }
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }
}

fun sendLocalNotification(ctx: Context, title: String, message: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val n = NotificationCompat.Builder(ctx, "saveurs_express").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
    (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(System.currentTimeMillis().toInt(), n)
}

fun startLocationUpdates(ctx: Context, uid: String) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    val client = LocationServices.getFusedLocationProviderClient(ctx)
    val req    = LocationRequest.Builder(5000L).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build()
    client.requestLocationUpdates(req, object : LocationCallback() {
        override fun onLocationResult(res: LocationResult) {
            val loc = res.lastLocation ?: return
            Firebase.database.reference.child("users").child(uid).updateChildren(mapOf("lat" to loc.latitude, "lng" to loc.longitude))
            Firebase.database.reference.child("orders").orderByChild("livreurId").equalTo(uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) { s.children.filter { it.child("status").value == "EN_COURS" }.forEach { order -> order.ref.updateChildren(mapOf("livreurLat" to loc.latitude, "livreurLng" to loc.longitude)) } }
                    override fun onCancelled(e: DatabaseError) {}
                })
        }
    }, Looper.getMainLooper())
}

fun uploadToImgBB(uri: Uri, ctx: Context, onResult: (String) -> Unit) {
    try {
        val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes() ?: return
        val body  = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("key", "71267471679a1414c6b726b82d1601a0").addFormDataPart("image", "img.jpg", RequestBody.create("image/jpeg".toMediaTypeOrNull(), bytes)).build()
        OkHttpClient().newCall(Request.Builder().url("https://api.imgbb.com/1/upload").post(body).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { android.os.Handler(Looper.getMainLooper()).post { onResult("") } }
            override fun onResponse(call: Call, response: Response) {
                val url = response.body?.string()?.substringAfter("\"url\":\"")?.substringBefore("\"")?.replace("\\/", "/")
                android.os.Handler(Looper.getMainLooper()).post { onResult(url ?: "") }
            }
        })
    } catch (e: Exception) { onResult("") }
}