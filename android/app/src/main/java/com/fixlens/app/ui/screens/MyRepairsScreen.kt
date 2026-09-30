package com.fixlens.app.ui.screens

import com.fixlens.app.ui.theme.paperSurface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fixlens.app.data.CaptureRecord
import com.fixlens.app.data.CaptureSource
import com.fixlens.app.data.CaptureStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * My Repairs: local, on-device history of confirmed captures.
 * Phase 1 scope: capture records only. Repair guidance arrives in later phases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRepairsScreen(
    viewModel: RepairsViewModel,
    onBack: () -> Unit,
) {
    val records by viewModel.records.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().paperSurface()) {
        TopAppBar(
            title = { Text("My repairs", style = MaterialTheme.typography.headlineMedium) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
        )
        if (records.isEmpty()) {
            EmptyState(
                title = "Every fix starts with a look.",
                body = "Your confirmed photo and camera captures will collect here. Start with something that needs a little care.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    RepairRecordRow(
                        record = record,
                        onDelete = { viewModel.delete(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RepairRecordRow(
    record: CaptureRecord,
    onDelete: () -> Unit,
) {
    Card(
        border = androidx.compose.foundation.BorderStroke(1.dp, com.fixlens.app.ui.theme.FixLensColors.Rule),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CaptureThumb(record = record)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (record.source) {
                        CaptureSource.PHOTO_MODE -> "Photo scan"
                        CaptureSource.LIVE_CAMERA -> "Live camera scan"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatTimestamp(record.createdAtMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CaptureThumb(record: CaptureRecord) {
    if (record.imagePath == null) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = record.imagePath) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            android.graphics.BitmapFactory.decodeFile(record.imagePath!!)
        }
    }
    val image = bitmap?.let { it.asImageBitmap() }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

private fun formatTimestamp(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))

/** Holds the capture history for My Repairs. */
class RepairsViewModel(
    private val captureStore: CaptureStore,
) : ViewModel() {

    val records = captureStore.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(record: CaptureRecord) {
        viewModelScope.launch { captureStore.delete(record) }
    }

    companion object {
        fun factory(application: android.app.Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as com.fixlens.app.FixLensApp).appContainer
                    return RepairsViewModel(container.captureStore) as T
                }
            }
    }
}
