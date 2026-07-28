package com.example.mehanikpro

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.mehanikpro.ui.theme.MechanicAppTheme
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    // ID загрузки для DownloadManager
    private var downloadId: Long = -1

    // BroadcastReceiver для отслеживания завершения загрузки
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                installApk()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Регистрируем BroadcastReceiver через LocalBroadcastManager
        LocalBroadcastManager.getInstance(this).registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )

        // Проверка обновлений
        checkForUpdate()

        setContent {
            MechanicAppTheme {
                var showUpdateDialog by remember { mutableStateOf(false) }
                var updateApkUrl by remember { mutableStateOf("") }
                var updateChangelog by remember { mutableStateOf("") }
                var isDownloading by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val result = checkForUpdateCompose()
                    if (result != null) {
                        updateApkUrl = result.first
                        updateChangelog = result.second
                        showUpdateDialog = true
                    }
                }

                if (showUpdateDialog) {
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        title = { Text("📱 Доступно обновление!") },
                        text = { Text("Что нового:\n$updateChangelog") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showUpdateDialog = false
                                    isDownloading = true
                                    downloadApk(updateApkUrl)
                                }
                            ) {
                                Text("Скачать и установить")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateDialog = false }) {
                                Text("Позже")
                            }
                        }
                    )
                }

                // Индикатор загрузки
                if (isDownloading) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("⏳ Загрузка обновления...") },
                        text = { Text("Пожалуйста, подождите. Файл скачивается в фоне.") },
                        confirmButton = {},
                        dismissButton = {}
                    )
                }

                NavigationApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // Ресивер уже отписан
        }
    }

    // ============================================================
    // ПРОВЕРКА ОБНОВЛЕНИЙ (возвращает пару (apkUrl, changelog) или null)
    // ============================================================
    private suspend fun checkForUpdateCompose(): Pair<String, String>? {
        return try {
            val url = URL("https://raw.githubusercontent.com/yurocheck/mehanikpro/main/update.json")
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
            val jsonString = reader.readText()
            reader.close()

            val latestVersion = jsonString
                .substringAfter("\"latestVersion\":")
                .substringBefore(",")
                .trim()
                .toIntOrNull() ?: 0

            val apkUrl = jsonString
                .substringAfter("\"apkUrl\":\"")
                .substringBefore("\"")

            val changelog = jsonString
                .substringAfter("\"changelog\":\"")
                .substringBefore("\"")

            val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode

            if (latestVersion > currentVersion && apkUrl.isNotEmpty()) {
                Pair(apkUrl, changelog)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================
    // ПРОВЕРКА ОБНОВЛЕНИЙ (для обратной совместимости)
    // ============================================================
    private fun checkForUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = checkForUpdateCompose()
            // результат обрабатывается в Compose через LaunchedEffect
        }
    }

    // ============================================================
    // СКАЧИВАНИЕ APK через DownloadManager
    // ============================================================
    private fun downloadApk(apkUrl: String) {
        try {
            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

            // Создаём URI для скачивания
            val uri = Uri.parse(apkUrl)

            // Создаём запрос на скачивание
            val request = DownloadManager.Request(uri).apply {
                // Разрешаем скачивание через любую сеть
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                // Запрещаем роуминг
                setAllowedOverRoaming(false)
                // Устанавливаем заголовок
                setTitle("Обновление справочника механика")
                setDescription("Скачивание новой версии...")
                // Сохраняем в папку Download
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "mehanikpro-update.apk"
                )
                // Показываем уведомление о загрузке
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            }

            // Запускаем скачивание
            downloadId = downloadManager.enqueue(request)

            Toast.makeText(
                this,
                "Загрузка началась. Уведомление появится в шторке.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Ошибка загрузки: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ============================================================
    // УСТАНОВКА APK (вызывается после завершения загрузки)
    // ============================================================
    private fun installApk() {
        try {
            val apkFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "mehanikpro-update.apk"
            )

            if (!apkFile.exists()) {
                Toast.makeText(this, "Файл обновления не найден", Toast.LENGTH_LONG).show()
                return
            }

            val intent = Intent(Intent.ACTION_VIEW)
            val apkUri: Uri

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    apkFile
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                apkUri = Uri.fromFile(apkFile)
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Ошибка установки: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

// ============================================================
// 2. КЛАССЫ ДЛЯ НАВИГАЦИИ
// ============================================================
sealed class Screen {
    object Main : Screen()
    object Category : Screen()
    object Machine : Screen()
    object Problem : Screen()
    object Search : Screen()
}

// ============================================================
// 3. НАВИГАЦИЯ
// ============================================================
@Composable
fun NavigationApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var selectedRole by remember { mutableStateOf("🔧 Механики") }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedMachine by remember { mutableStateOf("") }
    var selectedProblem by remember { mutableStateOf("") }

    fun navigateToProblem(role: String, category: String, machine: String, problem: String) {
        selectedRole = role
        selectedCategory = category
        selectedMachine = machine
        selectedProblem = problem
        currentScreen = Screen.Problem
    }

    when (currentScreen) {
        is Screen.Main -> MainScreen(
            onCategoryClick = { category ->
                selectedCategory = category
                currentScreen = Screen.Category
            },
            onSearchClick = {
                currentScreen = Screen.Search
            }
        )
        is Screen.Category -> CategoryScreen(
            categoryName = selectedCategory,
            roleName = selectedRole,
            onMachineClick = { machine ->
                selectedMachine = machine
                currentScreen = Screen.Machine
            },
            onBack = { currentScreen = Screen.Main },
            onHome = { currentScreen = Screen.Main }
        )
        is Screen.Machine -> MachineScreen(
            machineName = selectedMachine,
            categoryName = selectedCategory,
            roleName = selectedRole,
            onProblemClick = { problem ->
                selectedProblem = problem
                currentScreen = Screen.Problem
            },
            onBack = { currentScreen = Screen.Category },
            onHome = { currentScreen = Screen.Main }
        )
        is Screen.Problem -> ProblemScreen(
            roleName = selectedRole,
            categoryName = selectedCategory,
            machineName = selectedMachine,
            problemName = selectedProblem,
            onBack = { currentScreen = Screen.Machine },
            onHome = { currentScreen = Screen.Main }
        )
        is Screen.Search -> SearchScreen(
            onResultClick = { role, category, machine, problem ->
                navigateToProblem(role, category, machine, problem)
            },
            onBack = { currentScreen = Screen.Main }
        )
    }
}

// ============================================================
// 4. ГЛАВНЫЙ ЭКРАН (СПИСОК РАЗДЕЛОВ)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    onCategoryClick: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    val categories = factoryData["🔧 Механики"] ?: emptyMap()
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredCategories = categories.keys.filter {
        it.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "🏭 ООО \"ППП\"",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = "Справочник механика",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = "Разделы",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, contentDescription = "Поиск")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("🔍 Поиск раздела...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                })
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(filteredCategories) { category ->
                    Button(
                        onClick = { onCategoryClick(category) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(text = category, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

// ============================================================
// 5. ЭКРАН СПИСКА МАШИН
// ============================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CategoryScreen(
    categoryName: String,
    roleName: String,
    onMachineClick: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val roleData = factoryData[roleName] ?: emptyMap()
    val categoryData = roleData[categoryName]
    val items = if (categoryData is Map<*, *>) {
        categoryData.keys.map { it.toString() }
    } else {
        emptyList()
    }

    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredItems = items.filter {
        it.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "🏭 ООО \"ППП\"",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = "Справочник механика",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = categoryName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Filled.Home, contentDescription = "На главную")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("🔍 Поиск машины...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                })
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(filteredItems) { item ->
                    Button(
                        onClick = { onMachineClick(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(text = item, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

// ============================================================
// 6. ЭКРАН СПИСКА ПРОБЛЕМ
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineScreen(
    machineName: String,
    categoryName: String,
    roleName: String,
    onProblemClick: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val roleData = factoryData[roleName] ?: emptyMap()
    val categoryData = roleData[categoryName]
    val machineData = if (categoryData is Map<*, *>) {
        categoryData[machineName]
    } else null

    val problemList = when (machineData) {
        is Map<*, *> -> machineData.keys.map { it.toString() }
        is List<*> -> machineData.map { it.toString() }
        else -> emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "🏭 ООО \"ППП\"",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = "Справочник механика",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = machineName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Filled.Home, contentDescription = "На главную")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Выберите неисправность:",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn {
                items(problemList) { problem ->
                    Button(
                        onClick = { onProblemClick(problem) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(text = problem, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

// ============================================================
// 7. ЭКРАН ИНСТРУКЦИИ
// ============================================================
@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemScreen(
    roleName: String,
    categoryName: String,
    machineName: String,
    problemName: String,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val roleData = factoryData[roleName] ?: emptyMap()
    val categoryData = roleData[categoryName]
    val machineData = if (categoryData is Map<*, *>) {
        categoryData[machineName]
    } else null
    val problemData = if (machineData is Map<*, *>) {
        machineData[problemName]
    } else null

    val stepList = if (problemData is List<*>) {
        problemData
    } else {
        listOf("Инструкция не найдена")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "🏭 ООО \"ППП\"",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = "Справочник механика",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = problemName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Filled.Home, contentDescription = "На главную")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Пошаговая инструкция:",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                for (step in stepList) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = step.toString(),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Text(
                text = "📷 Здесь будет схема/фото",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

// ============================================================
// 8. ЭКРАН ПОИСКА
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onResultClick: (role: String, category: String, machine: String, problem: String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            searchResults = emptyList()
            return
        }

        val results = mutableListOf<SearchResult>()
        val lowerQuery = query.lowercase()

        for (role in factoryData.keys) {
            val roleData = factoryData[role] ?: continue
            for (category in roleData.keys) {
                val categoryData = roleData[category]
                when (categoryData) {
                    is Map<*, *> -> {
                        for (machine in categoryData.keys) {
                            val machineName = machine.toString()
                            val machineData = categoryData[machine]
                            when (machineData) {
                                is Map<*, *> -> {
                                    for (problem in machineData.keys) {
                                        val problemName = problem.toString()
                                        val steps = (machineData[problem] as? List<*>) ?: emptyList<Any?>()

                                        val problemMatches = problemName.lowercase().contains(lowerQuery)
                                        val stepMatches = steps.any { step ->
                                            step.toString().lowercase().contains(lowerQuery)
                                        }

                                        if (problemMatches || stepMatches) {
                                            results.add(
                                                SearchResult(
                                                    role = role,
                                                    category = category,
                                                    machine = machineName,
                                                    problem = problemName
                                                )
                                            )
                                        }
                                    }
                                }
                                is List<*> -> {
                                    for (item in machineData) {
                                        val itemName = item.toString()
                                        if (itemName.lowercase().contains(lowerQuery)) {
                                            results.add(
                                                SearchResult(
                                                    role = role,
                                                    category = category,
                                                    machine = machineName,
                                                    problem = itemName
                                                )
                                            )
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                    is List<*> -> {
                        for (item in categoryData) {
                            val itemName = item.toString()
                            if (itemName.lowercase().contains(lowerQuery)) {
                                results.add(
                                    SearchResult(
                                        role = role,
                                        category = category,
                                        machine = "",
                                        problem = itemName
                                    )
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        searchResults = results
    }

    LaunchedEffect(searchQuery) {
        performSearch(searchQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "🏭 ООО \"ППП\"",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = "Справочник механика",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = "Поиск",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("🔍 Поиск по неисправностям и решениям...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {})
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
                Text(
                    text = "😕 Ничего не найдено",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn {
                items(searchResults) { result ->
                    SearchResultCard(
                        result = result,
                        onClick = {
                            onResultClick(
                                result.role,
                                result.category,
                                result.machine,
                                result.problem
                            )
                        }
                    )
                }
            }
        }
    }
}
// ============================================================
// 9. КЛАСС ДЛЯ РЕЗУЛЬТАТОВ ПОИСКА
// ============================================================
data class SearchResult(
    val role: String,
    val category: String,
    val machine: String,
    val problem: String
)

// ============================================================
// 10. КАРТОЧКА РЕЗУЛЬТАТА ПОИСКА
// ============================================================
@Composable
fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = result.problem,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = buildString {
                    append(result.role)
                    append(" → ")
                    append(result.category)
                    if (result.machine.isNotEmpty()) {
                        append(" → ")
                        append(result.machine)
                    }
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}