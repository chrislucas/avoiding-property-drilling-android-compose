package com.br.avoidingpropertydilling.feature.sample.propdrilling.patterneventsolution

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.collections.filterNot
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
    https://gemini.google.com/app/bf44712b98dfdb6d

    Padrao de eventos (Sealed interfaces - MVI)

    Se uma tela possui muitas interacoes (cliques, swipes, digitacao), passar 10 lambdas
    diferentes torna-se insustentavel, mesmo sem Prop Drilling profundo.

    A solucao é consolidar todas as acoes de uma arvore de UI em unico canal de eventos

    - Use uma sealed interface para representar as acoes e passe apneas
    um lambda generico para a estrutura
 */

sealed interface UserAction {
    data class DeleteUserOnClick(val id: String) : UserAction

    data class AddUserOnClick(val user: User) : UserAction

    data class FilterByName(val name: String) : UserAction

    data object Reset : UserAction

    data object Refresh : UserAction
}


sealed class UserListUIState {
    data object Idle : UserListUIState()
    data class OnLoading(val data: UserListUI) : UserListUIState()
    data class OnSuccess(val data: UserListUI) : UserListUIState()
    data class Error(val message: String) : UserListUIState()
}

/**
 * Dados exibidos pela lista: usuarios visiveis e o termo de filtro atual.
 * E reaproveitado por [UserListUIState.OnLoading] e [UserListUIState.OnSuccess],
 * evitando a necessidade de um StateFlow separado para o filtro.
 */
data class UserListUI(val users: List<User> = emptyList(), val filter: String = "")


/**
 * Encapsula o resultado de uma chamada que pode falhar:
 * - [Success] carrega o retorno da operacao.
 * - [Failure] carrega a excecao capturada.
 */
sealed interface RequestResult<out T> {
    data class Success<T>(val data: T) : RequestResult<T>
    data class Failure(val exception: Throwable) : RequestResult<Nothing>
}

/**
 * Executa [block] capturando qualquer excecao e devolvendo o resultado
 * encapsulado em [RequestResult], sem deixar a excecao vazar para o chamador.
 */
suspend inline fun <T> safeRequest(block: suspend () -> T): RequestResult<T> {
    return try {
        RequestResult.Success(block())
    } catch (throwable: Throwable) {
        RequestResult.Failure(throwable)
    }
}


interface UserRepository {
    suspend fun getUsers(): RequestResult<List<User>>
}

interface UserClient {
    suspend fun getUsers(): List<User>
}

class UserClientMock : UserClient {
    override suspend fun getUsers(): List<User> {
        delay(1000.milliseconds)
        return listOf(
            User(id = "1", name = "John Doe"),
            User(id = "2", name = "Jane Smith"),
            User(id = "3", name = "Alice Johnson"),
            User(id = "4", name = "Bob Williams"),
            User(id = "5", name = "Charlie Brown"),
            User(id = "6", name = "Diana Prince"),
            User(id = "7", name = "Ethan Hunt"),
            User(id = "8", name = "Fiona Gallagher"),
            User(id = "9", name = "George Martin"),
            User(id = "10", name = "Hannah Montana"),
        )
    }
}


class UserRepositoryMock(private val client: UserClient) : UserRepository {
    override suspend fun getUsers(): RequestResult<List<User>> {
        return safeRequest { client.getUsers() }
    }
}

