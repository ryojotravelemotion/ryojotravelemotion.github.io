package io.github.ryojotravelemotion.jikokuhyo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val savedKey by vm.apiKey.collectAsStateWithLifecycle()
    var key by rememberSaveable { mutableStateOf(savedKey) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ODPT アクセストークン", style = MaterialTheme.typography.titleMedium)
            Text(
                "時刻表は「公共交通オープンデータセンター」から取ってきます。" +
                    "開発者サイトで無料の登録をすると、アクセストークンがもらえます。",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { uri.openUri("https://developer.odpt.org/") }) {
                Text("developer.odpt.org を開く")
            }
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    message = null
                },
                label = { Text("アクセストークン") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.saveApiKey(key)
                    message = "保存しました"
                }) { Text("保存") }
                OutlinedButton(onClick = {
                    vm.clearCache()
                    message = "時刻表の控えを消しました"
                }) { Text("控えを消す") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("データについて", style = MaterialTheme.typography.titleMedium)
            Text(
                "読み込んだ時刻表は端末に控えておき、電波の無いところでも見られるようにしています。" +
                    "ダイヤ改正のあとに古い時刻が出るときは「控えを消す」を押してください。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "載っている会社・路線は、公共交通オープンデータセンターで公開されているものに限られます。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                ODPT_CREDIT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
