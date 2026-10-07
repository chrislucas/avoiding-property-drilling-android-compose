package com.br.avoidingpropertydilling.feature.sample.propdrilling.patterneventsolution

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Testes de UI dos composables de [UserListContent] rodando na JVM com
 * Robolectric + Compose UI Test. Nao exigem device/emulador.
 *
 * A estrategia e testar o composable "burro" [UserListContent], controlando
 * o [UserListUIState] de entrada e capturando as [UserAction] emitidas pelo
 * canal unico onAction - sem instanciar a ViewModel real.
 */
@RunWith(RobolectricTestRunner::class)
/*
 * @GraphicsMode controla como o Robolectric emula a camada grafica (renderizacao)
 * durante os testes na JVM. Isso importa para testes de UI do Compose, que
 * dependem de medir/desenhar nos (layout e drawing) para que acoes como clique,
 * digitacao e assercoes de visibilidade funcionem.
 *
 * Possiveis valores de GraphicsMode.Mode:
 * - LEGACY: modo antigo/padrao historico. Nao executa a renderizacao nativa real;
 *   operacoes de Canvas/desenho sao em grande parte no-ops. Pode ser insuficiente
 *   para o Compose, levando a nos "nao desenhados" e testes instaveis.
 * - NATIVE: usa o Android Native Graphics (a mesma pilha grafica nativa do Android,
 *   embarcada no Robolectric). Produz layout/medidas/desenho reais na JVM, que e o
 *   que o Compose UI Test precisa para interagir e assertar corretamente.
 *
 * Usamos NATIVE porque os testes exercitam composables reais (OutlinedTextField,
 * LazyColumn, cliques e performTextInput): sem a renderizacao nativa, os nos
 * poderiam nao ser medidos/desenhados e as interacoes/assercoes falhariam.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class UserListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleUsers = listOf(
        User(id = "1", name = "John Doe"),
        User(id = "2", name = "Jane Smith"),
        User(id = "3", name = "Alice Johnson"),
    )

    @Test
    fun onSuccess_rendersAllUsers() {
        composeTestRule.setContent {
            UserListContent(
                uiState = UserListUIState.OnSuccess(UserListUI(users = sampleUsers)),
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText("John Doe").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jane Smith").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice Johnson").assertIsDisplayed()
    }

    @Test
    fun error_rendersErrorMessage() {
        composeTestRule.setContent {
            UserListContent(
                uiState = UserListUIState.Error("Falha ao carregar"),
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText("Falha ao carregar").assertIsDisplayed()
    }

    @Test
    fun idle_rendersEmptyMessage() {
        composeTestRule.setContent {
            UserListContent(
                uiState = UserListUIState.Idle,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText("Nenhum usuario carregado").assertIsDisplayed()
    }

    @Test
    fun clickDelete_emitsDeleteUserAction() {
        val actions = mutableListOf<UserAction>()

        composeTestRule.setContent {
            UserListContent(
                uiState = UserListUIState.OnSuccess(UserListUI(users = sampleUsers)),
                onAction = { actions += it }
            )
        }

        // testTag "delete_2" corresponde ao usuario de id "2" (Jane Smith).
        composeTestRule.onNodeWithTag("delete_2").performClick()

        assertEquals(1, actions.size)
        assertEquals(UserAction.DeleteUserOnClick("2"), actions.first())
    }

    @Test
    fun clickAdd_emitsAddUserActionWithTypedName() {
        val actions = mutableListOf<UserAction>()

        composeTestRule.setContent {
            UserListContent(
                uiState = UserListUIState.OnSuccess(UserListUI(users = sampleUsers)),
                onAction = { actions += it }
            )
        }

        composeTestRule.onNodeWithText("Novo usuario").performTextInput("New Person")
        composeTestRule.onNodeWithText("Adicionar").performClick()

        val addActions = actions.filterIsInstance<UserAction.AddUserOnClick>()
        assertEquals(1, addActions.size)
        assertEquals("New Person", addActions.first().user.name)
    }

    @Test
    fun clickReset_emitsResetAction() {
        val actions = mutableListOf<UserAction>()

        composeTestRule.setContent {
            UserListContent(
                uiState = UserListUIState.OnSuccess(UserListUI(users = sampleUsers)),
                onAction = { actions += it }
            )
        }

        composeTestRule.onNodeWithText("Reset").performClick()

        assertTrue(actions.contains(UserAction.Reset))
    }

    @Test
    fun clickRefresh_emitsRefreshAction() {
        val actions = mutableListOf<UserAction>()

        composeTestRule.setContent {
            UserListContent(
                uiState = UserListUIState.OnSuccess(UserListUI(users = sampleUsers)),
                onAction = { actions += it }
            )
        }

        composeTestRule.onNodeWithText("Refresh").performClick()

        assertTrue(actions.contains(UserAction.Refresh))
    }

    @Test
    fun typingFilter_emitsFilterByNameAfterDebounce() {
        val actions = mutableListOf<UserAction>()

        composeTestRule.setContent {
            UserListContent(
                uiState = UserListUIState.OnSuccess(UserListUI(users = sampleUsers)),
                onAction = { actions += it }
            )
        }

        composeTestRule.onNodeWithText("Filtrar por nome").performTextInput("Jane")

        // O filtro usa debounce(300ms); avanca o relogio virtual para dispara-lo.
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        val filterActions = actions.filterIsInstance<UserAction.FilterByName>()
        assertTrue(filterActions.isNotEmpty())
        assertEquals("Jane", filterActions.last().name)
    }
}
