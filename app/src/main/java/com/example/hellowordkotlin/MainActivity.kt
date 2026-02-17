package com.example.hellowordkotlin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hellowordkotlin.ui.theme.BgGray
import com.example.hellowordkotlin.ui.theme.HelloWordKotlinTheme
import com.example.hellowordkotlin.ui.theme.PrimaryRed
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

// --- 1. Modèle de données ---
data class Product(val id: Int, val name: String, val category: String, val price: Double, val imageRes: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloWordKotlinTheme {
                var selectedProduct by remember { mutableStateOf<Product?>(null) }
                var isCartOpen by remember { mutableStateOf(false) }
                val cartItems = remember { mutableStateListOf<Product>() }
                val context = LocalContext.current

                Box(modifier = Modifier.fillMaxSize().background(BgGray)) {
                    when {
                        isCartOpen -> {
                            CartScreen(
                                cartItems = cartItems,
                                onBack = { isCartOpen = false },
                                onRemove = { cartItems.remove(it) }
                            )
                        }
                        selectedProduct != null -> {
                            DetailScreen(
                                product = selectedProduct!!,
                                onBack = { selectedProduct = null },
                                onAddToCart = {
                                    cartItems.add(it)
                                    Toast.makeText(context, "${it.name} ajouté !", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        else -> {
                            HomeScreen(
                                onProductClick = { selectedProduct = it },
                                onAddToCart = {
                                    cartItems.add(it)
                                    Toast.makeText(context, "${it.name} ajouté !", Toast.LENGTH_SHORT).show()
                                }
                            )
                            BottomNavBar(
                                cartSize = cartItems.size,
                                onCartClick = { isCartOpen = true },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- 2. Écran d'accueil ---
@Composable
fun HomeScreen(onProductClick: (Product) -> Unit, onAddToCart: (Product) -> Unit) {
    val categories = listOf("All", "Pizza", "Burger", "Sandwich", "Drinks")
    var selectedCategory by remember { mutableStateOf("All") }

    // MISE À JOUR : Vérifie bien tes noms de fichiers ici !
    val allProducts = listOf(
        Product(1, "Classic Burger", "Burger", 250.0, R.drawable.burger),
        Product(2, "Pizza Pepperoni", "Pizza", 350.0, R.drawable.pizza),
        Product(3, "Cheese Sandwich", "Sandwich", 200.0, R.drawable.sandwich),
        Product(4, "Fresh Drink", "Drinks", 120.0, R.drawable.burger) // REMPLACE burger PAR LE NOM DE TON IMAGE BOISSON
    )

    val filteredProducts = if (selectedCategory == "All") allProducts else allProducts.filter { it.category == selectedCategory }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(50.dp))
        Text("Choose\nYour Favorite Food", fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 35.sp)

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(55.dp).background(Color.White, RoundedCornerShape(15.dp)).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Color.Gray)
            Text(" Search...", color = Color.LightGray, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(25.dp))

        LazyRow {
            items(categories) { category ->
                CategoryItem(
                    name = category,
                    isSelected = category == selectedCategory,
                    onSelect = { selectedCategory = category }
                )
            }
        }

        Spacer(modifier = Modifier.height(25.dp))
        Text("Popular Food", fontWeight = FontWeight.Bold, fontSize = 20.sp)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp)
        ) {
            items(filteredProducts) { product ->
                FoodCard(product, onClick = { onProductClick(product) }, onAddToCart = { onAddToCart(product) })
            }
        }
    }
}

// --- 3. Écran Panier ---
@Composable
fun CartScreen(cartItems: MutableList<Product>, onBack: () -> Unit, onRemove: (Product) -> Unit) {
    val totalPrice = cartItems.sumOf { it.price }
    val context = LocalContext.current

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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(product.imageRes), null, modifier = Modifier.size(50.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(product.name, fontWeight = FontWeight.Bold)
                                Text("${product.price} Rs", color = PrimaryRed)
                            }
                        }
                        IconButton(onClick = { onRemove(product) }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Gray)
                        }
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
                    val database = Firebase.database.reference.child("orders")
                    val orderData = mapOf(
                        "items" to cartItems.map { it.name },
                        "total" to totalPrice,
                        "status" to "En attente",
                        "timestamp" to System.currentTimeMillis()
                    )

                    database.push().setValue(orderData)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Commande envoyée !", Toast.LENGTH_LONG).show()
                            // SOLUTION COMPATIBLE POUR VIDER LA LISTE
                            cartItems.removeAll { true }
                            onBack()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Erreur réseau...", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                shape = RoundedCornerShape(20.dp),
                enabled = cartItems.isNotEmpty()
            ) {
                Text("Commander maintenant", fontSize = 18.sp)
            }
        }
    }
}

// --- 4. Écran Détails ---
@Composable
fun DetailScreen(product: Product, onBack: () -> Unit, onAddToCart: (Product) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.45f).background(PrimaryRed, RoundedCornerShape(bottomStart = 50.dp, bottomEnd = 50.dp)),
            contentAlignment = Alignment.Center
        ) {
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
            Button(
                onClick = { onAddToCart(product) },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Add to Cart", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- 5. Composants réutilisables ---

@Composable
fun FoodCard(product: Product, onClick: () -> Unit, onAddToCart: () -> Unit) {
    Card(
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.padding(8.dp).fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
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
    Surface(
        modifier = Modifier.padding(end = 12.dp).clickable { onSelect() },
        shape = RoundedCornerShape(25.dp),
        color = if (isSelected) PrimaryRed else Color.White,
        shadowElevation = 2.dp
    ) {
        Text(text = name, modifier = Modifier.padding(horizontal = 25.dp, vertical = 10.dp), color = if (isSelected) Color.White else Color.Black)
    }
}

@Composable
fun BottomNavBar(cartSize: Int, onCartClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(20.dp).fillMaxWidth().height(70.dp),
        color = Color(0xFF3E0A0A),
        shape = RoundedCornerShape(35.dp)
    ) {
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
            Icon(Icons.Default.Person, null, tint = Color.Gray)
        }
    }
}