package com.example.hellowordkotlin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter

// --- THEME ---
import com.example.hellowordkotlin.ui.theme.BgGray
import com.example.hellowordkotlin.ui.theme.HelloWordKotlinTheme
import com.example.hellowordkotlin.ui.theme.PrimaryRed

// --- FIREBASE ---
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import com.google.firebase.database.DataSnapshot

// --- RÉSEAU (OKHTTP) ---
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloWordKotlinTheme {
                var user by remember { mutableStateOf(Firebase.auth.currentUser) }
                var isAdmin by remember { mutableStateOf(false) }

                var selectedProduct by remember { mutableStateOf<DataSnapshot?>(null) }
                var isCartOpen by remember { mutableStateOf(false) }
                var showAuthScreen by remember { mutableStateOf(false) }
                var isProfileOpen by remember { mutableStateOf(false) }
                var isAdminMode by remember { mutableStateOf(false) }
                var isAddingProduct by remember { mutableStateOf(false) }

                val cartItems = remember { mutableStateListOf<DataSnapshot>() }
                val context = LocalContext.current

                LaunchedEffect(user) {
                    if (user != null) {
                        Firebase.database.reference.child("users").child(user!!.uid).child("isAdmin")
                            .get().addOnSuccessListener { isAdmin = it.value == true }
                    } else { isAdmin = false }
                }

                Box(modifier = Modifier.fillMaxSize().background(BgGray)) {
                    when {
                        isAdminMode -> AdminOrdersScreen(onBack = { isAdminMode = false })
                        isAddingProduct -> AddProductScreen(onBack = { isAddingProduct = false })
                        isProfileOpen -> ProfileScreen(
                            isAdmin = isAdmin,
                            onBack = { isProfileOpen = false },
                            onAdminClick = { isProfileOpen = false; isAdminMode = true },
                            onAddProductClick = { isProfileOpen = false; isAddingProduct = true },
                            onLogout = { user = null; isProfileOpen = false }
                        )
                        showAuthScreen -> AuthScreen(
                            onAuthSuccess = { user = Firebase.auth.currentUser; showAuthScreen = false },
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
                                    Toast.makeText(context, "Ajouté au panier", Toast.LENGTH_SHORT).show()
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
                                        Toast.makeText(context, "Ajouté !", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            BottomNavBar(
                                cartSize = cartItems.size,
                                onCartClick = { if (user == null) showAuthScreen = true else isCartOpen = true },
                                onProfileClick = { if (user == null) showAuthScreen = true else isProfileOpen = true },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- UTILITAIRE UPLOAD ---
fun uploadImageToImgBB(uri: android.net.Uri, context: android.content.Context, onResult: (String?) -> Unit) {
    val client = OkHttpClient.Builder().connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    val apiKey = "71267471679a1414c6b726b82d1601a0"
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes() ?: throw IOException("Erreur lecture")
        inputStream.close()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("key", apiKey)
            .addFormDataPart("image", "image.jpg", RequestBody.create("image/jpeg".toMediaTypeOrNull(), bytes))
            .build()

        val request = Request.Builder().url("https://api.imgbb.com/1/upload").post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("LUBUMBASHI_DEBUG", "Échec réseau: ${e.message}")
                mainHandler.post { onResult(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                android.util.Log.d("LUBUMBASHI_DEBUG", "Réponse: $resBody")

                mainHandler.post {
                    if (response.isSuccessful && resBody != null && resBody.contains("\"url\":\"")) {
                        // On cherche spécifiquement l'URL directe dans l'objet "data"
                        try {
                            val url = resBody.substringAfter("\"url\":\"").substringBefore("\"").replace("\\/", "/")
                            android.util.Log.d("LUBUMBASHI_DEBUG", "URL Extraite: $url")
                            onResult(url)
                        } catch (e: Exception) {
                            android.util.Log.e("LUBUMBASHI_DEBUG", "Erreur parsing: ${e.message}")
                            onResult(null)
                        }
                    } else {
                        android.util.Log.e("LUBUMBASHI_DEBUG", "Réponse API invalide")
                        onResult(null)
                    }
                }
            }
        })
    } catch (e: Exception) {
        android.util.Log.e("LUBUMBASHI_DEBUG", "Exception globale: ${e.message}")
        onResult(null)
    }
}
// --- ÉCRANS ---

@Composable
fun HomeScreen(onProductClick: (DataSnapshot) -> Unit, onAddToCart: (DataSnapshot) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Pizza", "Burger", "Sandwich", "Drinks")
    var products by remember { mutableStateOf(listOf<DataSnapshot>()) }

    LaunchedEffect(Unit) {
        Firebase.database.reference.child("products").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { products = s.children.toList() }
            override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
        })
    }

    val filtered = products.filter {
        val name = it.child("name").value.toString()
        val cat = it.child("category").value.toString()
        name.contains(searchQuery, ignoreCase = true) && (selectedCategory == "All" || cat == selectedCategory)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(60.dp))
        Text("Livraison Lubumbashi 📍", color = PrimaryRed, fontWeight = FontWeight.Bold)
        Text("Notre Menu", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(15.dp))
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("Rechercher...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        Spacer(Modifier.height(15.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(categories) { cat ->
                Button(
                    onClick = { selectedCategory = cat },
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedCategory == cat) PrimaryRed else Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(cat, color = if (selectedCategory == cat) Color.White else Color.Black) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            items(filtered) { doc -> FoodCard(doc, onClick = { onProductClick(doc) }, onAddToCart = { onAddToCart(doc) }) }
        }
    }
}

@Composable
fun FoodCard(doc: DataSnapshot, onClick: () -> Unit, onAddToCart: () -> Unit) {
    val name = doc.child("name").value.toString()
    val price = doc.child("price").value.toString()
    val imageUrl = doc.child("imageUrl").value.toString()

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(name, fontWeight = FontWeight.Bold, maxLines = 1)
                Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("$price Rs", color = PrimaryRed, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onAddToCart, modifier = Modifier.size(28.dp).background(PrimaryRed, CircleShape)) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit, onBack: () -> Unit) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Icon(Icons.Default.Close, null) }
        Text(if (isSignUp) "Créer un compte" else "Connexion", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            val auth = Firebase.auth
            if (isSignUp) auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { if (it.isSuccessful) onAuthSuccess() else Toast.makeText(context, "Erreur", Toast.LENGTH_SHORT).show() }
            else auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { if (it.isSuccessful) onAuthSuccess() else Toast.makeText(context, "Échec connexion", Toast.LENGTH_SHORT).show() }
        }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)) {
            Text(if (isSignUp) "S'inscrire" else "Se connecter")
        }
        TextButton(onClick = { isSignUp = !isSignUp }) { Text(if (isSignUp) "Déjà un compte ? Connectez-vous" else "Pas de compte ? Inscrivez-vous") }
    }
}

