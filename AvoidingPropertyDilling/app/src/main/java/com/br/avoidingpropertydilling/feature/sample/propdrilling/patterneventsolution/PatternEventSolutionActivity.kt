package com.br.avoidingpropertydilling.feature.sample.propdrilling.patterneventsolution

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.br.avoidingpropertydilling.feature.sample.propdrilling.patterneventsolution.ui.theme.AvoidingPropertyDillingTheme

class PatternEventSolutionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvoidingPropertyDillingTheme {
                PatternEventApp()
            }
        }
    }
}

@Composable
private fun PatternEventApp() {
    Scaffold(
        topBar = { Text(text = "User List") },
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) { innerPadding ->
        UserListScreen(modifier = Modifier.padding(innerPadding))
    }
}

// O Preview renderiza o composable "burro" UserListContent com um estado fake,
// em vez de PatternEventApp/UserListScreen. Esses obtem a ViewModel via
// viewModel(factory = ...), que nao pode ser instanciada no ambiente de Preview
// (sem ViewModelStoreOwner/Activity), fazendo o Preview falhar.
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PatternEventAppPreview() {
    AvoidingPropertyDillingTheme {
        Scaffold(
            topBar = { Text(text = "User List") },
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .displayCutoutPadding()
                .imePadding()

        ) { innerPadding ->
            UserListContent(
                modifier = Modifier.padding(innerPadding),
                uiState = UserListUIState.OnSuccess(
                    UserListUI(
                        users = listOf(
                            User(id = "1", name = "John Doe"),
                            User(id = "2", name = "Jane Smith"),
                            User(id = "3", name = "Alice Johnson"),
                        )
                    )
                ),
                onAction = {}
            )
        }
    }
}


