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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.mehanikpro.ui.theme.MechanicAppTheme
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL
import kotlinx.coroutines.*
import android.provider.Settings

class MainActivity : ComponentActivity() {

    // ID загрузки для DownloadManager (уже не используется, но оставляем для совместимости)
    private var downloadId: Long = -1

    // BroadcastReceiver для отслеживания завершения загрузки (уже не используется, но оставляем)
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                // Устаревший метод, теперь установка происходит через downloadApk
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
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MehanikPRO", "Проверка обновлений: начало")
                val url = URL("https://raw.githubusercontent.com/yurocheck/mehanikpro/main/update.json")
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
                val jsonString = reader.readText()
                reader.close()

                // ✅ ИСПОЛЬЗУЕМ JSONObject ДЛЯ ПАРСИНГА
                val jsonObject = JSONObject(jsonString)
                val latestVersion = jsonObject.getInt("latestVersion")
                val apkUrl = jsonObject.getString("apkUrl")
                val changelog = jsonObject.getString("changelog")

                val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode

                Log.d("MehanikPRO", "latestVersion=$latestVersion, currentVersion=$currentVersion")

                if (latestVersion > currentVersion && apkUrl.isNotEmpty()) {
                    Log.d("MehanikPRO", "Найдено обновление!")
                    Pair(apkUrl, changelog)
                } else {
                    Log.d("MehanikPRO", "Нет обновлений")
                    null
                }
            } catch (e: Exception) {
                Log.e("MehanikPRO", "Ошибка проверки обновлений", e)
                null
            }
        }
    }

    // ============================================================
    // ПРОВЕРКА ОБНОВЛЕНИЙ (для обратной совместимости)
    // ============================================================
    private fun checkForUpdate() {
        Log.d("MehanikPRO", "=== ПРОВЕРКА ОБНОВЛЕНИЙ ЗАПУЩЕНА ===")
        CoroutineScope(Dispatchers.IO).launch {
            val result = checkForUpdateCompose()
            // результат обрабатывается в Compose через LaunchedEffect
        }
    }

    // ============================================================
    // СКАЧИВАНИЕ APK (прямое скачивание через URL)
    // ============================================================
    private fun downloadApk(apkUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("MehanikPRO", "1. Начинаем скачивание APK")
                Log.d("MehanikPRO", "2. Ссылка: $apkUrl")

                val apkFile = File(getExternalFilesDir(null), "mehanikpro-update.apk")
                Log.d("MehanikPRO", "3. Путь для сохранения: ${apkFile.absolutePath}")

                // Скачиваем файл
                Log.d("MehanikPRO", "4. Открываем поток для скачивания...")
                val connection = URL(apkUrl).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                Log.d("MehanikPRO", "5. Соединение установлено. Начинаем чтение...")

                connection.getInputStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        var totalBytes = 0
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            if (totalBytes % (1024 * 100) == 0) { // Лог каждые 100 КБ
                                Log.d("MehanikPRO", "6. Скачано: ${totalBytes / 1024} КБ")
                            }
                        }
                        Log.d("MehanikPRO", "7. Скачивание завершено! Всего: ${totalBytes / 1024} КБ")
                    }
                }

                Log.d("MehanikPRO", "8. Файл сохранён: ${apkFile.exists()}, размер: ${apkFile.length() / 1024} КБ")

                // Если дошли сюда — файл скачался
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Файл скачан! Установка...",
                        Toast.LENGTH_SHORT
                    ).show()
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                Log.e("MehanikPRO", "ОШИБКА загрузки:", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ============================================================
    // УСТАНОВКА APK (ПРОСТОЙ И НАДЁЖНЫЙ СПОСОБ)
    // ============================================================
    private fun installApk(apkFile: File) {
        try {
            // Просто открываем APK через системный установщик
            val intent = Intent(Intent.ACTION_VIEW)
            val apkUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Ошибка установки: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            Log.e("MehanikPRO", "Ошибка установки", e)
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
            onProblemClick = { category, machine, problem ->
                selectedCategory = category
                selectedMachine = machine
                selectedProblem = problem
                currentScreen = Screen.Problem
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
            LazyColumn {
                items(categories.keys.toList()) { category ->
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
// 5. ЭКРАН СПИСКА МАШИН (С ПОИСКОМ ПО НЕИСПРАВНОСТЯМ)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CategoryScreen(
    categoryName: String,
    roleName: String,
    onMachineClick: (String) -> Unit,
    onProblemClick: (String, String, String) -> Unit,
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
                placeholder = { Text("🔍 Поиск по неисправностям...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                })
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    Text(
                        text = "😕 Ничего не найдено",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn {
                        items(searchResults) { result ->
                            SearchResultCard(
                                result = result,
                                onClick = {
                                    onProblemClick(result.category, result.machine, result.problem)
                                }
                            )
                        }
                    }
                }
            } else {
                LazyColumn {
                    items(items) { item ->
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
// 7. ЭКРАН ИНСТРУКЦИИ (С КАРТИНКАМИ И УВЕЛИЧЕНИЕМ)
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

    var showFullImage by remember { mutableStateOf(false) }

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
                    val stepText = step.toString()

                    if (stepText.contains("фото", ignoreCase = true)) {
                        // Карточка с фото (кликабельная)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { showFullImage = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.menu_ryazan),
                                contentDescription = "Фото меню",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .padding(8.dp)
                                    .clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = stepText,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = "📷 Нажми на фото, чтобы увеличить и масштабировать (щипок)",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }

    // Диалог с фото на весь экран (с зумом жестами)
    if (showFullImage) {
        ZoomableImage(
            imageRes = R.drawable.menu_ryazan,
            onDismiss = { showFullImage = false }
        )
    }
}

// ============================================================
// 8. КОМПОНЕНТ ДЛЯ ПРОСМОТРА ФОТО С ЗУМОМ
// ============================================================
@Composable
fun ZoomableImage(
    imageRes: Int,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 3f)
                        offsetX += pan.x / scale
                        offsetY += pan.y / scale
                    }
                }
                .clickable { onDismiss() }
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "Увеличенное фото",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}

// ============================================================
// 9. ЭКРАН ПОИСКА (ОТДЕЛЬНЫЙ ЭКРАН)
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
                placeholder = { Text("🔍 Поиск по неисправностям...") },
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
// 10. КЛАСС ДЛЯ РЕЗУЛЬТАТОВ ПОИСКА
// ============================================================
data class SearchResult(
    val role: String,
    val category: String,
    val machine: String,
    val problem: String
)

// ============================================================
// 11. КАРТОЧКА РЕЗУЛЬТАТА ПОИСКА
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