@Composable
fun CartScreen(cartItems: MutableList<DataSnapshot>, onBack: () -> Unit, onRemove: (DataSnapshot) -> Unit) {
    val total = cartItems.sumOf { it.child("price").value.toString().toDoubleOrNull() ?: 0.0 }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text("Mon Panier", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(cartItems) { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), Arrangement.SpaceBetween) {
                    Text(item.child("name").value.toString(), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onRemove(item) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                }
            }
        }
        Button(onClick = {
            val user = Firebase.auth.currentUser
            val order = mapOf(
                "userId" to user?.uid,
                "userEmail" to user?.email,
                "items" to cartItems.map { it.child("name").value.toString() },
                "total" to total,
                "status" to "En préparation",
                "timestamp" to System.currentTimeMillis()
            )
            Firebase.database.reference.child("orders").push().setValue(order).addOnSuccessListener {
                cartItems.clear()
                onBack()
                Toast.makeText(context, "Commande confirmée !", Toast.LENGTH_SHORT).show()
            }
        }, modifier = Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)) {
            Text("Payer ($total Rs)")
        }
    }
}

@Composable
fun DetailScreen(product: DataSnapshot, onBack: () -> Unit, onAddToCart: (DataSnapshot) -> Unit) {
    val name = product.child("name").value.toString()
    val price = product.child("price").value.toString()
    val imageUrl = product.child("imageUrl").value.toString()

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            Image(painter = rememberAsyncImagePainter(imageUrl), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            IconButton(onClick = onBack, modifier = Modifier.padding(20.dp).background(Color.White, CircleShape)) { Icon(Icons.Default.ArrowBack, null) }
        }
        Column(modifier = Modifier.padding(25.dp)) {
            Text(name, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("$price Rs", fontSize = 24.sp, color = PrimaryRed, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Text("Savourez ce délicieux plat préparé à Lubumbashi.", color = Color.Gray)
            Spacer(Modifier.weight(1f))
            Button(onClick = { onAddToCart(product) }, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)) {
                Text("Ajouter au Panier")
            }
        }
    }
}