class UserListViewModel(
    private val repository: UserRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mutableUserListUI: MutableStateFlow<UserListUIState> = MutableStateFlow(
        UserListUIState.Idle
    )

    val userListUIState: StateFlow<UserListUIState> = mutableUserListUI.asStateFlow()

    // Fonte da verdade: snapshot imutavel carregado do repositorio.
    // Nunca e alterada por add/delete/filtro, garantindo que o estado
    // inicial possa ser restaurado a qualquer momento.
    private var originalUsers: List<User> = emptyList()

    // Copia de trabalho: e sobre ela que add e delete atuam.
    private var workingUsers: List<User> = emptyList()

    // Termo de filtro atual, persistido no SavedStateHandle para sobreviver
    // a morte do processo iniciada pelo sistema (nao apenas config change).
    private var currentFilter: String
        get() = savedStateHandle[KEY_FILTER] ?: ""
        set(value) {
            savedStateHandle[KEY_FILTER] = value
        }

    fun getUsers() {
        viewModelScope.launch {
            mutableUserListUI.update {
                UserListUIState.OnLoading(UserListUI(workingUsers, currentFilter))
            }

            when (val result = repository.getUsers()) {
                is RequestResult.Success -> {
                    // Guarda a fonte da verdade e inicia a copia de trabalho a partir dela.
                    originalUsers = result.data
                    workingUsers = originalUsers
                    emitFilteredUsers()
                }

                is RequestResult.Failure -> {
                    mutableUserListUI.update {
                        UserListUIState.Error(
                            result.exception.message ?: "Erro ao carregar usuarios"
                        )
                    }
                }
            }
        }
    }

    fun onEvent(userAction: UserAction) {
        when (userAction) {
            is UserAction.DeleteUserOnClick -> deleteUser(userAction.id)
            is UserAction.AddUserOnClick -> addUser(userAction.user)
            is UserAction.FilterByName -> filterUser(userAction.name)
            is UserAction.Reset -> resetToInitialState()
            is UserAction.Refresh -> getUsers()
        }
    }

    private fun addUser(user: User) {
        // Altera apenas a copia de trabalho; a fonte da verdade permanece intacta.
        workingUsers = workingUsers + user
        emitFilteredUsers()
    }

    private fun deleteUser(userId: String) {
        workingUsers = workingUsers.filterNot { it.id == userId }
        emitFilteredUsers()
    }

    private fun filterUser(name: String) {
        // Guarda apenas o termo; a copia de trabalho permanece intacta.
        currentFilter = name
        emitFilteredUsers()
    }

    /**
     * Restaura a copia de trabalho e o filtro para o estado inicial carregado
     * do repositorio, usando a fonte da verdade imutavel [originalUsers].
     */
    fun resetToInitialState() {
        workingUsers = originalUsers
        currentFilter = ""
        emitFilteredUsers()
    }

    /**
     * Deriva a lista exibida a partir da copia de trabalho [workingUsers] e do
     * filtro [currentFilter], sem nunca tocar na fonte da verdade.
     */
    private fun emitFilteredUsers() {
        val visibleUsers = if (currentFilter.isBlank()) {
            workingUsers
        } else {
            workingUsers.filter { it.name.contains(currentFilter, ignoreCase = true) }
        }
        mutableUserListUI.update {
            UserListUIState.OnSuccess(UserListUI(visibleUsers, currentFilter))
        }
    }


    companion object {
        private const val KEY_FILTER = "user_list_filter"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UserListViewModel(
                    repository = UserRepositoryMock(UserClientMock()),
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}


@Composable
fun UserListScreen(
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = viewModel(factory = UserListViewModel.factory)
) {
    // Carrega os usuarios uma unica vez quando a tela entra em composicao.
    LaunchedEffect(Unit) {
        viewModel.getUsers()
    }

    val uiState by viewModel.userListUIState.collectAsStateWithLifecycle()

    // A tela so conhece o estado e um unico canal de eventos (onAction).
    // A ViewModel nunca desce para as camadas internas de UI.
    UserListContent(
        modifier = modifier,
        uiState = uiState,
        onAction = viewModel::onEvent
    )
}

@Composable
fun UserListContent(
    modifier: Modifier = Modifier,
    uiState: UserListUIState,
    onAction: (UserAction) -> Unit
) {
    // O filtro vive dentro dos dados UserListUI, carregados tanto por
    // OnLoading quanto por OnSuccess, entao e derivado do estado atual.
    val filterText = when (uiState) {
        is UserListUIState.OnLoading -> uiState.data.filter
        is UserListUIState.OnSuccess -> uiState.data.filter
        else -> ""
    }

    Column(modifier = modifier.fillMaxSize()) {
        UserFilterBar(filterText = filterText, onAction = onAction)
        AddUserBar(onAction = onAction)
        Button(
            onClick = { onAction(UserAction.Reset) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = "Reset")
        }

        Button(
            onClick = { onAction(UserAction.Refresh) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = "Refresh")
        }

        when (uiState) {
            is UserListUIState.OnLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UserListUIState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhum usuario carregado",
                        modifier = Modifier.padding(16.dp)
                    )
                }

            }

            is UserListUIState.OnSuccess -> {
                UserList(users = uiState.data.users, onAction = onAction)
            }

            is UserListUIState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = uiState.message, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}


@OptIn(FlowPreview::class)
@Composable
fun UserFilterBar(filterText: String, onAction: (UserAction) -> Unit) {
    // Inicializa com o valor vindo da ViewModel (fonte da verdade do filtro).
    // rememberTextFieldState usa um Saver, entao tambem sobrevive a
    // mudanca de configuracao e a morte do processo.
    val queryState = rememberTextFieldState(filterText)

    // UI -> ViewModel: reage ao que o usuario digita e dispara o filtro.
    // - debounce: espera o usuario parar de digitar antes de filtrar.
    // - distinctUntilChanged: evita refiltrar quando o texto nao mudou.
    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }
            .debounce(300.milliseconds)
            .distinctUntilChanged()
            .collect { text ->
                onAction(UserAction.FilterByName(text))
            }
    }

    // ViewModel -> UI: quando o filtro muda na ViewModel (ex.: Reset),
    // sincroniza o campo. A guarda evita loop com o snapshotFlow acima.
    LaunchedEffect(filterText) {
        if (filterText != queryState.text.toString()) {
            queryState.setTextAndPlaceCursorAtEnd(filterText)
        }
    }

    OutlinedTextField(
        state = queryState,
        label = { Text(text = "Filtrar por nome") },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun AddUserBar(onAction: (UserAction) -> Unit) {
    // Estado do campo que sobrevive a mudanca de configuracao e a morte do processo.
    val nameState = rememberTextFieldState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            state = nameState,
            label = { Text(text = "Novo usuario") },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                val name = nameState.text.toString().trim()
                if (name.isNotBlank()) {
                    val newUser = User(
                        id = System.currentTimeMillis().toString(),
                        name = name
                    )
                    onAction(UserAction.AddUserOnClick(newUser))
                    nameState.clearText()
                }
            }
        ) {
            Text(text = "Adicionar")
        }
    }
}

@Composable
fun UserList(users: List<User>, onAction: (UserAction) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(users, key = { it.id }) { user ->
            UserListItem(
                user = user,
                onAction = onAction
            )
        }
    }
}

@Composable
fun UserListItem(
    user: User,
    onAction: (UserAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = user.name)
        Text(
            text = "Delete",
            modifier = Modifier
                .testTag("delete_${user.id}")
                .clickable {
                    onAction(UserAction.DeleteUserOnClick(user.id))
                }
        )
    }
}

data class User(
    val id: String = "1",
    val name: String = "John Doe"
)