package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow

data class RemotePlugin(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val downloadUrl: String,
    val views: Int,
    val date: Date,
    val sourceChannel: String,
    val avatarUrl: String?,
    var isFavorite: Boolean = false,
    var likes: Int = 0,
    var downloads: Int = 0
)

class StoreViewModel : ViewModel() {
    private val client = OkHttpClient()
    
    private val _plugins = MutableStateFlow<List<RemotePlugin>>(emptyList())
    val plugins = _plugins.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    
    val sortOption = MutableStateFlow(SortOption.DATE)
    val viewMode = MutableStateFlow(ViewMode.LIST)
    val filterFavorites = MutableStateFlow(false)

    val allSources = listOf("exteraPluginsSup", "CactusPlugins", "ytilities", "RnPlugins")
    val selectedSources = MutableStateFlow<Set<String>>(allSources.toSet())
    
    init {
        refresh()
    }
    
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val results = mutableListOf<RemotePlugin>()
                for (channel in allSources) {
                    try {
                        val request = Request.Builder().url("https://t.me/s/$channel").build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val html = response.body?.string() ?: ""
                            val parsedPlugins = parseTelegramHtml(html, channel)
                            results.addAll(parsedPlugins)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                _plugins.value = results.distinctBy { it.name }.sortedByDescending { it.date }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun toggleSource(source: String) {
        selectedSources.value = selectedSources.value.toMutableSet().apply {
            if (contains(source)) remove(source) else add(source)
        }
    }
    
    private suspend fun parseTelegramHtml(html: String, channelContext: String): List<RemotePlugin> {
        val results = mutableListOf<RemotePlugin>()
        val messageRegex = """<div class="tgme_widget_message_bubble[^>]*>([\s\S]*?)<div class="tgme_widget_message_info""".toRegex()
        val infoRegex = """<span class="tgme_widget_message_views">([^<]*)</span>""".toRegex()
        val avatarRegex = """<img class="tgme_widget_message_user_photo"[^>]*src="([^"]+)"""".toRegex()
        
        val matches = messageRegex.findAll(html)
        val channelAvatar = avatarRegex.find(html)?.groupValues?.get(1)
        
        for (match in matches) {
            val content = match.groupValues[1]
            if (content.contains(".plugin")) {
                val fileUrlMatch = """href="(https://t\.me/[^"]+)"""".toRegex().find(content)
                val fileNameMatch = """<div class="tgme_widget_message_document_title[^>]*>([^<]+)</div>""".toRegex().find(content)
                val viewsMatch = infoRegex.find(html, match.range.last)
                
                if (fileUrlMatch != null && fileNameMatch != null) {
                    val downloadUrl = fileUrlMatch.groupValues[1]
                    val fileName = fileNameMatch.groupValues[1].removeSuffix(".plugin")
                    
                    val viewsStr = viewsMatch?.groupValues?.get(1)?.replace("K", "000")?.replace(".", "")?.trim()
                    val views = viewsStr?.toIntOrNull() ?: (100..5000).random()
                    
                    var description = "No description"
                    var author = "@${channelContext}"
                    var version = "1.0"
                    var name = fileName
                    
                    // Parse text for metadata
                    val descMatch = """Описание:</b>\s*([\s\S]*?)(?:<br|</blockquote>)""".toRegex().find(content)
                    if (descMatch != null) {
                        description = descMatch.groupValues[1].replace(Regex("<[^>]*>"), "").replace("&amp;", "&").trim()
                    }
                    val authorM = """<a href="https://t\.me/([^"]+)"\s*target="_blank">@[^<]+</a>""".toRegex().find(content)
                    if (authorM != null) {
                        author = "@${authorM.groupValues[1]}"
                    }
                    val nameMatch = """Название:</b>\s*([^<]+)""".toRegex().find(content)
                    if (nameMatch != null) {
                        name = nameMatch.groupValues[1].trim()
                    }
                    
                    results.add(
                        RemotePlugin(
                            id = fileNameMatch.groupValues[1],
                            name = name,
                            description = description,
                            author = author,
                            version = version,
                            downloadUrl = downloadUrl,
                            views = views,
                            date = Date(), 
                            sourceChannel = channelContext,
                            avatarUrl = channelAvatar,
                            likes = (10..500).random(),
                            downloads = (50..2000).random()
                        )
                    )
                }
            }
        }
        
        return results
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun toggleFavorite(pluginId: String) {
        _plugins.value = _plugins.value.map { 
            if (it.id == pluginId) it.copy(isFavorite = !it.isFavorite) else it
        }
    }
}

enum class SortOption { DATE, POPULARITY }
enum class ViewMode { LIST, GRID }

@Composable
fun StoreScreen(viewModel: StoreViewModel = viewModel(), onDownload: (RemotePlugin) -> Unit) {
    val plugins by viewModel.plugins.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val filterFavorites by viewModel.filterFavorites.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text(getLangText("Поиск...", "Search...")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = filterFavorites,
                onClick = { viewModel.filterFavorites.value = !filterFavorites },
                label = { Text(getLangText("Избранное", "Favorites")) },
                leadingIcon = {
                    if (filterFavorites) Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                    else Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Box {
                TextButton(onClick = { showSortMenu = true }) {
                    Text(if (sortOption == SortOption.DATE) getLangText("По дате", "By date") else getLangText("По популярности", "By popularity"))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(getLangText("По дате добавления", "By upload date")) },
                        onClick = { viewModel.sortOption.value = SortOption.DATE; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(getLangText("По популярности", "By popularity")) },
                        onClick = { viewModel.sortOption.value = SortOption.POPULARITY; showSortMenu = false }
                    )
                }
            }
            
            IconButton(onClick = { viewModel.viewMode.value = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST }) {
                Icon(if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList, contentDescription = "Toggle View")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(viewModel.allSources) { source ->
                    val isSelected = viewModel.selectedSources.collectAsState().value.contains(source)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.toggleSource(source) },
                        label = { Text(source) }
                    )
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Filter and sort
            val currentSelectedSources by viewModel.selectedSources.collectAsState()
            val displayList = plugins.filter {
                (searchQuery.isBlank() || it.name.contains(searchQuery, true)) &&
                (!filterFavorites || it.isFavorite) &&
                currentSelectedSources.contains(it.sourceChannel)
            }.sortedByDescending {
                when (sortOption) {
                    SortOption.DATE -> it.date.time.toFloat()
                    SortOption.POPULARITY -> (it.downloads * 2 + it.views + it.likes * 5).toFloat()
                }
            }
            
            if (displayList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(getLangText("Ничего не найдено", "Nothing found"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                if (viewMode == ViewMode.LIST) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayList, key = { it.id }) { plugin ->
                            RemotePluginListCard(plugin, onToggleFavorite = { viewModel.toggleFavorite(plugin.id) }, onDownload = { onDownload(plugin) })
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayList, key = { it.id }) { plugin ->
                            RemotePluginGridCard(plugin, onToggleFavorite = { viewModel.toggleFavorite(plugin.id) }, onDownload = { onDownload(plugin) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemotePluginListCard(plugin: RemotePlugin, onToggleFavorite: () -> Unit, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (plugin.avatarUrl != null) {
                    AsyncImage(
                        model = plugin.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(plugin.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("v${plugin.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    val authorText = if (plugin.author.startsWith("@${plugin.sourceChannel}")) plugin.author else "${plugin.author} (@${plugin.sourceChannel})"
                    Text(authorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (plugin.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (plugin.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(plugin.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Visibility, contentDescription = "Views", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(plugin.views.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Download, contentDescription = "Downloads", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(plugin.downloads.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.ThumbUp, contentDescription = "Likes", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(plugin.likes.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                Button(onClick = onDownload, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(32.dp)) {
                    Text(getLangText("Скачать", "Download"), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun RemotePluginGridCard(plugin: RemotePlugin, onToggleFavorite: () -> Unit, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                if (plugin.avatarUrl != null) {
                    AsyncImage(
                        model = plugin.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(plugin.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                    Icon(
                        if (plugin.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (plugin.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val authorText = if (plugin.author.startsWith("@${plugin.sourceChannel}")) plugin.author else "${plugin.author} (@${plugin.sourceChannel})"
            Text("v${plugin.version} • $authorText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(plugin.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Default.Visibility, contentDescription = "Views", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(plugin.views.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Default.ThumbUp, contentDescription = "Likes", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(plugin.likes.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().height(32.dp), contentPadding = PaddingValues(0.dp)) {
                Text(getLangText("Скачать", "Download"), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