@Composable
fun ProfileScreen(isAdmin: Boolean, onBack: () -> Unit, onAdminClick: () -> Unit, onAddProductClick: () -> Unit, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(25.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
        Text("Mon Profil", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(30.dp))
        if (isAdmin) {
            Button(onClick = onAdminClick, modifier = Modifier.fillMaxWidth()) { Text("📦 Gérer les Commandes") }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAddProductClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("➕ Ajouter un Plat") }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { Firebase.auth.signOut(); onLogout() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Déconnexion", color = Color.Red)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOrdersScreen(onBack: () -> Unit) {
    var orders by remember { mutableStateOf(listOf<DataSnapshot>()) }
    LaunchedEffect(Unit) {
        Firebase.database.reference.child("orders").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { orders = s.children.toList().reversed() }
            override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
        })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Commandes") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(orders) { order -> OrderAdminCard(order) }
        }
    }
}

@Composable
fun OrderAdminCard(order: DataSnapshot) {
    val email = order.child("userEmail").value?.toString() ?: "Inconnu"
    val status = order.child("status").value?.toString() ?: "En attente"

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(email, fontWeight = FontWeight.Bold)
            Text("Statut: $status")
            Button(onClick = { order.ref.child("status").setValue("Livré") }) { Text("Marquer comme Livré") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Pizza") }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it }

    Column(modifier = Modifier.fillMaxSize().padding(25.dp).verticalScroll(rememberScrollState())) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
        Text("Ajouter au Menu", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(15.dp))
        Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.LightGray, RoundedCornerShape(15.dp)).clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) {
            if (imageUri == null) Text("Sélectionner Image")
            else Image(painter = rememberAsyncImagePainter(imageUri), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Prix") }, modifier = Modifier.fillMaxWidth())

        Row(Modifier.padding(vertical = 10.dp)) {
            listOf("Pizza", "Burger", "Drinks").forEach { cat ->
                FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                Spacer(Modifier.width(8.dp))
            }
        }

        Button(
            onClick = {
                if (imageUri != null && name.isNotEmpty() && price.isNotEmpty()) {
                    isUploading = true
                    uploadImageToImgBB(imageUri!!, context) { url ->
                        if (url != null) {
                            val p = mapOf(
                                "name" to name,
                                "price" to (price.toDoubleOrNull() ?: 0.0),
                                "category" to category,
                                "imageUrl" to url
                            )
                            Firebase.database.reference.child("products").push().setValue(p)
                                .addOnSuccessListener {
                                    isUploading = false
                                    onBack()
                                    Toast.makeText(context, "Produit ajouté !", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    isUploading = false
                                    Toast.makeText(context, "Erreur Firebase", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            isUploading = false
                            Toast.makeText(context, "Échec de l'upload de l'image", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            enabled = !isUploading,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
        ) {
            if (isUploading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Enregistrer le produit")
            }
        }
    }
} // <--- CETTE ACCOLADE FERME AddProductScreen

@Composable
fun BottomNavBar(cartSize: Int, onCartClick: () -> Unit, onProfileClick: () -> Unit, modifier: Modifier) {
    Surface(modifier = modifier.fillMaxWidth().height(70.dp), color = Color.White, shadowElevation = 10.dp) {
        Row(modifier = Modifier.fillMaxSize(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            IconButton(onClick = {}) { Icon(Icons.Default.Home, null, tint = PrimaryRed) }
            Box {
                IconButton(onClick = onCartClick) { Icon(Icons.Default.ShoppingCart, null) }
                if (cartSize > 0) {
                    Surface(color = PrimaryRed, shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).size(18.dp)) {
                        Text("$cartSize", color = Color.White, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            IconButton(onClick = onProfileClick) { Icon(Icons.Default.Person, null) }
        }
    }
}
