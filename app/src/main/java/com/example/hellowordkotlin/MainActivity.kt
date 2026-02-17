package com.example.hellowordkotlin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- IMPORTS DES COULEURS (Vérifie bien que ces fichiers existent dans ton dossier ui/theme) ---
import com.example.hellowordkotlin.ui.theme.BgGray
import com.example.hellowordkotlin.ui.theme.HelloWordKotlinTheme
import com.example.hellowordkotlin.ui.theme.PrimaryRed

// --- IMPORTS FIREBASE ---
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import com.google.firebase.database.DataSnapshot
// --- Modèles ---
data class Product(val id: Int, val name: String, val category: String, val price: Double, val imageRes: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloWordKotlinTheme {
                var user by remember { mutableStateOf(Firebase.auth.currentUser) }
                var isAdmin by remember { mutableStateOf(false) }

                var selectedProduct by remember { mutableStateOf<Product?>(null) }
                var isCartOpen by remember { mutableStateOf(false) }
                var showAuthScreen by remember { mutableStateOf(false) }
                var isProfileOpen by remember { mutableStateOf(false) }
                var isAdminMode by remember { mutableStateOf(false) }

                val cartItems = remember { mutableStateListOf<Product>() }
                val context = LocalContext.current

                // Logique pour vérifier si l'utilisateur est Admin
                LaunchedEffect(user) {
                    if (user != null) {
                        Firebase.database.reference.child("users").child(user!!.uid).child("isAdmin")
                            .get().addOnSuccessListener { snapshot ->
                                isAdmin = snapshot.value == true
                            }
                    } else {
                        isAdmin = false
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(BgGray)) {
                    when {
                        isAdminMode -> AdminOrdersScreen(onBack = { isAdminMode = false })

                        isProfileOpen -> ProfileScreen(
                            isAdmin = isAdmin,
                            onBack = { isProfileOpen = false },
                            onAdminClick = {
                                isProfileOpen = false
                                isAdminMode = true
                            },
                            onLogout = {
                                user = null
                                isProfileOpen = false
                            }
                        )

                        showAuthScreen -> AuthScreen(
                            onAuthSuccess = {
                                user = Firebase.auth.currentUser
                                showAuthScreen = false
                            },
                            onBack = { showAuthScreen = false }
                        )

                        isCartOpen -> CartScreen(
                            cartItems = cartItems,
                            onBack = { isCartOpen = false },
                            onRemove = { cartItems.remove(it) }
                        )

                        selectedProduct != null -> DetailScreen(
                            product = selectedProduct!!,
                            onBack = { selectedProduct = null },
                            onAddToCart = { prod ->
                                if (user == null) showAuthScreen = true
                                else {
                                    cartItems.add(prod)
                                    Toast.makeText(context, "${prod.name} ajouté !", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        else -> {
                            HomeScreen(
                                onProductClick = { selectedProduct = it },
                                onAddToCart = { prod ->
                                    if (user == null) showAuthScreen = true
                                    else {
                                        cartItems.add(prod)
                                        Toast.makeText(context, "${prod.name} ajouté !", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            BottomNavBar(
                                cartSize = cartItems.size,
                                onCartClick = {
                                    if (user == null) showAuthScreen = true
                                    else isCartOpen = true
                                },
                                onProfileClick = {
                                    if (user == null) showAuthScreen = true
                                    else isProfileOpen = true
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- ÉCRAN : AUTHENTIFICATION ---
@Composable
fun AuthScreen(onAuthSuccess: () -> Unit, onBack: () -> Unit) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val auth = Firebase.auth
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())) {
        Box(modifier = Modifier.fillMaxWidth().height(280.dp).background(PrimaryRed), contentAlignment = Alignment.Center) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 10.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(80.dp))
                Text("Food", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Column(modifier = Modifier.fillMaxSize().offset(y = (-30).dp).background(Color.White, RoundedCornerShape(topStart = 35.dp, topEnd = 35.dp)).padding(30.dp)) {
            Text(if (isSignUp) "Create Account" else "Welcome Back", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(20.dp))

            Text("Email Address", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), placeholder = { Text("Enter your email") })

            Spacer(modifier = Modifier.height(15.dp))

            Text("Password", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Enter your password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) } }
            )

            if (isSignUp) {
                Spacer(modifier = Modifier.height(15.dp))
                Text("Confirm Password", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), placeholder = { Text("Confirm your password") }, visualTransformation = PasswordVisualTransformation())
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = {
                    if (isSignUp) {
                        if (password == confirmPassword) {
                            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener {
                                if (it.isSuccessful) onAuthSuccess() else Toast.makeText(context, it.exception?.localizedMessage, Toast.LENGTH_LONG).show()
                            }
                        } else Toast.makeText(context, "Mots de passe différents", Toast.LENGTH_SHORT).show()
                    } else {
                        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
                            if (it.isSuccessful) onAuthSuccess() else Toast.makeText(context, "Erreur de connexion", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                shape = RoundedCornerShape(15.dp)
            ) { Text(if (isSignUp) "Sign Up" else "Sign In", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(if (isSignUp) "Already have an account? " else "Don't have an account? ")
                Text(if (isSignUp) "Sign In" else "Sign Up", color = PrimaryRed, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { isSignUp = !isSignUp })
            }
        }
    }
}

// --- ÉCRAN D'ACCUEIL ---
@Composable
fun HomeScreen(onProductClick: (Product) -> Unit, onAddToCart: (Product) -> Unit) {
    // --- État de la recherche et des catégories ---
    val categories = listOf("All", "Pizza", "Burger", "Sandwich", "Drinks")
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") } // Pour la barre de recherche

    // --- Ta liste de produits ---
    val allProducts = listOf(
        Product(1, "Classic Burger", "Burger", 250.0, R.drawable.burger),
        Product(2, "Pizza Pepperoni", "Pizza", 350.0, R.drawable.pizza),
        Product(3, "Cheese Sandwich", "Sandwich", 200.0, R.drawable.sandwich),
        Product(4, "Fresh Drink", "Drinks", 120.0, R.drawable.burger) // Change l'image si tu as un icône boisson
    )

    // --- Logique de filtrage (Catégorie + Recherche) ---
    val filteredProducts = allProducts.filter { product ->
        val matchCategory = if (selectedCategory == "All") true else product.category == selectedCategory
        val matchSearch = product.name.contains(searchQuery, ignoreCase = true)
        matchCategory && matchSearch
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(60.dp))

        // --- EN-TÊTE DE BIENVENUE ---
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Salut 👋", fontSize = 16.sp, color = Color.Gray)
                Text("C'est l'heure du miam !", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }
            Surface(
                shape = CircleShape,
                modifier = Modifier.size(45.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(8.dp), tint = PrimaryRed)
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        // --- BARRE DE RECHERCHE FONCTIONNELLE ---
        Surface(
            modifier = Modifier.fillMaxWidth().height(55.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 3.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 15.dp)) {
                Icon(Icons.Default.Search, null, tint = Color.Gray)
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Chercher un délice...", color = Color.LightGray)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        // --- LISTE DES CATÉGORIES ---
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(categories) { category ->
                CategoryItem(
                    name = category,
                    isSelected = category == selectedCategory,
                    onSelect = { selectedCategory = category }
                )
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        // --- GRILLE DE PRODUITS ---
        Text("Populaire à Lubumbashi", fontWeight = FontWeight.Bold, fontSize = 20.sp)

        if (filteredProducts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun produit trouvé 🍕", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredProducts) { product ->
                    FoodCard(
                        product = product,
                        onClick = { onProductClick(product) },
                        onAddToCart = { onAddToCart(product) }
                    )
                }
            }
        }
    }
}
// --- ÉCRAN PANIER ---
@Composable
fun CartScreen(cartItems: MutableList<Product>, onBack: () -> Unit, onRemove: (Product) -> Unit) {
    val totalPrice = cartItems.sumOf { it.price }
    val context = LocalContext.current
    val user = Firebase.auth.currentUser

    Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text("Mon Panier", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(20.dp))
        if (cartItems.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Votre panier est vide", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(cartItems) { product ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(product.imageRes), null, modifier = Modifier.size(50.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(product.name, fontWeight = FontWeight.Bold)
                                Text("${product.price} Rs", color = PrimaryRed)
                            }
                        }
                        IconButton(onClick = { onRemove(product) }) { Icon(Icons.Default.Delete, null, tint = Color.Gray) }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${totalPrice} Rs", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryRed)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (user == null) {
                        Toast.makeText(context, "Connectez-vous !", Toast.LENGTH_SHORT).show()
                    } else {
                        val database = Firebase.database.reference.child("orders")
                        val orderData = mapOf(
                            "userId" to user.uid,
                            "userEmail" to user.email,
                            "items" to cartItems.map { it.name },
                            "total" to totalPrice,
                            "status" to "En attente",
                            "timestamp" to System.currentTimeMillis()
                        )
                        database.push().setValue(orderData).addOnSuccessListener {
                            Toast.makeText(context, "Commande envoyée !", Toast.LENGTH_LONG).show()
                            cartItems.clear()
                            onBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                shape = RoundedCornerShape(20.dp),
                enabled = cartItems.isNotEmpty()
            ) { Text("Commander maintenant", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// --- ÉCRAN DÉTAILS ---
@Composable
fun DetailScreen(product: Product, onBack: () -> Unit, onAddToCart: (Product) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.45f).background(PrimaryRed, RoundedCornerShape(bottomStart = 50.dp, bottomEnd = 50.dp)), contentAlignment = Alignment.Center) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 20.dp)) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Image(painter = painterResource(id = product.imageRes), null, modifier = Modifier.size(220.dp))
        }
        Column(modifier = Modifier.padding(25.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = product.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(text = "Rs. ${product.price}", fontSize = 24.sp, color = PrimaryRed, fontWeight = FontWeight.Bold)
            }
            Text(text = product.category, color = Color.Gray)
            Spacer(modifier = Modifier.height(20.dp))
            Text("A delicious ${product.name} prepared with fresh ingredients.", color = Color.Gray, lineHeight = 22.sp)
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { onAddToCart(product) }, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed), shape = RoundedCornerShape(20.dp)) {
                Text("Add to Cart", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- ÉCRAN PROFIL ---
@Composable
fun ProfileScreen(isAdmin: Boolean, onBack: () -> Unit, onAdminClick: () -> Unit, onLogout: () -> Unit) {
    val user = Firebase.auth.currentUser
    val database = Firebase.database.reference.child("users").child(user?.uid ?: "")
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        database.get().addOnSuccessListener { snapshot ->
            phone = snapshot.child("phone").value.toString().replace("null", "")
            address = snapshot.child("address").value.toString().replace("null", "")
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text("Mon Profil", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Email: ${user?.email}", color = Color.Gray)

        if (isAdmin) {
            Button(onClick = onAdminClick, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) {
                Text("🛠️ GESTION COMMANDES (ADMIN)")
            }
        }

        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse de livraison") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

        Spacer(modifier = Modifier.height(30.dp))
        Button(onClick = {
            database.updateChildren(mapOf("phone" to phone, "address" to address)).addOnSuccessListener {
                Toast.makeText(context, "Profil sauvegardé !", Toast.LENGTH_SHORT).show()
            }
        }, modifier = Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed), shape = RoundedCornerShape(15.dp)) {
            Text("Sauvegarder les infos")
        }

        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = {
            Firebase.auth.signOut()
            onLogout()
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Se déconnecter", color = Color.Red)
        }
    }
}

// --- ÉCRAN ADMIN ---
@Composable
fun AdminOrdersScreen(onBack: () -> Unit) {
    val database = Firebase.database.reference.child("orders")
    var ordersList by remember { mutableStateOf(listOf<DataSnapshot>()) }

    LaunchedEffect(Unit) {
        database.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ordersList = snapshot.children.reversed()
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    Column(modifier = Modifier.fillMaxSize().background(BgGray).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text("Gestion des Commandes", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn {
            items(ordersList) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Client: ${order.child("userEmail").value}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            // BOUTON SUPPRIMER
                            IconButton(onClick = { order.ref.removeValue() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color.Gray)
                            }
                        }
                        Text("Articles: ${order.child("items").value}", fontSize = 14.sp)
                        Text("Total: ${order.child("total").value} Rs", color = PrimaryRed, fontWeight = FontWeight.Bold)

                        Text(
                            "Statut: ${order.child("status").value}",
                            color = if (order.child("status").value == "Livré") Color(0xFF4CAF50) else Color.Blue,
                            fontWeight = FontWeight.Bold
                        )

                        Row(modifier = Modifier.padding(top = 12.dp)) {
                            Button(
                                onClick = { order.ref.child("status").setValue("En préparation") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
                                modifier = Modifier.weight(1f).height(35.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Préparer", fontSize = 11.sp, color = Color.White) }

                            Spacer(Modifier.width(8.dp))

                            Button(
                                onClick = { order.ref.child("status").setValue("Livré") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                modifier = Modifier.weight(1f).height(35.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Livré", fontSize = 11.sp, color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

// --- COMPOSANTS ---
@Composable
fun FoodCard(product: Product, onClick: () -> Unit, onAddToCart: () -> Unit) {
    Card(shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.padding(8.dp).fillMaxWidth().clickable { onClick() }, elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(id = product.imageRes), null, modifier = Modifier.size(90.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = product.name, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${product.price} Rs", fontWeight = FontWeight.ExtraBold)
                Surface(shape = CircleShape, color = PrimaryRed, modifier = Modifier.size(32.dp).clickable { onAddToCart() }) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
fun CategoryItem(name: String, isSelected: Boolean, onSelect: () -> Unit) {
    Surface(modifier = Modifier.padding(end = 12.dp).clickable { onSelect() }, shape = RoundedCornerShape(25.dp), color = if (isSelected) PrimaryRed else Color.White, shadowElevation = 2.dp) {
        Text(text = name, modifier = Modifier.padding(horizontal = 25.dp, vertical = 10.dp), color = if (isSelected) Color.White else Color.Black)
    }
}

@Composable
fun BottomNavBar(cartSize: Int, onCartClick: () -> Unit, onProfileClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.padding(20.dp).fillMaxWidth().height(70.dp), color = Color(0xFF3E0A0A), shape = RoundedCornerShape(35.dp)) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Home, null, tint = Color.White)
            Icon(Icons.Default.FavoriteBorder, null, tint = Color.Gray)
            Box(modifier = Modifier.clickable { onCartClick() }) {
                Icon(Icons.Default.ShoppingCart, null, tint = Color.Gray)
                if (cartSize > 0) {
                    Surface(color = Color.Red, shape = CircleShape, modifier = Modifier.size(18.dp).align(Alignment.TopEnd).offset(x = 10.dp, y = (-8).dp)) {
                        Text(cartSize.toString(), color = Color.White, fontSize = 10.sp, modifier = Modifier.wrapContentSize(Alignment.Center))
                    }
                }
            }
            Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.clickable { onProfileClick() })
        }
    }
}